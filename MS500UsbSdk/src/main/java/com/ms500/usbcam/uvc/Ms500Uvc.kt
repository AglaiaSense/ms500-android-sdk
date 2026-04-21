package com.ms500.usbcam.uvc

import android.content.Context
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.jiangdg.usb.USBMonitor
import com.ms500.ausbc.MultiCameraClient
import com.ms500.ausbc.callback.ICaptureCallBack
import com.ms500.ausbc.callback.ICameraStateCallBack
import com.ms500.ausbc.callback.IDeviceConnectCallBack
import com.ms500.ausbc.camera.CameraUVC
import com.ms500.usbcam.api.CaptureCallback
import com.ms500.ausbc.camera.bean.CameraRequest
import com.ms500.ausbc.render.env.RotateType
import com.ms500.usbcam.api.PreviewDevice
import com.ms500.usbcam.api.PreviewSizeInfo
import com.ms500.usbcam.api.PreviewState
import com.ms500.usbcam.result.SdkResult
import com.ms500.usbcam.result.SdkError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MS500 UVC Camera core management class。
 *
 * Responsible for USB Device discovery、Permission application、Camera open/Close、Preview state machine maintenance，
 * And photo capture/Unified entry for recording and other functions。
 *
 * Core manager for MS500 UVC cameras.
 * Handles USB device discovery, permission requests, camera open/close,
 * preview state machine, and capture/recording operations.
 *
 * Application context.
 */
class MS500Uvc(private val context: Context) {

    /** Main thread handler，Used to chain async operations after device switching and permission requests。 */
    private val mainHandler = Handler(Looper.getMainLooper())
    /** Internal mutable state flow。 */
    private val _stateFlow = MutableStateFlow<PreviewState>(PreviewState.Idle)
    /** Public read-only preview state flow。 */
    val stateFlow: StateFlow<PreviewState> = _stateFlow.asStateFlow()

    /** libausbc Provided multi-camera client，Responsible for device discovery and permission application。 */
    private var cameraClient: MultiCameraClient? = null
    /** Camera instance cache for discovered devices，Avoids repeated creation CameraUVC。 */
    private val cameraMap = linkedMapOf<Int, MultiCameraClient.ICamera>()
    /** Currently used or most recently successfully connected camera instance。 */
    private var currentCamera: MultiCameraClient.ICamera? = null
    /** Currently corresponding to camera USB Control block。 */
    private var currentCtrlBlock: USBMonitor.UsbControlBlock? = null
    /** Current preview output target。 */
    private var previewSurface: PreviewSurfaceAdapter? = null
    /** Target preview width. */
    private var previewWidth: Int = 1920
    /** Target preview height. */
    private var previewHeight: Int = 1080
    /** User-preferred device id；Prioritizes it after device switching。 */
    private var preferredDeviceId: Int? = null
    /** Whether user wants to maintain preview state，Rather than just completing initialization。 */
    private var desiredPreview: Boolean = false
    /** Device currently in permission application or open process id，Used to avoid duplicate initiation。 */
    private var openingDeviceId: Int? = null
    /** Currently initiated to underlying openCamera device id，Used to avoid reopening the same camera。 */
    private var openRequestedDeviceId: Int? = null

    /** Current device id；When no device is selected null。 */
    val currentDeviceId: Int?
        get() = currentCamera?.getUsbDevice()?.deviceId

    /** Currently recorded target preview size。 */
    val currentPreviewSize: PreviewSizeInfo
        get() = PreviewSizeInfo(previewWidth, previewHeight)

    /**
     * Companion object，Provides default camera request parameter construction。
     *
     * Companion object providing default camera request parameters.
     */
    companion object {
        /**
         * Constructs default UVC Open parameters，Unifies current SDK preview strategy。
         *
         * Builds the default UVC camera request for consistent preview behavior.
         *
         * Preview width.
         * Preview height.
         * Configured [CameraRequest].
         */
        fun defaultCameraRequest(width: Int = 1920, height: Int = 1080): CameraRequest {
            return CameraRequest.Builder()
                .setPreviewWidth(width)
                .setPreviewHeight(height)
                .setRenderMode(CameraRequest.RenderMode.OPENGL)
                .setDefaultRotateType(RotateType.ANGLE_0)
                .setAudioSource(CameraRequest.AudioSource.SOURCE_SYS_MIC)
                .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
                .setAspectRatioShow(true)
                .setCaptureRawImage(false)
                .setRawPreviewData(false)
                .create()
        }
    }

