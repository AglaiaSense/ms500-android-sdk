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

/**
 * [UvcPreviewApi] 的内部实现类。
 *
 * 通过委托 [MS500Uvc] 完成所有 UVC 预览相关操作，
 * 对外屏蔽底层细节，提供类型安全的 API 入口。
 *
 * Internal implementation of [UvcPreviewApi].
 * Delegates all UVC preview operations to [MS500Uvc],
 * hiding underlying details and providing a type-safe API surface.
 *
 * @param context 应用上下文 / Application context.
 */
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

    /** 是否已有底层相机处于录像态。 */
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

    /**
     * 初始化或更新预览 Surface。
     *
     * Initializes or updates the preview surface.
     *
     * @param surface 预览输出 Surface / Preview output surface.
     * @param width   预览宽度 / Preview width.
     * @param height  预览高度 / Preview height.
     * @return [SdkResult] 表示成功或失败 / [SdkResult] indicating success or failure.
     */
    override fun initPreview(surface: Surface, width: Int, height: Int): SdkResult<Unit> {
        return uvc.initPreview(surface, width, height)
    }

    /**
     * 请求开始预览。
     *
     * Requests to start preview.
     *
     * @return [SdkResult] 表示成功或失败 / [SdkResult] indicating success or failure.
     */
    override fun startPreview(): SdkResult<Unit> {
        return uvc.startPreview()
    }

    /**
     * 请求停止预览。
     *
     * Requests to stop preview.
     *
     * @return [SdkResult] 表示成功或失败 / [SdkResult] indicating success or failure.
     */
    override fun stopPreview(): SdkResult<Unit> {
        return uvc.stopPreview()
    }

    /**
     * 释放预览相关资源。
     *
     * Releases all preview-related resources.
     */
    override fun release() {
        uvc.release()
    }

    /**
     * 返回当前已发现的 UVC 设备列表。
     *
     * Returns the list of currently discovered UVC devices.
     *
     * @return 设备列表 / List of devices.
     */
    override fun getDeviceList(): List<PreviewDevice> {
        return uvc.getDeviceList()
    }

    /**
     * 返回当前相机支持的预览分辨率。
     *
     * Returns the preview resolutions supported by the current camera.
     *
     * @return 支持的预览尺寸列表 / List of supported preview sizes.
     */
    override fun getSupportedPreviewSizes(): List<PreviewSizeInfo> {
        return uvc.getSupportedPreviewSizes()
    }

    /**
     * 切换到指定设备继续预览。
     *
     * Switches to the specified device and continues preview.
     *
     * @param deviceId 目标设备 id / Target device ID.
     * @return [SdkResult] 表示成功或失败 / [SdkResult] indicating success or failure.
     */
    override fun switchDevice(deviceId: Int): SdkResult<Unit> {
        return uvc.switchDevice(deviceId)
    }

    /**
     * 更新目标预览分辨率。
     *
     * Updates the target preview resolution.
     *
     * @param width  新宽度 / New width.
     * @param height 新高度 / New height.
     * @return [SdkResult] 表示成功或失败 / [SdkResult] indicating success or failure.
     */
    override fun updateResolution(width: Int, height: Int): SdkResult<Unit> {
        return uvc.updateResolution(width, height)
    }

    /**
     * 拍照并保存到指定路径。
     *
     * Captures a still image and saves it to the given path.
     *
     * @param path     保存路径，可为 null / Save path, may be null.
     * @param callback 捕获结果回调 / Capture result callback.
     * @return [SdkResult] 表示成功或失败 / [SdkResult] indicating success or failure.
     */
    override fun captureImage(path: String?, callback: CaptureCallback): SdkResult<Unit> {
        return uvc.captureImage(path, callback)
    }

    /**
     * 开始录像并保存到指定路径。
     *
     * Starts video recording and saves it to the given path.
     *
     * @param path          保存路径，可为 null / Save path, may be null.
     * @param durationInSec 录制时长限制（秒），0 表示无限制 / Duration limit in seconds, 0 for unlimited.
     * @param callback      捕获结果回调 / Capture result callback.
     * @return [SdkResult] 表示成功或失败 / [SdkResult] indicating success or failure.
     */
    override fun startRecording(path: String?, durationInSec: Long, callback: CaptureCallback): SdkResult<Unit> {
        return uvc.startRecording(path, durationInSec, callback)
    }

    /**
     * 停止当前录像。
     *
     * Stops the current video recording.
     *
     * @return [SdkResult] 表示成功或失败 / [SdkResult] indicating success or failure.
     */
    override fun stopRecording(): SdkResult<Unit> {
        return uvc.stopRecording()
    }
}
