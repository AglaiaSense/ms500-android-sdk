package com.ms500.usbcam.cdc

import android.util.Log
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * CDC 协议定义。
 *
 * 帧格式: [MAGIC(2字节)][LEN(2字节)][TYPE(2字节)][PAYLOAD]
 * 字节序: 小端 (Little Endian)
 *
 * CDC protocol definitions.
 * Frame format: [MAGIC(2 bytes)][LEN(2 bytes)][TYPE(2 bytes)][PAYLOAD]
 * Byte order: Little Endian.
 */
object CdcProtocol {
    /** 协议层解析日志使用的 tag。 / Log tag for protocol parsing. */
    private const val TAG = "CdcProtocol"

    /** 消息分类日志使用的独立 tag，便于单独过滤。 / Dedicated log tag for message classification. */
    private const val LOG_TAG = "CdcMessage"

    /** 是否输出消息分类明细日志。 / Whether to log classification details. */
    private const val LOG_CLASSIFICATION_DETAILS = true

    /** 帧头 magic 第 1 个字节。 / First magic byte of the frame header. */
    private const val MAGIC_0 = 0x55.toByte()

    /** 帧头 magic 第 2 个字节。 / Second magic byte of the frame header. */
    private const val MAGIC_1 = 0xAA.toByte()

    /** 固定帧头长度: magic(2) + len(2) + type(2)。 / Fixed header size in bytes. */
    private const val HEADER_SIZE = 6

    /** 单帧允许的最大 payload，避免异常长度撑爆缓冲区。 / Maximum allowed payload length per frame. */
    private const val MAX_PAYLOAD_LEN = 60 * 1024

    /** 参数读取请求/响应类型。 / Parameter read request/response type. */
    const val MSG_TYPE_PARAM_GET = 0x0001

    /** 参数设置请求/响应类型。 / Parameter set request/response type. */
    const val MSG_TYPE_PARAM_SET = 0x0002

    /** 寄存器读取请求/响应类型。 / Register read request/response type. */
    const val MSG_TYPE_REG_GET = 0x0003

    /** 寄存器写入请求/响应类型。 / Register write request/response type. */
    const val MSG_TYPE_REG_SET = 0x0004

    /** 文件数据通道类型。 / File data channel type. */
    const val MSG_TYPE_FILE_DATA = 0x0005

    /** OTA/大文件类命令通道类型。 / OTA / large-file command channel type. */
    const val MSG_TYPE_OTA_UPGRADE = 0x0006

    /** 推理结果上报类型。 / Inference result report type. */
    const val MSG_TYPE_GAZE_DATA = 0x1004

    /** 通用成功响应类型。 / Generic success response type. */
    const val MSG_TYPE_RESP_OK = 0x9000

    /** 通用失败响应类型。 / Generic error response type. */
    const val MSG_TYPE_RESP_ERROR = 0x9001

    /**
     * 解出的单帧数据。 / A decoded frame.
     *
     * @property type 帧类型，对应底层 msgType。 / Frame type (msgType).
     * @property payload 纯 payload 数据，不含帧头。 / Pure payload without header.
     */
    data class Frame(
        /** 帧类型。 / Frame type. */
        val type: Int,
        /** 帧负载字节数组。 / Frame payload bytes. */
        val payload: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Frame
            if (type != other.type) return false
            if (!payload.contentEquals(other.payload)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = type
            result = 31 * result + payload.contentHashCode()
            return result
        }

        override fun toString(): String {
            return "Frame(type=0x${type.toString(16).padStart(4, '0')}, payload=${payload.size} bytes)"
        }
    }

    /** 上层关心的消息大类，用于把底层 msgType 归并成更稳定的语义。 / High-level message categories derived from msgType. */
    enum class MessageCategory {
        /** 参数读取响应。 / Parameter get response. */
        PARAM_GET_RESPONSE,
        /** 参数设置响应。 / Parameter set response. */
        PARAM_SET_RESPONSE,
        /** 凝视/推理数据上报。 / Gaze / inference data report. */
        GAZE_DATA_REPORT,
        /** 文件数据。 / File data. */
        FILE_DATA,
        /** OTA 升级响应。 / OTA upgrade response. */
        OTA_UPGRADE_RESPONSE,
        /** 通用成功响应。 / Generic OK response. */
        GENERIC_OK_RESPONSE,
        /** 通用错误响应。 / Generic error response. */
        GENERIC_ERROR_RESPONSE,
        /** 未知类型。 / Unknown type. */
        UNKNOWN;

