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
 * CDC 连接实现。
 *
 * 这一层是 SDK 在 CDC 通道上的统一门面，负责把底层 `CdcPortConnection`
 * 包装成上层可消费的 `CdcConnectApi`：
 *
 * 1. 管理 CDC 生命周期
 *    - `start()` 创建并启动底层 client
 *    - `stop()` 释放 sender / client，并回收兼容监听器
 *    - `connect()` / `disconnect()` / `reconnect()` 负责连接流程控制
 *
 * 2. 维护面向业务层的状态流
 *    - `stateFlow` / `stateChanges` 暴露连接状态
 *    - `statusEvents` 暴露设备侧状态文本
 *    - `messageEvents` 暴露 CDC 原始消息事件
 *
 * 3. 桥接历史回调式接口
 *    目前 SDK 内部仍有一部分旧逻辑习惯直接通过回调接收消息，
 *    所以这里保留了 `compatMessageListener` / `compatInferenceReportListener`
 *    两条兼容链路，并统一通过 `CdcSender` 安装桥接。
 *
 * 4. 统一管理共享 sender / client
 *    参数 API、推理 API、OTA API 等都会复用同一个 CDC 通道，
 *    所以这里提供 `getSender()` / `getClient()` 给内部实现层共享，
 *    避免每个 API 自己维护一套 CDC 连接。
 */
