package com.ms500.usbcam.cdc

import com.ms500.usbcam.api.internal.CdcConnectApiImpl
import com.ms500.usbcam.cdc.CdcProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * 单次 input tensor 抓取会话。
 *
 * 对照 Python 示例：
 * reset -> request -> receive
 */
data class InputTensorMetadata(
    val width: Int,
    val height: Int,
    val format: Int,
    val size: Int
)

data class InputTensorCaptureResult(
    val meta: InputTensorMetadata,
    val bytes: ByteArray
)

internal class InputTensorSession(
    private val connection: CdcConnectApiImpl,
    private val onStatus: (String) -> Unit = {},
    private val onProgress: (Int, Int) -> Unit = { _, _ -> }
) : CdcPortConnection.MessageListener {

    companion object {
        private const val FIRST_CHUNK_TIMEOUT_MS = 15_000L
        private const val STALL_TIMEOUT_MS = 5_000L
        private const val MIN_VALID_JPG_BYTES = 100
    }

    private var metadata: InputTensorMetadata? = null
    private var buffer = ByteArrayOutputStream()
    private var captureDeferred: CompletableDeferred<InputTensorCaptureResult>? = null
    private var registered = false
    private var active = false
    private var requestStartedAtMs = 0L
    private var firstChunkAtMs = 0L
    private var lastChunkAtMs = 0L

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

    fun markRequestStarted() {
        requestStartedAtMs = System.currentTimeMillis()
    }

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

    override fun onFrame(frame: CdcProtocol.Frame) {
        if (!active) return

        when (frame.type) {
            CdcProtocol.MSG_TYPE_RESP_OK,
            CdcProtocol.MSG_TYPE_RESP_ERROR -> handleJsonFrame(frame)

            CdcProtocol.MSG_TYPE_FILE_DATA -> handleFileChunk(frame.payload)
        }
    }

    override fun onConnectionStateChanged(state: CdcPortConnection.ConnectionState) {
        if (!active) return
        if (state == CdcPortConnection.ConnectionState.DISCONNECTED) {
            fail("CDC disconnected during input tensor transfer")
        }
    }

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

    private fun register() {
        if (registered) return
        connection.addClientMessageListener(this)
        registered = true
    }

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

    private fun parseMetadataPayload(payload: ByteArray): JSONObject? {
        val raw = payload.toString(Charsets.UTF_8).trim()
        if (!raw.startsWith("{") || !raw.endsWith("}")) return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun emitStatus(message: String) {
        onStatus(message)
    }

    fun fail(message: String) {
        if (captureDeferred?.isCompleted == false) {
            captureDeferred?.completeExceptionally(IllegalStateException(message))
        }
        emitStatus(message)
    }

    private fun tryCompleteFromCurrentBuffer() {
        runCatching { completeFromCurrentBuffer() }
            .onFailure { fail(it.message ?: "Failed to finalize JPG stream") }
    }

    private fun completeFromCurrentBuffer() {
        val meta = metadata ?: InputTensorMetadata(width = 512, height = 384, format = 0, size = buffer.size())
        val rawBytes = buffer.toByteArray()
        val sanitizedBytes = sanitizeJpgBytes(rawBytes)
        if (captureDeferred?.isCompleted == false) {
            captureDeferred?.complete(InputTensorCaptureResult(meta.copy(size = sanitizedBytes.size), sanitizedBytes))
        }
        emitStatus("receive complete")
    }

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

    private fun hasJpgEndMarker(bytes: ByteArray): Boolean {
        return bytes.size >= 2 && bytes[bytes.size - 2] == 0xFF.toByte() && bytes[bytes.size - 1] == 0xD9.toByte()
    }

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













