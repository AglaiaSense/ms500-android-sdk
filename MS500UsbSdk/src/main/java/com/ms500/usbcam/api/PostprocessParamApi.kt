/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.usbcam.api

import com.ms500.usbcam.dto.PostprocessParamDto
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.dto.RoiPointDto
import com.ms500.usbcam.dto.WatchingRegionDto
import com.ms500.usbcam.result.SdkResult

/**
 * Post-processing parameter API for inference thresholds, ROI settings, smoothing, and tracker tuning.
 *
 * Protocol mapping:
 * - Read: `get_postprocess_param`, parsed from the response `result` object into [PostprocessParamDto].
 * - Reset: `reset_postprocess_param`, restoring ESP32-side postprocess defaults.
 * - Write: `set_postprocess_param`, with writable fields placed directly under request `params`.
 *
 * This API follows the same setter rule used by [PqConfigParamApi]: single-field setters build the exact
 * protocol JSON directly, while [setParams] is the batch-setting entry that converts [PostprocessParamDto]
 * through `toSetJson()`. The postprocess protocol is mostly flat, except object/list fields such as
 * `cluster_roi`, `face_detection_roi`, and `watching_region`.
 */
interface PostprocessParamApi {

    /**
     * Gets current post-processing parameters.
     *
     * Sends `{"cmd":"get_postprocess_param"}`. The CDC parser removes the response wrapper and passes
     * the `result` object to [PostprocessParamDto.fromJson]. The returned DTO may include threshold,
     * tracker, ROI, smoothing, gender/age, and distribution-adjustment fields.
     *
     * @return Result containing current postprocess business fields.
     */
    suspend fun getParams(): SdkResult<PostprocessParamDto>

    /**
     * Resets post-processing parameters to protocol default values.
     *
     * Sends `{"cmd":"reset_postprocess_param"}`. This is a device-side reset and does not require a
     * [PostprocessParamDto] payload. Use [getParams] after reset when the caller needs to display the
     * actual values applied by firmware.
     *
     * @return Operation result returned by the device.
     */
    suspend fun resetPostprocess(): SdkResult<ResultDto>

    /**
     * Sets post-processing parameters in one request.
     *
     * This is the batch-setting API. It converts [params] through `PostprocessParamDto.toSetJson()` and
     * sends the resulting object as `set_postprocess_param.params`. Only non-null fields are sent. Use
     * individual setters when the caller wants to make the exact single protocol field obvious at the
     * call site.
     *
     * @param params Post-processing parameters to write; omit a field by leaving it null.
     * @return Operation result returned by the device.
     */
    suspend fun setParams(params: PostprocessParamDto): SdkResult<ResultDto>

    /**
     * Sets face detection ROI.
     *
     * Range: 4 points, x 0..512, y 0..384; default: full region (0,0)-(512,384).
     */
    suspend fun setFaceDetectionRoi(points: List<RoiPointDto>): SdkResult<ResultDto>

    /**
     * Sets trajectory DB lifetime.
     *
     * Range: 0..3600; default: 10.
     */
    suspend fun setTrajectoryDbLifetime(value: Int): SdkResult<ResultDto>

    /**
     * Sets gaze focus threshold.
     *
     * Range: 0.0..1.0; default: 0.0.
     */
    suspend fun setGazeFocusThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets watching-region enable flag.
     *
     * Range: true/false; default: true.
     */
    suspend fun setEnableWatchingRegion(value: Boolean): SdkResult<ResultDto>

    /**
     * Sets watching region.
     *
     * Field ranges/defaults:
     * - minYaw: -90..90; default: -35.
     * - maxYaw: -90..90; default: 35.
     * - minPitch: -90..90; default: -20.
     * - maxPitch: -90..90; default: 20.
     */
    suspend fun setWatchingRegion(value: WatchingRegionDto): SdkResult<ResultDto>

    /**
     * Sets inference-result filter enable flag.
     *
     * Range: true/false; default: true.
     */
    suspend fun setInferenceResultFilterEnable(value: Boolean): SdkResult<ResultDto>

    /**
     * Sets gaze-time functions enable flag.
     *
     * Range: true/false; default: true.
     */
    suspend fun setEnableGazeTimeFunctions(value: Boolean): SdkResult<ResultDto>

    /**
     * Sets age smoothing enable flag.
     *
     * Range: true/false; default: true.
     */
    suspend fun setEnableAgeSmooth(value: Boolean): SdkResult<ResultDto>

    /**
     * Sets continuous age smoothing enable flag.
     *
     * Range: true/false; default: false.
     */
    suspend fun setEnableAgeSmoothContinuous(value: Boolean): SdkResult<ResultDto>

    /**
     * Sets age buffer size.
     *
     * Range: 5..7; default: 5.
     */
    suspend fun setAgeBufSize(value: Int): SdkResult<ResultDto>

    /**
     * Sets age ID size.
     *
     * Range: 10..15; default: 15.
     */
    suspend fun setAgeIdSize(value: Int): SdkResult<ResultDto>

    /**
     * Sets gender vote enable flag.
     *
     * Range: true/false; default: true.
     */
    suspend fun setEnableGenderVote(value: Boolean): SdkResult<ResultDto>

    /**
     * Sets continuous gender vote enable flag.
     *
     * Range: true/false; default: false.
     */
    suspend fun setEnableGenderVoteContinuous(value: Boolean): SdkResult<ResultDto>

    /**
     * Sets gender buffer size.
     *
     * Range: 5..7; default: 5.
     */
    suspend fun setGenderBufSize(value: Int): SdkResult<ResultDto>

