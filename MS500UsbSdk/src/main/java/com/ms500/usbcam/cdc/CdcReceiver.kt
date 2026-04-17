package com.ms500.usbcam.cdc

import android.util.Log
import org.json.JSONObject

/**
 * CDC 接收器。
 *
 * 专门负责字节流解码、JSON 解析和消息分类。
 */
class CdcReceiver {
    companion object {
        /** 接收链路日志 tag。 */
        private const val TAG = "CdcReceiver"
    }

    /** 接收器向外广播 frame 和结构化消息的监听接口。 */
    interface Listener {
        /** 收到一帧完整协议数据时回调。 */
        fun onFrame(frame: CdcProtocol.Frame) {}

        /** 收到完成分类的消息时回调。 */
        fun onMessage(message: CdcProtocol.IncomingMessage) {}
    }

    /** 唯一流式解码器实例，负责处理串口拆包和粘包。 */
    private val decoder = CdcProtocol.Decoder()

    /** 当前已注册的接收监听器集合。 */
    private val listeners = LinkedHashSet<Listener>()

    /** 注册一个新的接收监听器。 */
    fun addListener(listener: Listener) {
        listeners += listener
    }

    /** 移除一个已注册的接收监听器。 */
    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    /** 重置内部解码状态，通常在断连后调用。 */
    fun reset() {
        decoder.reset()
    }

    /**
     * 处理新收到的串口字节流，并分发已解出的完整帧。
     *
     * @return 本次从缓冲区解出的所有完整帧。
     */
    fun onBytesReceived(data: ByteArray): List<CdcProtocol.Frame> {
        decoder.append(data)
        val frames = decoder.decodeAvailable()
        for (frame in frames) {
            dispatchFrame(frame)
        }
        return frames
    }

    /** 先广播原始 frame，再按协议类型进入 JSON / file / unknown 三条分支。 */
    private fun dispatchFrame(frame: CdcProtocol.Frame) {
        Log.d(TAG, "dispatchFrame: ${CdcProtocol.getTypeName(frame.type)}, payload=${frame.payload.size} bytes")
        listeners.forEach { it.onFrame(frame) }

        val category = CdcProtocol.toMessageCategory(frame.type)
        when (category) {
            CdcProtocol.MessageCategory.FILE_DATA -> dispatchFileData(frame, category)
            CdcProtocol.MessageCategory.UNKNOWN -> dispatchUnknown(frame)
            else -> dispatchJson(frame, category)
        }
    }

    /** 文件通道默认按二进制看待，只在可解析时顺便附带 JSON 视图给上层调试。 */
    private fun dispatchFileData(frame: CdcProtocol.Frame, category: CdcProtocol.MessageCategory) {
        val raw = try {
            String(frame.payload, Charsets.UTF_8).trim()
        } catch (_: Exception) {
            "[Binary data: ${frame.payload.size} bytes]"
        }
        val json = if (raw.startsWith("{")) runCatching { JSONObject(raw) }.getOrNull() else null
        listeners.forEach {
            it.onMessage(
                CdcProtocol.IncomingMessage(
                    raw = raw,
                    json = json,
                    error = null,
                    msgType = frame.type,
                    category = category
                )
            )
        }
    }

    /** 未知类型帧按原始文本透传，同时补充错误说明。 */
    private fun dispatchUnknown(frame: CdcProtocol.Frame) {
        CdcProtocol.logUnknownType(frame.type, frame.payload)
        val raw = String(frame.payload, Charsets.UTF_8)
        listeners.forEach {
            it.onMessage(
                CdcProtocol.IncomingMessage(
                    raw = raw,
                    json = null,
                    error = "Unknown frame type: ${CdcProtocol.getTypeName(frame.type)}",
                    msgType = frame.type,
                    category = CdcProtocol.MessageCategory.UNKNOWN
                )
            )
        }
    }

    /** JSON 通道统一在这里做完整性检查和解析，避免业务层重复处理字符串。 */
    private fun dispatchJson(frame: CdcProtocol.Frame, category: CdcProtocol.MessageCategory) {
        val raw = String(frame.payload, Charsets.UTF_8).trim()
        if (raw.isEmpty()) {
            Log.w(TAG, "Empty JSON payload")
            return
        }

        val lastBrace = raw.lastIndexOf('}')
        val lastBracket = raw.lastIndexOf(']')
        val lastValidEnd = maxOf(lastBrace, lastBracket)
        val isComplete = lastValidEnd == raw.length - 1

        Log.d(
            TAG,
            "JSON payload (${raw.length} bytes, type=${CdcProtocol.getTypeName(frame.type)}): $raw"
        )

        if (!isComplete) {
            Log.w(TAG, "JSON appears incomplete: ends at pos $lastValidEnd, length=${raw.length}")
            Log.w(TAG, "JSON tail: ...${raw.substring(maxOf(0, raw.length - 100))}")
        }

        try {
            val json = JSONObject(raw)
            CdcProtocol.logClassification(frame.type, category, json)
            listeners.forEach {
                it.onMessage(
                    CdcProtocol.IncomingMessage(
                        raw = raw,
                        json = json,
                        error = null,
                        msgType = frame.type,
                        category = category
                    )
                )
            }
        } catch (e: Exception) {
            val err = "JSON Parse Error: ${e.message}"
            Log.e(TAG, err)
            Log.e(TAG, "Failed JSON length: ${raw.length}")
            Log.e(TAG, "Failed JSON tail: ...${raw.substring(maxOf(0, raw.length - 50))}")
            listeners.forEach {
                it.onMessage(
                    CdcProtocol.IncomingMessage(
                        raw = raw,
                        json = null,
                        error = err,
                        msgType = frame.type,
                        category = category
                    )
                )
            }
        }
    }
}
