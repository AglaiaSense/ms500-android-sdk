package com.ms500.usbcam.dto

import org.json.JSONArray
import org.json.JSONObject

data class RoiPointDto(
    val x: Double,
    val y: Double
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("x", x)
        put("y", y)
    }
}

data class WatchingRegionDto(
    val minYaw: Double? = null,
    val maxYaw: Double? = null,
    val minPitch: Double? = null,
    val maxPitch: Double? = null
) {
    companion object {
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

    fun toJson(): JSONObject = JSONObject().apply {
        minYaw?.let { put("min_yaw", it) }
        maxYaw?.let { put("max_yaw", it) }
        minPitch?.let { put("min_pitch", it) }
        maxPitch?.let { put("max_pitch", it) }
    }

    fun isEmpty(): Boolean = minYaw == null && maxYaw == null && minPitch == null && maxPitch == null
}

data class PplParameterDto(
    val faceDetectionRoi: List<RoiPointDto>? = null,
    val trajectoryDbLifetime: Int? = null,
    val gazeFocusThreshold: Double? = null,
    val enableWatchingRegion: Boolean? = null,
    val watchingRegion: WatchingRegionDto? = null,
    val inferenceResultFilterEnable: Boolean? = null,
    val enableGazeTimeFunctions: Boolean? = null,
    val enableAgeSmooth: Boolean? = null,
    val enableAgeSmoothContinuous: Boolean? = null,
    val ageBufSize: Int? = null,
    val ageIdSize: Int? = null,
    val enableGenderVote: Boolean? = null,
    val enableGenderVoteContinuous: Boolean? = null,
    val genderBufSize: Int? = null,
    val genderIdSize: Int? = null,
    val faceMinSizePpl: Int? = null,
    val enableZeroAge: Boolean? = null,
    val enableInvalidGender: Boolean? = null,
    val aiModelBundleId: String? = null,
    val raw: JSONObject? = null
) {
    companion object {
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

        private fun parseRoiArray(jsonArray: JSONArray): List<RoiPointDto> {
            val result = mutableListOf<RoiPointDto>()
            for (index in 0 until jsonArray.length()) {
                val point = jsonArray.optJSONObject(index) ?: continue
                if (!point.has("x") || !point.has("y")) continue
                result.add(RoiPointDto(point.optDouble("x"), point.optDouble("y")))
            }
            return result
        }

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

data class PostprocessParamDto(
    val confThreshold: Double? = null,
    val bodyThreshold: Double? = null,
    val faceThreshold: Double? = null,
    val gazeThreshold: Double? = null,
    val nmsThreshold: Double? = null,
    val back2cameraThreshold: Double? = null,
    val genderThreshold: Double? = null,
    val maxBodyDetection: Int? = null,
    val faceMinSize: Int? = null,
    val maxAge: Int? = null,
    val minHits: Int? = null,
    val iouThreshold: Double? = null,
    val maxTrackers: Int? = null,
    val minBboxArea: Double? = null,
    val minHeight: Double? = null,
    val stabilityThreshold: Double? = null,
    val bodyScoreTrackingThreshold: Double? = null,
    val pitchThreshold: Int? = null,
    val yawThreshold: Int? = null,
    val inferenceEnabled: Boolean? = null,
    val pplParameter: PplParameterDto? = null,
    val raw: JSONObject? = null
) {
    companion object {
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
