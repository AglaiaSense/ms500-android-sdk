package com.ms500.usbcam.api

import android.view.Surface
import com.ms500.usbcam.result.SdkResult
import kotlinx.coroutines.flow.StateFlow

/**
 * 预览状态
 */
sealed class PreviewState {
    object Idle : PreviewState()
    object WaitingForSurface : PreviewState()
    object WaitingForDevice : PreviewState()
    data class Opening(val deviceName: String?) : PreviewState()
    data class Previewing(val width: Int, val height: Int, val deviceName: String?) : PreviewState()
    object Stopped : PreviewState()
    data class Error(val message: String) : PreviewState()
}

/**
 * UVC 设备信息
 */
data class PreviewDevice(
    val deviceId: Int,
    val displayName: String,
    val vendorId: Int,
    val productId: Int
)

/**
 * 预览分辨率
 */
data class PreviewSizeInfo(
    val width: Int,
    val height: Int
) {
    val label: String
        get() = "${width} x ${height}"
}

interface CaptureCallback {
    fun onBegin()
    fun onError(error: String?)
    fun onComplete(path: String?)
}

/**
 * 预览 API
 */
interface UvcPreviewApi {
    val isSupported: Boolean
    val isPreviewing: Boolean
    val isRecording: Boolean
    val stateFlow: StateFlow<PreviewState>
    val currentDeviceId: Int?
    val currentPreviewSize: PreviewSizeInfo?

    fun initPreview(
        surface: Surface,
        width: Int = 1920,
        height: Int = 1080
    ): SdkResult<Unit>

    fun startPreview(): SdkResult<Unit>

    fun stopPreview(): SdkResult<Unit>

    fun release()

    fun getDeviceList(): List<PreviewDevice>

    fun getSupportedPreviewSizes(): List<PreviewSizeInfo>

    fun switchDevice(deviceId: Int): SdkResult<Unit>

    fun updateResolution(width: Int, height: Int): SdkResult<Unit>

    fun captureImage(path: String? = null, callback: CaptureCallback): SdkResult<Unit>

    fun startRecording(
        path: String? = null,
        durationInSec: Long = 0L,
        callback: CaptureCallback
    ): SdkResult<Unit>

    fun stopRecording(): SdkResult<Unit>
}


