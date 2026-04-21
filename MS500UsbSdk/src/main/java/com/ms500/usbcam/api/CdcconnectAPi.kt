package com.ms500.usbcam.api

import com.ms500.usbcam.result.SdkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * CDC status event.
 *
 * @property message Status description text
 */
data class CdcStatusEvent(
    val message: String
)

/**
 * CDC message high-level category.
 */
enum class CdcMessageCategory {
    PARAM_RESPONSE,
    REPORT,
    FILE_DATA,
    REG_RESPONSE,
    GAZE_DATA_REPORT,
    UNKNOWN;

    /**
     * Whether this is a response message.
     */
    fun isResponse(): Boolean {
        return this == PARAM_RESPONSE || this == REG_RESPONSE
    }

    /**
     * Whether this is a report message.
     */
    fun isReport(): Boolean {
        return this == REPORT || this == GAZE_DATA_REPORT
    }
}

/**
 * CDC message event.
 *
 * @property raw Raw message string
 * @property jsonText Parsed JSON text, may be null
 * @property error Error information, may be null
 * @property msgType Message type, may be null
 */
data class CdcMessageEvent(
    val raw: String,
    val jsonText: String? = null,
    val error: String? = null,
    val msgType: Int? = null
) {
    /**
     * Whether this is an error message.
     */
    val isError: Boolean
        get() = error != null

    /**
     * Message high-level category.
     */
    val messageCategory: CdcMessageCategory
        get() = when (msgType) {
            0x0006 -> CdcMessageCategory.PARAM_RESPONSE
            0x0002 -> CdcMessageCategory.REPORT
            0x0101 -> CdcMessageCategory.FILE_DATA
            0x0104 -> CdcMessageCategory.REG_RESPONSE
            0x1004 -> CdcMessageCategory.GAZE_DATA_REPORT
            else -> CdcMessageCategory.UNKNOWN
        }

    /**
     * Whether this is a gaze / inference report message.
     */
    val isGazeData: Boolean
        get() = messageCategory == CdcMessageCategory.GAZE_DATA_REPORT

    /**
     * Whether this is a response message.
     */
    val isResponse: Boolean
        get() = messageCategory.isResponse()

    /**
     * Whether this is a status/result report message.
     */
    val isStatus: Boolean
        get() = messageCategory.isReport()
}

/**
 * CDC connection state.
 */
sealed class ConnectionState {
    /** Connected */
    object Connected : ConnectionState()

    /** Disconnected */
    object Disconnected : ConnectionState()

    /** Connecting */
    object Connecting : ConnectionState()

    /**
     * Reconnecting.
     *
     * @property attempt Current retry count
     * @property maxAttempts Maximum retry count
     */
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ConnectionState()

    /**
     * Connection error.
     *
     * @property message Error description
     */
    data class Error(val message: String) : ConnectionState()
}

/**
 * CDC connection API for managing lifecycle and state observation.
 */
interface CdcConnectApi {

    /**
     * Current connection state flow (StateFlow).
     */
    val stateFlow: StateFlow<ConnectionState>

    /**
     * Connection state changes flow (Flow).
     */
    val stateChanges: Flow<ConnectionState>

    /**
     * CDC status text flow.
     */
    val statusEvents: Flow<CdcStatusEvent>

    /**
     * CDC raw message flow.
     */
    val messageEvents: Flow<CdcMessageEvent>

    /**
     * Whether currently connected.
     */
    val isConnected: Boolean

    /**
     * Starts CDC service.
     */
    fun start()

    /**
     * Stops CDC service.
     */
    fun stop()

    /**
     * Establishes connection.
     *
     * @return Connection result
     */
    suspend fun connect(): SdkResult<Unit>

    /**
     * Disconnects.
     */
    fun disconnect()

    /**
     * Reconnects.
     *
     * @return Connection result
     */
    suspend fun reconnect(): SdkResult<Unit>
}
