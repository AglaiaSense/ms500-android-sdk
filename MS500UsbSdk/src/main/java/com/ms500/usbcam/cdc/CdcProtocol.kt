package com.ms500.usbcam.cdc

import android.util.Log
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * CDC protocol definitions.
 * Frame format: [MAGIC(2 bytes)][LEN(2 bytes)][TYPE(2 bytes)][PAYLOAD]
 * Byte order: Little Endian.
 */
object CdcProtocol {
    /** Log tag for protocol parsing. */
    private const val TAG = "CdcProtocol"

    /** Dedicated log tag for message classification. */
    private const val LOG_TAG = "CdcMessage"

    /** Whether to log classification details. */
    private const val LOG_CLASSIFICATION_DETAILS = true

    /** First magic byte of the frame header. */
    private const val MAGIC_0 = 0x55.toByte()

    /** Second magic byte of the frame header. */
    private const val MAGIC_1 = 0xAA.toByte()

    /** Fixed header size in bytes. */
    private const val HEADER_SIZE = 6

    /** Maximum allowed payload length per frame. */
    private const val MAX_PAYLOAD_LEN = 60 * 1024

    /** Parameter read request/response type. */
    const val MSG_TYPE_PARAM_GET = 0x0001

    /** Parameter set request/response type. */
    const val MSG_TYPE_PARAM_SET = 0x0002

    /** Register read request/response type. */
    const val MSG_TYPE_REG_GET = 0x0003

    /** Register write request/response type. */
    const val MSG_TYPE_REG_SET = 0x0004

    /** File data channel type. */
    const val MSG_TYPE_FILE_DATA = 0x0005

    /** OTA / large-file command channel type. */
    const val MSG_TYPE_OTA_UPGRADE = 0x0006

    /** Inference result report type. */
    const val MSG_TYPE_GAZE_DATA = 0x1004

    /** Generic success response type. */
    const val MSG_TYPE_RESP_OK = 0x9000

    /** Generic error response type. */
    const val MSG_TYPE_RESP_ERROR = 0x9001

