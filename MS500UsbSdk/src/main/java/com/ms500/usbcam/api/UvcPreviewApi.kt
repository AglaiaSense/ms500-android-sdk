package com.ms500.usbcam.api

import android.view.Surface
import com.ms500.usbcam.result.SdkResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Preview state.
 */
sealed class PreviewState {
    /** Idle state */
    object Idle : PreviewState()

    /** Waiting for Surface */
    object WaitingForSurface : PreviewState()

    /** Waiting for device */
    object WaitingForDevice : PreviewState()

    /**
     * Opening device.
     *
     * @property deviceName Device name, may be null
     */
    data class Opening(val deviceName: String?) : PreviewState()

    /**
     * Previewing.
     *
     * @property width Preview width
     * @property height Preview height
     * @property deviceName Device name, may be null
     */
    data class Previewing(val width: Int, val height: Int, val deviceName: String?) : PreviewState()

    /** Stopped */
    object Stopped : PreviewState()

    /**
     * Preview error.
     *
     * @property message Error description
     */
    data class Error(val message: String) : PreviewState()
}

/**
 * UVC preview device info.
 *
 * @property deviceId Device ID
 * @property displayName Display name
 * @property vendorId Vendor ID
 * @property productId Product ID
 */
data class PreviewDevice(
    val deviceId: Int,
    val displayName: String,
    val vendorId: Int,
    val productId: Int
)

/**
 * Preview resolution info.
 *
 * @property width Width
 * @property height Height
 */
data class PreviewSizeInfo(
    val width: Int,
    val height: Int
) {
    /**
     * Resolution label, e.g. "1920 x 1080".
     */
    val label: String
        get() = "${width} x ${height}"
}

/**
 * Screenshot/recording callback interface.
 */
interface CaptureCallback {
    /**
     * Called when capture begins.
     */
    fun onBegin()

    /**
     * Called when capture errors.
     *
     * @param error Error information, may be null
     */
    fun onError(error: String?)

    /**
     * Called when capture completes.
     *
     * @param path Save path, may be null
     */
    fun onComplete(path: String?)
}

/**
 * UVC preview API for managing preview, recording, and device switching.
 */
interface UvcPreviewApi {

    /**
     * Whether current device supports UVC preview.
     */
    val isSupported: Boolean

    /**
     * Whether currently previewing.
     */
    val isPreviewing: Boolean

    /**
     * Whether currently recording.
     */
    val isRecording: Boolean

    /**
     * Preview state flow (StateFlow).
     */
    val stateFlow: StateFlow<PreviewState>

    /**
     * Currently previewing device ID, may be null.
     */
    val currentDeviceId: Int?

    /**
     * Current preview resolution, may be null.
     */
    val currentPreviewSize: PreviewSizeInfo?

    /**
     * Initializes preview.
     *
     * @param surface Surface for rendering
     * @param width Preview width, default 1920
     * @param height Preview height, default 1080
     * @return Initialization result
     */
    fun initPreview(
        surface: Surface,
        width: Int = 1920,
        height: Int = 1080
    ): SdkResult<Unit>

    /**
     * Starts preview.
     *
     * @return Operation result
     */
    fun startPreview(): SdkResult<Unit>

    /**
     * Stops preview.
     *
     * @return Operation result
     */
    fun stopPreview(): SdkResult<Unit>

    /**
     * Releases preview resources.
     */
    fun release()

    /**
     * Gets available UVC device list.
     *
     * @return Device list
     */
    fun getDeviceList(): List<PreviewDevice>

    /**
     * Gets supported preview resolution list for current device.
     *
     * @return Resolution list
     */
    fun getSupportedPreviewSizes(): List<PreviewSizeInfo>

    /**
     * Switches to specified device.
     *
     * @param deviceId Target device ID
     * @return Operation result
     */
    fun switchDevice(deviceId: Int): SdkResult<Unit>

    /**
     * Updates preview resolution.
     *
     * @param width Width
     * @param height Height
     * @return Operation result
     */
    fun updateResolution(width: Int, height: Int): SdkResult<Unit>

    /**
     * Captures image.
     *
     * @param path Save path, null for internal default
     * @param callback Capture callback
     * @return Operation result
     */
    fun captureImage(path: String? = null, callback: CaptureCallback): SdkResult<Unit>

    /**
     * Starts recording.
     *
     * @param path Save path, null for internal default
     * @param durationInSec Recording duration in seconds, 0 for unlimited
     * @param callback Capture callback
     * @return Operation result
     */
    fun startRecording(
        path: String? = null,
        durationInSec: Long = 0L,
        callback: CaptureCallback
    ): SdkResult<Unit>

    /**
     * Stops recording.
     *
     * @return Operation result
     */
    fun stopRecording(): SdkResult<Unit>
}
