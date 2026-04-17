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
 * ROI 点 DTO / Region of Interest point DTO
 */
data class RoiPointDto(
    /** X 坐标 / X coordinate */
    val x: Double,
    /** Y 坐标 / Y coordinate */
    val y: Double
) {
    /**
     * 转换为 JSON / Convert to JSON
     */
    fun toJson(): JSONObject = JSONObject().apply {
        put("x", x)
        put("y", y)
    }
}

/**
 * 注视区域 DTO / Watching region DTO
 */
data class WatchingRegionDto(
    /** 最小偏航角 / Minimum yaw */
    val minYaw: Double? = null,
    /** 最大偏航角 / Maximum yaw */
    val maxYaw: Double? = null,
    /** 最小俯仰角 / Minimum pitch */
    val minPitch: Double? = null,
    /** 最大俯仰角 / Maximum pitch */
    val maxPitch: Double? = null
) {
    companion object {
        /**
         * 从 JSON 解析注视区域 / Parse watching region from JSON
         *
         * @param json JSON 对象
         * @return [WatchingRegionDto]
         */
        fun fromJson(json: JSONObject): WatchingRegionDto {
            fun optDoubleOrNull(key: String): Double? = if (json.has(key)) json.optDouble(key) else null
            return WatchingRegionDto(
                minYaw = optDoubleOrNull("min_yaw") ?: optDoubleOrNull("watching_min_yaw"),
                maxYaw = optDoubleOrNull("max_yaw") ?: optDoubleOrNull("watching_max_yaw"),
                minPitch = optDoubleOrNull("min_pitch") ?: optDoubleOrNull("watching_min_pitch"),
                maxPitch = optDoubleOrNull("max_pitch") ?: optDoubleOrNull("watching_max_pitch")
            )
        }
    }

    /**
     * 转换为 JSON / Convert to JSON
     */
    fun toJson(): JSONObject = JSONObject().apply {
        minYaw?.let { put("min_yaw", it) }
        maxYaw?.let { put("max_yaw", it) }
        minPitch?.let { put("min_pitch", it) }
        maxPitch?.let { put("max_pitch", it) }
    }

    /**
     * 是否为空 / Whether empty
     */
    fun isEmpty(): Boolean = minYaw == null && maxYaw == null && minPitch == null && maxPitch == null
}

/**
 * PPL 参数 DTO / PPL parameter DTO
 */
