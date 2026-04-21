package com.ms500.usbcam.api

import com.ms500.usbcam.dto.PostprocessParamDto
import com.ms500.usbcam.dto.PplParameterDto
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.result.SdkResult

/**
 * Post-processing parameters API for inference threshold and tracker settings.
 */
interface PostprocessParamApi {

    /**
     * Gets current post-processing parameters.
     *
     * @return Result containing [PostprocessParamDto]
     */
    suspend fun getParams(): SdkResult<PostprocessParamDto>

    /**
     * Sets post-processing parameters.
     *
     * @param params Post-processing parameters
     * @return Operation result
     */
    suspend fun setParams(params: PostprocessParamDto): SdkResult<ResultDto>

    /**
     * Sets PPL parameters.
     *
     * @param params PPL parameters
     * @return Operation result
     */
    suspend fun setPplParameter(params: PplParameterDto): SdkResult<ResultDto>

    /**
     * Sets confidence threshold.
     *
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setConfThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets body detection threshold.
     *
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setBodyThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets face detection threshold.
     *
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setFaceThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets gaze detection threshold.
     *
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setGazeThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets NMS threshold.
     *
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setNmsThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets back-to-camera threshold.
     *
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setBack2cameraThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets gender detection threshold.
     *
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setGenderThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets maximum body detection count.
     *
     * @param value Count
     * @return Operation result
     */
    suspend fun setMaxBodyDetection(value: Int): SdkResult<ResultDto>

    /**
     * Sets face minimum size.
     *
     * @param value Size in pixels
     * @return Operation result
     */
    suspend fun setFaceMinSize(value: Int): SdkResult<ResultDto>

    /**
     * Sets maximum tracking age.
     *
     * @param value Age
     * @return Operation result
     */
    suspend fun setMaxAge(value: Int): SdkResult<ResultDto>

    /**
     * Sets minimum hit count.
     *
     * @param value Count
     * @return Operation result
     */
    suspend fun setMinHits(value: Int): SdkResult<ResultDto>

    /**
     * Sets IOU threshold.
     *
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setIouThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets maximum tracker count.
     *
     * @param value Count
     * @return Operation result
     */
    suspend fun setMaxTrackers(value: Int): SdkResult<ResultDto>

    /**
     * Sets minimum BBox area.
     *
     * @param value Area
     * @return Operation result
     */
    suspend fun setMinBboxArea(value: Double): SdkResult<ResultDto>

    /**
     * Sets minimum height.
     *
     * @param value Height
     * @return Operation result
     */
    suspend fun setMinHeight(value: Double): SdkResult<ResultDto>

    /**
     * Sets stability threshold.
     *
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setStabilityThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets body tracking score threshold.
     *
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setBodyScoreTrackingThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets pitch threshold.
     *
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setPitchThreshold(value: Int): SdkResult<ResultDto>

    /**
     * Sets yaw threshold.
     *
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setYawThreshold(value: Int): SdkResult<ResultDto>

    /**
     * Sets inference enabled switch.
     *
     * @param value Whether to enable
     * @return Operation result
     */
    suspend fun setInferenceEnabled(value: Boolean): SdkResult<ResultDto>
}
