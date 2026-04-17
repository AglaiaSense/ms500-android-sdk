package com.ms500.usbcam.api

import com.ms500.usbcam.result.SdkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class CdcStatusEvent(
    val message: String
)

data class CdcMessageEvent(
    val raw: String,
    val jsonText: String? = null,
    val error: String? = null,
    val msgType: Int? = null
) {
    val isError: Boolean
        get() = error != null
}

/**
 * 连接状态
 */
sealed class ConnectionState {
    object Connected : ConnectionState()
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * CDC 连接 API
 *
 * 管理 CDC 连接生命周期和状态监听。
 */
interface CdcConnectApi {

    val stateFlow: StateFlow<ConnectionState>

    val stateChanges: Flow<ConnectionState>

    /**
     * CDC 状态文本流。
     */
    val statusEvents: Flow<CdcStatusEvent>

    /**
     * CDC 原始消息流。
     */
    val messageEvents: Flow<CdcMessageEvent>

    val isConnected: Boolean

    fun start()

    fun stop()

    suspend fun connect(): SdkResult<Unit>

    fun disconnect()

    suspend fun reconnect(): SdkResult<Unit>
}