internal class CdcConnectApiImpl(
    private val context: Context
) : CdcConnectApi, CdcPortConnection.StatusListener {

    /** 底层 CDC 客户端，负责实际的 USB CDC 打开、关闭和收发。 */
    private var cdcClient: CdcPortConnection? = null

    /**
     * 懒创建的命令发送器。
     *
     * `CdcSender` 依赖已经启动好的 `CdcPortConnection`，所以这里不在 `start()`
     * 时立即创建，而是在参数 API / OTA API 真正需要发命令时再创建。
     */
    private var cdcSender: CdcSender? = null

    /** 对外连接状态源，所有连接结果都统一收口到这里。 */
    private val _stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val stateFlow: StateFlow<ConnectionState> = _stateFlow.asStateFlow()
    override val stateChanges: Flow<ConnectionState> = stateFlow

    /** 设备侧状态文本事件，例如底层状态变化、调试提示等。 */
    private val _statusEvents = MutableSharedFlow<CdcStatusEvent>(extraBufferCapacity = 32)
    override val statusEvents: Flow<CdcStatusEvent> = _statusEvents

    /** CDC 消息事件流，统一承接 sender 回调并转成 Flow 供上层消费。 */
    private val _messageEvents = MutableSharedFlow<CdcMessageEvent>(extraBufferCapacity = 64)
    override val messageEvents: Flow<CdcMessageEvent> = _messageEvents

    /** 仅反映底层 client 当前是否处于已连接态。 */
    override val isConnected: Boolean
        get() = cdcClient?.isConnected() == true

    /**
     * 启动底层 CDC client，但不主动执行 connect。
     *
     * 这里把“初始化通道”和“真正发起连接”拆开，调用方可以先 `start()`，
     * 再在合适的页面生命周期或用户动作中调用 `connect()`。
     */
    override fun start() {
        if (cdcClient == null) {
            cdcClient = CdcPortConnection(context, this).also { it.start() }
        }
    }

    /**
     * 停止 CDC 通道并清理关联资源。
     *
     * 这里除了关闭 `CdcPortConnection`，还会一并释放懒创建的 `CdcSender`
     * 和历史兼容监听器，确保下一次 `start()` / `connect()` 是干净状态。
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
     * 发起一次连接，并等待状态流进入终态。
     *
     * 连接成功条件：
     * - 状态流收到 `ConnectionState.Connected`
     *
     * 连接失败条件：
     * - 状态流收到 `ConnectionState.Error`
     * - 或超过 `MS500UsbCamSdk.DEFAULT_TIMEOUT_MS`
     *
     * 这里不用底层阻塞式等待，而是监听 `stateFlow`，
     * 这样状态来源在 SDK 内部保持统一。
     *
     * 额外的约束是：
     * - 如果外部重复调用 `connect()`，这里仍然只信任底层状态回调结果
     * - 超时和错误最终都转换成 `SdkResult.failure(...)`
     * - 一旦完成，会立即取消 collector / timeout 两个协程，避免泄漏
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

        /** 连接流程结束后统一回收状态监听和超时任务。 */
        fun cancelJobs() {
            collectJob?.cancel()
            timeoutJob?.cancel()
        }

        /** 连接流程只允许完成一次，避免状态回调和超时同时 resume。 */
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
     *
     * 适用于“保持 CDC 组件存活，仅断开设备连接”的场景；
     * 如果要彻底释放资源，应该调用 `stop()`。
     */
    override fun disconnect() {
        cdcClient?.disconnect()
    }

    /**
     * 简单重连实现：先断开，短暂等待，再重新 connect。
     *
     * 当前没有引入更复杂的重试策略，原因是重连时机通常由页面或上层业务决定。
     * 这里保留最小实现，避免 SDK 内部私自叠加不可见的重试行为。
     */
    override suspend fun reconnect(): SdkResult<Unit> {
        disconnect()
        delay(500)
        return connect()
    }

    /**
     * 底层状态文本回调，直接转发为 `statusEvents`。
     *
     * 这里不做二次解释或翻译，保持日志和设备状态文本尽量原样透传。
     */
    override fun onStatus(status: String) {
        _statusEvents.tryEmit(CdcStatusEvent(status))
    }

    /**
     * 把底层 `CdcPortConnection.ConnectionState` 翻译成 SDK 对外状态模型。
     *
     * SDK 对外只暴露自身定义的 `ConnectionState`，这样后续即使底层 client
     * 实现变化，也不影响上层 API 的状态类型。
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
     * 获取共享 `CdcSender`。
     *
     * `CdcSender` 按需懒创建，并且只创建一次。
     * 这样各业务 API 在真正需要发送 CDC 命令前不会额外初始化 sender。
     *
     * 注意：sender 的生命周期依附于当前 `cdcClient`。一旦 `stop()` 被调用，
     * sender 也会一并释放，下一次使用时重新创建。
     */
    internal fun getSender(): CdcSender? {
        if (cdcSender == null && cdcClient != null) {
            cdcSender = CdcSender(cdcClient!!).also { installSenderBridges(it) }
        }
        return cdcSender
    }

    /**
     * 暴露底层 client 给少量需要直接监听 message 的内部模块复用。
     *
     * 这里只给 SDK 内部实现层使用，不对外公开，避免业务层直接耦合底层 client。
     */
    internal fun getClient(): CdcPortConnection? = cdcClient

    /**
     * 注册旧式通用消息监听器，并确保 sender 桥接已安装。
     *
     * 这是兼容旧页面/旧模块的过渡接口，新代码优先使用 `messageEvents`。
     */
    internal fun setMessageListener(listener: ((raw: String, json: JSONObject?, error: String?, msgType: Int) -> Unit)?) {
        compatMessageListener = listener
        getSender()
    }

    /**
     * 注册旧式推理结果监听器，并确保 sender 桥接已安装。
     *
     * 推理结果历史上单独走一条回调链，这里仍保留兼容入口，
     * 避免一次性改动所有旧代码。
     */
    internal fun setInferenceReportListener(listener: ((raw: String, json: JSONObject?) -> Unit)?) {
        compatInferenceReportListener = listener
        getSender()
    }

    /** 给内部模块补充直接 client 级消息监听能力。 */
    internal fun addClientMessageListener(listener: CdcPortConnection.MessageListener) {
        getClient()?.addMessageListener(listener)
    }

    /** 移除内部模块注册的 client 级消息监听器。 */
    internal fun removeClientMessageListener(listener: CdcPortConnection.MessageListener) {
        getClient()?.removeMessageListener(listener)
    }

    /** 旧式通用消息回调桥。 */
    private var compatMessageListener: ((raw: String, json: JSONObject?, error: String?, msgType: Int) -> Unit)? = null

    /** 旧式推理结果回调桥。 */
    private var compatInferenceReportListener: ((raw: String, json: JSONObject?) -> Unit)? = null

    /**
     * 安装 sender -> SDK 的事件桥接。
     *
     * `CdcSender` 仍然是当前很多命令 API 的实际发送入口，
     * 所以这里把 sender 的回调统一转换成：
     * - `messageEvents`
     * - 兼容监听器回调
     *
     * 这样无论上层使用 Flow 还是旧式 callback，
     * 都能从同一套消息来源得到结果。
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
