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

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * CDC sender.
 * Handles command encoding, transmission, timeout, and response matching.
 */
class CdcSender(private val cdcClient: CdcPortConnection) : CdcPortConnection.MessageListener {

    companion object {
        /** Log tag for the sender chain. */
        private const val TAG = "CdcSender"

        /** Default timeout for ordinary commands in milliseconds. */
        private const val DEFAULT_TIMEOUT_MS = 5000L

        /** Starting request ID for local pending table ordering and log tracing. */
        private const val REQUEST_ID_START = 1000
    }

    /** Main thread [Handler] for consistent callbacks and timeout handling. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Pending request table; responses are matched by cmd until the device supports transaction IDs. */
    private val pendingCallbacks = ConcurrentHashMap<Int, PendingRequest>()

    /** Monotonically increasing local request ID generator. */
    private val requestIdGenerator = AtomicInteger(REQUEST_ID_START)

    /** Dedicated inference report listener. */
    private var onInferenceReportListener: ((raw: String, json: JSONObject?) -> Unit)? = null

    /** Passthrough listener for sent JSON commands. */
    private var onSendListener: ((raw: String, cmd: String, msgType: Int) -> Unit)? = null

    /** Passthrough listener for all structured messages. */
    private var onMessageListener: ((raw: String, json: JSONObject?, error: String?, msgType: Int) -> Unit)? = null

    /** Connection state change listener. */
    private var onConnectionStateListener: ((state: CdcPortConnection.ConnectionState) -> Unit)? = null

    /** Lock guarding latest inference report replacement and dispatch state. */
    private val inferenceReportLock = Any()

    /** Whether an inference report dispatch is already queued on the main thread. */
    private val inferenceDispatchPending = AtomicBoolean(false)

    /** Latest inference report waiting to be delivered; older reports are intentionally overwritten. */
    @Volatile
    private var latestInferenceReport: InferenceReport? = null

    /** Number of old realtime detection reports dropped because a newer report arrived first. */
    private var droppedInferenceReports = 0L

    /** Last timestamp when the detection-drop summary was logged. */
    private var lastInferenceDropLogAtMs = 0L

    /**
     * A command request awaiting a response.
     *
     * @property requestId Local request ID.
     * @property cmd Command name used for response matching.
     * @property callback Callback invoked on completion or timeout.
     * @property timeoutHandler Timeout runnable handle.
     */
    private data class PendingRequest(
        /** Local request ID. */
        val requestId: Int,
        /** Command name. */
        val cmd: String,
        /** Result callback. */
        val callback: (raw: String, json: JSONObject?, error: String?) -> Unit,
        /** Timeout runnable. */
        val timeoutHandler: Runnable
    )

    /** Coalesced inference report waiting for main-thread delivery. */
    private data class InferenceReport(
        /** Raw response text. */
        val raw: String,
        /** Parsed JSON or null. */
        val json: JSONObject?,
        /** Error string or null. */
        val error: String?,
        /** CDC message type. */
        val msgType: Int
    )

    /** Unified response parsing result. */
    private data class ParseResult(
        /** Raw response text. */
        val raw: String,
        /** Parsed JSON or null. */
        val json: JSONObject?,
        /** Error string or null. */
        val error: String?
    )

    /** Registers itself with the CDC client during initialization to receive structured messages. */
    init {
        cdcClient.addMessageListener(this)
    }

    /**
     * Releases the sender and cancels all pending requests.
     * All incomplete callbacks are resolved with a "Connection closed" error to prevent indefinite waits.
     */
    fun release() {
        cdcClient.removeMessageListener(this)
        pendingCallbacks.values.forEach { request ->
            mainHandler.removeCallbacks(request.timeoutHandler)
            try {
                request.callback("", null, "Connection closed")
            } catch (e: Exception) {
                Log.e(TAG, "Error calling callback on release", e)
            }
        }
        pendingCallbacks.clear()
        onInferenceReportListener = null
        onSendListener = null
        onMessageListener = null
        onConnectionStateListener = null
        latestInferenceReport = null
        inferenceDispatchPending.set(false)
        droppedInferenceReports = 0L
        lastInferenceDropLogAtMs = 0L
    }

    /** Sets the inference report listener. */
    fun setOnInferenceReportListener(listener: ((raw: String, json: JSONObject?) -> Unit)?) {
        onInferenceReportListener = listener
    }

    /** Sets the sent JSON listener. */
    fun setOnSendListener(listener: ((raw: String, cmd: String, msgType: Int) -> Unit)?) {
        onSendListener = listener
    }

    /** Sets the all-messages listener. */
    fun setOnMessageListener(listener: ((raw: String, json: JSONObject?, error: String?, msgType: Int) -> Unit)?) {
        onMessageListener = listener
    }

    /** Sets the connection state listener. */
    fun setOnConnectionStateListener(listener: ((state: CdcPortConnection.ConnectionState) -> Unit)?) {
        onConnectionStateListener = listener
    }

