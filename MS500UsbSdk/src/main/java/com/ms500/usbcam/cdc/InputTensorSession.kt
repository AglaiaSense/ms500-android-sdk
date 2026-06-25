/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.usbcam.cdc

import com.ms500.usbcam.api.internal.CdcConnectApiImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Single-shot input tensor capture session.
 * Receives metadata and file chunks after a download_input_tensor request.
 */

/**
 * Input tensor metadata.
 *
 * @property width Image width in pixels.
 * @property height Image height in pixels.
 * @property format Pixel format identifier.
 * @property size Total data size in bytes.
 */
data class InputTensorMetadata(
    /** Width in pixels. */
    val width: Int,
    /** Height in pixels. */
    val height: Int,
    /** Format identifier. */
    val format: Int,
    /** Total data size in bytes. */
    val size: Int
)

/**
 * Input tensor capture result.
 *
 * @property meta Metadata.
 * @property bytes Raw byte array.
 */
data class InputTensorCaptureResult(
    /** Metadata. */
    val meta: InputTensorMetadata,
    /** Raw JPEG byte array. */
    val bytes: ByteArray,
    /** Number of FILE_DATA chunks received. */
    val chunkCount: Int = 0,
    /** Delay from request start to the first FILE_DATA chunk. */
    val firstChunkDelayMs: Long = 0L
)

/**
 * Internal input tensor session manager.
 * Handles listener registration, file chunk reception, metadata parsing, timeout control, and JPG sanitization.
 */
