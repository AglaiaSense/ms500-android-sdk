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
 * CDC 连接 API 内部实现类。
 *
 * 作为 SDK 在 CDC 通道上的统一门面，负责：
 * 1. 管理 CDC 生命周期（启动、停止、连接、断开、重连）
 * 2. 维护连接状态流与事件流
 * 3. 桥接历史回调式接口
 * 4. 向内部模块提供共享的 CdcSender / CdcPortConnection
 *
 * Internal implementation of CDC connection APIs.
 * Manages CDC lifecycle, exposes state/events as Flows, bridges legacy callbacks,
 * and provides shared sender/client to internal modules.
 */
internal class CdcConnectApiImpl(
    /** Android 上下文，用于创建底层 CDC 客户端。 */
    private val context: Context
) : CdcConnectApi, CdcPortConnection.StatusListener {

    /** 底层 CDC 客户端实例，负责 USB CDC 的实际打开、关闭和收发。 */
    private var cdcClient: CdcPortConnection? = null

    /**
     * 懒创建的命令发送器。
     * 依赖已启动的 `CdcPortConnection`，在首次需要发送命令时创建。
     */
    private var cdcSender: CdcSender? = null

    /** 对外连接状态源。 */
    private val _stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val stateFlow: StateFlow<ConnectionState> = _stateFlow.asStateFlow()
    override val stateChanges: Flow<ConnectionState> = stateFlow

    /** 设备侧状态文本事件流。 */
    private val _statusEvents = MutableSharedFlow<CdcStatusEvent>(extraBufferCapacity = 32)
    override val statusEvents: Flow<CdcStatusEvent> = _statusEvents

    /** CDC 消息事件流，统一承接 sender 回调并转成 Flow。 */
    private val _messageEvents = MutableSharedFlow<CdcMessageEvent>(extraBufferCapacity = 64)
    override val messageEvents: Flow<CdcMessageEvent> = _messageEvents

    /** 当前底层 client 是否已连接。 */
    override val isConnected: Boolean
        get() = cdcClient?.isConnected() == true

    /**
     * 启动底层 CDC client，但不主动连接。
     * Initializes the underlying CDC client without initiating a connection.
     */
    override fun start() {
        if (cdcClient == null) {
            cdcClient = CdcPortConnection(context, this).also { it.start() }
        }
    }

    /**
     * 停止 CDC 通道并清理所有关联资源。
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
     * 发起 CDC 连接，并等待状态流进入终态（成功/失败/超时）。
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

        /** 统一取消监听与超时任务。 */
        fun cancelJobs() {
            collectJob?.cancel()
            timeoutJob?.cancel()
        }

        /** 确保连接流程只完成一次。 */
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
     * 请求底层 CDC 断开连接，但不销毁 client。
     * Requests disconnection without destroying the underlying client.
     */
    override fun disconnect() {
        cdcClient?.disconnect()
    }

    /**
     * 简单重连：先断开，等待 500ms，再重新连接。
     * Simple reconnect: disconnect, wait 500ms, then connect again.
     */
    override suspend fun reconnect(): SdkResult<Unit> {
        disconnect()
        delay(500)
        return connect()
    }

    /**
     * 底层状态文本回调，直接转发为 `statusEvents`。
     * Forwards raw status text from the underlying client as status events.
     */
    override fun onStatus(status: String) {
        _statusEvents.tryEmit(CdcStatusEvent(status))
    }

    /**
     * 把底层连接状态转换为 SDK 对外状态模型。
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
     * 获取共享的 `CdcSender`，按需懒创建。
     * Returns the shared CdcSender, lazily creating it if necessary.
     */
    internal fun getSender(): CdcSender? {
        if (cdcSender == null && cdcClient != null) {
            cdcSender = CdcSender(cdcClient!!).also { installSenderBridges(it) }
        }
        return cdcSender
    }

    /**
     * 获取底层 `CdcPortConnection`，仅供内部模块复用。
     * Returns the underlying CdcPortConnection for internal reuse only.
     */
    internal fun getClient(): CdcPortConnection? = cdcClient

    /**
     * 注册旧式通用消息监听器。
     * Registers a legacy generic message listener.
     */
    internal fun setMessageListener(listener: ((raw: String, json: JSONObject?, error: String?, msgType: Int) -> Unit)?) {
        compatMessageListener = listener
        getSender()
    }

    /**
     * 注册旧式推理结果监听器。
     * Registers a legacy inference report listener.
     */
    internal fun setInferenceReportListener(listener: ((raw: String, json: JSONObject?) -> Unit)?) {
        compatInferenceReportListener = listener
        getSender()
    }

    /**
     * 添加 client 级别的消息监听器。
     * Adds a client-level message listener.
     */
    internal fun addClientMessageListener(listener: CdcPortConnection.MessageListener) {
        getClient()?.addMessageListener(listener)
    }

    /**
     * 移除 client 级别的消息监听器。
     * Removes a client-level message listener.
     */
    internal fun removeClientMessageListener(listener: CdcPortConnection.MessageListener) {
        getClient()?.removeMessageListener(listener)
    }

    /** 旧式通用消息回调桥。 */
    private var compatMessageListener: ((raw: String, json: JSONObject?, error: String?, msgType: Int) -> Unit)? = null

    /** 旧式推理结果回调桥。 */
    private var compatInferenceReportListener: ((raw: String, json: JSONObject?) -> Unit)? = null

    /**
     * 安装 sender -> SDK 事件桥接。
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
