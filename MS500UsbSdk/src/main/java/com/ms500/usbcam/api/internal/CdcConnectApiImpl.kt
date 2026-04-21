package com.ms500.usbcam.api.internal

import android.content.Context
import com.ms500.usbcam.api.CdcMessageEvent
import com.ms500.usbcam.api.CdcStatusEvent
import com.ms500.usbcam.api.CdcConnectApi
import com.ms500.usbcam.api.ConnectionState
import com.ms500.usbcam.MS500UsbCamSdk
import com.ms500.usbcam.cdc.CdcSender
import com.ms500.usbcam.cdc.CdcPortConnection
import com.ms500.usbcam.result.SdkResult
import com.ms500.usbcam.result.SdkError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Internal implementation of CDC connection APIs.
 * Manages CDC lifecycle, exposes state/events as Flows, bridges legacy callbacks,
 * and provides shared sender/client to internal modules.
 */
internal class CdcConnectApiImpl(
    /** Android context, used to create underlying CDC client. */
    private val context: Context
) : CdcConnectApi, CdcPortConnection.StatusListener {

    /** Underlying CDC client instance, responsible for actual USB CDC open/close and send/receive. */
    private var cdcClient: CdcPortConnection? = null

    /**
     * Lazily created command sender.
     * Depends on started `CdcPortConnection`, created on first command send.
     */
    private var cdcSender: CdcSender? = null

    /** Public connection state source. */
    private val _stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val stateFlow: StateFlow<ConnectionState> = _stateFlow.asStateFlow()
    override val stateChanges: Flow<ConnectionState> = stateFlow

    /** Device-side status text event flow. */
    private val _statusEvents = MutableSharedFlow<CdcStatusEvent>(extraBufferCapacity = 32)
    override val statusEvents: Flow<CdcStatusEvent> = _statusEvents

    /** CDC message event flow, unified from sender callbacks and converted to Flow. */
    private val _messageEvents = MutableSharedFlow<CdcMessageEvent>(extraBufferCapacity = 64)
    override val messageEvents: Flow<CdcMessageEvent> = _messageEvents

    /** Whether current underlying client is connected. */
    override val isConnected: Boolean
        get() = cdcClient?.isConnected() == true

    /**
     * Initializes the underlying CDC client without initiating a connection.
     */
    override fun start() {
        if (cdcClient == null) {
            cdcClient = CdcPortConnection(context, this).also { it.start() }
        }
    }

    /**
     * Stops the CDC channel and releases all associated resources.
     */
    override fun stop() {
        cdcSender?.release()
        cdcSender = null
        cdcClient?.stop()
        cdcClient = null
        compatMessageListener = null
        compatInferenceReportListener = null
        _stateFlow.value = ConnectionState.Disconnected
    }

    /**
     * Initiates a CDC connection and suspends until the state reaches a terminal state.
     */
    override suspend fun connect(): SdkResult<Unit> = suspendCancellableCoroutine { continuation ->
        val client = cdcClient
            ?: return@suspendCancellableCoroutine continuation.resume(
                SdkResult.failure(SdkError.NotConnected)
            )

        if (client.isConnected()) {
            continuation.resume(SdkResult.success(Unit))
            return@suspendCancellableCoroutine
        }

        val finished = AtomicBoolean(false)
        val scope = CoroutineScope(continuation.context)
        var collectJob: Job? = null
        var timeoutJob: Job? = null

        /** Cancels listening and timeout jobs. */
        fun cancelJobs() {
            collectJob?.cancel()
            timeoutJob?.cancel()
        }

        /** Ensures connection flow completes only once. */
        fun complete(result: SdkResult<Unit>) {
            if (!finished.compareAndSet(false, true)) return
            cancelJobs()
            continuation.resume(result)
        }

        client.connect()

        collectJob = scope.launch {
            stateFlow.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> complete(SdkResult.success(Unit))
                    is ConnectionState.Error -> complete(SdkResult.failure(SdkError.Unknown(state.message)))
                    else -> Unit
                }
            }
        }

        continuation.invokeOnCancellation {
            if (finished.compareAndSet(false, true)) {
                cancelJobs()
            }
        }

        timeoutJob = scope.launch {
            delay(MS500UsbCamSdk.DEFAULT_TIMEOUT_MS)
            complete(SdkResult.failure(SdkError.Timeout("connect")))
        }
    }

    /**
     * Requests disconnection without destroying the underlying client.
     */
    override fun disconnect() {
        cdcClient?.disconnect()
    }

    /**
     * Simple reconnect: disconnect, wait 500ms, then connect again.
     */
    override suspend fun reconnect(): SdkResult<Unit> {
        disconnect()
        delay(500)
        return connect()
    }

    /**
     * Forwards raw status text from the underlying client as status events.
     */
    override fun onStatus(status: String) {
        _statusEvents.tryEmit(CdcStatusEvent(status))
    }

    /**
     * Maps underlying connection states to the SDK's public ConnectionState model.
     */
    override fun onConnectionStateChanged(state: CdcPortConnection.ConnectionState) {
        val newState = when (state) {
            CdcPortConnection.ConnectionState.CONNECTED -> ConnectionState.Connected
            CdcPortConnection.ConnectionState.DISCONNECTED -> ConnectionState.Disconnected
            CdcPortConnection.ConnectionState.CONNECTING -> ConnectionState.Connecting
            CdcPortConnection.ConnectionState.RECONNECTING -> ConnectionState.Reconnecting(0, 3)
        }
        _stateFlow.value = newState
    }

    /**
     * Returns the shared CdcSender, lazily creating it if necessary.
     */
    internal fun getSender(): CdcSender? {
        if (cdcSender == null && cdcClient != null) {
            cdcSender = CdcSender(cdcClient!!).also { installSenderBridges(it) }
        }
        return cdcSender
    }

    /**
     * Returns the underlying CdcPortConnection for internal reuse only.
     */
    internal fun getClient(): CdcPortConnection? = cdcClient

    /**
     * Registers a legacy generic message listener.
     */
    internal fun setMessageListener(listener: ((raw: String, json: JSONObject?, error: String?, msgType: Int) -> Unit)?) {
        compatMessageListener = listener
        getSender()
    }

    /**
     * Registers a legacy inference report listener.
     */
    internal fun setInferenceReportListener(listener: ((raw: String, json: JSONObject?) -> Unit)?) {
        compatInferenceReportListener = listener
        getSender()
    }

    /**
     * Adds a client-level message listener.
     */
    internal fun addClientMessageListener(listener: CdcPortConnection.MessageListener) {
        getClient()?.addMessageListener(listener)
    }

    /**
     * Removes a client-level message listener.
     */
    internal fun removeClientMessageListener(listener: CdcPortConnection.MessageListener) {
        getClient()?.removeMessageListener(listener)
    }

    /** Legacy generic message callback bridge. */
    private var compatMessageListener: ((raw: String, json: JSONObject?, error: String?, msgType: Int) -> Unit)? = null

    /** Legacy inference result callback bridge. */
    private var compatInferenceReportListener: ((raw: String, json: JSONObject?) -> Unit)? = null

    /**
     * Installs bridges from CdcSender callbacks to SDK events and legacy listeners.
     */
    private fun installSenderBridges(sender: CdcSender) {
        sender.setOnMessageListener { raw, json, error, msgType ->
            _messageEvents.tryEmit(
                CdcMessageEvent(
                    raw = raw,
                    jsonText = json?.toString(),
                    error = error,
                    msgType = msgType
                )
            )
            compatMessageListener?.invoke(raw, json, error, msgType)
        }
        sender.setOnInferenceReportListener { raw, json ->
            compatInferenceReportListener?.invoke(raw, json)
        }
    }
}
