/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */

package com.ms500.usbcam.dto

import org.json.JSONObject

/**
 * WTA post-process parameters.
 *
 * The WTA firmware serves the same `get/set/reset_postprocess_param` commands as GAZE, but the
 * field set splits three ways (see `docs/sync/cdc_protocol/GAZE_WTA_FIELD_MATRIX.md`):
 *
 * - **Mapped legacy fields** - same meaning, different ranges and defaults. Exposed here.
 * - **WTA-only fields** - the 18 JSON leaves added by protocol 4.3. Exposed here.
 * - **Legacy-only fields** - accepted, stored and read back by WTA firmware but unused by its
 *   algorithms (`gaze_threshold`, `nms_threshold`, `cluster_roi`, age-smoothing, gender-voting,
 *   and so on). Deliberately **absent** from this DTO so WTA integrators cannot mistake a
 *   `status: ok` for an effective setting. They remain reachable through
 *   `sdk.gaze.postprocessParam` for anyone who needs the raw wire behaviour.
 *
 * Ranges below are enforced locally before sending, using the **WTA** bounds. The same field may
 * accept a wider range through `sdk.gaze.postprocessParam`; validation follows the facade the
 * caller chose, not the model the device happens to be running.
 *
 * [raw] keeps the full response envelope, so legacy-only fields are still inspectable after a
 * [WtaPostprocessParamApi.getParams] call even though this DTO has no typed property for them.
 */
