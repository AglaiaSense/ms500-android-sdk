package com.ms500.usbcam.cdc

import com.ms500.usbcam.api.internal.CdcConnectApiImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * 单次 input tensor 抓取会话。
 *
 * 对照 Python 示例：reset -> request -> receive
 *
 * Single-shot input tensor capture session.
 * Mirrors the Python flow: reset -> request -> receive.
 */

/**
 * Input tensor 元数据。
 *
 * @property width 图像宽度。 / Image width.
 * @property height 图像高度。 / Image height.
 * @property format 像素格式标识。 / Pixel format identifier.
 * @property size 预期总字节数。 / Expected total byte size.
 */
data class InputTensorMetadata(
    /** 宽度。 / Width in pixels. */
    val width: Int,
    /** 高度。 / Height in pixels. */
    val height: Int,
    /** 格式。 / Format identifier. */
    val format: Int,
    /** 数据总大小（字节）。 / Total data size in bytes. */
    val size: Int
)

/**
 * Input tensor 抓取结果。
 *
 * @property meta 元数据。 / Metadata.
 * @property bytes 原始字节数据。 / Raw byte data.
 */
data class InputTensorCaptureResult(
    /** 元数据。 / Metadata. */
    val meta: InputTensorMetadata,
    /** 原始字节数组。 / Raw byte array. */
    val bytes: ByteArray
)

/**
 * 内部使用的 input tensor 会话管理类。
 *
 * 负责注册监听器、接收文件分片、解析元数据、超时控制以及 JPG 数据裁剪。
 *
 * Internal input tensor session manager.
 * Handles listener registration, file chunk reception, metadata parsing, timeout control, and JPG sanitization.
 */
