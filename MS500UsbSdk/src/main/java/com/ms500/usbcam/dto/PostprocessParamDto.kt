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

import org.json.JSONArray
import org.json.JSONObject

/**
 * Region of Interest point DTO.
 */
data class RoiPointDto(
    /** X coordinate. */
    val x: Float,
    /** Y coordinate. */
    val y: Float
) {
    /** Converts to protocol JSON. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("x", x)
        put("y", y)
    }
}

/**
 * Watching region DTO.
 */
data class WatchingRegionDto(
    /** Minimum yaw. */
    val minYaw: Double? = null,
    /** Maximum yaw. */
    val maxYaw: Double? = null,
    /** Minimum pitch. */
    val minPitch: Double? = null,
    /** Maximum pitch. */
    val maxPitch: Double? = null
) {
    companion object {
        /** Parses watching region from JSON. */
        fun fromJson(json: JSONObject): WatchingRegionDto {
            fun optDoubleOrNull(key: String): Double? = if (json.has(key)) json.optDouble(key) else null
            return WatchingRegionDto(
                minYaw = optDoubleOrNull("min_yaw"),
                maxYaw = optDoubleOrNull("max_yaw"),
                minPitch = optDoubleOrNull("min_pitch"),
                maxPitch = optDoubleOrNull("max_pitch")
            )
        }
    }

    /** Converts to protocol JSON. */
    fun toJson(): JSONObject = JSONObject().apply {
        minYaw?.let { put("min_yaw", it) }
        maxYaw?.let { put("max_yaw", it) }
        minPitch?.let { put("min_pitch", it) }
        maxPitch?.let { put("max_pitch", it) }
    }

    /** Returns true when no field is set. */
    fun isEmpty(): Boolean = minYaw == null && maxYaw == null && minPitch == null && maxPitch == null
}

/**
 * Post-process parameter DTO.
 *
 * This DTO maps directly to the flat fields in get_postprocess_param.result and
 * set_postprocess_param.params. Single-field setters build JSON directly; this
 * DTO's toSetJson() is used for batch setParams(...).
 */
data class PostprocessParamDto(
    /** Body detection threshold. */
    val bodyThreshold: Double? = null,
    /** Face threshold. */
    val faceThreshold: Double? = null,
    /** Gaze threshold. */
    val gazeThreshold: Double? = null,
    /** NMS threshold. */
    val nmsThreshold: Double? = null,
    /** Back-to-camera threshold. */
    val back2cameraThreshold: Double? = null,
    /** Gender threshold. */
    val genderThreshold: Double? = null,
    /** Maximum body detections. */
    val maxBodyDetection: Int? = null,
    /** Face minimum size. */
    val faceMinSize: Int? = null,
    /** Maximum age for tracking. */
    val maxAge: Int? = null,
    /** Minimum hits for tracking. */
    val minHits: Int? = null,
    /** IOU threshold. */
    val iouThreshold: Double? = null,
    /** Maximum trackers. */
    val maxTrackers: Int? = null,
    /** Minimum bounding box area. */
    val minBboxArea: Double? = null,
    /** Minimum height. */
    val minHeight: Double? = null,
    /** Stability threshold. */
    val stabilityThreshold: Double? = null,
    /** Body score tracking threshold. */
    val bodyScoreTrackingThreshold: Double? = null,
    /** Body box height threshold. */
    val bodyBoxHeightThreshold: Double? = null,
    /** ID creation threshold. */
    val idCreationThreshold: Double? = null,
    /** Cluster ROI points. */
    val clusterRoi: List<RoiPointDto>? = null,
    /** Pitch threshold. */
    val pitchThreshold: Int? = null,
    /** Yaw threshold. */
    val yawThreshold: Int? = null,
    /** Whether inference is enabled. */
    val inferenceEnabled: Boolean? = null,
    /** Face detection ROI points. */
    val faceDetectionRoi: List<RoiPointDto>? = null,
    /** Trajectory DB lifetime. */
    val trajectoryDbLifetime: Int? = null,
    /** Gaze focus threshold. */
    val gazeFocusThreshold: Double? = null,
    /** Whether watching region is enabled. */
    val enableWatchingRegion: Boolean? = null,
    /** Watching region. */
    val watchingRegion: WatchingRegionDto? = null,
    /** Whether inference result filtering is enabled. */
    val inferenceResultFilterEnable: Boolean? = null,
    /** Whether gaze time functions are enabled. */
    val enableGazeTimeFunctions: Boolean? = null,
    /** Whether age smoothing is enabled. */
    val enableAgeSmooth: Boolean? = null,
    /** Whether continuous age smoothing is enabled. */
    val enableAgeSmoothContinuous: Boolean? = null,
    /** Age buffer size. */
    val ageBufSize: Int? = null,
    /** Age ID size. */
    val ageIdSize: Int? = null,
    /** Whether gender vote is enabled. */
    val enableGenderVote: Boolean? = null,
    /** Whether continuous gender vote is enabled. */
    val enableGenderVoteContinuous: Boolean? = null,
    /** Gender buffer size. */
    val genderBufSize: Int? = null,
    /** Gender ID size. */
    val genderIdSize: Int? = null,
    /** Face minimum size for people processing. */
    val faceMinSizePpl: Int? = null,
    /** Whether zero age is enabled. */
    val enableZeroAge: Boolean? = null,
    /** Whether invalid gender is enabled. */
    val enableInvalidGender: Boolean? = null,
    /** Whether distribution adjustment is enabled. */
    val enableDistributionAdjust: Boolean? = null,
    /** Source mean for distribution adjustment. */
    val sMean: Double? = null,
    /** Source standard deviation for distribution adjustment. */
    val sStd: Double? = null,
    /** Target mean for distribution adjustment. */
    val tMean: Double? = null,
    /** Target standard deviation for distribution adjustment. */
    val tStd: Double? = null,
    /** Male bias for distribution adjustment. */
    val mBias: Double? = null,
    /** Female bias for distribution adjustment. */
    val sBias: Double? = null,
    /**
     * Full parsed CDC response object for debugging.
     *
     * Parameter fields are parsed from `get_postprocess_param.result`. This
     * field keeps the complete response envelope, including `cmd`, `result`, and
     * optional `meta.ranges`, so SDK developers can inspect protocol details.
     * Use `SdkResult.raw` for the original response string.
     */
    val raw: JSONObject? = null
) {
    companion object {
        /** Parses post-process parameters from protocol JSON. */
        fun fromJson(json: JSONObject): PostprocessParamDto {
            fun optDoubleOrNull(key: String): Double? = if (json.has(key)) json.optDouble(key) else null
            fun optIntOrNull(key: String): Int? = if (json.has(key)) json.optInt(key) else null
            fun optBooleanOrNull(key: String): Boolean? = if (json.has(key)) json.optBoolean(key) else null

            return PostprocessParamDto(
                bodyThreshold = optDoubleOrNull("body_detection_threshold"),
                faceThreshold = optDoubleOrNull("face_threshold"),
                gazeThreshold = optDoubleOrNull("gaze_threshold"),
                nmsThreshold = optDoubleOrNull("nms_threshold"),
                back2cameraThreshold = optDoubleOrNull("back2camera_threshold"),
                genderThreshold = optDoubleOrNull("gender_threshold"),
                maxBodyDetection = optIntOrNull("max_body_detection"),
                faceMinSize = optIntOrNull("face_min_size"),
                maxAge = optIntOrNull("max_age"),
                minHits = optIntOrNull("min_hits"),
                iouThreshold = optDoubleOrNull("iou_threshold"),
                maxTrackers = optIntOrNull("max_trackers"),
                minBboxArea = optDoubleOrNull("min_bbox_area"),
                minHeight = optDoubleOrNull("min_height"),
                stabilityThreshold = optDoubleOrNull("stability_threshold"),
                bodyScoreTrackingThreshold = optDoubleOrNull("body_score_tracking_threshold"),
                bodyBoxHeightThreshold = optDoubleOrNull("body_box_height_threshold"),
                idCreationThreshold = optDoubleOrNull("id_creation_threshold"),
                clusterRoi = parseRoiArray(json.optJSONArray("cluster_roi")),
                pitchThreshold = optIntOrNull("pitch_threshold"),
                yawThreshold = optIntOrNull("yaw_threshold"),
                inferenceEnabled = optBooleanOrNull("inference_enabled"),
                faceDetectionRoi = parseRoiArray(json.optJSONArray("face_detection_roi")),
                trajectoryDbLifetime = optIntOrNull("trajectory_db_lifetime"),
                gazeFocusThreshold = optDoubleOrNull("gaze_focus_threshold"),
                enableWatchingRegion = optBooleanOrNull("enable_watching_region"),
                watchingRegion = json.optJSONObject("watching_region")?.let { WatchingRegionDto.fromJson(it) }
                    ?.takeUnless { it.isEmpty() },
                inferenceResultFilterEnable = optBooleanOrNull("inference_result_filter_enable"),
                enableGazeTimeFunctions = optBooleanOrNull("enable_gaze_time_functions"),
                enableAgeSmooth = optBooleanOrNull("enable_age_smooth"),
                enableAgeSmoothContinuous = optBooleanOrNull("enable_age_smooth_continuous"),
                ageBufSize = optIntOrNull("age_buf_size"),
                ageIdSize = optIntOrNull("age_id_size"),
                enableGenderVote = optBooleanOrNull("enable_gender_vote"),
                enableGenderVoteContinuous = optBooleanOrNull("enable_gender_vote_continuous"),
                genderBufSize = optIntOrNull("gender_buf_size"),
                genderIdSize = optIntOrNull("gender_id_size"),
                faceMinSizePpl = optIntOrNull("face_min_size_ppl"),
                enableZeroAge = optBooleanOrNull("enable_zero_age"),
                enableInvalidGender = optBooleanOrNull("enable_invalid_gender"),
                enableDistributionAdjust = optBooleanOrNull("enable_distribution_adjust"),
                sMean = optDoubleOrNull("s_mean"),
                sStd = optDoubleOrNull("s_std"),
                tMean = optDoubleOrNull("t_mean"),
                tStd = optDoubleOrNull("t_std"),
                mBias = optDoubleOrNull("m_bias"),
                sBias = optDoubleOrNull("s_bias"),
                raw = json
            )
        }

        private fun parseRoiArray(jsonArray: JSONArray?): List<RoiPointDto>? {
            if (jsonArray == null) return null
            val result = mutableListOf<RoiPointDto>()
            for (index in 0 until jsonArray.length()) {
                val point = jsonArray.optJSONObject(index) ?: continue
                if (!point.has("x") || !point.has("y")) continue
                result.add(RoiPointDto(point.optDouble("x").toFloat(), point.optDouble("y").toFloat()))
            }
            return result.takeIf { it.isNotEmpty() }
        }
    }

    /** Converts non-null fields to set_postprocess_param.params JSON. */
    fun toSetJson(): JSONObject = JSONObject().apply {
        bodyThreshold?.let { put("body_detection_threshold", it) }
        faceThreshold?.let { put("face_threshold", it) }
        gazeThreshold?.let { put("gaze_threshold", it) }
        nmsThreshold?.let { put("nms_threshold", it) }
        back2cameraThreshold?.let { put("back2camera_threshold", it) }
        genderThreshold?.let { put("gender_threshold", it) }
        maxBodyDetection?.let { put("max_body_detection", it) }
        faceMinSize?.let { put("face_min_size", it) }
        maxAge?.let { put("max_age", it) }
        minHits?.let { put("min_hits", it) }
        iouThreshold?.let { put("iou_threshold", it) }
        maxTrackers?.let { put("max_trackers", it) }
        minBboxArea?.let { put("min_bbox_area", it) }
        minHeight?.let { put("min_height", it) }
        stabilityThreshold?.let { put("stability_threshold", it) }
        bodyScoreTrackingThreshold?.let { put("body_score_tracking_threshold", it) }
        bodyBoxHeightThreshold?.let { put("body_box_height_threshold", it) }
        idCreationThreshold?.let { put("id_creation_threshold", it) }
        clusterRoi?.takeIf { it.isNotEmpty() }?.let { put("cluster_roi", it.toJsonArray()) }
        pitchThreshold?.let { put("pitch_threshold", it) }
        yawThreshold?.let { put("yaw_threshold", it) }
        inferenceEnabled?.let { put("inference_enabled", it) }
        faceDetectionRoi?.takeIf { it.isNotEmpty() }?.let { put("face_detection_roi", it.toJsonArray()) }
        trajectoryDbLifetime?.let { put("trajectory_db_lifetime", it) }
        gazeFocusThreshold?.let { put("gaze_focus_threshold", it) }
        enableWatchingRegion?.let { put("enable_watching_region", it) }
        watchingRegion?.takeUnless { it.isEmpty() }?.let { put("watching_region", it.toJson()) }
        inferenceResultFilterEnable?.let { put("inference_result_filter_enable", it) }
        enableGazeTimeFunctions?.let { put("enable_gaze_time_functions", it) }
        enableAgeSmooth?.let { put("enable_age_smooth", it) }
        enableAgeSmoothContinuous?.let { put("enable_age_smooth_continuous", it) }
        ageBufSize?.let { put("age_buf_size", it) }
        ageIdSize?.let { put("age_id_size", it) }
        enableGenderVote?.let { put("enable_gender_vote", it) }
        enableGenderVoteContinuous?.let { put("enable_gender_vote_continuous", it) }
        genderBufSize?.let { put("gender_buf_size", it) }
        genderIdSize?.let { put("gender_id_size", it) }
        faceMinSizePpl?.let { put("face_min_size_ppl", it) }
        enableZeroAge?.let { put("enable_zero_age", it) }
        enableInvalidGender?.let { put("enable_invalid_gender", it) }
        enableDistributionAdjust?.let { put("enable_distribution_adjust", it) }
        sMean?.let { put("s_mean", it) }
        sStd?.let { put("s_std", it) }
        tMean?.let { put("t_mean", it) }
        tStd?.let { put("t_std", it) }
        mBias?.let { put("m_bias", it) }
        sBias?.let { put("s_bias", it) }
    }
}

private fun List<RoiPointDto>.toJsonArray(): JSONArray = JSONArray().apply {
    forEach { put(it.toJson()) }
}
