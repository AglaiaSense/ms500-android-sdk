package com.ms500.usbcam.api

import com.ms500.usbcam.dto.InferenceState
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.dto.ResultSendModeConfig
import com.ms500.usbcam.dto.RoiConfig
import com.ms500.usbcam.result.SdkResult

interface InferenceParamApi {
    suspend fun getState(): SdkResult<InferenceState>

    suspend fun setState(enabled: Boolean): SdkResult<ResultDto>

    suspend fun startInference(): SdkResult<ResultDto>

    suspend fun stopInference(): SdkResult<ResultDto>

    suspend fun getResultSendMode(): SdkResult<ResultSendModeConfig>

    suspend fun setResultSendMode(config: ResultSendModeConfig): SdkResult<ResultDto>

    suspend fun setBatchMode(frameCount: Int): SdkResult<ResultDto>

    suspend fun setRealtimeMode(): SdkResult<ResultDto>

    suspend fun getRoi(): SdkResult<RoiConfig>

    suspend fun setRoi(config: RoiConfig): SdkResult<ResultDto>

    suspend fun clearRoi(): SdkResult<ResultDto>

    suspend fun setDetectionUpload(enabled: Boolean): SdkResult<ResultDto>

    suspend fun startDetectionUpload(): SdkResult<ResultDto>

    suspend fun stopDetectionUpload(): SdkResult<ResultDto>
}
