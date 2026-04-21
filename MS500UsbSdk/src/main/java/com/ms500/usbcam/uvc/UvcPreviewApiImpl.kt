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
 * Internal implementation class of [UvcPreviewApi].
 *
 * Through delegation [MS500Uvc] Completes all UVC Preview-related operations，
 * Shields underlying details externally，Provides type-safe API Entry。
 *
 * Internal implementation of [UvcPreviewApi].
 * Delegates all UVC preview operations to [MS500Uvc],
 * hiding underlying details and providing a type-safe API surface.
 *
 * Application context.
 */
internal class UvcPreviewApiImpl(
    context: Context
) : UvcPreviewApi {
    /** Real UVC Session implementation，Responsible for device、Surface And state machine orchestration。 */
    private val uvc = MS500Uvc(context.applicationContext)

    /** SDK Currently built into process UVC Capability，Therefore always returns true。 */
    override val isSupported: Boolean = true

    /** Whether underlying camera is already in open state。 */
    override val isPreviewing: Boolean
        get() = uvc.isPreviewing()

    /** Whether underlying camera is already in recording state。 */
    override val isRecording: Boolean
        get() = uvc.isRecording()

    /** Exposes preview state flow externally，For UI Or business layer subscription。 */
    override val stateFlow: StateFlow<PreviewState>
        get() = uvc.stateFlow

    /** Currently connected and selected device id。 */
    override val currentDeviceId: Int?
        get() = uvc.currentDeviceId

    /** Current preview size，Derived from UVC Internally recorded target resolution。 */
    override val currentPreviewSize: PreviewSizeInfo?
        get() = uvc.currentPreviewSize

    /**
     * Initializes or updates preview Surface。
     *
     * Initializes or updates the preview surface.
     *
     * Preview output surface.
     * Preview width.
     * Preview height.
     * [SdkResult] indicating success or failure.
     */
    override fun initPreview(surface: Surface, width: Int, height: Int): SdkResult<Unit> {
        return uvc.initPreview(surface, width, height)
    }

    /**
     * Requests to start preview.
     *
     * @return [SdkResult] indicating success or failure.
     */
    override fun startPreview(): SdkResult<Unit> {
        return uvc.startPreview()
    }

    /**
     * Requests to stop preview.
     *
     * @return [SdkResult] indicating success or failure.
     */
    override fun stopPreview(): SdkResult<Unit> {
        return uvc.stopPreview()
    }

    /**
     * Releases all preview-related resources.
     */
    override fun release() {
        uvc.release()
    }

    /**
     * Returns the list of currently discovered UVC devices.
     *
     * @return List of devices.
     */
    override fun getDeviceList(): List<PreviewDevice> {
        return uvc.getDeviceList()
    }

    /**
     * Returns preview resolutions supported by current camera。
     *
     * Returns the preview resolutions supported by the current camera.
     *
     * List of supported preview sizes.
     */
    override fun getSupportedPreviewSizes(): List<PreviewSizeInfo> {
        return uvc.getSupportedPreviewSizes()
    }

    /**
     * Switches to specified device and continues preview。
     *
     * Switches to the specified device and continues preview.
     *
     * Target device ID.
     * [SdkResult] indicating success or failure.
     */
    override fun switchDevice(deviceId: Int): SdkResult<Unit> {
        return uvc.switchDevice(deviceId)
    }

    /**
     * Updates the target preview resolution.
     *
     * @param width New width.
     * @param height New height.
     * @return [SdkResult] indicating success or failure.
     */
    override fun updateResolution(width: Int, height: Int): SdkResult<Unit> {
        return uvc.updateResolution(width, height)
    }

    /**
     * Captures photo and saves to specified path。
     *
     * Captures a still image and saves it to the given path.
     *
     * Save path, may be null.
     * Capture result callback.
     * [SdkResult] indicating success or failure.
     */
    override fun captureImage(path: String?, callback: CaptureCallback): SdkResult<Unit> {
        return uvc.captureImage(path, callback)
    }

    /**
     * Starts video recording and saves it to the given path.
     *
     * @param path Save path, may be null.
     * @param durationInSec Duration limit in seconds, 0 for unlimited.
     * @param callback Capture result callback.
     * @return [SdkResult] indicating success or failure.
     */
    override fun startRecording(path: String?, durationInSec: Long, callback: CaptureCallback): SdkResult<Unit> {
        return uvc.startRecording(path, durationInSec, callback)
    }

    /**
     * Stops current recording。
     *
     * Stops the current video recording.
     *
     * [SdkResult] indicating success or failure.
     */
    override fun stopRecording(): SdkResult<Unit> {
        return uvc.stopRecording()
    }
}
