package com.ms500.usbcam.api

import com.ms500.usbcam.dto.InferenceState
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.dto.ResultSendModeConfig
import com.ms500.usbcam.dto.RoiConfig
import com.ms500.usbcam.result.SdkResult

/**
 * Inference parameters API for controlling AI inference settings.
 */
interface InferenceParamApi {

    /**
     * Gets current inference state.
     *
     * @return Result containing [InferenceState]
     */
    suspend fun getState(): SdkResult<InferenceState>

    /**
     * Sets inference on/off state.
     *
     * @param enabled Whether to enable inference
     * @return Operation result
     */
    suspend fun setState(enabled: Boolean): SdkResult<ResultDto>

    /**
     * Starts inference.
     *
     * @return Operation result
     */
    suspend fun startInference(): SdkResult<ResultDto>

    /**
     * Stops inference.
     *
     * @return Operation result
     */
    suspend fun stopInference(): SdkResult<ResultDto>

    /**
     * Gets result send mode.
     *
     * @return Result containing [ResultSendModeConfig]
     */
    suspend fun getResultSendMode(): SdkResult<ResultSendModeConfig>

    /**
     * Sets result send mode.
     *
     * @param config Send mode configuration
     * @return Operation result
     */
    suspend fun setResultSendMode(config: ResultSendModeConfig): SdkResult<ResultDto>

    /**
     * Sets batch send mode.
     *
     * @param frameCount Number of frames per batch
     * @return Operation result
     */
    suspend fun setBatchMode(frameCount: Int): SdkResult<ResultDto>

    /**
     * Sets real-time send mode.
     *
     * @return Operation result
     */
    suspend fun setRealtimeMode(): SdkResult<ResultDto>

    /**
     * Gets ROI configuration.
     *
     * @return Result containing [RoiConfig]
     */
    suspend fun getRoi(): SdkResult<RoiConfig>

    /**
     * Sets ROI configuration.
     *
     * @param config ROI configuration
     * @return Operation result
     */
    suspend fun setRoi(config: RoiConfig): SdkResult<ResultDto>

    /**
     * Clears ROI configuration.
     *
     * @return Operation result
     */
    suspend fun clearRoi(): SdkResult<ResultDto>

    /**
     * Sets detection upload on/off.
     *
     * @param enabled Whether to enable detection upload
     * @return Operation result
     */
    suspend fun setDetectionUpload(enabled: Boolean): SdkResult<ResultDto>

    /**
     * Starts detection upload.
     *
     * @return Operation result
     */
    suspend fun startDetectionUpload(): SdkResult<ResultDto>

    /**
     * Stops detection upload.
     *
     * @return Operation result
     */
    suspend fun stopDetectionUpload(): SdkResult<ResultDto>
}