    /** Forwards connection state changes to the upper layer. */
    override fun onConnectionStateChanged(state: CdcPortConnection.ConnectionState) {
        mainHandler.post {
            onConnectionStateListener?.invoke(state)
        }
    }

    /** The sender consumes structured messages only, not raw byte streams. */
    override fun onMessage(raw: String, json: JSONObject?, error: String?, msgType: Int) {
        if (CdcWireFormat.toMessageCategory(msgType) == CdcWireFormat.MessageCategory.INFERENCE_RESULT_REPORT) {
            enqueueLatestInferenceReport(raw, json, error, msgType)
            return
        }

        mainHandler.post {
            onMessageListener?.invoke(raw, json, error, msgType)

            if (error != null) {
                Log.e(TAG, "Received error: $error")
                return@post
            }

            if (json == null) {
                Log.w(TAG, "Received empty JSON")
                return@post
            }

            handleCommandResponse(raw, json, msgType)
        }
    }

    /** Delivers only the newest realtime inference report to avoid main-thread backlog. */
    private fun enqueueLatestInferenceReport(raw: String, json: JSONObject?, error: String?, msgType: Int) {
        var droppedOldReport = false
        val shouldPost = synchronized(inferenceReportLock) {
            droppedOldReport = latestInferenceReport != null
            latestInferenceReport = InferenceReport(raw, json, error, msgType)
            if (inferenceDispatchPending.get()) {
                false
            } else {
                inferenceDispatchPending.set(true)
                true
            }
        }
        if (droppedOldReport) {
            logInferenceDrop()
        }
        if (shouldPost) {
            mainHandler.post { drainLatestInferenceReport() }
        }
    }

    /** Logs a throttled summary when old realtime detection reports are overwritten. */
    private fun logInferenceDrop() {
        droppedInferenceReports += 1
        val now = System.currentTimeMillis()
        if (now - lastInferenceDropLogAtMs >= 1000L) {
            Log.w(TAG, "CDC detection dropped old frame count=$droppedInferenceReports")
            lastInferenceDropLogAtMs = now
        }
    }

    /** Drains the newest pending inference report and schedules another pass if a newer one arrived meanwhile. */
    private fun drainLatestInferenceReport() {
        val report = synchronized(inferenceReportLock) {
            val pending = latestInferenceReport
            latestInferenceReport = null
            inferenceDispatchPending.set(false)
            pending
        } ?: return

        onMessageListener?.invoke(report.raw, report.json, report.error, report.msgType)
        if (report.error != null) {
            Log.e(TAG, "Received inference report error: ${report.error}")
        } else {
            onInferenceReportListener?.invoke(report.raw, report.json)
        }

        val shouldPostAgain = synchronized(inferenceReportLock) {
            if (latestInferenceReport != null && !inferenceDispatchPending.get()) {
                inferenceDispatchPending.set(true)
                true
            } else {
                false
            }
        }
        if (shouldPostAgain) {
            mainHandler.post { drainLatestInferenceReport() }
        }
    }

    /**
     * Sends a regular JSON command with parameters supplied as a [Map].
     * Automatically adds the `cmd` field and selects the appropriate msgType.
     */
    fun sendCommand(
        cmd: String,
        params: Map<String, Any?> = emptyMap(),
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        callback: ((raw: String, json: JSONObject?, error: String?) -> Unit)? = null
    ) {
        val requestId = requestIdGenerator.incrementAndGet()
        val json = JSONObject()
        json.put("cmd", cmd)
        for ((key, value) in params) {
            if (value != null) {
                json.put(key, value)
            }
        }
        sendJsonCommand(requestId, cmd, json.toString(), timeoutMs, callback)
    }

    /** Sends a pre-constructed JSON command object. */
    fun sendCommand(
        cmd: String,
        json: JSONObject,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        callback: ((raw: String, json: JSONObject?, error: String?) -> Unit)? = null
    ) {
        val requestId = requestIdGenerator.incrementAndGet()
        json.put("cmd", cmd)
        sendJsonCommand(requestId, cmd, json.toString(), timeoutMs, callback)
    }

    /** Sends a register read command. */
    fun getRegister(
        addr: Int,
        size: Int,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        callback: ((String, JSONObject?, String?) -> Unit)? = null
    ) {
        val requestId = requestIdGenerator.incrementAndGet()
        val json = JSONObject().apply {
            put("cmd", "register_get")
            put("addr", addr)
            put("size", size)
        }
        sendTypedJsonCommand(requestId, "register_get", json.toString(), CdcProtocol.MSG_TYPE_REG_GET, timeoutMs, callback)
    }