        /** 当前消息是否属于持续上报流。 / Whether this message is a continuous report stream. */
        fun isReport(): Boolean = this == GAZE_DATA_REPORT

        /** 当前消息是否属于命令响应。 / Whether this message is a command response. */
        fun isResponse(): Boolean = this in setOf(
            PARAM_GET_RESPONSE,
            PARAM_SET_RESPONSE,
            OTA_UPGRADE_RESPONSE,
            GENERIC_OK_RESPONSE,
            GENERIC_ERROR_RESPONSE
        )
    }

    /**
     * 接收器向上抛出的结构化消息。 / Structured message emitted by the receiver.
     *
     * @property raw 原始 UTF-8 文本。 / Raw UTF-8 text.
     * @property json 解析成功后的 JSON；非 JSON 或解析失败时为 null。 / Parsed JSON or null.
     * @property error 接收阶段发现的问题描述。 / Error description if any.
     * @property msgType 底层协议类型。 / Underlying protocol type.
     * @property category 协议层归类结果。 / Protocol-level classification.
     */
    data class IncomingMessage(
        /** 原始文本。 / Raw text payload. */
        val raw: String,
        /** 解析后的 JSON 对象。 / Parsed [JSONObject] or null. */
        val json: JSONObject?,
        /** 错误信息。 / Error string or null. */
        val error: String?,
        /** 底层消息类型。 / Underlying msgType. */
        val msgType: Int,
        /** 消息类别。 / Message category. */
        val category: MessageCategory
    )

