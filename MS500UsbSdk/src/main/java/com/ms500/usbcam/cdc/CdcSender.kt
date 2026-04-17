package com.ms500.usbcam.cdc

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * CDC 发送器。
 *
 * 负责命令编码、发送、超时和响应匹配。
 *
 * CDC sender.
 * Handles command encoding, transmission, timeout, and response matching.
 */
class CdcSender(private val cdcClient: CdcPortConnection) : CdcPortConnection.MessageListener {

    companion object {
        /** 发送链路日志 tag。 / Log tag for the sender chain. */
        private const val TAG = "CdcSender"

        /** 普通命令默认超时时间（毫秒）。 / Default timeout for ordinary commands in milliseconds. */
        private const val DEFAULT_TIMEOUT_MS = 5000L

        /** 请求 ID 起始值，仅用于本地挂起表排序和排查日志。 / Starting request ID for local pending table ordering and log tracing. */
        private const val REQUEST_ID_START = 1000
    }

    /** 主线程 handler，用于统一回调线程和超时处理。 / Main thread [Handler] for consistent callbacks and timeout handling. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 当前挂起请求表；设备侧没有事务号前，仍按 cmd 匹配响应。 / Pending request table; responses are matched by cmd until the device supports transaction IDs. */
    private val pendingCallbacks = ConcurrentHashMap<Int, PendingRequest>()

    /** 单调递增的本地请求 ID 生成器。 / Monotonically increasing local request ID generator. */
    private val requestIdGenerator = AtomicInteger(REQUEST_ID_START)

    /** 推理上报专用监听器。 / Dedicated inference report listener. */
    private var onInferenceReportListener: ((raw: String, json: JSONObject?) -> Unit)? = null

    /** 所有结构化消息的透传监听器。 / Passthrough listener for all structured messages. */
    private var onMessageListener: ((raw: String, json: JSONObject?, error: String?, msgType: Int) -> Unit)? = null

    /** 连接状态变化监听器。 / Connection state change listener. */
    private var onConnectionStateListener: ((state: CdcPortConnection.ConnectionState) -> Unit)? = null

    /**
     * 一个等待响应的命令请求。 / A command request awaiting a response.
     *
     * @property requestId 本地请求编号。 / Local request ID.
     * @property cmd 用于响应匹配的命令名。 / Command name used for response matching.
     * @property callback 命令完成或超时后的回调。 / Callback invoked on completion or timeout.
     * @property timeoutHandler 超时任务句柄。 / Timeout runnable handle.
     */
    private data class PendingRequest(
        /** 本地请求 ID。 / Local request ID. */
        val requestId: Int,
        /** 命令名。 / Command name. */
        val cmd: String,
        /** 结果回调。 / Result callback. */
        val callback: (raw: String, json: JSONObject?, error: String?) -> Unit,
        /** 超时 runnable。 / Timeout runnable. */
        val timeoutHandler: Runnable
    )

    /** 统一的响应解析结果。 / Unified response parsing result. */
    private data class ParseResult(
        /** 原始响应文本。 / Raw response text. */
        val raw: String,
        /** 解析后的 JSON。 / Parsed JSON or null. */
        val json: JSONObject?,
        /** 错误信息。 / Error string or null. */
        val error: String?
    )

    /** 初始化时把自身注册到 CDC 客户端，接收结构化消息。 / Registers itself with the CDC client during initialization to receive structured messages. */
    init {
        cdcClient.addMessageListener(this)
    }

    /**
     * 释放发送器并终止所有挂起请求。
     *
     * 所有未完成回调都会以连接关闭结束，避免调用方永久等待。
     *
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
        onMessageListener = null
        onConnectionStateListener = null
    }

    /** 设置推理上报监听器。 / Sets the inference report listener. */
    fun setOnInferenceReportListener(listener: ((raw: String, json: JSONObject?) -> Unit)?) {
        onInferenceReportListener = listener
    }

    /** 设置全量消息监听器。 / Sets the all-messages listener. */
    fun setOnMessageListener(listener: ((raw: String, json: JSONObject?, error: String?, msgType: Int) -> Unit)?) {
        onMessageListener = listener
    }

    /** 设置连接状态监听器。 / Sets the connection state listener. */
    fun setOnConnectionStateListener(listener: ((state: CdcPortConnection.ConnectionState) -> Unit)?) {
        onConnectionStateListener = listener
    }

    /** 将客户端的连接状态变化转发给上层。 / Forwards connection state changes to the upper layer. */
    override fun onConnectionStateChanged(state: CdcPortConnection.ConnectionState) {
        mainHandler.post {
            onConnectionStateListener?.invoke(state)
        }
    }