internal class InputTensorSession(
    /** CDC 连接实现，用于添加/移除消息监听器。 / CDC connection implementation for adding/removing message listeners. */
    private val connection: CdcConnectApiImpl,
    /** 状态回调。 / Status callback. */
    private val onStatus: (String) -> Unit = {},
    /** 进度回调（已接收字节, 预期总字节）。 / Progress callback (received bytes, expected total bytes). */
    private val onProgress: (Int, Int) -> Unit = { _, _ -> }
) : CdcPortConnection.MessageListener {

    companion object {
        /** 等待首包数据的最大超时（毫秒）。 / Maximum timeout for the first chunk in milliseconds. */
        private const val FIRST_CHUNK_TIMEOUT_MS = 15_000L

        /** 数据停滞超时（毫秒）。 / Stall timeout in milliseconds. */
        private const val STALL_TIMEOUT_MS = 5_000L

        /** 有效 JPG 的最小字节数。 / Minimum valid JPG size in bytes. */
        private const val MIN_VALID_JPG_BYTES = 100
    }

    /** 当前已解析出的元数据。 / Currently parsed metadata. */
    private var metadata: InputTensorMetadata? = null

    /** 接收数据缓冲区。 / Reception buffer. */
    private var buffer = ByteArrayOutputStream()

    /** 用于挂起协程等待抓取完成的 Deferred。 / [CompletableDeferred] used to suspend until capture completes. */
    private var captureDeferred: CompletableDeferred<InputTensorCaptureResult>? = null

    /** 是否已向 connection 注册监听器。 / Whether the listener is registered with the connection. */
    private var registered = false

    /** 会话是否处于活跃状态。 / Whether the session is active. */
    private var active = false

    /** 请求开始的时间戳（毫秒）。 / Timestamp when the request was started. */
    private var requestStartedAtMs = 0L

    /** 收到首包数据的时间戳（毫秒）。 / Timestamp when the first chunk was received. */
    private var firstChunkAtMs = 0L

    /** 收到最后一包数据的时间戳（毫秒）。 / Timestamp when the last chunk was received. */
    private var lastChunkAtMs = 0L

    /** 启动会话：清理旧状态、初始化缓冲区并注册监听器。 / Starts the session: cleans up old state, initializes the buffer, and registers the listener. */
    fun start() {
        cleanup()
        metadata = null
        buffer = ByteArrayOutputStream()
        captureDeferred = CompletableDeferred()
        active = true
        requestStartedAtMs = 0L
        firstChunkAtMs = 0L
        lastChunkAtMs = 0L
        register()
    }

    /** 标记请求已发出，用于计算首包超时。 / Marks that the request has been sent, used for first-chunk timeout calculation. */
    fun markRequestStarted() {
        requestStartedAtMs = System.currentTimeMillis()
    }

    /**
     * 挂起等待抓取完成，支持总超时、首包超时和停滞超时。
     *
     * @param timeoutMs 整体等待超时（毫秒）。 / Overall wait timeout in milliseconds.
     * @return 抓取结果。 / Capture result.
     */
    suspend fun awaitCapture(timeoutMs: Long): InputTensorCaptureResult {
        val deferred = captureDeferred ?: error("Input tensor session not started")
        val deadline = System.currentTimeMillis() + timeoutMs

        while (active && !deferred.isCompleted) {
            val now = System.currentTimeMillis()
            if (now >= deadline) {
                fail("Timeout after ${timeoutMs}ms")
                break
            }

            if (requestStartedAtMs != 0L && firstChunkAtMs == 0L && now - requestStartedAtMs > FIRST_CHUNK_TIMEOUT_MS) {
                fail("No data received within ${FIRST_CHUNK_TIMEOUT_MS}ms")
                break
            }

            if (firstChunkAtMs != 0L && now - lastChunkAtMs > STALL_TIMEOUT_MS) {
                val bytes = buffer.toByteArray()
                if (bytes.size > MIN_VALID_JPG_BYTES && hasJpgEndMarker(bytes)) {
                    completeFromCurrentBuffer()
                } else {
                    fail("Data reception stalled at ${bytes.size} bytes")
                }
                break
            }

            delay(20)
        }

        return deferred.await()
    }

    /** 收到结构化消息时的回调：处理错误和元数据 JSON。 / Callback for structured messages: handles errors and metadata JSON. */
    override fun onMessage(raw: String, json: JSONObject?, error: String?, msgType: Int) {
        if (!active) return

        if (error != null && msgType == CdcProtocol.MSG_TYPE_FILE_DATA) {
            fail(error)
            return
        }

        if (msgType == CdcProtocol.MSG_TYPE_FILE_DATA && json != null) {
            applyMetadataIfPresent(json)
        }
    }

    /** 收到原始帧时的回调：区分 JSON 响应与文件分片。 / Callback for raw frames: distinguishes JSON responses from file chunks. */
    override fun onFrame(frame: CdcProtocol.Frame) {
        if (!active) return

        when (frame.type) {
            CdcProtocol.MSG_TYPE_RESP_OK,
            CdcProtocol.MSG_TYPE_RESP_ERROR -> handleJsonFrame(frame)

            CdcProtocol.MSG_TYPE_FILE_DATA -> handleFileChunk(frame.payload)
        }
    }

    /** 连接状态变化回调：断连时标记失败。 / Connection state change callback: marks failure on disconnect. */
    override fun onConnectionStateChanged(state: CdcPortConnection.ConnectionState) {
        if (!active) return
        if (state == CdcPortConnection.ConnectionState.DISCONNECTED) {
            fail("CDC disconnected during input tensor transfer")
        }
    }

    /** 清理会话状态并注销监听器。 / Cleans up session state and unregisters the listener. */
    fun cleanup() {
        active = false
        if (registered) {
            connection.removeClientMessageListener(this)
            registered = false
        }
        metadata = null
        buffer.reset()
        captureDeferred = null
    }

    /** 向 connection 注册消息监听器。 / Registers the message listener with the connection. */
    private fun register() {
        if (registered) return
        connection.addClientMessageListener(this)
        registered = true
    }

    /** 处理 JSON 响应帧：解析命令并根据状态决定成功或失败。 / Handles JSON response frames: parses cmd and resolves success or failure. */
    private fun handleJsonFrame(frame: CdcProtocol.Frame) {
        val raw = frame.payload.toString(Charsets.UTF_8).trim()
        if (raw.isEmpty() || !raw.startsWith("{")) return

        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val cmd = json.optString("cmd")

        if (frame.type == CdcProtocol.MSG_TYPE_RESP_ERROR || json.optString("status") == "error") {
            fail(json.optString("message").ifBlank { json.optString("msg", "unknown") })
            return
        }

        when (cmd) {
            "get_input_tensor_jpg_via_cdc" -> {
                emitStatus(json.optString("message").ifBlank { json.optString("msg", "request accepted") })
            }

            "input_tensor_data" -> {
                applyMetadataIfPresent(json)
            }
        }
    }

    /** 处理文件分片：先尝试解析元数据 JSON，否则写入缓冲区并检查完成条件。 / Handles file chunks: tries to parse metadata JSON first, otherwise writes to buffer and checks completion. */
    private fun handleFileChunk(payload: ByteArray) {
        if (payload.isEmpty()) return

        parseMetadataPayload(payload)?.let { metadataJson ->
            applyMetadataIfPresent(metadataJson)
            return
        }

        val now = System.currentTimeMillis()
        if (firstChunkAtMs == 0L) {
            firstChunkAtMs = now
            val elapsedMs = if (requestStartedAtMs > 0L) now - requestStartedAtMs else 0L
            emitStatus(
                if (elapsedMs > 0L) "first chunk received after ${elapsedMs}ms"
                else "first chunk received"
            )
        }
        lastChunkAtMs = now

        buffer.write(payload, 0, payload.size)
        val received = buffer.size()
        val expectedSize = metadata?.size ?: 0
        onProgress(received, expectedSize)

        if (expectedSize > 0 && received >= expectedSize) {
            tryCompleteFromCurrentBuffer()
            return
        }

        val bytes = buffer.toByteArray()
        if (expectedSize <= 0 && bytes.size > MIN_VALID_JPG_BYTES && hasJpgEndMarker(bytes)) {
            tryCompleteFromCurrentBuffer()
        }
    }

    /** 如果 JSON 中包含元数据信息，则解析并保存。 / Parses and saves metadata if the JSON contains it. */
    private fun applyMetadataIfPresent(json: JSONObject) {
        if (metadata != null) return

        val cmd = json.optString("cmd")
        val size = json.optInt("size", 0)
        val hasTensorShape = json.has("width") || json.has("height") || json.has("format")
        val looksLikeMetadata = cmd == "input_tensor_data" || (size > 0 && hasTensorShape)
        if (!looksLikeMetadata) return

        if (size <= 0) {
            fail("Expected size is 0")
            return
        }

        val meta = InputTensorMetadata(
            width = json.optInt("width", 512),
            height = json.optInt("height", 384),
            format = json.optInt("format", 0),
            size = size
        )
        metadata = meta
        emitStatus("metadata ${meta.size} bytes (${meta.width}x${meta.height})")
        onProgress(0, meta.size)
    }

    /** 尝试将 payload 解析为元数据 JSON。 / Attempts to parse the payload as metadata JSON. */
    private fun parseMetadataPayload(payload: ByteArray): JSONObject? {
        val raw = payload.toString(Charsets.UTF_8).trim()
        if (!raw.startsWith("{") || !raw.endsWith("}")) return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    /** 发射状态消息。 / Emits a status message. */
    private fun emitStatus(message: String) {
        onStatus(message)
    }

    /** 将会话标记为失败并完成 deferred。 / Marks the session as failed and completes the deferred exceptionally. */
    fun fail(message: String) {
        if (captureDeferred?.isCompleted == false) {
            captureDeferred?.completeExceptionally(IllegalStateException(message))
        }
        emitStatus(message)
    }

    /** 安全地完成当前缓冲区数据。 / Safely finalizes capture from the current buffer. */
    private fun tryCompleteFromCurrentBuffer() {
        runCatching { completeFromCurrentBuffer() }
            .onFailure { fail(it.message ?: "Failed to finalize JPG stream") }
    }

    /** 从当前缓冲区构建最终结果并完成 deferred。 / Builds the final result from the current buffer and completes the deferred. */
    private fun completeFromCurrentBuffer() {
        val meta = metadata ?: InputTensorMetadata(width = 512, height = 384, format = 0, size = buffer.size())
        val rawBytes = buffer.toByteArray()
        val sanitizedBytes = sanitizeJpgBytes(rawBytes)
        if (captureDeferred?.isCompleted == false) {
            captureDeferred?.complete(InputTensorCaptureResult(meta.copy(size = sanitizedBytes.size), sanitizedBytes))
        }
        emitStatus("receive complete")
    }

    /**
     * 裁剪原始字节数组，保留从 SOI (0xFFD8) 到 EOI (0xFFD9) 的合法 JPG 区间。
     *
     * @param rawBytes 原始接收到的字节数组。 / Raw received byte array.
     * @return 裁剪后的 JPG 字节数组。 / Sanitized JPG byte array.
     * @throws IllegalStateException 当找不到合法 JPG 标记或数据过短时抛出。 / Thrown when valid JPG markers are missing or data is too short.
     */
    private fun sanitizeJpgBytes(rawBytes: ByteArray): ByteArray {
        val startIndex = rawBytes.indexOfSequence(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        if (startIndex < 0) {
            throw IllegalStateException("No JPG start marker found")
        }

        val endIndex = rawBytes.lastIndexOfSequence(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        val cropped = if (endIndex >= startIndex) {
            rawBytes.copyOfRange(startIndex, endIndex + 2)
        } else {
            rawBytes.copyOfRange(startIndex, rawBytes.size) + byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        }
        if (cropped.size <= MIN_VALID_JPG_BYTES) {
            throw IllegalStateException("Insufficient JPG data (${cropped.size} bytes)")
        }
        return cropped
    }

    /** 检查字节数组是否以 JPG EOI 标记结尾。 / Checks whether the byte array ends with a JPG EOI marker. */
    private fun hasJpgEndMarker(bytes: ByteArray): Boolean {
        return bytes.size >= 2 && bytes[bytes.size - 2] == 0xFF.toByte() && bytes[bytes.size - 1] == 0xD9.toByte()
    }

    /** 在字节数组中查找目标序列的首次出现位置。 / Finds the first occurrence of the target byte sequence. */
    private fun ByteArray.indexOfSequence(target: ByteArray): Int {
        if (target.isEmpty() || this.size < target.size) return -1
        for (i in 0..this.size - target.size) {
            var matched = true
            for (j in target.indices) {
                if (this[i + j] != target[j]) {
                    matched = false
                    break
                }
            }
            if (matched) return i
        }
        return -1
    }

    /** 在字节数组中查找目标序列的最后一次出现位置。 / Finds the last occurrence of the target byte sequence. */
    private fun ByteArray.lastIndexOfSequence(target: ByteArray): Int {
        if (target.isEmpty() || this.size < target.size) return -1
        for (i in this.size - target.size downTo 0) {
            var matched = true
            for (j in target.indices) {
                if (this[i + j] != target[j]) {
                    matched = false
                    break
                }
            }
            if (matched) return i
        }
        return -1
    }
}
