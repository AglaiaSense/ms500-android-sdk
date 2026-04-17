package com.ms500.usbcam.api

import com.ms500.usbcam.dto.PqConfigCameraImageSize
import com.ms500.usbcam.dto.PqConfigImageCropping
import com.ms500.usbcam.dto.PqConfigManualExposure
import com.ms500.usbcam.dto.PqConfigManualWhiteBalancePreset
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.dto.PqcongifParam
import com.ms500.usbcam.result.SdkResult

interface PqConfigParamApi {
    suspend fun getPqFixConfig(): SdkResult<PqcongifParam>

    suspend fun setPqFixConfig(config: PqcongifParam): SdkResult<ResultDto>

    suspend fun resetPqFixConfig(): SdkResult<ResultDto>

    suspend fun setCameraImageSize(value: PqConfigCameraImageSize): SdkResult<ResultDto>

    suspend fun setExposureMode(value: Int): SdkResult<ResultDto>

    suspend fun setManualExposure(value: PqConfigManualExposure): SdkResult<ResultDto>

    suspend fun setManualExposureTime(value: Int): SdkResult<ResultDto>

    suspend fun setManualGain(value: Int): SdkResult<ResultDto>

    suspend fun setWhiteBalanceMode(value: Int): SdkResult<ResultDto>

    suspend fun setManualWhiteBalancePreset(value: PqConfigManualWhiteBalancePreset): SdkResult<ResultDto>

    suspend fun setManualWhiteBalanceColorTemperature(value: Int): SdkResult<ResultDto>

    suspend fun setEvCompensation(value: Int): SdkResult<ResultDto>

    suspend fun setAeAntiFlickerMode(value: Int): SdkResult<ResultDto>

    suspend fun setImageCropping(value: PqConfigImageCropping): SdkResult<ResultDto>
}
