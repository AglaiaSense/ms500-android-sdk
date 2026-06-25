package com.ms500.usbcam.api

import android.view.Surface
import com.ms500.usbcam.result.SdkResult
import kotlinx.coroutines.flow.StateFlow

/**
 * UVC preview state emitted by [UvcPreviewApi.stateFlow].
 */
sealed class PreviewState {
    /** Preview has not been initialized or is currently idle. */
    object Idle : PreviewState()

    /** Preview is waiting for a valid Android [Surface]. */
    object WaitingForSurface : PreviewState()

    /** Preview is waiting for an available UVC device. */
    object WaitingForDevice : PreviewState()

    /**
     * UVC device is being opened.
     *
     * @property deviceName Display name of the device being opened, may be null if not available.
     */
    data class Opening(val deviceName: String?) : PreviewState()

    /**
     * Preview frames are being rendered.
     *
     * @property width Active preview width in pixels.
     * @property height Active preview height in pixels.
     * @property deviceName Display name of the active device, may be null if not available.
     */
    data class Previewing(val width: Int, val height: Int, val deviceName: String?) : PreviewState()

    /** Preview has been stopped. */
    object Stopped : PreviewState()

    /**
     * Preview entered an error state.
     *
     * @property message Human-readable error description.
     */
    data class Error(val message: String) : PreviewState()
}

/**
 * UVC preview device information.
 *
 * @property deviceId SDK-local device identifier used by [UvcPreviewApi.switchDevice].
 * @property displayName Human-readable device name for UI lists.
 * @property vendorId USB vendor ID.
 * @property productId USB product ID.
 */
data class PreviewDevice(
    val deviceId: Int,
    val displayName: String,
    val vendorId: Int,
    val productId: Int
)

/**
 * Preview resolution information.
 *
 * @property width Width in pixels.
 * @property height Height in pixels.
 */
data class PreviewSizeInfo(
    val width: Int,
    val height: Int
) {
    /** Resolution label for UI display, for example `1920 x 1080`. */
    val label: String
        get() = "${width} x ${height}"
}

/**
 * Screenshot and recording callback interface.
 *
 * Callbacks are used for one capture/recording operation. The implementation reports start, error, and
 * completion events while the API method itself returns whether the request was accepted.
 */
interface CaptureCallback {
    /** Called when the capture or recording request begins. */
    fun onBegin()

    /**
     * Called when capture or recording fails.
     *
     * @param error Error information, may be null if the lower layer did not provide details.
     */
    fun onError(error: String?)

    /**
     * Called when capture or recording completes.
     *
     * @param path Saved file path, may be null when no output file was produced.
     */
    fun onComplete(path: String?)
}

/**
 * UVC preview API for preview rendering, image capture, recording, and device switching.
 *
 * Typical usage:
 * 1. Observe [stateFlow] for UI state.
 * 2. Call [initPreview] with a valid [Surface].
 * 3. Call [startPreview].
 * 4. Optionally call [captureImage], [startRecording], [stopRecording], [updateResolution], or
 *    [switchDevice].
 * 5. Call [stopPreview] or [release] when leaving the screen.
 */
interface UvcPreviewApi {

    /** Whether the current environment and attached device support UVC preview. */
    val isSupported: Boolean

    /** Whether preview frames are currently being rendered. */
    val isPreviewing: Boolean

    /** Whether video recording is currently active. */
    val isRecording: Boolean

    /** Current preview lifecycle state. UI should prefer this over polling booleans. */
    val stateFlow: StateFlow<PreviewState>

    /** Currently active preview device ID, or null when no device is selected/open. */
    val currentDeviceId: Int?

    /** Current preview resolution, or null before preview is initialized. */
    val currentPreviewSize: PreviewSizeInfo?

    /**
     * Initializes preview with a render [surface] and requested resolution.
     *
     * This prepares preview resources but does not necessarily start streaming frames; call
     * [startPreview] after successful initialization. The implementation may adjust the requested
     * resolution to a supported size.
     *
     * @param surface Android surface used for rendering preview frames.
     * @param width Requested preview width, default 1920.
     * @param height Requested preview height, default 1080.
     * @return Operation result indicating whether initialization was accepted.
     */
    fun initPreview(
        surface: Surface,
        width: Int = 1920,
        height: Int = 1080
    ): SdkResult<Unit>

    /**
     * Starts preview streaming on the initialized surface.
     *
     * @return Operation result. Observe [stateFlow] for the final preview state.
     */
    fun startPreview(): SdkResult<Unit>

    /**
     * Stops preview streaming while keeping the API object available for later reuse.
     *
     * @return Operation result. Active recording should be stopped before stopping preview.
     */
    fun stopPreview(): SdkResult<Unit>

    /**
     * Releases preview resources.
     *
     * Call this when the owning screen/service is destroyed. After release, callers should initialize
     * preview again before starting a new preview session.
     */
    fun release()

    /**
     * Gets the current list of available UVC devices.
     *
     * @return Device list, possibly empty when no supported device is attached.
     */
    fun getDeviceList(): List<PreviewDevice>

    /**
     * Gets supported preview resolutions for the current device.
     *
     * @return Resolution list, possibly empty before a device is selected/opened.
     */
    fun getSupportedPreviewSizes(): List<PreviewSizeInfo>

    /**
     * Switches preview to a specified UVC device.
     *
     * If preview is already active, the implementation may need to reopen the stream. Observe
     * [stateFlow] for intermediate opening or error states.
     *
     * @param deviceId Target device ID from [getDeviceList].
     * @return Operation result indicating whether the switch request was accepted.
     */
    fun switchDevice(deviceId: Int): SdkResult<Unit>

    /**
     * Updates preview resolution.
     *
     * The requested size should come from [getSupportedPreviewSizes] when possible. If preview is active,
     * changing resolution may restart the stream internally.
     *
     * @param width Requested width in pixels.
     * @param height Requested height in pixels.
     * @return Operation result indicating whether the resolution request was accepted.
     */
    fun updateResolution(width: Int, height: Int): SdkResult<Unit>

    /**
     * Captures a still image from the current preview stream.
     *
     * @param path Save path. When null, the implementation uses its internal default path policy.
     * @param callback Capture lifecycle callback.
     * @return Operation result indicating whether the capture request was accepted.
     */
    fun captureImage(path: String? = null, callback: CaptureCallback): SdkResult<Unit>

    /**
     * Starts video recording from the current preview stream.
     *
     * @param path Save path. When null, the implementation uses its internal default path policy.
     * @param durationInSec Recording duration in seconds. Use 0 for unlimited duration until
     * [stopRecording] is called.
     * @param callback Recording lifecycle callback.
     * @return Operation result indicating whether the recording request was accepted.
     */
    fun startRecording(
        path: String? = null,
        durationInSec: Long = 0L,
        callback: CaptureCallback
    ): SdkResult<Unit>

    /**
     * Stops active video recording.
     *
     * @return Operation result indicating whether the stop request was accepted.
     */
    fun stopRecording(): SdkResult<Unit>
}
