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

import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.dto.WtaPostprocessParamDto
import com.ms500.usbcam.dto.WtaWatchingRegionDto
import com.ms500.usbcam.result.SdkResult

/**
 * WTA post-process parameter API.
 *
 * Protocol mapping - the same three commands GAZE uses:
 * - Read: `get_postprocess_param`, parsed into [WtaPostprocessParamDto].
 * - Reset: `reset_postprocess_param`, restoring WTA firmware defaults.
 * - Write: `set_postprocess_param`, writable fields directly under request `params`.
 *
 * Two things make this different from [PostprocessParamApi]:
 *
 * 1. **Ranges and defaults differ.** `max_body_detection` is 1..10 here versus GAZE's 1..100,
 *    `m_bias`/`s_bias` are -15..15 versus -1000..1000, and the watching angles stop at 80 rather
 *    than 90. Values are validated against the **WTA** bounds before sending, because validation
 *    follows the facade the caller chose, not the model the device happens to run.
 * 2. **Legacy-only fields are absent.** Setters exist only for fields WTA algorithms actually use.
 *    `gaze_threshold`, `nms_threshold`, `back2camera_threshold`, `cluster_roi`, the age-smoothing
 *    and gender-voting groups and the rest are reachable through `sdk.gaze.postprocessParam`, where
 *    their KDoc notes they have no effect on WTA. WTA firmware still accepts and stores them -
 *    `status: ok` is not evidence the algorithm used the value.
 *
 * Changing any field that reaches the postprocess library rebuilds it and **clears all current
 * tracking**; track IDs are reassigned and there may be a ~0.5s gap in results. The firmware-level
 * switches ([setInferenceEnabled], [setTrajectoryDbLifetime], [setInferenceResultFilterEnable]) do
 * not trigger a rebuild.
 *
 * Field-level reference: `docs/sync/cdc_protocol/GAZE_WTA_FIELD_MATRIX.md`.
 */
interface WtaPostprocessParamApi {

    /**
     * Gets current WTA post-process parameters.
     *
     * Sends `{"cmd":"get_postprocess_param"}`. Legacy-only fields this DTO does not model remain
     * inspectable through [WtaPostprocessParamDto.raw].
     *
     * @return Result containing current WTA post-process fields.
     */
    suspend fun getParams(): SdkResult<WtaPostprocessParamDto>

    /**
     * Resets post-process parameters to WTA firmware defaults.
     *
     * Sends `{"cmd":"reset_postprocess_param"}`. This restores the WTA-only fields as well, but
     * **not** the region rectangles - use [WtaRegionParamApi.resetRegions] for those. Region 0's
     * shared angles and d1/d2 are affected by both resets.
     *
     * @return Operation result returned by the device.
     */
    suspend fun resetPostprocess(): SdkResult<ResultDto>

    /**
     * Writes several parameters in one request.
     *
     * Converts [params] through [WtaPostprocessParamDto.toSetJson] and sends the result as
     * `set_postprocess_param.params`; only non-null fields are sent. The **whole object** is
     * validated first, so one invalid field means nothing is sent - never a partial write.
     *
     * @param params Parameters to write; leave a field null to omit it.
     * @return Operation result, or `SdkError.InvalidArgument` when validation fails.
     */
    suspend fun setParams(params: WtaPostprocessParamDto): SdkResult<ResultDto>

    // --- Mapped legacy fields -----------------------------------------------

    /**
     * Sets the person score threshold.
     *
     * Range 0.0..1.0, default 0.5. One call sets the male, female and unknown person thresholds
     * together - WTA has three separate score slots and this field drives all of them.
     */
    suspend fun setBodyThreshold(value: Double): SdkResult<ResultDto>

    /** Sets the face score threshold. Range 0.0..1.0, default 0.3. */
    suspend fun setFaceThreshold(value: Double): SdkResult<ResultDto>

    /**
     * Sets the maximum number of tracked persons.
     *
     * Range **1..10**, default 5. GAZE accepts 1..100, so a value such as 20 is valid through
     * `sdk.gaze.postprocessParam` but fails locally here.
     */
    suspend fun setMaxBodyDetection(value: Int): SdkResult<ResultDto>

    /** Sets the maximum tracking age. Range 0..20, default 8. */
    suspend fun setMaxAge(value: Int): SdkResult<ResultDto>

    /** Sets the minimum hits before a track is confirmed. Range 0..10, default 3. */
    suspend fun setMinHits(value: Int): SdkResult<ResultDto>

    /** Enables or disables inference. Firmware-level switch; does not rebuild the tracker. */
    suspend fun setInferenceEnabled(value: Boolean): SdkResult<ResultDto>

    /**
     * Sets the track timeout before a summary is emitted.
     *
     * Range 0..3600 **seconds**, default 10. A track that disappears and returns inside this window
     * counts as one visit.
     */
    suspend fun setTrajectoryDbLifetime(value: Int): SdkResult<ResultDto>