    /** Sends a register write command. */
    fun setRegister(
        addr: Int,
        value: Int,
        size: Int,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        callback: ((String, JSONObject?, String?) -> Unit)? = null
    ) {
        val requestId = requestIdGenerator.incrementAndGet()
        val json = JSONObject().apply {
            put("cmd", "register_set")
            put("params", JSONObject().apply {
                put("addr", addr)
                put("value", value)
                put("size", size)
            })
        }
        sendTypedJsonCommand(requestId, "register_set", json.toString(), CdcProtocol.MSG_TYPE_REG_SET, timeoutMs, callback)
    }

    /** Matches responses by normalized cmd since the device does not yet provide stable transaction IDs. */
    private fun handleCommandResponse(raw: String, json: JSONObject, msgType: Int) {
        val candidateCmds = buildResponseCommandCandidates(json)
        if (candidateCmds.isEmpty()) {
            Log.w(TAG, "Response without resolvable cmd field: $raw")
            return
        }

        val matchedRequest = pendingCallbacks.values
            .filter { request -> candidateCmds.contains(request.cmd) }
            .minByOrNull { it.requestId }

        if (matchedRequest != null) {
            mainHandler.removeCallbacks(matchedRequest.timeoutHandler)
            pendingCallbacks.remove(matchedRequest.requestId)
            Log.i(
                TAG,
                "CDC command response received type=${CdcProtocol.getTypeName(msgType)} " +
                    "len=${raw.toByteArray(Charsets.UTF_8).size} cmd=${matchedRequest.cmd}"
            )
            val result = parseResponse(raw, json)
            matchedRequest.callback(result.raw, result.json, result.error)
        } else {
            Log.w(TAG, "Received response for unmatched request: candidates=$candidateCmds")
        }
    }

    /** Derives all possible command name candidates from the response JSON. */
    private fun buildResponseCommandCandidates(json: JSONObject): Set<String> {
        val candidates = linkedSetOf<String>()
        val responseCmd = json.optString("cmd", "").trim()

        if (responseCmd.isNotEmpty()) {
            candidates += CdcWireFormat.normalizeResponseCmd(responseCmd)
        }

        if (responseCmd.isEmpty() && json.has("mode") && json.has("batch_frame_count")) {
            candidates += "get_inference_send_mode"
        }

        return candidates
    }

    /** Collapses device responses into a unified success/failure result. */
    private fun parseResponse(raw: String, json: JSONObject): ParseResult {
        val ret = json.optString("ret", "")
        val status = json.optString("status", "")
        val msg = json.optString("msg", "")
        val resultCode = when {
            ret.isNotEmpty() -> ret
            status.isNotEmpty() -> status
            else -> ""
        }

        return when (resultCode.lowercase()) {
            "success", "ok" -> ParseResult(raw, json, null)
            "fail", "error" -> ParseResult(raw, null, msg.ifEmpty { "Command failed" })
            else -> {
                when {
                    msg.contains("ready", ignoreCase = true) || msg.contains("complete", ignoreCase = true) -> {
                        ParseResult(raw, json, null)
                    }
                    msg.contains("error", ignoreCase = true) -> {
                        ParseResult(raw, null, msg)
                    }
                    else -> ParseResult(raw, json, null)
                }
            }
        }
    }

    /** Selects msgType by command name and enters the unified send path. */
    private fun sendJsonCommand(
        requestId: Int,
        cmd: String,
        jsonStr: String,
        timeoutMs: Long,
        callback: ((raw: String, json: JSONObject?, error: String?) -> Unit)?
    ) {
        val msgType = CdcWireFormat.getMessageType(cmd)
        sendTypedJsonCommand(requestId, cmd, jsonStr, msgType, timeoutMs, callback)
    }

    /** Final dispatch for all commands: encodes frame, registers timeout, and sends via the underlying serial port. */
    private fun sendTypedJsonCommand(
        requestId: Int,
        cmd: String,
        jsonStr: String,
        msgType: Int,
        timeoutMs: Long,
        callback: ((raw: String, json: JSONObject?, error: String?) -> Unit)?
    ) {
        val frame = CdcWireFormat.buildFrame(msgType, jsonStr.toByteArray(Charsets.UTF_8))

        if (!cdcClient.isConnected()) {
            Log.e(TAG, "Cannot send command: CDC not connected")
            callback?.invoke("", null, "CDC not connected")
            return
        }

        if (callback != null) {
            val handler = Runnable {
                val removed = pendingCallbacks.remove(requestId)
                if (removed != null) {
                    Log.w(TAG, "Request timeout: cmd=$cmd, requestId=$requestId")
                    callback("", null, "Timeout after ${timeoutMs}ms")
                }
            }
            pendingCallbacks[requestId] = PendingRequest(requestId, cmd, callback, handler)
            mainHandler.postDelayed(handler, timeoutMs)
        }

        Log.d(
            TAG,
            "Sending JSON payload (${jsonStr.length} bytes, type=0x${msgType.toString(16).padStart(4, '0')}): $jsonStr"
        )
        onSendListener?.invoke(jsonStr, cmd, msgType)
        cdcClient.send(frame)
    }
}
