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

class MS500Uvc(private val context: Context) {

    /** 主线程 handler，用于串联设备切换和权限请求后的异步操作。 */
    private val mainHandler = Handler(Looper.getMainLooper())
    /** 内部可写状态流。 */
    private val _stateFlow = MutableStateFlow<PreviewState>(PreviewState.Idle)
    /** 对外只读的预览状态流。 */
    val stateFlow: StateFlow<PreviewState> = _stateFlow.asStateFlow()

    /** libausbc 提供的多相机客户端，负责设备发现和权限申请。 */
    private var cameraClient: MultiCameraClient? = null
    /** 已发现设备对应的相机实例缓存，避免重复创建 CameraUVC。 */
    private val cameraMap = linkedMapOf<Int, MultiCameraClient.ICamera>()
    /** 当前正在使用或最近一次连接成功的相机实例。 */
    private var currentCamera: MultiCameraClient.ICamera? = null
    /** 当前相机对应的 USB 控制块。 */
    private var currentCtrlBlock: USBMonitor.UsbControlBlock? = null
    /** 当前预览输出目标。 */
    private var previewSurface: PreviewSurfaceAdapter? = null
    /** 目标预览宽度。 */
    private var previewWidth: Int = 1920
    /** 目标预览高度。 */
    private var previewHeight: Int = 1080
    /** 用户偏好的设备 id；切设备后优先选择它。 */
    private var preferredDeviceId: Int? = null
    /** 用户是否希望维持预览态，而不是仅完成初始化。 */
    private var desiredPreview: Boolean = false
    /** 当前正处于权限申请或打开流程中的设备 id，用于避免重复发起。 */
    private var openingDeviceId: Int? = null
    /** 当前已向底层发起 openCamera 的设备 id，用于避免重复打开同一相机。 */
    private var openRequestedDeviceId: Int? = null

    /** 当前设备 id；没有选中设备时为 null。 */
    val currentDeviceId: Int?
        get() = currentCamera?.getUsbDevice()?.deviceId

    /** 当前记录的目标预览尺寸。 */
    val currentPreviewSize: PreviewSizeInfo
        get() = PreviewSizeInfo(previewWidth, previewHeight)

    companion object {
        /** 构造默认的 UVC 打开参数，统一当前 SDK 的预览策略。 */
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

    /** 初始化或更新预览 Surface，并在需要时继续补齐打开流程。 */
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

    /** 开始预览；如果设备未连接或权限未完成，会进入等待态。 */
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

    /** 停止预览并关闭当前相机。 */
    fun stopPreview(): SdkResult<Unit> {
        desiredPreview = false
        openingDeviceId = null
        openRequestedDeviceId = null
        currentCamera?.closeCamera()
        _stateFlow.value = PreviewState.Stopped
        return SdkResult.success(Unit)
    }

    /** 返回当前底层相机是否处于已打开状态。 */
    fun isPreviewing(): Boolean = currentCamera?.isCameraOpened() == true

    /** 返回当前底层是否正在录像。 */
    fun isRecording(): Boolean = currentCamera?.isRecording() == true

    /** 返回当前 camera client 已发现的全部设备。 */
    fun getDeviceList(): List<PreviewDevice> {
        return (cameraClient?.getDeviceList() ?: mutableListOf()).map { it.toPreviewDevice() }
    }

    /** 返回当前相机支持的预览尺寸列表；未连接时返回空列表。 */
    fun getSupportedPreviewSizes(): List<PreviewSizeInfo> {
        return currentCamera?.getAllPreviewSizes()
            ?.map { PreviewSizeInfo(it.width, it.height) }
            ?.distinctBy { "${it.width}x${it.height}" }
            ?: emptyList()
    }

    /** 切换目标设备，并在短暂延迟后重新走权限与打开流程。 */
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

    /** 更新目标分辨率；若相机已打开，则立即通知底层重建预览。 */
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

    /** 拍照保存到指定路径。 */
    fun captureImage(path: String? = null, callback: CaptureCallback): SdkResult<Unit> {
        val camera = currentCamera
            ?: return SdkResult.failure(SdkError.NotConnected)
        if (!camera.isCameraOpened()) {
            return SdkResult.failure(SdkError.InvalidArgument("Preview is not started"))
        }
        camera.captureImage(callback.toAusbc(), path)
        return SdkResult.success(Unit)
    }

    /** 开始录像并保存到指定路径。 */
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

    /** 停止当前录像。 */
    fun stopRecording(): SdkResult<Unit> {
        val camera = currentCamera
            ?: return SdkResult.failure(SdkError.NotConnected)
        if (!camera.isRecording()) {
            return SdkResult.failure(SdkError.InvalidArgument("Video recording is not running"))
        }
        camera.captureVideoStop()
        return SdkResult.success(Unit)
    }

    /** 释放相机、USB 连接和 Surface 适配器等全部资源。 */
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

    /** 确保底层 MultiCameraClient 已注册监听，只初始化一次。 */
    private fun ensureClientRegistered() {
        if (cameraClient != null) {
            return
        }
        cameraClient = MultiCameraClient(context, object : IDeviceConnectCallBack {
            /** 设备插入时创建对应相机实例，并在需要时继续预览流程。 */
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

            /** 设备物理拔出时清理缓存，并根据期望状态决定是否继续等待设备。 */
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

            /** USB 权限通过后绑定控制块，并在需要时真正打开相机。 */
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

            /** 连接断开时关闭当前相机，并回到等待设备或停止状态。 */
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

            /** 权限被拒绝时，把状态切到错误态供上层提示。 */
            override fun onCancelDev(device: UsbDevice?) {
                if (desiredPreview) {
                    openingDeviceId = null
                    openRequestedDeviceId = null
                    _stateFlow.value = PreviewState.Error("USB permission denied")
                }
            }
        }).also { it.register() }
    }

    /** 优先选择用户指定设备，否则选择首个可用设备并发起权限申请。 */
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

    /** 在 Surface 和控制块都就绪后真正调用底层打开相机。 */
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

    /** 底层相机状态回调，负责把 libausbc 状态同步到 SDK 的 PreviewState。 */
    private val cameraStateCallback = object : ICameraStateCallBack {
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

    /** 将底层 UsbDevice 转成 SDK 对外设备模型。 */
    private fun UsbDevice.toPreviewDevice(): PreviewDevice {
        return PreviewDevice(
            deviceId = deviceId,
            displayName = displayName(),
            vendorId = vendorId,
            productId = productId
        )
    }

    /** 为 UI 提供更稳定的设备展示名称。 */
    private fun UsbDevice.displayName(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !productName.isNullOrBlank()) {
            "$productName ($deviceId)"
        } else {
            deviceName
        }
    }

    private fun CaptureCallback.toAusbc(): ICaptureCallBack {
        return object : ICaptureCallBack {
            override fun onBegin() = this@toAusbc.onBegin()

            override fun onError(error: String?) = this@toAusbc.onError(error)

            override fun onComplete(path: String?) = this@toAusbc.onComplete(path)
        }
    }
}