    /** Sender 只消费结构化消息，不直接处理原始字节流。 / The sender consumes structured messages only, not raw byte streams. */
    override fun onMessage(raw: String, json: JSONObject?, error: String?, msgType: Int) {
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

            when (CdcProtocol.toMessageCategory(msgType)) {
                CdcProtocol.MessageCategory.GAZE_DATA_REPORT -> onInferenceReportListener?.invoke(raw, json)
                else -> handleCommandResponse(raw, json)
            }
        }
    }

    /**
     * 发送普通 JSON 命令，参数以 Map 形式传入。
     *
     * 该方法会自动补齐 `cmd` 字段并选择对应 msgType。
     *
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

    /** 发送已经构造好的 JSON 命令对象。 / Sends a pre-constructed JSON command object. */
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

    /** 发送寄存器读取命令。 / Sends a register read command. */
    fun getRegister(
        addr: Int,
        size: Int,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        callback: ((String, JSONObject?, String?) -> Unit)? = null
    ) {
        val requestId = requestIdGenerator.incrementAndGet()
        val json = JSONObject().apply {
            put("addr", addr)
            put("size", size)
        }
        sendTypedJsonCommand(requestId, "reg_get", json.toString(), CdcProtocol.MSG_TYPE_REG_GET, timeoutMs, callback)
    }

    /** 发送寄存器写入命令。 / Sends a register write command. */
    fun setRegister(
        addr: Int,
        value: Int,
        size: Int,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        callback: ((String, JSONObject?, String?) -> Unit)? = null
    ) {
        val requestId = requestIdGenerator.incrementAndGet()
        val json = JSONObject().apply {
            put("addr", addr)
            put("value", value)
            put("size", size)
        }
        sendTypedJsonCommand(requestId, "reg_set", json.toString(), CdcProtocol.MSG_TYPE_REG_SET, timeoutMs, callback)
    }

    /** 设备侧暂时没有稳定事务号，短期内仍按 cmd 归一化后匹配响应。 / Matches responses by normalized cmd since the device does not yet provide stable transaction IDs. */
    private fun handleCommandResponse(raw: String, json: JSONObject) {
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
            val result = parseResponse(json)
            matchedRequest.callback(result.raw, result.json, result.error)
        } else {
            Log.w(TAG, "Received response for unmatched request: candidates=$candidateCmds")
        }
    }

    /** 从响应 JSON 中推导所有可能的命令名候选。 / Derives all possible command name candidates from the response JSON. */
    private fun buildResponseCommandCandidates(json: JSONObject): Set<String> {
        val candidates = linkedSetOf<String>()
        val responseCmd = json.optString("cmd", "").trim()

        if (responseCmd.isNotEmpty()) {
            candidates += CdcProtocol.normalizeResponseCmd(responseCmd)
        }

        if (responseCmd.isEmpty() && json.has("mode") && json.has("batch_frame_count")) {
            candidates += "get_result_send_mode"
        }

        return candidates
    }

    /** 将设备响应统一折叠成成功/失败结果。 / Collapses device responses into a unified success/failure result. */
    private fun parseResponse(json: JSONObject): ParseResult {
        val ret = json.optString("ret", "")
        val status = json.optString("status", "")
        val msg = json.optString("msg", "")
        val resultCode = when {
            ret.isNotEmpty() -> ret
            status.isNotEmpty() -> status
            else -> ""
        }

        return when (resultCode.lowercase()) {
            "success", "ok" -> ParseResult(json.toString(), json, null)
            "fail", "error" -> ParseResult(json.toString(), null, msg.ifEmpty { "Command failed" })
            else -> {
                when {
                    msg.contains("ready", ignoreCase = true) || msg.contains("complete", ignoreCase = true) -> {
                        ParseResult(json.toString(), json, null)
                    }
                    msg.contains("error", ignoreCase = true) -> {
                        ParseResult(json.toString(), null, msg)
                    }
                    else -> ParseResult(json.toString(), json, null)
                }
            }
        }
    }

    /** 根据命令名选择 msgType，再走统一发送入口。 / Selects msgType by command name and enters the unified send path. */
    private fun sendJsonCommand(
        requestId: Int,
        cmd: String,
        jsonStr: String,
        timeoutMs: Long,
        callback: ((raw: String, json: JSONObject?, error: String?) -> Unit)?
    ) {
        val msgType = CdcProtocol.getMessageType(cmd)
        sendTypedJsonCommand(requestId, cmd, jsonStr, msgType, timeoutMs, callback)
    }

    /** 所有命令最终都会落到这里：编码 frame、登记超时、再交给底层串口发送。 / Final dispatch for all commands: encodes frame, registers timeout, and sends via the underlying serial port. */
    private fun sendTypedJsonCommand(
        requestId: Int,
        cmd: String,
        jsonStr: String,
        msgType: Int,
        timeoutMs: Long,
        callback: ((raw: String, json: JSONObject?, error: String?) -> Unit)?
    ) {
        val frame = CdcProtocol.buildFrame(msgType, jsonStr.toByteArray(Charsets.UTF_8))

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
        cdcClient.send(frame)
    }
}