data class WtaPostprocessParamDto(
    // --- Mapped legacy fields -----------------------------------------------
    /** Person score threshold, 0..1, default 0.5. Sets the male/female/unknown person thresholds together. */
    val bodyThreshold: Double? = null,
    /** Face score threshold, 0..1, default 0.3. */
    val faceThreshold: Double? = null,
    /** Maximum tracked persons, 1..10, default 5. GAZE allows 1..100. */
    val maxBodyDetection: Int? = null,
    /** Maximum tracking age, 0..20, default 8. */
    val maxAge: Int? = null,
    /** Minimum hits before a track is confirmed, 0..10, default 3. */
    val minHits: Int? = null,
    /** Whether inference is enabled. Firmware-level switch, does not rebuild the tracker. */
    val inferenceEnabled: Boolean? = null,
    /** Track timeout in seconds before a summary is emitted, 0..3600, default 10. */
    val trajectoryDbLifetime: Int? = null,
    /** Whether empty frames are filtered out before upload. */
    val inferenceResultFilterEnable: Boolean? = null,
    /** Age distribution adjustment switch, default false. */
    val enableDistributionAdjust: Boolean? = null,
    /** Source mean for age distribution adjustment, 0..100, default 0. */
    val sMean: Double? = null,
    /** Source standard deviation, 0..100, default 1. */
    val sStd: Double? = null,
    /** Target mean, 0..100, default 0. */
    val tMean: Double? = null,
    /** Target standard deviation, 0..100, default 1. */
    val tStd: Double? = null,
    /** Mean bias, **-15..15**, default 0. GAZE allows -1000..1000. */
    val mBias: Double? = null,
    /** Standard deviation bias, **-15..15**, default 0. GAZE allows -1000..1000. */
    val sBias: Double? = null,
    /**
     * Watching region angles and d1/d2 for region 0.
     *
     * Shares storage with `watching_regions[0]` of the region commands: writing through either
     * command is visible from the other. WTA angle bounds are -90..80, narrower than GAZE's
     * -90..90.
     */
    val watchingRegion: WtaWatchingRegionDto? = null,

    // --- WTA-only fields ----------------------------------------------------
    /** Head score threshold, 0..1, default 0.3. */
    val headScoreThresh: Double? = null,
    /** Person box NMS IoU threshold, 0..1, default 0.6. */
    val personIouThresh: Double? = null,
    /** Head box NMS IoU threshold, 0..1, default 0.6. */
    val headIouThresh: Double? = null,
    /** Face box NMS IoU threshold, 0..1, default 0.6. */
    val faceIouThresh: Double? = null,
    /** Minimum person box width in model pixels, 0..200, default 40. */
    val personWidthThresh: Int? = null,
    /** Minimum head box width in model pixels, 0..100, default 20. */
    val headWidthThresh: Int? = null,
    /** Minimum face box width in model pixels, 0..100, default 20. */
    val faceWidthThresh: Int? = null,
    /** IoU threshold for associating body/face boxes to a head, 0..1, default 0.5. */
    val associationIouThresh: Double? = null,
    /** Gender distribution adjustment, 0..1, default 0.5 (0.5 disables it). */
    val gDistroAdj: Double? = null,
    /** Maximum inter-frame distance treated as the same person, in model pixels, 0..300, default 100. */
    val maxDist: Int? = null,
    /** Minimum head area for gaze judgement, in model pixels squared, 0..40000, default 400. */
    val headMinArea: Int? = null,
    /** Minimum face area for gaze judgement, in model pixels squared, 0..40000, default 400. */
    val faceMinArea: Int? = null,
    /** Lower yaw bound for valid age estimation, -90..80, default -50. */
    val aYawMin: Int? = null,
    /** Upper yaw bound for valid age estimation, -90..80, default 50. */
    val aYawMax: Int? = null,
    /** Lower pitch bound for valid age estimation, -90..80, default -50. */
    val aPitchMin: Int? = null,
    /** Upper pitch bound for valid age estimation, -90..80, default 50. */
    val aPitchMax: Int? = null,

    /**
     * Full parsed CDC response object for debugging.
     *
     * Keeps the complete envelope including `meta.ranges` and the legacy-only fields this DTO does
     * not model. Note that `meta.ranges` reports `"default": false` for every boolean regardless of
     * the real default, so it must not be used as a defaults source.
     */
    val raw: JSONObject? = null
) {
    companion object {
        /** Parses WTA post-process parameters from `get_postprocess_param.result`. */
        fun fromJson(json: JSONObject): WtaPostprocessParamDto {
            fun double(key: String): Double? = if (json.has(key)) json.optDouble(key) else null
            fun int(key: String): Int? = if (json.has(key)) json.optInt(key) else null
            fun bool(key: String): Boolean? = if (json.has(key)) json.optBoolean(key) else null

            return WtaPostprocessParamDto(
                bodyThreshold = double("body_detection_threshold"),
                faceThreshold = double("face_threshold"),
                maxBodyDetection = int("max_body_detection"),
                maxAge = int("max_age"),
                minHits = int("min_hits"),
                inferenceEnabled = bool("inference_enabled"),
                trajectoryDbLifetime = int("trajectory_db_lifetime"),
                inferenceResultFilterEnable = bool("inference_result_filter_enable"),
                enableDistributionAdjust = bool("enable_distribution_adjust"),
                sMean = double("s_mean"),
                sStd = double("s_std"),
                tMean = double("t_mean"),
                tStd = double("t_std"),
                mBias = double("m_bias"),
                sBias = double("s_bias"),
                watchingRegion = json.optJSONObject("watching_region")
                    ?.let { WtaWatchingRegionDto.fromJson(it) }
                    ?.takeUnless { it.isEmpty() },
                headScoreThresh = double("head_score_thresh"),
                personIouThresh = double("person_iou_thresh"),
                headIouThresh = double("head_iou_thresh"),
                faceIouThresh = double("face_iou_thresh"),
                personWidthThresh = int("person_width_thresh"),
                headWidthThresh = int("head_width_thresh"),
                faceWidthThresh = int("face_width_thresh"),
                associationIouThresh = double("association_iou_thresh"),
                gDistroAdj = double("g_distro_adj"),
                maxDist = int("max_dist"),
                headMinArea = int("head_min_area"),
                faceMinArea = int("face_min_area"),
                aYawMin = int("a_yaw_min"),
                aYawMax = int("a_yaw_max"),
                aPitchMin = int("a_pitch_min"),
                aPitchMax = int("a_pitch_max"),
                raw = json
            )
        }
    }

    /** Converts non-null fields to `set_postprocess_param.params` JSON. */
    fun toSetJson(): JSONObject = JSONObject().apply {
        bodyThreshold?.let { put("body_detection_threshold", it) }
        faceThreshold?.let { put("face_threshold", it) }
        maxBodyDetection?.let { put("max_body_detection", it) }
        maxAge?.let { put("max_age", it) }
        minHits?.let { put("min_hits", it) }
        inferenceEnabled?.let { put("inference_enabled", it) }
        trajectoryDbLifetime?.let { put("trajectory_db_lifetime", it) }
        inferenceResultFilterEnable?.let { put("inference_result_filter_enable", it) }
        enableDistributionAdjust?.let { put("enable_distribution_adjust", it) }
        sMean?.let { put("s_mean", it) }
        sStd?.let { put("s_std", it) }
        tMean?.let { put("t_mean", it) }
        tStd?.let { put("t_std", it) }
        mBias?.let { put("m_bias", it) }
        sBias?.let { put("s_bias", it) }
        watchingRegion?.takeUnless { it.isEmpty() }?.let { put("watching_region", it.toJson()) }
        headScoreThresh?.let { put("head_score_thresh", it) }
        personIouThresh?.let { put("person_iou_thresh", it) }
        headIouThresh?.let { put("head_iou_thresh", it) }
        faceIouThresh?.let { put("face_iou_thresh", it) }
        personWidthThresh?.let { put("person_width_thresh", it) }
        headWidthThresh?.let { put("head_width_thresh", it) }
        faceWidthThresh?.let { put("face_width_thresh", it) }
        associationIouThresh?.let { put("association_iou_thresh", it) }
        gDistroAdj?.let { put("g_distro_adj", it) }
        maxDist?.let { put("max_dist", it) }
        headMinArea?.let { put("head_min_area", it) }
        faceMinArea?.let { put("face_min_area", it) }
        aYawMin?.let { put("a_yaw_min", it) }
        aYawMax?.let { put("a_yaw_max", it) }
        aPitchMin?.let { put("a_pitch_min", it) }
        aPitchMax?.let { put("a_pitch_max", it) }
    }
}