    /**
     * Initializes or updates preview Surface，And continues to complete the open flow when needed。
     *
     * Initializes or updates the preview surface and resumes open flow when needed.
     *
     * Preview output surface.
     * Preview width.
     * Preview height.
     * [SdkResult] indicating success or failure.
     */
    fun initPreview(surface: Surface, width: Int = 1920, height: Int = 1080): SdkResult<Unit> {
        if (!surface.isValid) {
            return SdkResult.failure(SdkError.InvalidArgument("Preview surface is invalid"))
        }
        previewWidth = width.coerceAtLeast(1)
        previewHeight = height.coerceAtLeast(1)
        if (previewSurface == null) {
            previewSurface = PreviewSurfaceAdapter(surface, previewWidth, previewHeight)
        } else {
            previewSurface?.update(surface, previewWidth, previewHeight)
        }
        ensureClientRegistered()
        if (!desiredPreview) {
            _stateFlow.value = PreviewState.Stopped
            return SdkResult.success(Unit)
        }
        if (currentCamera?.isCameraOpened() == true || _stateFlow.value is PreviewState.Previewing) {
            return SdkResult.success(Unit)
        }
        if (_stateFlow.value is PreviewState.Opening && currentCamera == null) {
            return SdkResult.success(Unit)
        }
        currentCamera?.let {
            openCurrentCamera(it)
        } ?: requestPreferredOrFirstDevice()
        return SdkResult.success(Unit)
    }

    /**
     * Starts preview；If device is not connected or permission is incomplete，Will enter waiting state。
     *
     * Starts preview; enters waiting state if the device is not connected or permission is pending.
     *
     * [SdkResult] indicating success or failure.
     */
    fun startPreview(): SdkResult<Unit> {
        val targetSurface = previewSurface
            ?: return SdkResult.failure(SdkError.InvalidArgument("Preview surface not initialized"))
        desiredPreview = true
        ensureClientRegistered()
        if (currentCamera?.isCameraOpened() == true) {
            _stateFlow.value = PreviewState.Previewing(previewWidth, previewHeight, currentCamera?.getUsbDevice()?.displayName())
            return SdkResult.success(Unit)
        }
        if (targetSurface.getSurface() == null) {
            _stateFlow.value = PreviewState.WaitingForSurface
            return SdkResult.failure(SdkError.InvalidArgument("Preview surface is not available"))
        }
        if (_stateFlow.value is PreviewState.Opening && currentCamera == null) {
            return SdkResult.success(Unit)
        }
        currentCamera?.let {
            openCurrentCamera(it)
            return SdkResult.success(Unit)
        }
        requestPreferredOrFirstDevice()
        return SdkResult.success(Unit)
    }

    /**
     * Stops preview and closes the current camera.
     *
     * @return [SdkResult] indicating success.
     */
    fun stopPreview(): SdkResult<Unit> {
        desiredPreview = false
        openingDeviceId = null
        openRequestedDeviceId = null
        currentCamera?.closeCamera()
        _stateFlow.value = PreviewState.Stopped
        return SdkResult.success(Unit)
    }

    /**
     * Returns whether the underlying camera is currently in open state。
     *
     * Returns whether the underlying camera is currently opened.
     *
     * true if previewing.
     */
    fun isPreviewing(): Boolean = currentCamera?.isCameraOpened() == true

    /**
     * Returns whether the underlying layer is currently recording。
     *
     * Returns whether the underlying camera is currently recording video.
     *
     * true if recording.
     */
    fun isRecording(): Boolean = currentCamera?.isRecording() == true

    /**
     * Returns all devices discovered by the camera client.
     *
     * @return List of devices.
     */
    fun getDeviceList(): List<PreviewDevice> {
        return (cameraClient?.getDeviceList() ?: mutableListOf()).map { it.toPreviewDevice() }
    }

    /**
     * Returns preview size list supported by current camera；Returns empty list when not connected。
     *
     * Returns supported preview sizes for the current camera; empty if not connected.
     *
     * List of supported preview sizes.
     */
    fun getSupportedPreviewSizes(): List<PreviewSizeInfo> {
        return currentCamera?.getAllPreviewSizes()
            ?.map { PreviewSizeInfo(it.width, it.height) }
            ?.distinctBy { "${it.width}x${it.height}" }
            ?: emptyList()
    }

