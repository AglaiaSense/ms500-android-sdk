package com.ms500.usbcam.uvc

import android.content.Context
import android.view.Surface
import com.ms500.usbcam.api.CaptureCallback
import com.ms500.usbcam.api.PreviewDevice
import com.ms500.usbcam.api.PreviewSizeInfo
import com.ms500.usbcam.api.PreviewState
import com.ms500.usbcam.api.UvcPreviewApi
import com.ms500.usbcam.result.SdkResult
import kotlinx.coroutines.flow.StateFlow

internal class UvcPreviewApiImpl(
    context: Context
) : UvcPreviewApi {
    /** 真实 UVC 会话实现，负责设备、Surface 和状态机编排。 */
    private val uvc = MS500Uvc(context.applicationContext)

    /** SDK 当前进程已内置 UVC 能力，因此始终返回 true。 */
    override val isSupported: Boolean = true

    /** 是否已有底层相机处于打开态。 */
    override val isPreviewing: Boolean
        get() = uvc.isPreviewing()

    override val isRecording: Boolean
        get() = uvc.isRecording()

    /** 对外暴露预览状态流，供 UI 或业务层订阅。 */
    override val stateFlow: StateFlow<PreviewState>
        get() = uvc.stateFlow

    /** 当前已连接并被选中的设备 id。 */
    override val currentDeviceId: Int?
        get() = uvc.currentDeviceId

    /** 当前预览尺寸，来源于 UVC 内部记录的目标分辨率。 */
    override val currentPreviewSize: PreviewSizeInfo?
        get() = uvc.currentPreviewSize

    /** 初始化或更新预览 Surface。 */
    override fun initPreview(surface: Surface, width: Int, height: Int): SdkResult<Unit> {
        return uvc.initPreview(surface, width, height)
    }

    /** 请求开始预览。 */
    override fun startPreview(): SdkResult<Unit> {
        return uvc.startPreview()
    }

    /** 请求停止预览。 */
    override fun stopPreview(): SdkResult<Unit> {
        return uvc.stopPreview()
    }

    /** 释放预览相关资源。 */
    override fun release() {
        uvc.release()
    }

    /** 返回当前已发现的 UVC 设备列表。 */
    override fun getDeviceList(): List<PreviewDevice> {
        return uvc.getDeviceList()
    }

    /** 返回当前相机支持的预览分辨率。 */
    override fun getSupportedPreviewSizes(): List<PreviewSizeInfo> {
        return uvc.getSupportedPreviewSizes()
    }

    /** 切换到指定设备继续预览。 */
    override fun switchDevice(deviceId: Int): SdkResult<Unit> {
        return uvc.switchDevice(deviceId)
    }

    /** 更新目标预览分辨率。 */
    override fun updateResolution(width: Int, height: Int): SdkResult<Unit> {
        return uvc.updateResolution(width, height)
    }

    override fun captureImage(path: String?, callback: CaptureCallback): SdkResult<Unit> {
        return uvc.captureImage(path, callback)
    }

    override fun startRecording(path: String?, durationInSec: Long, callback: CaptureCallback): SdkResult<Unit> {
        return uvc.startRecording(path, durationInSec, callback)
    }

    override fun stopRecording(): SdkResult<Unit> {
        return uvc.stopRecording()
    }
}
