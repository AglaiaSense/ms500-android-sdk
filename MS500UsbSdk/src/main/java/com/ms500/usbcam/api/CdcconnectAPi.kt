package com.ms500.usbcam.api

import com.ms500.usbcam.cdc.CdcProtocol
import com.ms500.usbcam.result.SdkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * CDC status event emitted by [CdcConnectApi.statusEvents].
 *
 * Status events are human-readable lifecycle messages intended for logs and UI status text. They are not
 * command responses; use typed SDK methods or [CdcConnectApi.messageEvents] for protocol data.
 *
 * @property message Status description text.
 */
data class CdcStatusEvent(
    val message: String
)

/**
 * CDC send event emitted by [CdcConnectApi.sendEvents].
 *
 * This event contains the final JSON text passed to the CDC frame encoder. It is intended for debug
 * logs and protocol verification, so callers can see exactly what Android sent to the device.
 *
 * @property raw Raw JSON string sent by Android.
 * @property cmd Command name used for response matching.
 * @property msgType Numeric CDC message type selected for this command.
 */
data class CdcSendEvent(
    val raw: String,
    val cmd: String,
    val msgType: Int
)

/**
 * CDC message event emitted by [CdcConnectApi.messageEvents].
 *
 * This is a diagnostic/event stream for callers that need to observe raw traffic. Typed APIs such as
 * [CameraParamApi], [PostprocessParamApi], [PqConfigParamApi], and [DeviceParamApi] should be preferred
 * for normal command execution because they parse responses into DTOs.
 *
 * @property raw Raw message string or raw data description.
 * @property jsonText Parsed JSON text when the message contained JSON, otherwise null.
 * @property error Error information, if the lower layer reported an error.
 * @property msgType Numeric CDC message type, if available.
 */
data class CdcMessageEvent(
    val raw: String,
    val jsonText: String? = null,
    val error: String? = null,
    val msgType: Int? = null
) {
    /** Whether this event represents an error from the CDC layer. */
    val isError: Boolean
        get() = error != null

    /** Whether this event is the current async inference result report MSG_TYPE_INFERENCE_RESULT(0x1000). */
    val isInferenceResult: Boolean
        get() = msgType == CdcProtocol.MSG_TYPE_INFERENCE_RESULT
}

/**
 * CDC connection state.
 *
 * This state is exposed by [CdcConnectApi.stateFlow]. It describes the connection lifecycle, not the
 * state of individual commands.
 */
sealed class ConnectionState {
    /** CDC is connected and command APIs may send requests. */
    object Connected : ConnectionState()

    /** CDC is disconnected. */
    object Disconnected : ConnectionState()

    /** A connection attempt is in progress. */
    object Connecting : ConnectionState()

    /**
     * Automatic or explicit reconnect is in progress.
     *
     * @property attempt Current retry count.
     * @property maxAttempts Maximum retry count.
     */
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ConnectionState()

    /**
     * CDC connection entered an error state.
     *
     * @property message Error description.
     */
    data class Error(val message: String) : ConnectionState()
}

/**
 * CDC connection API for lifecycle management, state observation, and raw message observation.
 *
 * Start the service with [start], connect with [connect], then use typed parameter APIs. UI code should
 * observe [stateFlow] for current connection state and [statusEvents] for readable status messages.
 * [sendEvents] and [messageEvents] are useful for debugging or automation logs but should not replace
 * typed API results.
 */
interface CdcConnectApi {

    /** Current connection state as a hot [StateFlow]. */
    val stateFlow: StateFlow<ConnectionState>

    /** Human-readable CDC lifecycle/status messages. */
    val statusEvents: Flow<CdcStatusEvent>

    /** Raw CDC send JSON events for diagnostics. */
    val sendEvents: Flow<CdcSendEvent>

    /** Raw CDC receive traffic and parsed message events for diagnostics. */
    val messageEvents: Flow<CdcMessageEvent>

    /** Whether CDC is currently connected. */
    val isConnected: Boolean

    /**
     * Starts the CDC service/lower layer.
     *
     * This prepares resources for connection but does not guarantee that a device is connected. Call
     * [connect] after starting when the app wants to establish an active CDC session.
     */
    fun start()

    /**
     * Stops the CDC service/lower layer.
     *
     * This should be called when the owning component is being destroyed. Active command calls should not
     * be started after stop.
     */
    fun stop()

    /**
     * Establishes CDC connection.
     *
     * On success, [stateFlow] should move to [ConnectionState.Connected]. On failure, the returned
     * [SdkResult] describes the error and [stateFlow] may emit [ConnectionState.Error].
     *
     * @return Connection result.
     */
    suspend fun connect(): SdkResult<Unit>

    /**
     * Disconnects the current CDC session.
     *
     * This is a local lifecycle operation. Use [connect] or [reconnect] to establish a new session later.
     */
    fun disconnect()

    /**
     * Reconnects CDC by disconnecting/reopening the session.
     *
     * This is useful after device reboot, firmware upgrade, USB reattach, or transient transport errors.
     *
     * @return Connection result.
     */
    suspend fun reconnect(): SdkResult<Unit>
}