data class PplParameterDto(
    /** 人脸检测 ROI / Face detection ROI */
    val faceDetectionRoi: List<RoiPointDto>? = null,
    /** 轨迹数据库生命周期 / Trajectory DB lifetime */
    val trajectoryDbLifetime: Int? = null,
    /** 凝视聚焦阈值 / Gaze focus threshold */
    val gazeFocusThreshold: Double? = null,
    /** 是否启用注视区域 / Enable watching region */
    val enableWatchingRegion: Boolean? = null,
    /** 注视区域 / Watching region */
    val watchingRegion: WatchingRegionDto? = null,
    /** 是否启用推理结果过滤 / Enable inference result filter */
    val inferenceResultFilterEnable: Boolean? = null,
    /** 是否启用凝视时间功能 / Enable gaze time functions */
    val enableGazeTimeFunctions: Boolean? = null,
    /** 是否启用年龄平滑 / Enable age smoothing */
    val enableAgeSmooth: Boolean? = null,
    /** 是否启用连续年龄平滑 / Enable continuous age smoothing */
    val enableAgeSmoothContinuous: Boolean? = null,
    /** 年龄缓冲区大小 / Age buffer size */
    val ageBufSize: Int? = null,
    /** 年龄 ID 大小 / Age ID size */
    val ageIdSize: Int? = null,
    /** 是否启用性别投票 / Enable gender vote */
    val enableGenderVote: Boolean? = null,
    /** 是否启用连续性别投票 / Enable continuous gender vote */
    val enableGenderVoteContinuous: Boolean? = null,
    /** 性别缓冲区大小 / Gender buffer size */
    val genderBufSize: Int? = null,
    /** 性别 ID 大小 / Gender ID size */
    val genderIdSize: Int? = null,
    /** PPL 人脸最小尺寸 / Face minimum size for PPL */
    val faceMinSizePpl: Int? = null,
    /** 是否允许零岁 / Enable zero age */
    val enableZeroAge: Boolean? = null,
    /** 是否允许无效性别 / Enable invalid gender */
    val enableInvalidGender: Boolean? = null,
    /** AI 模型包 ID / AI model bundle ID */
    val aiModelBundleId: String? = null,
    /** 原始 JSON 数据 / Raw JSON data */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * 从 JSON 解析 PPL 参数 / Parse PPL parameters from JSON
         *
         * @param json JSON 对象
         * @return [PplParameterDto]
         */
        fun fromJson(json: JSONObject): PplParameterDto {
            fun optDoubleOrNull(key: String): Double? = if (json.has(key)) json.optDouble(key) else null
            fun optIntOrNull(key: String): Int? = if (json.has(key)) json.optInt(key) else null
            fun optBooleanOrNull(key: String): Boolean? = if (json.has(key)) json.optBoolean(key) else null

            val roiArray = json.optJSONArray("face_detection_roi")
            val roiPoints = when {
                roiArray != null -> parseRoiArray(roiArray)
                json.optJSONObject("face_detection_ROI") != null -> parseFlattenedRoiObject(json.optJSONObject("face_detection_ROI")!!)
                else -> parseFlattenedRoi(json)
            }

            val watchingRegion = WatchingRegionDto.fromJson(
                json.optJSONObject("watching_region") ?: json
            ).takeUnless { it.isEmpty() }

            return PplParameterDto(
                faceDetectionRoi = roiPoints.takeIf { it.isNotEmpty() },
                trajectoryDbLifetime = optIntOrNull("trajectory_db_lifetime"),
                gazeFocusThreshold = optDoubleOrNull("gaze_focus_threshold"),
                enableWatchingRegion = optBooleanOrNull("enable_watching_region"),
                watchingRegion = watchingRegion,
                inferenceResultFilterEnable = optBooleanOrNull("inference_result_filter_enable"),
                enableGazeTimeFunctions = optBooleanOrNull("enable_gaze_time_functions"),
                enableAgeSmooth = optBooleanOrNull("enable_age_smooth"),
                enableAgeSmoothContinuous = optBooleanOrNull("enable_age_smooth_continuous"),
                ageBufSize = optIntOrNull("age_buf_size"),
                ageIdSize = optIntOrNull("age_id_size") ?: optIntOrNull("age_ID_size"),
                enableGenderVote = optBooleanOrNull("enable_gender_vote"),
                enableGenderVoteContinuous = optBooleanOrNull("enable_gender_vote_continuous"),
                genderBufSize = optIntOrNull("gender_buf_size"),
                genderIdSize = optIntOrNull("gender_id_size") ?: optIntOrNull("gender_ID_size"),
                faceMinSizePpl = optIntOrNull("face_min_size_ppl") ?: optIntOrNull("face_min_size"),
                enableZeroAge = optBooleanOrNull("enable_zero_age"),
                enableInvalidGender = optBooleanOrNull("enable_invalid_gender"),
                aiModelBundleId = json.optString("ai_model_bundle_id").ifBlank { null },
                raw = json
            )
        }

        /**
         * 从 JSON 数组解析 ROI / Parse ROI from JSON array
         */
        private fun parseRoiArray(jsonArray: JSONArray): List<RoiPointDto> {
            val result = mutableListOf<RoiPointDto>()
            for (index in 0 until jsonArray.length()) {
                val point = jsonArray.optJSONObject(index) ?: continue
                if (!point.has("x") || !point.has("y")) continue
                result.add(RoiPointDto(point.optDouble("x"), point.optDouble("y")))
            }
            return result
        }

        /**
         * 从扁平化字段解析 ROI / Parse ROI from flattened fields
         */
        private fun parseFlattenedRoi(json: JSONObject): List<RoiPointDto> {
            val result = mutableListOf<RoiPointDto>()
            for (index in 1..4) {
                val xKey = "face_detection_roi_p${index}_x"
                val yKey = "face_detection_roi_p${index}_y"
                if (json.has(xKey) && json.has(yKey)) {
                    result.add(RoiPointDto(json.optDouble(xKey), json.optDouble(yKey)))
                }
            }
            return result
        }

        /**
         * 从扁平化 ROI 对象解析 / Parse ROI from flattened ROI object
         */
        private fun parseFlattenedRoiObject(json: JSONObject): List<RoiPointDto> {
            val result = mutableListOf<RoiPointDto>()
            for (index in 1..4) {
                val xKey = "p${index}_x"
                val yKey = "p${index}_y"
                if (json.has(xKey) && json.has(yKey)) {
                    result.add(RoiPointDto(json.optDouble(xKey), json.optDouble(yKey)))
                }
            }
            return result
        }
    }

    /**
     * 转换为 JSON / Convert to JSON
     */
    fun toJson(): JSONObject = JSONObject().apply {
        faceDetectionRoi?.takeIf { it.isNotEmpty() }?.let { points ->
            put("face_detection_roi", JSONArray().apply {
                points.forEach { put(it.toJson()) }
            })
        }
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
        aiModelBundleId?.let { put("ai_model_bundle_id", it) }
    }

    /**
     * 是否为空 / Whether empty
     */
    fun isEmpty(): Boolean {
        return faceDetectionRoi.isNullOrEmpty() &&
            trajectoryDbLifetime == null &&
            gazeFocusThreshold == null &&
            enableWatchingRegion == null &&
            (watchingRegion == null || watchingRegion.isEmpty()) &&
            inferenceResultFilterEnable == null &&
            enableGazeTimeFunctions == null &&
            enableAgeSmooth == null &&
            enableAgeSmoothContinuous == null &&
            ageBufSize == null &&
            ageIdSize == null &&
            enableGenderVote == null &&
            enableGenderVoteContinuous == null &&
            genderBufSize == null &&
            genderIdSize == null &&
            faceMinSizePpl == null &&
            enableZeroAge == null &&
            enableInvalidGender == null &&
            aiModelBundleId == null
    }
}

