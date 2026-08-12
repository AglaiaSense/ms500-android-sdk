/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
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
 * SDK entry point for MS500 USB camera.
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

    /** CDC connection module. */
    val cdcConnect: CdcConnectApi = cdcConnectApiImpl

    /** UVC preview module. */
    val uvcPreview: UvcPreviewApi = UvcPreviewApiImpl(appContext)

    /** Camera parameter module. */
    val cameraParam: CameraParamApi = CameraParamApiImpl(cdcConnectApiImpl)

    /** Post-process parameter module. */
    val postprocessParam: PostprocessParamApi = PostprocessParamApiImpl(cdcConnectApiImpl)

    /** PQ configuration parameter module. */
    val pqcongifParam: PqConfigParamApi = PqConfigParamApiImpl(cdcConnectApiImpl)

    /** Inference parameter module. */
    val inferenceParam: InferenceParamApi = InferenceParamApiImpl(cdcConnectApiImpl)

    /** Gaze information module. */
    val gazeInfo: GazeInfoApi = GazeInfoApiImpl(cdcConnectApiImpl)

    /** Device parameter module. */
    val deviceParam: DeviceParamApi = DeviceParamApiImpl(cdcConnectApiImpl)

    /** Input tensor module. */
    val inputTensor: InputTensorApi = InputTensorApiImpl(cdcConnectApiImpl, deviceParam)

    /** OTA parameter module. */
    val otaParam: OtaParamApi = OtaParamApiImpl(cdcConnectApiImpl, appContext.filesDir)

    /**
     * Stable method-based API for AAR consumers that should not rely on Kotlin synthetic property access.
     */

    /**
     * Returns the CDC connection API (Java-friendly accessor).
     */
    fun cdcConnectApi(): CdcConnectApi = cdcConnect

    /**
     * Returns the UVC preview API (Java-friendly accessor).
     */
    fun uvcPreviewApi(): UvcPreviewApi = uvcPreview

    /**
     * Returns the camera parameter API (Java-friendly accessor).
     */
    fun cameraParamApi(): CameraParamApi = cameraParam

    /**
     * Returns the post-process parameter API (Java-friendly accessor).
     */
    fun postprocessParamApi(): PostprocessParamApi = postprocessParam

    /**
     * Returns the PQ configuration parameter API (Java-friendly accessor).
     */
    fun pqConfigParamApi(): PqConfigParamApi = pqcongifParam

    /**
     * Returns the inference parameter API (Java-friendly accessor).
     */
    fun inferenceParamApi(): InferenceParamApi = inferenceParam

    /**
     * Returns the gaze information API (Java-friendly accessor).
     */
    fun gazeInfoApi(): GazeInfoApi = gazeInfo

    /**
     * Returns the device parameter API (Java-friendly accessor).
     */
    fun deviceParamApi(): DeviceParamApi = deviceParam

    /**
     * Returns the input tensor API (Java-friendly accessor).
     */
    fun inputTensorApi(): InputTensorApi = inputTensor

    /**
     * Returns the OTA parameter API (Java-friendly accessor).
     */
    fun otaParamApi(): OtaParamApi = otaParam

    companion object {
        /** SDK name. */
        const val SDK_NAME = "MS500UsbSdk"

        /** SDK version. */
        const val SDK_VERSION = "1.1.0"

        /** Default timeout in milliseconds. */
        const val DEFAULT_TIMEOUT_MS = 5000L

        /**
         * Factory method to create an [MS500UsbCamSdk] instance.
         *
         * @param context Application context.
         * @return An instance of [MS500UsbCamSdk].
         */
        fun create(context: Context): MS500UsbCamSdk {
            return MS500UsbCamSdk(context.applicationContext)
        }
    }
}