internal class InputTensorSession(
    /** CDC connection implementation for adding/removing message listeners. */
    private val connection: CdcConnectApiImpl,
    /** Status callback. */
    private val onStatus: (String) -> Unit = {},
    /** Progress callback (received bytes, expected total bytes). */
    private val onProgress: (Int, Int) -> Unit = { _, _ -> }
) : CdcPortConnection.MessageListener {

    companion object {
        /** Maximum timeout for the first chunk in milliseconds. */
        private const val FIRST_CHUNK_TIMEOUT_MS = 30_000L

        /** Stall timeout in milliseconds. */
        private const val STALL_TIMEOUT_MS = 5_000L

        /** Minimum valid JPG size in bytes. */
        private const val MIN_VALID_JPG_BYTES = 100
    }

    /** Currently parsed metadata. */
    private var metadata: InputTensorMetadata? = null

    /** Reception buffer. */
    private var buffer = ByteArrayOutputStream()

    /** [CompletableDeferred] used to suspend until capture completes. */
    private var captureDeferred: CompletableDeferred<InputTensorCaptureResult>? = null

    /** Whether the listener is registered with the connection. */
    private var registered = false

    /** Whether the session is active. */
    private var active = false

    /** Timestamp when the request was started. */
    private var requestStartedAtMs = 0L

    /** Timestamp when the first chunk was received. */
    private var firstChunkAtMs = 0L

    /** Timestamp when the last chunk was received. */
    private var lastChunkAtMs = 0L

    /** Number of received JPG data chunks. */
    private var chunkCount = 0

    /** Starts the session: cleans up old state, initializes the buffer, and registers the listener. */
    fun start() {
        cleanup()
        metadata = null
        buffer = ByteArrayOutputStream()
        captureDeferred = CompletableDeferred()
        active = true
        requestStartedAtMs = 0L
        firstChunkAtMs = 0L
        lastChunkAtMs = 0L
        chunkCount = 0
        register()
    }

    /** Marks that the request has been sent, used for first-chunk timeout calculation. */
    fun markRequestStarted() {
        requestStartedAtMs = System.currentTimeMillis()
    }

    /**
     * Suspends until capture completes, supporting overall timeout, first-chunk timeout, and stall timeout.
     *
     * @param timeoutMs Overall wait timeout in milliseconds.
     * @return Capture result.
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
                fail("No data received within ${FIRST_CHUNK_TIMEOUT_MS}ms; make sure inference is enabled and IMX500 is producing input tensor data")
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

    /** Callback for structured messages: handles errors and metadata JSON. */
    override fun onMessage(raw: String, json: JSONObject?, error: String?, msgType: Int) {
        if (!active) return

        if (error != null && msgType == CdcProtocol.MSG_TYPE_FILE_DATA) {
            fail(error)
            return
        }

        if (msgType == CdcProtocol.MSG_TYPE_FILE_DATA && json != null) {
            applyMetadataIfPresent(json)
        }

        if (msgType == CdcProtocol.MSG_TYPE_TENSOR_EXPORT && json != null) {
            applyMetadataIfPresent(json)
        }
    }

    /** Callback for raw frames: distinguishes JSON responses from file chunks. */
    override fun onFrame(frame: CdcWireFormat.Frame) {
        if (!active) return

        when (frame.type) {
            CdcProtocol.MSG_TYPE_RESP_OK,
            CdcProtocol.MSG_TYPE_RESP_ERROR,
            CdcProtocol.MSG_TYPE_TENSOR_EXPORT -> handleJsonFrame(frame)

            CdcProtocol.MSG_TYPE_FILE_DATA -> handleFileChunk(frame.payload)
        }
    }

    /** Connection state change callback: marks failure on disconnect. */
    override fun onConnectionStateChanged(state: CdcPortConnection.ConnectionState) {
        if (!active) return
        if (state == CdcPortConnection.ConnectionState.DISCONNECTED) {
            fail("CDC disconnected during input tensor transfer")
        }
    }

    /** Cleans up session state and unregisters the listener. */
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

    /** Registers the message listener with the connection. */
    private fun register() {
        if (registered) return
        connection.addClientMessageListener(this)
        registered = true
    }

    /** Handles JSON response frames: parses cmd and resolves success or failure. */
    private fun handleJsonFrame(frame: CdcWireFormat.Frame) {
        val raw = frame.payload.toString(Charsets.UTF_8).trim()
        if (raw.isEmpty() || !raw.startsWith("{")) return

        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val cmd = json.optString("cmd")

        val status = json.optString("status")
        if (frame.type == CdcProtocol.MSG_TYPE_RESP_ERROR || status.equals("error", ignoreCase = true) || status.equals("fail", ignoreCase = true)) {
            val errorMessage = json.optString("msg").ifBlank { json.optString("message", "unknown") }
            fail(errorMessage)
            return
        }

        when (cmd) {
            "download_input_tensor" -> {
                if (!applyMetadataIfPresent(json)) {
                    val responseMessage = json.optString("msg").ifBlank { json.optString("message", "request accepted") }
                    emitStatus("Device response: $responseMessage")
                }
            }

            "input_tensor_data" -> {
                applyMetadataIfPresent(json)
            }
        }
    }

    /** Handles file chunks: tries to parse metadata JSON first, otherwise writes to buffer and checks completion. */
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
                if (elapsedMs > 0L) "First chunk received after ${formatSeconds(elapsedMs)}s"
                else "First chunk received"
            )
        }
        lastChunkAtMs = now
        chunkCount += 1

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

    /** Parses and saves metadata if the JSON contains it. */
    private fun applyMetadataIfPresent(json: JSONObject): Boolean {
        if (metadata != null) return true

        val cmd = json.optString("cmd")
        val size = json.optInt("size", 0)
        val hasTensorShape = json.has("width") || json.has("height") || json.has("format")
        val looksLikeMetadata = cmd == "input_tensor_data" || cmd == "download_input_tensor" || (size > 0 && hasTensorShape)
        if (!looksLikeMetadata || size <= 0 || !hasTensorShape) return false

        val meta = InputTensorMetadata(
            width = json.optInt("width", 512),
            height = json.optInt("height", 384),
            format = json.optInt("format", 0),
            size = size
        )
        metadata = meta
        emitStatus("Metadata: ${meta.size} bytes (${meta.width}x${meta.height}, format=${meta.format})")
        onProgress(0, meta.size)
        return true
    }

    /** Attempts to parse the payload as metadata JSON. */
    private fun parseMetadataPayload(payload: ByteArray): JSONObject? {
        val raw = payload.toString(Charsets.UTF_8).trim()
        if (!raw.startsWith("{") || !raw.endsWith("}")) return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    /** Emits a status message. */
    private fun emitStatus(message: String) {
        onStatus(message)
    }

    /** Marks the session as failed and completes the deferred exceptionally. */
    fun fail(message: String) {
        if (captureDeferred?.isCompleted == false) {
            captureDeferred?.completeExceptionally(IllegalStateException(message))
        }
        emitStatus(message)
    }

    /** Safely finalizes capture from the current buffer. */
    private fun tryCompleteFromCurrentBuffer() {
        runCatching { completeFromCurrentBuffer() }
            .onFailure { fail(it.message ?: "Failed to finalize JPG stream") }
    }

    /** Builds the final result from the current buffer and completes the deferred. */
    private fun completeFromCurrentBuffer() {
        val meta = metadata ?: InputTensorMetadata(width = 512, height = 384, format = 0, size = buffer.size())
        val rawBytes = buffer.toByteArray()
        val sanitizedBytes = sanitizeJpgBytes(rawBytes)
        if (captureDeferred?.isCompleted == false) {
            val firstChunkDelay = if (requestStartedAtMs > 0L && firstChunkAtMs > 0L) firstChunkAtMs - requestStartedAtMs else 0L
            captureDeferred?.complete(
                InputTensorCaptureResult(
                    meta = meta.copy(size = sanitizedBytes.size),
                    bytes = sanitizedBytes,
                    chunkCount = chunkCount,
                    firstChunkDelayMs = firstChunkDelay
                )
            )
        }
        emitStatus("JPG receive completed: ${sanitizedBytes.size} bytes in ${chunkCount} chunks")
    }

    /**
     * Crops raw byte array to keep only the valid JPG range from SOI (0xFFD8) to EOI (0xFFD9).
     *
     * @param rawBytes Raw received byte array.
     * @return Sanitized JPG byte array.
     * @throws IllegalStateException Thrown when valid JPG markers are missing or data is too short.
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

    /** Formats milliseconds as seconds with one decimal place. */
    private fun formatSeconds(ms: Long): String = String.format(Locale.US, "%.1f", ms / 1000.0)

    /** Checks whether the byte array ends with a JPG EOI marker. */
    private fun hasJpgEndMarker(bytes: ByteArray): Boolean {
        return bytes.size >= 2 && bytes[bytes.size - 2] == 0xFF.toByte() && bytes[bytes.size - 1] == 0xD9.toByte()
    }

    /** Finds the first occurrence of the target byte sequence. */
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

    /** Finds the last occurrence of the target byte sequence. */
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