/**
 * 后处理参数 DTO / Post-process parameter DTO
 */
data class PostprocessParamDto(
    /** 置信度阈值 / Confidence threshold */
    val confThreshold: Double? = null,
    /** 人体阈值 / Body threshold */
    val bodyThreshold: Double? = null,
    /** 人脸阈值 / Face threshold */
    val faceThreshold: Double? = null,
    /** 凝视阈值 / Gaze threshold */
    val gazeThreshold: Double? = null,
    /** NMS 阈值 / NMS threshold */
    val nmsThreshold: Double? = null,
    /** 背对摄像头阈值 / Back-to-camera threshold */
    val back2cameraThreshold: Double? = null,
    /** 性别阈值 / Gender threshold */
    val genderThreshold: Double? = null,
    /** 最大人体检测数 / Maximum body detections */
    val maxBodyDetection: Int? = null,
    /** 人脸最小尺寸 / Face minimum size */
    val faceMinSize: Int? = null,
    /** 最大年龄（跟踪） / Maximum age (tracking) */
    val maxAge: Int? = null,
    /** 最小命中次数 / Minimum hits */
    val minHits: Int? = null,
    /** IOU 阈值 / IOU threshold */
    val iouThreshold: Double? = null,
    /** 最大跟踪器数 / Maximum trackers */
    val maxTrackers: Int? = null,
    /** 最小边界框面积 / Minimum bounding box area */
    val minBboxArea: Double? = null,
    /** 最小高度 / Minimum height */
    val minHeight: Double? = null,
    /** 稳定性阈值 / Stability threshold */
    val stabilityThreshold: Double? = null,
    /** 人体跟踪分数阈值 / Body score tracking threshold */
    val bodyScoreTrackingThreshold: Double? = null,
    /** 俯仰角阈值 / Pitch threshold */
    val pitchThreshold: Int? = null,
    /** 偏航角阈值 / Yaw threshold */
    val yawThreshold: Int? = null,
    /** 推理是否启用 / Whether inference is enabled */
    val inferenceEnabled: Boolean? = null,
    /** PPL 参数 / PPL parameters */
    val pplParameter: PplParameterDto? = null,
    /** 原始 JSON 数据 / Raw JSON data */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * 从 JSON 解析后处理参数 / Parse post-process parameters from JSON
         *
         * @param json JSON 对象
         * @return [PostprocessParamDto]
         */
        fun fromJson(json: JSONObject): PostprocessParamDto {
            fun optDoubleAny(vararg keys: String): Double? {
                keys.forEach { key ->
                    if (json.has(key)) return json.optDouble(key)
                }
                return null
            }

            fun optIntAny(vararg keys: String): Int? {
                keys.forEach { key ->
                    if (json.has(key)) return json.optInt(key)
                }
                return null
            }

            return PostprocessParamDto(
                confThreshold = optDoubleAny("conf_threshold", "conf_thresh"),
                bodyThreshold = optDoubleAny("body_threshold", "body_thresh"),
                faceThreshold = optDoubleAny("face_threshold", "face_thresh"),
                gazeThreshold = optDoubleAny("gaze_threshold", "gaze_thresh"),
                nmsThreshold = optDoubleAny("nms_threshold", "nms_thresh"),
                back2cameraThreshold = optDoubleAny("back2camera_threshold"),
                genderThreshold = optDoubleAny("gender_threshold"),
                maxBodyDetection = optIntAny("max_body_detection"),
                faceMinSize = optIntAny("face_min_size"),
                maxAge = optIntAny("max_age"),
                minHits = optIntAny("min_hits"),
                iouThreshold = optDoubleAny("iou_threshold"),
                maxTrackers = optIntAny("max_trackers"),
                minBboxArea = optDoubleAny("min_bbox_area"),
                minHeight = optDoubleAny("min_height"),
                stabilityThreshold = optDoubleAny("stability_threshold"),
                bodyScoreTrackingThreshold = optDoubleAny("body_score_tracking_threshold"),
                pitchThreshold = optIntAny("pitch_threshold"),
                yawThreshold = optIntAny("yaw_threshold"),
                inferenceEnabled = json.takeIf { it.has("inference_enabled") }?.optBoolean("inference_enabled"),
                pplParameter = json.optJSONObject("ppl_parameter")
                    ?.let { PplParameterDto.fromJson(it) }
                    ?.takeUnless { it.isEmpty() },
                raw = json
            )
        }
    }

    /**
     * 转换为设置用 JSON / Convert to setting JSON
     */
    fun toSetJson(): JSONObject {
        return JSONObject().apply {
            confThreshold?.let { put("conf_threshold", it) }
            bodyThreshold?.let { put("body_threshold", it) }
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
            pitchThreshold?.let { put("pitch_threshold", it) }
            yawThreshold?.let { put("yaw_threshold", it) }
            inferenceEnabled?.let { put("inference_enabled", it) }
            pplParameter?.takeUnless { it.isEmpty() }?.let { put("ppl_parameter", it.toJson()) }
        }
    }
}