    /**
     * Switches target device，And re-enters permission and open flow after a short delay。
     *
     * Switches to the target device and re-runs permission and open flow after a short delay.
     *
     * Target device ID.
     * [SdkResult] indicating success or failure.
     */
    fun switchDevice(deviceId: Int): SdkResult<Unit> {
        preferredDeviceId = deviceId
        val device = (cameraClient?.getDeviceList() ?: mutableListOf()).firstOrNull { it.deviceId == deviceId }
            ?: return SdkResult.failure(SdkError.InvalidArgument("Preview device $deviceId not found"))
        desiredPreview = true
        if (currentDeviceId == deviceId && isPreviewing()) {
            return SdkResult.success(Unit)
        }
        currentCamera?.closeCamera()
        currentCamera = null
        currentCtrlBlock = null
        openingDeviceId = device.deviceId
        openRequestedDeviceId = null
        _stateFlow.value = PreviewState.Opening(device.displayName())
        mainHandler.postDelayed({ requestPreferredOrFirstDevice() }, 300)
        return SdkResult.success(Unit)
    }

    /**
     * Updates target resolution；If camera is already open，Then immediately notifies underlying to rebuild preview。
     *
     * Updates the target resolution; notifies the underlying camera to rebuild preview if already open.
     *
     * New width.
     * New height.
     * [SdkResult] indicating success or failure.
     */
    fun updateResolution(width: Int, height: Int): SdkResult<Unit> {
        previewWidth = width.coerceAtLeast(1)
        previewHeight = height.coerceAtLeast(1)
        previewSurface?.setAspectRatio(previewWidth, previewHeight)
        val camera = currentCamera ?: return SdkResult.success(Unit)
        if (!camera.isCameraOpened()) {
            return SdkResult.success(Unit)
        }
        camera.updateResolution(previewWidth, previewHeight)
        _stateFlow.value = PreviewState.Opening(camera.getUsbDevice().displayName())
        return SdkResult.success(Unit)
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
    fun captureImage(path: String? = null, callback: CaptureCallback): SdkResult<Unit> {
        val camera = currentCamera
            ?: return SdkResult.failure(SdkError.NotConnected)
        if (!camera.isCameraOpened()) {
            return SdkResult.failure(SdkError.InvalidArgument("Preview is not started"))
        }
        camera.captureImage(callback.toAusbc(), path)
        return SdkResult.success(Unit)
    }

    /**
     * Starts video recording and saves it to the given path.
     *
     * @param path Save path, may be null.
     * @param durationInSec Duration limit in seconds, 0 for unlimited.
     * @param callback Capture result callback.
     * @return [SdkResult] indicating success or failure.
     */
    fun startRecording(path: String? = null, durationInSec: Long = 0L, callback: CaptureCallback): SdkResult<Unit> {
        val camera = currentCamera
            ?: return SdkResult.failure(SdkError.NotConnected)
        if (!camera.isCameraOpened()) {
            return SdkResult.failure(SdkError.InvalidArgument("Preview is not started"))
        }
        if (camera.isRecording()) {
            return SdkResult.failure(SdkError.InvalidArgument("Video recording is already running"))
        }
        camera.captureVideoStart(callback.toAusbc(), path, durationInSec)
        return SdkResult.success(Unit)
    }

    /**
     * Stops current recording。
     *
     * Stops the current video recording.
     *
     * [SdkResult] indicating success or failure.
     */
    fun stopRecording(): SdkResult<Unit> {
        val camera = currentCamera
            ?: return SdkResult.failure(SdkError.NotConnected)
        if (!camera.isRecording()) {
            return SdkResult.failure(SdkError.InvalidArgument("Video recording is not running"))
        }
        camera.captureVideoStop()
        return SdkResult.success(Unit)
    }

    /**
     * Releases camera、USB Connection and Surface Adapter and all other resources。
     *
     * Releases all resources including camera, USB connection, and surface adapter.
     */
    fun release() {
        desiredPreview = false
        openingDeviceId = null
        openRequestedDeviceId = null
        cameraMap.values.forEach { camera ->
            camera.setCameraStateCallBack(null)
            camera.closeCamera()
            camera.setUsbControlBlock(null)
        }
        cameraMap.clear()
        currentCamera = null
        currentCtrlBlock = null
        cameraClient?.unRegister()
        cameraClient?.destroy()
        cameraClient = null
        previewSurface = null
        _stateFlow.value = PreviewState.Idle
    }

    /**
     * Ensures underlying MultiCameraClient Already registered listener，Only initializes once。
     *
     * Ensures the underlying [MultiCameraClient] is registered; initializes only once.
     */
    private fun ensureClientRegistered() {
        if (cameraClient != null) {
            return
        }
        cameraClient = MultiCameraClient(context, object : IDeviceConnectCallBack {
            /**
             * Creates corresponding camera instance when device is inserted，And continues preview flow when needed。
             *
             * Called when a device is attached.
             *
             * Attached USB device.
             */
            override fun onAttachDev(device: UsbDevice?) {
                device ?: return
                if (!cameraMap.containsKey(device.deviceId)) {
                    cameraMap[device.deviceId] = CameraUVC(context, device).also {
                        it.setCameraStateCallBack(cameraStateCallback)
                    }
                }
                if (desiredPreview && currentCamera == null && _stateFlow.value !is PreviewState.Opening) {
                    mainHandler.post { requestPreferredOrFirstDevice() }
                }
            }

            /**
             * Cleans up cache when device is physically removed，And decides whether to continue waiting for device based on desired state。
             *
             * Called when a device is detached.
             *
             * Detached USB device.
             */
            override fun onDetachDec(device: UsbDevice?) {
                val detachedId = device?.deviceId ?: return
                cameraMap.remove(detachedId)?.setUsbControlBlock(null)
                if (currentDeviceId == detachedId) {
                    currentCamera = null
                    currentCtrlBlock = null
                    openingDeviceId = null
                    openRequestedDeviceId = null
                    if (desiredPreview) {
                        _stateFlow.value = PreviewState.WaitingForDevice
                        mainHandler.postDelayed({ requestPreferredOrFirstDevice() }, 300)
                    } else {
                        _stateFlow.value = PreviewState.Stopped
                    }
                }
            }

            /**
             * USB Binds control block after permission is granted，And truly opens camera when needed。
             *
             * Called when the device is connected (permission granted).
             *
             * Connected USB device.
             * USB control block.
             */
            override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                device ?: return
                ctrlBlock ?: return
                val camera = cameraMap[device.deviceId] ?: CameraUVC(context, device).also {
                    cameraMap[device.deviceId] = it
                }
                currentCamera = camera
                currentCtrlBlock = ctrlBlock
                camera.setUsbControlBlock(ctrlBlock)
                camera.setCameraStateCallBack(cameraStateCallback)
                if (desiredPreview) {
                    openCurrentCamera(camera)
                }
            }

            /**
             * Called when the device is disconnected.
             *
             * @param device Disconnected USB device.
             * @param ctrlBlock USB control block.
             */
            override fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                val disconnectedId = device?.deviceId ?: return
                if (currentDeviceId == disconnectedId) {
                    currentCamera?.closeCamera()
                    currentCamera = null
                    currentCtrlBlock = null
                    openingDeviceId = null
                    openRequestedDeviceId = null
                    if (desiredPreview) {
                        _stateFlow.value = PreviewState.WaitingForDevice
                        mainHandler.postDelayed({ requestPreferredOrFirstDevice() }, 300)
                    } else {
                        _stateFlow.value = PreviewState.Stopped
                    }
                }
            }

            /**
             * When permission is denied，Switches state to error state for upper layer prompting。
             *
             * Called when USB permission is cancelled/denied.
             *
             * USB device whose permission was denied.
             */
            override fun onCancelDev(device: UsbDevice?) {
                if (desiredPreview) {
                    openingDeviceId = null
                    openRequestedDeviceId = null
                    _stateFlow.value = PreviewState.Error("USB permission denied")
                }
            }
        }).also { it.register() }
    }

    /**
     * Prioritizes user-specified device，Otherwise selects first available device and initiates permission request。
     *
     * Prefers the user-specified device, otherwise selects the first available device and requests permission.
     */
    private fun requestPreferredOrFirstDevice() {
        if (!desiredPreview) {
            return
        }
        val devices = cameraClient?.getDeviceList() ?: mutableListOf()
        if (devices.isEmpty()) {
            openingDeviceId = null
            _stateFlow.value = PreviewState.WaitingForDevice
            return
        }
        val target = preferredDeviceId?.let { preferred ->
            devices.firstOrNull { it.deviceId == preferred }
        } ?: devices.firstOrNull()
        if (target == null) {
            openingDeviceId = null
            _stateFlow.value = PreviewState.WaitingForDevice
            return
        }
        if (openingDeviceId == target.deviceId && _stateFlow.value is PreviewState.Opening) {
            return
        }
        openingDeviceId = target.deviceId
        _stateFlow.value = PreviewState.Opening(target.displayName())
        cameraClient?.requestPermission(target)
    }

    /**
     * In Surface Truly calls underlying to open camera when both Surface and control block are ready。
     *
     * Actually opens the underlying camera once both surface and control block are ready.
     *
     * Camera instance to open.
     */
    private fun openCurrentCamera(camera: MultiCameraClient.ICamera) {
        val targetSurface = previewSurface
        val deviceId = camera.getUsbDevice().deviceId
        if (targetSurface == null || targetSurface.getSurface() == null) {
            openingDeviceId = null
            openRequestedDeviceId = null
            _stateFlow.value = PreviewState.WaitingForSurface
            return
        }
        if (camera.isCameraOpened()) {
            openingDeviceId = null
            openRequestedDeviceId = null
            _stateFlow.value = PreviewState.Previewing(previewWidth, previewHeight, camera.getUsbDevice().displayName())
            return
        }
        if (openRequestedDeviceId == deviceId && _stateFlow.value is PreviewState.Opening) {
            return
        }
        openRequestedDeviceId = deviceId
        _stateFlow.value = PreviewState.Opening(camera.getUsbDevice().displayName())
        camera.openCamera(targetSurface, defaultCameraRequest(previewWidth, previewHeight))
    }

    /**
     * Underlying camera state callback，Responsible for libausbc State synchronized to SDK of PreviewState。
     *
     * Underlying camera state callback that syncs libausbc states to SDK [PreviewState].
     */
    private val cameraStateCallback = object : ICameraStateCallBack {
        /**
         * Camera state change callback。
         *
         * Called when the camera state changes.
         *
         * Camera instance whose state changed.
         * State code.
         * Additional message.
         */
        override fun onCameraState(
            self: MultiCameraClient.ICamera,
            code: ICameraStateCallBack.State,
            msg: String?
        ) {
            when (code) {
                ICameraStateCallBack.State.OPENED -> {
                    currentCamera = self
                    openingDeviceId = null
                    openRequestedDeviceId = null
                    _stateFlow.value = PreviewState.Previewing(previewWidth, previewHeight, self.getUsbDevice().displayName())
                }
                ICameraStateCallBack.State.CLOSED -> {
                    openingDeviceId = null
                    openRequestedDeviceId = null
                    _stateFlow.value = PreviewState.Stopped
                }
                ICameraStateCallBack.State.ERROR -> {
                    openingDeviceId = null
                    openRequestedDeviceId = null
                    _stateFlow.value = PreviewState.Error(msg ?: "Unknown preview error")
                }
            }
        }
    }

    /**
     * Converts underlying UsbDevice Convert to SDK External device model。
     *
     * Converts an underlying [UsbDevice] to the SDK external device model.
     *
     * [PreviewDevice] instance.
     */
    private fun UsbDevice.toPreviewDevice(): PreviewDevice {
        return PreviewDevice(
            deviceId = deviceId,
            displayName = displayName(),
            vendorId = vendorId,
            productId = productId
        )
    }

    /**
     * Provides a stable display name for UI.
     *
     * @return Display name.
     */
    private fun UsbDevice.displayName(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !productName.isNullOrBlank()) {
            "$productName ($deviceId)"
        } else {
            deviceName
        }
    }

    /**
     * Converts an SDK [CaptureCallback] to the underlying [ICaptureCallBack].
     *
     * @return Underlying callback instance.
     */
    private fun CaptureCallback.toAusbc(): ICaptureCallBack {
        return object : ICaptureCallBack {
            override fun onBegin() = this@toAusbc.onBegin()

            override fun onError(error: String?) = this@toAusbc.onError(error)

            override fun onComplete(path: String?) = this@toAusbc.onComplete(path)
        }
    }
}
