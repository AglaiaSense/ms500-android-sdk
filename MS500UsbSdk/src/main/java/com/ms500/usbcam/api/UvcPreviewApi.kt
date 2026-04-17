package com.ms500.usbcam.api

import android.view.Surface
import com.ms500.usbcam.result.SdkResult
import kotlinx.coroutines.flow.StateFlow

/**
 * 预览状态。
 */
sealed class PreviewState {
    /** 空闲状态 */
    object Idle : PreviewState()

    /** 等待 Surface */
    object WaitingForSurface : PreviewState()

    /** 等待设备 */
    object WaitingForDevice : PreviewState()

    /**
     * 正在打开设备。
     *
     * @property deviceName 设备名称，可能为 null
     */
    data class Opening(val deviceName: String?) : PreviewState()

    /**
     * 正在预览中。
     *
     * @property width 预览宽度
     * @property height 预览高度
     * @property deviceName 设备名称，可能为 null
     */
    data class Previewing(val width: Int, val height: Int, val deviceName: String?) : PreviewState()

    /** 已停止 */
    object Stopped : PreviewState()

    /**
     * 预览出错。
     *
     * @property message 错误描述
     */
    data class Error(val message: String) : PreviewState()
}

/**
 * UVC 预览设备信息。
 *
 * @property deviceId 设备 ID
 * @property displayName 显示名称
 * @property vendorId 厂商 ID
 * @property productId 产品 ID
 */
data class PreviewDevice(
    val deviceId: Int,
    val displayName: String,
    val vendorId: Int,
    val productId: Int
)

/**
 * 预览分辨率信息。
 *
 * @property width 宽度
 * @property height 高度
 */
data class PreviewSizeInfo(
    val width: Int,
    val height: Int
) {
    /**
     * 分辨率标签，例如 "1920 x 1080"。
     */
    val label: String
        get() = "${width} x ${height}"
}

/**
 * 截图/录像回调接口。
 */
interface CaptureCallback {
    /**
     * 开始捕获时调用。
     */
    fun onBegin()

    /**
     * 捕获出错时调用。
     *
     * @param error 错误信息，可能为 null
     */
    fun onError(error: String?)

    /**
     * 捕获完成时调用。
     *
     * @param path 保存路径，可能为 null
     */
    fun onComplete(path: String?)
}

/**
 * UVC 预览 API。
 * 管理 UVC 设备的预览、录像、分辨率切换及设备切换等功能。
 *
 * UVC preview API for managing preview, recording, and device switching.
 */
interface UvcPreviewApi {

    /**
     * 当前设备是否支持 UVC 预览。
     */
    val isSupported: Boolean

    /**
     * 当前是否正在预览。
     */
    val isPreviewing: Boolean

    /**
     * 当前是否正在录像。
     */
    val isRecording: Boolean

    /**
     * 预览状态流（StateFlow）。
     */
    val stateFlow: StateFlow<PreviewState>

    /**
     * 当前正在预览的设备 ID，可能为 null。
     */
    val currentDeviceId: Int?

    /**
     * 当前预览分辨率，可能为 null。
     */
    val currentPreviewSize: PreviewSizeInfo?

    /**
     * 初始化预览。
     *
     * @param surface 用于渲染的 Surface
     * @param width 预览宽度，默认 1920
     * @param height 预览高度，默认 1080
     * @return 初始化结果
     */
    fun initPreview(
        surface: Surface,
        width: Int = 1920,
        height: Int = 1080
    ): SdkResult<Unit>

    /**
     * 开始预览。
     *
     * @return 操作结果
     */
    fun startPreview(): SdkResult<Unit>

    /**
     * 停止预览。
     *
     * @return 操作结果
     */
    fun stopPreview(): SdkResult<Unit>

    /**
     * 释放预览资源。
     */
    fun release()

    /**
     * 获取可用的 UVC 设备列表。
     *
     * @return 设备列表
     */
    fun getDeviceList(): List<PreviewDevice>

    /**
     * 获取当前设备支持的预览分辨率列表。
     *
     * @return 分辨率列表
     */
    fun getSupportedPreviewSizes(): List<PreviewSizeInfo>

    /**
     * 切换到指定设备。
     *
     * @param deviceId 目标设备 ID
     * @return 操作结果
     */
    fun switchDevice(deviceId: Int): SdkResult<Unit>

    /**
     * 更新预览分辨率。
     *
     * @param width 宽度
     * @param height 高度
     * @return 操作结果
     */
    fun updateResolution(width: Int, height: Int): SdkResult<Unit>

    /**
     * 截图。
     *
     * @param path 保存路径，为 null 时由内部决定
     * @param callback 捕获回调
     * @return 操作结果
     */
    fun captureImage(path: String? = null, callback: CaptureCallback): SdkResult<Unit>

    /**
     * 开始录像。
     *
     * @param path 保存路径，为 null 时由内部决定
     * @param durationInSec 录像时长（秒），0 表示不限制
     * @param callback 捕获回调
     * @return 操作结果
     */
    fun startRecording(
        path: String? = null,
        durationInSec: Long = 0L,
        callback: CaptureCallback
    ): SdkResult<Unit>

    /**
     * 停止录像。
     *
     * @return 操作结果
     */
    fun stopRecording(): SdkResult<Unit>
}
