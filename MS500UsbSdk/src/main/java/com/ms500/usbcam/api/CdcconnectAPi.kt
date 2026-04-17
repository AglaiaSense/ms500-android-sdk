package com.ms500.usbcam.api

import com.ms500.usbcam.result.SdkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * CDC 状态事件。
 *
 * @property message 状态描述文本
 */
data class CdcStatusEvent(
    val message: String
)

/**
 * CDC 消息高层分类。
 */
enum class CdcMessageCategory {
    PARAM_RESPONSE,
    REPORT,
    FILE_DATA,
    REG_RESPONSE,
    GAZE_DATA_REPORT,
    UNKNOWN;

    /**
     * 是否为响应类消息。
     */
    fun isResponse(): Boolean {
        return this == PARAM_RESPONSE || this == REG_RESPONSE
    }

    /**
     * 是否为上报类消息。
     */
    fun isReport(): Boolean {
        return this == REPORT || this == GAZE_DATA_REPORT
    }
}

/**
 * CDC 消息事件。
 *
 * @property raw 原始消息字符串
 * @property jsonText 解析后的 JSON 文本，可能为 null
 * @property error 错误信息，可能为 null
 * @property msgType 消息类型，可能为 null
 */
data class CdcMessageEvent(
    val raw: String,
    val jsonText: String? = null,
    val error: String? = null,
    val msgType: Int? = null
) {
    /**
     * 是否为错误消息。
     */
    val isError: Boolean
        get() = error != null

    /**
     * 消息高层分类。
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
     * 是否为 gaze / inference 上报消息。
     */
    val isGazeData: Boolean
        get() = messageCategory == CdcMessageCategory.GAZE_DATA_REPORT

    /**
     * 是否为响应类消息。
     */
    val isResponse: Boolean
        get() = messageCategory.isResponse()

    /**
     * 是否为状态/结果上报类消息。
     */
    val isStatus: Boolean
        get() = messageCategory.isReport()
}

/**
 * CDC 连接状态。
 */
sealed class ConnectionState {
    /** 已连接 */
    object Connected : ConnectionState()

    /** 已断开 */
    object Disconnected : ConnectionState()

    /** 连接中 */
    object Connecting : ConnectionState()

    /**
     * 重连中。
     *
     * @property attempt 当前重试次数
     * @property maxAttempts 最大重试次数
     */
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ConnectionState()

    /**
     * 连接出错。
     *
     * @property message 错误描述
     */
    data class Error(val message: String) : ConnectionState()
}

/**
 * CDC 连接 API。
 * 管理 CDC（Communication Device Class）连接的生命周期和状态监听。
 *
 * CDC connection API for managing lifecycle and state observation.
 */
interface CdcConnectApi {

    /**
     * 当前连接状态流（StateFlow）。
     */
    val stateFlow: StateFlow<ConnectionState>

    /**
     * 连接状态变化流（Flow）。
     */
    val stateChanges: Flow<ConnectionState>

    /**
     * CDC 状态文本流。
     */
    val statusEvents: Flow<CdcStatusEvent>

    /**
     * CDC 原始消息流。
     */
    val messageEvents: Flow<CdcMessageEvent>

    /**
     * 当前是否已连接。
     */
    val isConnected: Boolean

    /**
     * 启动 CDC 服务。
     */
    fun start()

    /**
     * 停止 CDC 服务。
     */
    fun stop()

    /**
     * 建立连接。
     *
     * @return 连接结果
     */
    suspend fun connect(): SdkResult<Unit>

    /**
     * 断开连接。
     */
    fun disconnect()

    /**
     * 重新连接。
     *
     * @return 连接结果
     */
    suspend fun reconnect(): SdkResult<Unit>
}