/**
 * WTA watching-region angles and d1/d2 for region 0.
 *
 * The four angles share storage with `watching_regions[0]` of `get/set_wta_region_param`, and so do
 * [d1] and [d2] - which appear as `watching_d1` / `watching_d2` in `meta.ranges` but as
 * `watching_region.d1` / `.d2` in `result`.
 *
 * @property minYaw Lower yaw bound for gaze judgement, -90..80, default -50.
 * @property maxYaw Upper yaw bound, -90..80, default 50.
 * @property minPitch Lower pitch bound, -90..80, default -50.
 * @property maxPitch Upper pitch bound, -90..80, default 50.
 * @property d1 Head-to-face top-left distance over head diagonal, upper bound, 0..1, default 0.25.
 * @property d2 Head-to-face bottom-right distance over head diagonal, upper bound, 0..1, default 0.25.
 */
data class WtaWatchingRegionDto(
    val minYaw: Int? = null,
    val maxYaw: Int? = null,
    val minPitch: Int? = null,
    val maxPitch: Int? = null,
    val d1: Double? = null,
    val d2: Double? = null
) {
    companion object {
        /** Parses the watching region object from protocol JSON. */
        fun fromJson(json: JSONObject): WtaWatchingRegionDto {
            fun int(key: String): Int? = if (json.has(key)) json.optInt(key) else null
            fun double(key: String): Double? = if (json.has(key)) json.optDouble(key) else null
            return WtaWatchingRegionDto(
                minYaw = int("min_yaw"),
                maxYaw = int("max_yaw"),
                minPitch = int("min_pitch"),
                maxPitch = int("max_pitch"),
                d1 = double("d1"),
                d2 = double("d2")
            )
        }
    }

    /** Converts non-null fields to protocol JSON. */
    fun toJson(): JSONObject = JSONObject().apply {
        minYaw?.let { put("min_yaw", it) }
        maxYaw?.let { put("max_yaw", it) }
        minPitch?.let { put("min_pitch", it) }
        maxPitch?.let { put("max_pitch", it) }
        d1?.let { put("d1", it) }
        d2?.let { put("d2", it) }
    }

    /** Returns true when no field is set. */
    fun isEmpty(): Boolean =
        minYaw == null && maxYaw == null && minPitch == null && maxPitch == null &&
            d1 == null && d2 == null
}