    /**
     * A decoded frame.
     *
     * @property type Frame type (msgType).
     * @property payload Pure payload without header.
     */
    data class Frame(
        /** Frame type. */
        val type: Int,
        /** Frame payload bytes. */
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

    /** High-level message categories derived from msgType. */
    enum class MessageCategory {
        /** Parameter get response. */
        PARAM_GET_RESPONSE,
        /** Parameter set response. */
        PARAM_SET_RESPONSE,
        /** Gaze / inference data report. */
        GAZE_DATA_REPORT,
        /** File data. */
        FILE_DATA,
        /** OTA upgrade response. */
        OTA_UPGRADE_RESPONSE,
        /** Generic OK response. */
        GENERIC_OK_RESPONSE,
        /** Generic error response. */
        GENERIC_ERROR_RESPONSE,
        /** Unknown type. */
        UNKNOWN;

        /** Whether this message is a continuous report stream. */
        fun isReport(): Boolean = this == GAZE_DATA_REPORT

        /** Whether this message is a command response. */
        fun isResponse(): Boolean = this in setOf(
            PARAM_GET_RESPONSE,
            PARAM_SET_RESPONSE,
            OTA_UPGRADE_RESPONSE,
            GENERIC_OK_RESPONSE,
            GENERIC_ERROR_RESPONSE
        )
    }

    /**
     * Structured message emitted by the receiver.
     *
     * @property raw Raw UTF-8 text.
     * @property json Parsed JSON or null.
     * @property error Error string or null.
     * @property msgType Underlying protocol type.
     * @property category Protocol-level classification.
     */
    data class IncomingMessage(
        /** Raw text payload. */
        val raw: String,
        /** Parsed [JSONObject] or null. */
        val json: JSONObject?,
        /** Error string or null. */
        val error: String?,
        /** Underlying msgType. */
        val msgType: Int,
        /** Message category. */
        val category: MessageCategory
    )

    /**
     * Encodes payload into a CDC frame.
     *
     * @param type Underlying msgType.
     * @param payload Business payload to send.
     * @return Complete frame byte array ready for transmission.
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
     * Builds a JSON command frame for parameter get/set.
     *
     * @param isSet true for set, false for get.
     * @param jsonBytes JSON bytes in UTF-8.
     * @return Complete frame bytes.
     */
    fun buildJsonCommandFrame(isSet: Boolean, jsonBytes: ByteArray): ByteArray {
        val type = if (isSet) MSG_TYPE_PARAM_SET else MSG_TYPE_PARAM_GET
        return buildFrame(type, jsonBytes)
    }

    /** Returns a human-readable name for the given msgType. */
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

    /** Maps a raw msgType to a high-level [MessageCategory]. */
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

    /** Looks up msgType by command name; defaults to parameter read. */
    fun getMessageType(cmd: String): Int = commandTypeMap[cmd] ?: MSG_TYPE_PARAM_GET

    /** Checks whether the command is a parameter set command. */
    fun isSetCommand(cmd: String): Boolean = getMessageType(cmd) == MSG_TYPE_PARAM_SET

    /** Checks whether the command is an OTA/large-data command. */
    fun isOtaCommand(cmd: String): Boolean = getMessageType(cmd) == MSG_TYPE_OTA_UPGRADE

    /** Normalizes inconsistent response command names from the device. */
    fun normalizeResponseCmd(cmd: String): String = when (cmd) {
        "set_send_mode" -> "set_result_send_mode"
        else -> cmd
    }

    /** Logs message classification for debugging routing decisions. */
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

    /** Logs a summary for unknown frame types. */
    fun logUnknownType(frameType: Int, payload: ByteArray) {
        val preview = payload.take(50).joinToString(" ") { String.format("%02X", it) }
        warn(LOG_TAG, "Unknown message type: ${getTypeName(frameType)}, payload preview: $preview...")
    }

    /** Unified command-to-msgType mapping table. */
    private val commandTypeMap = mapOf(
        "get_camera_param" to MSG_TYPE_PARAM_GET,
        "reset_camera_param" to MSG_TYPE_PARAM_SET,
        "set_camera_param" to MSG_TYPE_PARAM_SET,
        "get_postprocess_param" to MSG_TYPE_PARAM_GET,
        "reset_postprocess_param" to MSG_TYPE_PARAM_SET,
        "set_postprocess_param" to MSG_TYPE_PARAM_SET,
        "get_inference_state" to MSG_TYPE_PARAM_GET,
        "set_inference_state" to MSG_TYPE_PARAM_SET,
        "set_detection_upload" to MSG_TYPE_PARAM_SET,
        "get_detection_draw_state" to MSG_TYPE_PARAM_GET,
        "set_detection_draw_state" to MSG_TYPE_PARAM_SET,
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
        "set_image_crop" to MSG_TYPE_PARAM_SET,
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

    /** Safely checks whether a log level is loggable. */
    private fun isLoggable(tag: String, level: Int): Boolean {
        return runCatching { Log.isLoggable(tag, level) }.getOrDefault(false)
    }

    /** Safely emits a debug log. */
    private fun debug(tag: String, message: String) {
        runCatching { Log.d(tag, message) }
    }

    /** Safely emits a warning log. */
    private fun warn(tag: String, message: String) {
        runCatching { Log.w(tag, message) }
    }

    /**
     * Streaming decoder.
     * Recovers complete frames from a continuous serial byte stream and auto-recovers on length errors.
     */
    class Decoder {
        /** Internal rolling buffer for accumulating fragmented serial data. */
        private var buffer = ByteArray(8 * 1024)

        /** Start index of valid data. */
        private var start = 0

        /** End index (exclusive) of valid data. */
        private var end = 0

        /** Current decode statistics. */
        private var stats = DecodeStats()

        /**
         * Decode statistics.
         *
         * @property totalFrames Total successfully decoded frames.
         * @property errorFrames Total error frames.
         * @property bytesDiscarded Bytes discarded for resynchronization.
         */
        data class DecodeStats(
            /** Successfully decoded frames. */
            var totalFrames: Int = 0,
            /** Error frames. */
            var errorFrames: Int = 0,
            /** Discarded bytes. */
            var bytesDiscarded: Int = 0
        )

        /** Appends new serial bytes to the internal buffer. */
        fun append(data: ByteArray) {
            if (data.isEmpty()) return
            ensureCapacity(data.size)
            System.arraycopy(data, 0, buffer, end, data.size)
            end += data.size
        }

        /** Decodes all complete frames currently in the buffer. */
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

        /** Returns a copy of decode statistics. */
        fun getStats(): DecodeStats = stats.copy()

        /** Clears the buffer and resets statistics. */
        fun reset() {
            start = 0
            end = 0
            stats = DecodeStats()
        }

        /** Parses one frame at a time; discards bytes to resync on invalid lengths. */
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

        /** Finds the next magic header within the valid window. */
        private fun findMagic(): Int {
            for (i in 0 until (end - start - 1)) {
                if (buffer[start + i] == MAGIC_0 && buffer[start + i + 1] == MAGIC_1) {
                    return i
                }
            }
            return -1
        }

        /** Ensures the buffer can accommodate extra bytes, compacting or expanding if needed. */
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

        /** Compacts the buffer only when half consumed or forced. */
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

        /** Reads an unsigned 8-bit value at the given offset. */
        private fun u8(offset: Int): Int = buffer[start + offset].toInt() and 0xFF

        /** Reads an unsigned 16-bit little-endian value at the given offset. */
        private fun u16At(offset: Int): Int = u8(offset) or (u8(offset + 1) shl 8)
    }
}
