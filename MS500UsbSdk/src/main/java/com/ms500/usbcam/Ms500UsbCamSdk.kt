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

/**
 * MS500 USB 相机 SDK 统一入口类 / SDK entry point for MS500 USB camera.
 *
 * 采用单例模式（通过 [create] 工厂方法获取实例），内部聚合了 CDC 连接、UVC 预览、参数配置、
 * 推理输入、OTA 升级等全部功能模块。外部只需持有本类实例即可访问各子模块 API。
 *
 * Uses a singleton-like pattern (obtain instance via [create]). Internally aggregates all
 * functional modules such as CDC connection, UVC preview, parameter configuration,
 * inference input, OTA update, etc.
 */
class MS500UsbCamSdk private constructor(
    context: Context
) {
    private val appContext = context.applicationContext
    private val cdcConnectApiImpl: CdcConnectApiImpl = CdcConnectApiImpl(
        appContext
    )

    /** CDC 连接模块 / CDC connection module. */
    val cdcConnect: CdcConnectApi = cdcConnectApiImpl

    /** UVC 预览模块 / UVC preview module. */
    val uvcPreview: UvcPreviewApi = UvcPreviewApiImpl(appContext)

    /** 相机参数模块 / Camera parameter module. */
    val cameraParam: CameraParamApi = CameraParamApiImpl(cdcConnectApiImpl)

    /** 后处理参数模块 / Post-process parameter module. */
    val postprocessParam: PostprocessParamApi = PostprocessParamApiImpl(cdcConnectApiImpl)

    /** PQ 配置参数模块 / PQ configuration parameter module. */
    val pqcongifParam: PqConfigParamApi = PqConfigParamApiImpl(cdcConnectApiImpl)

    /** 推理参数模块 / Inference parameter module. */
    val inferenceParam: InferenceParamApi = InferenceParamApiImpl(cdcConnectApiImpl)

    /**  gaze 信息模块 / Gaze information module. */
    val gazeInfo: GazeInfoApi = GazeInfoApiImpl(cdcConnectApiImpl)

    /** 设备参数模块 / Device parameter module. */
    val deviceParam: DeviceParamApi = DeviceParamApiImpl(cdcConnectApiImpl)

    /** 输入张量模块 / Input tensor module. */
    val inputTensor: InputTensorApi = InputTensorApiImpl(cdcConnectApiImpl, deviceParam)

    /** OTA 参数模块 / OTA parameter module. */
    val otaParam: OtaParamApi = OtaParamApiImpl(cdcConnectApiImpl, appContext.filesDir)

    /**
     * Stable method-based API for AAR consumers that should not rely on Kotlin synthetic property access.
     */

    /**
     * 获取 CDC 连接 API（Java 友好方法）/ Returns the CDC connection API (Java-friendly accessor).
     */
    fun cdcConnectApi(): CdcConnectApi = cdcConnect

    /**
     * 获取 UVC 预览 API（Java 友好方法）/ Returns the UVC preview API (Java-friendly accessor).
     */
    fun uvcPreviewApi(): UvcPreviewApi = uvcPreview

    /**
     * 获取相机参数 API（Java 友好方法）/ Returns the camera parameter API (Java-friendly accessor).
     */
    fun cameraParamApi(): CameraParamApi = cameraParam

    /**
     * 获取后处理参数 API（Java 友好方法）/ Returns the post-process parameter API (Java-friendly accessor).
     */
    fun postprocessParamApi(): PostprocessParamApi = postprocessParam

    /**
     * 获取 PQ 配置参数 API（Java 友好方法）/ Returns the PQ configuration parameter API (Java-friendly accessor).
     */
    fun pqConfigParamApi(): PqConfigParamApi = pqcongifParam

    /**
     * 获取推理参数 API（Java 友好方法）/ Returns the inference parameter API (Java-friendly accessor).
     */
    fun inferenceParamApi(): InferenceParamApi = inferenceParam

    /**
     * 获取 gaze 信息 API（Java 友好方法）/ Returns the gaze information API (Java-friendly accessor).
     */
    fun gazeInfoApi(): GazeInfoApi = gazeInfo

    /**
     * 获取设备参数 API（Java 友好方法）/ Returns the device parameter API (Java-friendly accessor).
     */
    fun deviceParamApi(): DeviceParamApi = deviceParam

    /**
     * 获取输入张量 API（Java 友好方法）/ Returns the input tensor API (Java-friendly accessor).
     */
    fun inputTensorApi(): InputTensorApi = inputTensor

    /**
     * 获取 OTA 参数 API（Java 友好方法）/ Returns the OTA parameter API (Java-friendly accessor).
     */
    fun otaParamApi(): OtaParamApi = otaParam

    companion object {
        /** SDK 名称 / SDK name. */
        const val SDK_NAME = "ms500-usb-cam"

        /** SDK 版本号 / SDK version. */
        const val SDK_VERSION = "1.0.0"

        /** 默认超时时间，单位毫秒 / Default timeout in milliseconds. */
        const val DEFAULT_TIMEOUT_MS = 5000L

        /**
         * 创建 [MS500UsbCamSdk] 实例的工厂方法 / Factory method to create an [MS500UsbCamSdk] instance.
         *
         * @param context 应用上下文 / Application context.
         * @return [MS500UsbCamSdk] 实例 / An instance of [MS500UsbCamSdk].
         */
        fun create(context: Context): MS500UsbCamSdk {
            return MS500UsbCamSdk(context.applicationContext)
        }
    }
}
