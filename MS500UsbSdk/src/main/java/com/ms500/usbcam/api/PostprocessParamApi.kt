package com.ms500.usbcam.api

import com.ms500.usbcam.dto.PostprocessParamDto
import com.ms500.usbcam.dto.PplParameterDto
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.result.SdkResult

interface PostprocessParamApi {
    suspend fun getParams(): SdkResult<PostprocessParamDto>

    suspend fun setParams(params: PostprocessParamDto): SdkResult<ResultDto>

    suspend fun setPplParameter(params: PplParameterDto): SdkResult<ResultDto>

    suspend fun setConfThreshold(value: Double): SdkResult<ResultDto>

    suspend fun setBodyThreshold(value: Double): SdkResult<ResultDto>

    suspend fun setFaceThreshold(value: Double): SdkResult<ResultDto>

    suspend fun setGazeThreshold(value: Double): SdkResult<ResultDto>

    suspend fun setNmsThreshold(value: Double): SdkResult<ResultDto>

    suspend fun setBack2cameraThreshold(value: Double): SdkResult<ResultDto>

    suspend fun setGenderThreshold(value: Double): SdkResult<ResultDto>

    suspend fun setMaxBodyDetection(value: Int): SdkResult<ResultDto>

    suspend fun setFaceMinSize(value: Int): SdkResult<ResultDto>

    suspend fun setMaxAge(value: Int): SdkResult<ResultDto>

    suspend fun setMinHits(value: Int): SdkResult<ResultDto>

    suspend fun setIouThreshold(value: Double): SdkResult<ResultDto>

    suspend fun setMaxTrackers(value: Int): SdkResult<ResultDto>

    suspend fun setMinBboxArea(value: Double): SdkResult<ResultDto>

    suspend fun setMinHeight(value: Double): SdkResult<ResultDto>

    suspend fun setStabilityThreshold(value: Double): SdkResult<ResultDto>

    suspend fun setBodyScoreTrackingThreshold(value: Double): SdkResult<ResultDto>

    suspend fun setPitchThreshold(value: Int): SdkResult<ResultDto>

    suspend fun setYawThreshold(value: Int): SdkResult<ResultDto>

    suspend fun setInferenceEnabled(value: Boolean): SdkResult<ResultDto>
}