    /**
     * Enables or disables empty-frame filtering.
     *
     * When false the device also sends frames with an empty `detections` array, which is how empty
     * frames are observed at all.
     */
    suspend fun setInferenceResultFilterEnable(value: Boolean): SdkResult<ResultDto>

    /**
     * Sets the watching region angles and d1/d2 for region 0.
     *
     * Shares storage with `watching_regions[0]` of [WtaRegionParamApi] - writing here is visible
     * there and vice versa. Angle range is -90..80 (GAZE allows -90..90), d1/d2 are 0.0..1.0.
     */
    suspend fun setWatchingRegion(value: WtaWatchingRegionDto): SdkResult<ResultDto>

    /** Enables or disables age distribution adjustment. Default false. */
    suspend fun setEnableDistributionAdjust(value: Boolean): SdkResult<ResultDto>

    /** Sets the source mean for age distribution adjustment. Range 0.0..100.0, default 0.0. */
    suspend fun setSMean(value: Double): SdkResult<ResultDto>

    /** Sets the source standard deviation. Range 0.0..100.0, default 1.0. */
    suspend fun setSStd(value: Double): SdkResult<ResultDto>

    /** Sets the target mean. Range 0.0..100.0, default 0.0. */
    suspend fun setTMean(value: Double): SdkResult<ResultDto>

    /** Sets the target standard deviation. Range 0.0..100.0, default 1.0. */
    suspend fun setTStd(value: Double): SdkResult<ResultDto>

    /** Sets the mean bias. Range **-15.0..15.0**, default 0.0. GAZE allows -1000..1000. */
    suspend fun setMBias(value: Double): SdkResult<ResultDto>

    /** Sets the standard deviation bias. Range **-15.0..15.0**, default 0.0. */
    suspend fun setSBias(value: Double): SdkResult<ResultDto>

    // --- WTA-only fields ----------------------------------------------------

    /** Sets the head score threshold. Range 0.0..1.0, default 0.3. */
    suspend fun setHeadScoreThresh(value: Double): SdkResult<ResultDto>

    /** Sets the person box NMS IoU threshold. Range 0.0..1.0, default 0.6. */
    suspend fun setPersonIouThresh(value: Double): SdkResult<ResultDto>

    /** Sets the head box NMS IoU threshold. Range 0.0..1.0, default 0.6. */
    suspend fun setHeadIouThresh(value: Double): SdkResult<ResultDto>

    /** Sets the face box NMS IoU threshold. Range 0.0..1.0, default 0.6. */
    suspend fun setFaceIouThresh(value: Double): SdkResult<ResultDto>

    /** Sets the minimum person box width in model pixels. Range 0..200, default 40. */
    suspend fun setPersonWidthThresh(value: Int): SdkResult<ResultDto>

    /** Sets the minimum head box width in model pixels. Range 0..100, default 20. */
    suspend fun setHeadWidthThresh(value: Int): SdkResult<ResultDto>

    /** Sets the minimum face box width in model pixels. Range 0..100, default 20. */
    suspend fun setFaceWidthThresh(value: Int): SdkResult<ResultDto>

    /**
     * Sets the IoU threshold for associating body and face boxes to a head.
     *
     * Range 0.0..1.0, default 0.5. WTA tracks heads, so this governs which body/face box gets
     * attached to which head.
     */
    suspend fun setAssociationIouThresh(value: Double): SdkResult<ResultDto>

    /**
     * Sets the gender distribution adjustment.
     *
     * Range 0.0..1.0, default 0.5, where 0.5 disables it. Lower values bias towards female, higher
     * towards male; roughly 20% per 0.1 step.
     */
    suspend fun setGDistroAdj(value: Double): SdkResult<ResultDto>

    /**
     * Sets the maximum inter-frame distance treated as the same person.
     *
     * Range 0..300 model pixels, default 100.
     */
    suspend fun setMaxDist(value: Int): SdkResult<ResultDto>

    /**
     * Sets the minimum head area required for a gaze judgement.
     *
     * Range 0..40000 model pixels squared, default 400.
     */
    suspend fun setHeadMinArea(value: Int): SdkResult<ResultDto>

    /**
     * Sets the minimum face area required for a gaze judgement.
     *
     * Range 0..40000 model pixels squared, default 400.
     */
    suspend fun setFaceMinArea(value: Int): SdkResult<ResultDto>

    /** Sets the lower yaw bound for valid age estimation. Range -90..80, default -50. */
    suspend fun setAYawMin(value: Int): SdkResult<ResultDto>

    /** Sets the upper yaw bound for valid age estimation. Range -90..80, default 50. */
    suspend fun setAYawMax(value: Int): SdkResult<ResultDto>

    /** Sets the lower pitch bound for valid age estimation. Range -90..80, default -50. */
    suspend fun setAPitchMin(value: Int): SdkResult<ResultDto>

    /** Sets the upper pitch bound for valid age estimation. Range -90..80, default 50. */
    suspend fun setAPitchMax(value: Int): SdkResult<ResultDto>
}