    /**
     * Sets gender ID size.
     *
     * Range: 10..15; default: 15.
     */
    suspend fun setGenderIdSize(value: Int): SdkResult<ResultDto>

    /**
     * Sets face minimum size.
     *
     * Range: 5..150; default: 16.
     */
    suspend fun setFaceMinSizePpl(value: Int): SdkResult<ResultDto>

    /**
     * Sets zero-age enable flag.
     *
     * Range: true/false; default: true.
     */
    suspend fun setEnableZeroAge(value: Boolean): SdkResult<ResultDto>

    /**
     * Sets invalid-gender enable flag.
     *
     * Range: true/false; default: true.
     */
    suspend fun setEnableInvalidGender(value: Boolean): SdkResult<ResultDto>

    /**
     * Sets distribution adjustment enable flag.
     *
     * Range: true/false; default: false.
     */
    suspend fun setEnableDistributionAdjust(value: Boolean): SdkResult<ResultDto>

    /**
     * Sets source mean.
     *
     * Range: 0.0..100.0; default: 0.0.
     */
    suspend fun setSMean(value: Double): SdkResult<ResultDto>

    /**
     * Sets source standard deviation.
     *
     * Range: 0.0..100.0; default: 1.0.
     */
    suspend fun setSStd(value: Double): SdkResult<ResultDto>

    /**
     * Sets target mean.
     *
     * Range: 0.0..100.0; default: 0.0.
     */
    suspend fun setTMean(value: Double): SdkResult<ResultDto>

    /**
     * Sets target standard deviation.
     *
     * Range: 0.0..100.0; default: 1.0.
     */
    suspend fun setTStd(value: Double): SdkResult<ResultDto>

    /**
     * Sets male bias.
     *
     * Range: -1000.0..1000.0; default: 0.0.
     */
    suspend fun setMBias(value: Double): SdkResult<ResultDto>

    /**
     * Sets female bias.
     *
     * Range: -1000.0..1000.0; default: 0.0.
     */
    suspend fun setSBias(value: Double): SdkResult<ResultDto>

    /**
     * Sets body detection threshold.
     *
     * Range: 0.0..1.0; default: 0.52.
     *
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setBodyThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets face detection threshold.
     *
     * Range: 0.0..1.0; default: 0.295.
     *
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setFaceThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets gaze detection threshold.
     *
     * Range: 0.0..1.0; default: 0.0.
     *
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setGazeThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets NMS threshold.
     *
     * Range: 0.0..1.0; default: 0.5.
     *
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setNmsThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets back-to-camera threshold.
     *
     * Range: 0.0..1.0; default: 0.45.
     *
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setBack2cameraThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets gender detection threshold.
     *
     * Range: 0.1..0.9; default: 0.55.
     *
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setGenderThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets maximum body detection count.
     *
     * Range: 1..100; default: 30.
     * @param value Count
     * @return Operation result
     */
    suspend fun setMaxBodyDetection(value: Int): SdkResult<ResultDto>

    /**
     * Sets face minimum size.
     *
     * Range: 5..150; default: 70.
     * @param value Size in pixels
     * @return Operation result
     */
    suspend fun setFaceMinSize(value: Int): SdkResult<ResultDto>

    /**
     * Sets maximum tracking age.
     *
     * Range: 0..20; default: 6.
     * @param value Age
     * @return Operation result
     */
    suspend fun setMaxAge(value: Int): SdkResult<ResultDto>

    /**
     * Sets minimum hit count.
     *
     * Range: 0..10; default: 2.
     * @param value Count
     * @return Operation result
     */
    suspend fun setMinHits(value: Int): SdkResult<ResultDto>

    /**
     * Sets IOU threshold.
     *
     * Range: 0.0..1.0; default: 0.2.
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setIouThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets maximum tracker count.
     *
     * Range: 0..50; default: 20.
     * @param value Count
     * @return Operation result
     */
    suspend fun setMaxTrackers(value: Int): SdkResult<ResultDto>

    /**
     * Sets minimum BBox area.
     *
     * Range: 0.0..5000.0; default: 100.0.
     * @param value Area
     * @return Operation result
     */
    suspend fun setMinBboxArea(value: Double): SdkResult<ResultDto>

    /**
     * Sets minimum height.
     *
     * Range: 0.0..200.0; default: 10.0.
     * @param value Height
     * @return Operation result
     */
    suspend fun setMinHeight(value: Double): SdkResult<ResultDto>

    /**
     * Sets stability threshold.
     *
     * Range: 0.0..1.0; default: 0.25.
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setStabilityThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets body tracking score threshold.
     *
     * Range: 0.0..1.0; default: 0.35.
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setBodyScoreTrackingThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets pitch threshold.
     *
     * Range: 1..90; default: 90.
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setPitchThreshold(value: Int): SdkResult<ResultDto>

    /**
     * Sets yaw threshold.
     *
     * Range: 1..180; default: 45.
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setYawThreshold(value: Int): SdkResult<ResultDto>

    /**
     * Sets body box height threshold.
     *
     * Range: 0.0..384.0; default: 48.0.
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setBodyBoxHeightThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets ID creation threshold.
     *
     * Range: 0.0..1.0; default: 0.6.
     * @param value Threshold
     * @return Operation result
     */
    suspend fun setIdCreationThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets cluster ROI points.
     *
     * Range: 4 points, x 0..512, y 0..384; default: full region (0,0)-(512,384).
     * @param points ROI point list
     * @return Operation result
     */
    suspend fun setClusterRoi(points: List<RoiPointDto>): SdkResult<ResultDto>
}
