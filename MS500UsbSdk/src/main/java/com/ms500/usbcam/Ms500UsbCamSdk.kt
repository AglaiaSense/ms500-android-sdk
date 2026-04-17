package com.ms500.usbcam
import android.content.Context
import com.ms500.usbcam.api.CameraParamApi
import com.ms500.usbcam.api.CdcConnectApi
import com.ms500.usbcam.api.DeviceParamApi
import com.ms500.usbcam.api.GazeInfoApi
import com.ms500.usbcam.api.InferenceParamApi
import com.ms500.usbcam.api.InputTensorApi
import com.ms500.usbcam.api.PqConfigParamApi
import com.ms500.usbcam.api.PostprocessParamApi
import com.ms500.usbcam.api.UvcPreviewApi
import com.ms500.usbcam.uvc.UvcPreviewApiImpl
import com.ms500.usbcam.api.OtaParamApi
import com.ms500.usbcam.api.internal.CameraParamApiImpl
import com.ms500.usbcam.api.internal.CdcConnectApiImpl
import com.ms500.usbcam.api.internal.DeviceParamApiImpl
import com.ms500.usbcam.api.internal.GazeInfoApiImpl
import com.ms500.usbcam.api.internal.InferenceParamApiImpl
import com.ms500.usbcam.api.internal.InputTensorApiImpl
import com.ms500.usbcam.api.internal.PostprocessParamApiImpl
import com.ms500.usbcam.api.internal.PqConfigParamApiImpl
import com.ms500.usbcam.api.internal.OtaParamApiImpl
class MS500UsbCamSdk private constructor(
    context: Context
) {
    private val appContext = context.applicationContext
    private val cdcConnectApiImpl: CdcConnectApiImpl = CdcConnectApiImpl(
        appContext
    )
    val cdcConnect: CdcConnectApi = cdcConnectApiImpl
    val uvcPreview: UvcPreviewApi = UvcPreviewApiImpl(appContext)
    val cameraParam: CameraParamApi = CameraParamApiImpl(cdcConnectApiImpl)
    val postprocessParam: PostprocessParamApi = PostprocessParamApiImpl(cdcConnectApiImpl)
    val pqcongifParam: PqConfigParamApi = PqConfigParamApiImpl(cdcConnectApiImpl)
    val inferenceParam: InferenceParamApi = InferenceParamApiImpl(cdcConnectApiImpl)
    val gazeInfo: GazeInfoApi = GazeInfoApiImpl(cdcConnectApiImpl)
    val deviceParam: DeviceParamApi = DeviceParamApiImpl(cdcConnectApiImpl)
    val inputTensor: InputTensorApi = InputTensorApiImpl(cdcConnectApiImpl, deviceParam)
    val otaParam: OtaParamApi = OtaParamApiImpl(cdcConnectApiImpl, appContext.filesDir)
    /**
     * Stable method-based API for AAR consumers that should not rely on Kotlin synthetic property access.
     */
    fun cdcConnectApi(): CdcConnectApi = cdcConnect
    fun uvcPreviewApi(): UvcPreviewApi = uvcPreview
    fun cameraParamApi(): CameraParamApi = cameraParam
    fun postprocessParamApi(): PostprocessParamApi = postprocessParam
    fun pqConfigParamApi(): PqConfigParamApi = pqcongifParam
    fun inferenceParamApi(): InferenceParamApi = inferenceParam
    fun gazeInfoApi(): GazeInfoApi = gazeInfo
    fun deviceParamApi(): DeviceParamApi = deviceParam
    fun inputTensorApi(): InputTensorApi = inputTensor
    fun otaParamApi(): OtaParamApi = otaParam
    companion object {
        const val SDK_NAME = "ms500-usb-cam"
        const val SDK_VERSION = "1.0.0"
        const val DEFAULT_TIMEOUT_MS = 5000L
        fun create(context: Context): MS500UsbCamSdk {
            return MS500UsbCamSdk(context.applicationContext)
        }
    }
}