    /**
     * 按 CDC 帧格式封装 payload。 / Encodes payload into a CDC frame.
     *
     * @param type 底层 msgType。 / Underlying msgType.
     * @param payload 需要发送的业务负载。 / Business payload to send.
     * @return 完整的可发送帧字节数组。 / Complete frame byte array ready for transmission.
     */
    fun buildFrame(type: Int, payload: ByteArray): ByteArray {
        require(payload.size <= MAX_PAYLOAD_LEN) { "Payload too large: ${payload.size} > $MAX_PAYLOAD_LEN" }

        val buffer = ByteBuffer.allocate(HEADER_SIZE + payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(MAGIC_0)
        buffer.put(MAGIC_1)
        buffer.putShort(payload.size.toShort())
        buffer.putShort(type.toShort())
        buffer.put(payload)
        return buffer.array()
    }

    /**
     * 为普通 JSON 命令构造参数读/写帧。 / Builds a JSON command frame for parameter get/set.
     *
     * @param isSet true 表示 set 命令，false 表示 get 命令。 / true for set, false for get.
     * @param jsonBytes JSON UTF-8 字节数组。 / JSON bytes in UTF-8.
     * @return 完整帧字节数组。 / Complete frame bytes.
     */
    fun buildJsonCommandFrame(isSet: Boolean, jsonBytes: ByteArray): ByteArray {
        val type = if (isSet) MSG_TYPE_PARAM_SET else MSG_TYPE_PARAM_GET
        return buildFrame(type, jsonBytes)
    }

    /** 将 msgType 转成便于日志阅读的名称。 / Returns a human-readable name for the given msgType. */
    fun getTypeName(type: Int): String = when (type) {
        MSG_TYPE_PARAM_GET -> "MSG_TYPE_PARAM_GET(0x0001)"
        MSG_TYPE_PARAM_SET -> "MSG_TYPE_PARAM_SET(0x0002)"
        MSG_TYPE_REG_GET -> "MSG_TYPE_REG_GET(0x0003)"
        MSG_TYPE_REG_SET -> "MSG_TYPE_REG_SET(0x0004)"
        MSG_TYPE_FILE_DATA -> "MSG_TYPE_FILE_DATA(0x0005)"
        MSG_TYPE_OTA_UPGRADE -> "MSG_TYPE_OTA_UPGRADE(0x0006)"
        MSG_TYPE_GAZE_DATA -> "MSG_TYPE_GAZE_DATA(0x1004)"
        MSG_TYPE_RESP_OK -> "MSG_TYPE_RESP_OK(0x9000)"
        MSG_TYPE_RESP_ERROR -> "MSG_TYPE_RESP_ERROR(0x9001)"
        else -> "UNKNOWN(0x${type.toString(16).padStart(4, '0')})"
    }

    /** 将底层 msgType 归并成上层使用的消息类别。 / Maps a raw msgType to a high-level [MessageCategory]. */
    fun toMessageCategory(type: Int): MessageCategory = when (type) {
        MSG_TYPE_PARAM_GET -> MessageCategory.PARAM_GET_RESPONSE
        MSG_TYPE_PARAM_SET -> MessageCategory.PARAM_SET_RESPONSE
        MSG_TYPE_GAZE_DATA -> MessageCategory.GAZE_DATA_REPORT
        MSG_TYPE_FILE_DATA -> MessageCategory.FILE_DATA
        MSG_TYPE_OTA_UPGRADE -> MessageCategory.OTA_UPGRADE_RESPONSE
        MSG_TYPE_RESP_OK -> MessageCategory.GENERIC_OK_RESPONSE
        MSG_TYPE_RESP_ERROR -> MessageCategory.GENERIC_ERROR_RESPONSE
        else -> MessageCategory.UNKNOWN
    }

    /** 根据命令名查底层 msgType，未知命令默认按参数读取处理。 / Looks up msgType by command name; defaults to parameter read. */
    fun getMessageType(cmd: String): Int = commandTypeMap[cmd] ?: MSG_TYPE_PARAM_GET

    /** 判断命令是否走参数写入通道。 / Checks whether the command is a parameter set command. */
    fun isSetCommand(cmd: String): Boolean = getMessageType(cmd) == MSG_TYPE_PARAM_SET

    /** 判断命令是否走 OTA/大数据通道。 / Checks whether the command is an OTA/large-data command. */
    fun isOtaCommand(cmd: String): Boolean = getMessageType(cmd) == MSG_TYPE_OTA_UPGRADE

    /** 统一设备侧不稳定的响应 cmd 命名。 / Normalizes inconsistent response command names from the device. */
    fun normalizeResponseCmd(cmd: String): String = when (cmd) {
        "set_send_mode" -> "set_result_send_mode"
        else -> cmd
    }

    /** 输出协议分类日志，辅助排查响应落在哪条处理链路。 / Logs message classification for debugging routing decisions. */
    fun logClassification(frameType: Int, category: MessageCategory, json: JSONObject?) {
        if (!LOG_CLASSIFICATION_DETAILS) return
        if (!isLoggable(LOG_TAG, Log.DEBUG)) return

        val hasCmd = json?.has("cmd") ?: false
        val cmdValue = if (hasCmd) json?.optString("cmd") else "N/A"
        debug(
            LOG_TAG,
            "Message classified: type=${getTypeName(frameType)}, category=$category, hasCmd=$hasCmd, cmd=$cmdValue"
        )
    }

    /** 输出未知类型帧的摘要日志。 / Logs a summary for unknown frame types. */
    fun logUnknownType(frameType: Int, payload: ByteArray) {
        val preview = payload.take(50).joinToString(" ") { String.format("%02X", it) }
        warn(LOG_TAG, "Unknown message type: ${getTypeName(frameType)}, payload preview: $preview...")
    }

    /** 命令名到底层 msgType 的统一映射表，避免多个模块重复维护。 / Unified command-to-msgType mapping table. */
    private val commandTypeMap = mapOf(
        "get_camera_param" to MSG_TYPE_PARAM_GET,
        "reset_camera_param" to MSG_TYPE_PARAM_SET,
        "set_camera_param" to MSG_TYPE_PARAM_SET,
        "get_postprocess_param" to MSG_TYPE_PARAM_GET,
        "set_postprocess_param" to MSG_TYPE_PARAM_SET,
        "get_inference_state" to MSG_TYPE_PARAM_GET,
        "set_inference_state" to MSG_TYPE_PARAM_SET,
        "set_detection_upload" to MSG_TYPE_PARAM_SET,
        "get_result_send_mode" to MSG_TYPE_PARAM_GET,
        "set_result_send_mode" to MSG_TYPE_PARAM_SET,
        "get_roi" to MSG_TYPE_PARAM_GET,
        "set_roi" to MSG_TYPE_PARAM_SET,
        "clear_roi" to MSG_TYPE_PARAM_SET,
        "get_pq_settings_param" to MSG_TYPE_PARAM_GET,
        "set_pq_settings_param" to MSG_TYPE_PARAM_SET,
        "reset_pq_settings_param" to MSG_TYPE_PARAM_SET,
        "get_device_info" to MSG_TYPE_PARAM_GET,
        "reboot" to MSG_TYPE_PARAM_SET,
        "reg_get" to MSG_TYPE_REG_GET,
        "reg_set" to MSG_TYPE_REG_SET,
        "capture_input_tensor" to MSG_TYPE_OTA_UPGRADE,
        "reset_input_tensor_counter" to MSG_TYPE_OTA_UPGRADE,
        "get_input_tensor_via_cdc" to MSG_TYPE_OTA_UPGRADE,
        "get_input_tensor_jpg_via_cdc" to MSG_TYPE_OTA_UPGRADE,
        "updata_bin" to MSG_TYPE_OTA_UPGRADE,
        "down_ai_info" to MSG_TYPE_OTA_UPGRADE,
        "down_ai_fpk" to MSG_TYPE_OTA_UPGRADE,
        "upgrade_start" to MSG_TYPE_OTA_UPGRADE,
        "upgrade_file" to MSG_TYPE_OTA_UPGRADE,
        "upgrade_verify" to MSG_TYPE_OTA_UPGRADE,
        "upgrade_commit" to MSG_TYPE_OTA_UPGRADE,
        "upgrade_cancel" to MSG_TYPE_OTA_UPGRADE
    )

    /** 安全判断指定日志级别是否开启，避免部分环境抛异常。 / Safely checks whether a log level is loggable. */
    private fun isLoggable(tag: String, level: Int): Boolean {
        return runCatching { Log.isLoggable(tag, level) }.getOrDefault(false)
    }

    /** 安全输出 debug 日志。 / Safely emits a debug log. */
    private fun debug(tag: String, message: String) {
        runCatching { Log.d(tag, message) }
    }

    /** 安全输出 warning 日志。 / Safely emits a warning log. */
    private fun warn(tag: String, message: String) {
        runCatching { Log.w(tag, message) }
    }

    /**
     * 流式解码器。
     *
     * 负责在串口连续字节流中恢复完整 frame，并在长度异常时自恢复。
     *
     * Streaming decoder.
     * Recovers complete frames from a continuous serial byte stream and auto-recovers on length errors.
     */
    class Decoder {
        /** 内部滚动缓冲区，用于累积串口碎片数据。 / Internal rolling buffer for accumulating fragmented serial data. */
        private var buffer = ByteArray(8 * 1024)

        /** 当前有效数据起始下标。 / Start index of valid data. */
        private var start = 0

        /** 当前有效数据结束下标的后一位。 / End index (exclusive) of valid data. */
        private var end = 0

        /** 当前解码统计信息。 / Current decode statistics. */
        private var stats = DecodeStats()

        /**
         * 解码阶段统计信息。 / Decode statistics.
         *
         * @property totalFrames 成功解出的帧数。 / Total successfully decoded frames.
         * @property errorFrames 因长度异常等原因记为错误的帧数。 / Total error frames.
         * @property bytesDiscarded 为重新同步丢弃的字节数。 / Bytes discarded for resynchronization.
         */
        data class DecodeStats(
            /** 成功解码帧数。 / Successfully decoded frames. */
            var totalFrames: Int = 0,
            /** 错误帧数。 / Error frames. */
            var errorFrames: Int = 0,
            /** 丢弃字节数。 / Discarded bytes. */
            var bytesDiscarded: Int = 0
        )

        /** 追加一段新的串口字节流到内部缓冲区。 / Appends new serial bytes to the internal buffer. */
        fun append(data: ByteArray) {
            if (data.isEmpty()) return
            ensureCapacity(data.size)
            System.arraycopy(data, 0, buffer, end, data.size)
            end += data.size
        }

        /** 解码当前缓冲区内所有完整帧。 / Decodes all complete frames currently in the buffer. */
        fun decodeAvailable(): List<Frame> {
            val frames = ArrayList<Frame>()
            while (true) {
                val frame = parseOne() ?: break
                frames.add(frame)
                stats.totalFrames++
            }
            compactIfNeeded()
            return frames
        }

        /** 返回当前统计快照，避免外部直接修改内部状态。 / Returns a copy of decode statistics. */
        fun getStats(): DecodeStats = stats.copy()

        /** 清空缓冲区和统计信息。 / Clears the buffer and resets statistics. */
        fun reset() {
            start = 0
            end = 0
            stats = DecodeStats()
        }

        /** 每次只尝试解析一帧；如果长度非法，会丢弃一个字节继续同步 magic。 / Parses one frame at a time; discards bytes to resync on invalid lengths. */
        private fun parseOne(): Frame? {
            val available = end - start
            if (available < 2) return null

            val magicPos = findMagic()
            if (magicPos < 0) {
                if (available > 1) {
                    stats.bytesDiscarded += available - 1
                    start = end - 1
                }
                return null
            }

            if (magicPos > 0) {
                stats.bytesDiscarded += magicPos
                start += magicPos
            }

            if (end - start < HEADER_SIZE) return null

            val payloadLen = u16At(2)
            if (isLoggable(TAG, Log.DEBUG)) {
                val headerHex = (0 until minOf(8, end - start)).joinToString(" ") {
                    String.format("%02X", buffer[start + it])
                }
                debug(TAG, "Parse header: $headerHex, payloadLen=$payloadLen")
            }

            if (payloadLen < 0 || payloadLen > MAX_PAYLOAD_LEN) {
                warn(TAG, "Invalid payload length: $payloadLen, discarding magic byte")
                stats.errorFrames++
                stats.bytesDiscarded += 1
                start += 1
                return parseOne()
            }

            val frameSize = HEADER_SIZE + payloadLen
            if (end - start < frameSize) {
//                debug(TAG, "Waiting for more data: have=${end - start}, need=$frameSize, payload=$payloadLen")
                return null
            }

            val type = u16At(4)
            val payload = if (payloadLen > 0) {
                ByteArray(payloadLen).apply {
                    System.arraycopy(buffer, start + HEADER_SIZE, this, 0, payloadLen)
                }
            } else {
                ByteArray(0)
            }

//            debug(TAG, "Frame parsed: type=0x${type.toString(16).padStart(4, '0')}, payload=$payloadLen bytes")
            start += frameSize
            return Frame(type = type, payload = payload)
        }

        /** 在当前有效窗口内查找下一处 magic 头。 / Finds the next magic header within the valid window. */
        private fun findMagic(): Int {
            for (i in 0 until (end - start - 1)) {
                if (buffer[start + i] == MAGIC_0 && buffer[start + i + 1] == MAGIC_1) {
                    return i
                }
            }
            return -1
        }

        /** 确保内部缓冲区还能容纳额外字节，不够时先压缩再扩容。 / Ensures the buffer can accommodate extra bytes, compacting or expanding if needed. */
        private fun ensureCapacity(extra: Int) {
            if (end + extra <= buffer.size) return

            compactIfNeeded(force = true)
            if (end + extra <= buffer.size) return

            var newSize = buffer.size
            while (newSize < end + extra) {
                newSize *= 2
            }
            val newBuffer = ByteArray(newSize)
            if (end > start) {
                System.arraycopy(buffer, start, newBuffer, 0, end - start)
            }
            end -= start
            start = 0
            buffer = newBuffer
            debug(TAG, "Buffer expanded to $newSize bytes")
        }

        /** 缓冲区只在消费过半或强制时压缩，避免每次 decode 都做内存拷贝。 / Compacts the buffer only when half consumed or forced. */
        private fun compactIfNeeded(force: Boolean = false) {
            if (start == 0) return
            if (!force && start < buffer.size / 2) return

            val remain = end - start
            if (remain > 0) {
                System.arraycopy(buffer, start, buffer, 0, remain)
            }
            end = remain
            start = 0
        }

        /** 读取指定偏移处的无符号 8 位值。 / Reads an unsigned 8-bit value at the given offset. */
        private fun u8(offset: Int): Int = buffer[start + offset].toInt() and 0xFF

        /** 读取指定偏移处的无符号 16 位小端值。 / Reads an unsigned 16-bit little-endian value at the given offset. */
        private fun u16At(offset: Int): Int = u8(offset) or (u8(offset + 1) shl 8)
    }
}
