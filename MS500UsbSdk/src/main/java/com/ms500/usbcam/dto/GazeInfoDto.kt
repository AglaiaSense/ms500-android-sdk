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
 * Face attributes
 */
data class FaceAttribute(
    /** Gender */
    val gender: String? = null,
    /** Age */
    val age: Int? = null,
    /** Gender confidence score */
    val genderScore: Double? = null
) {
    companion object {
        /**
         * Parse face attributes from JSON
         *
         * @param json JSON object
         * @return [FaceAttribute]
         */
        fun fromJson(json: JSONObject): FaceAttribute {
            val genderObj = json.optJSONObject("gender")
            return FaceAttribute(
                gender = genderObj?.optString("type")?.takeIf { it.isNotBlank() },
                age = json.optInt("age"),
                genderScore = genderObj?.optDouble("score", -1.0)?.takeIf { it >= 0 }
            )
        }
    }
}

/**
 * Face pose (yaw, pitch, roll)
 */
data class FacePose(
    /** Yaw angle */
    val yaw: Double = 0.0,
    /** Pitch angle */
    val pitch: Double = 0.0,
    /** Roll angle */
    val roll: Double = 0.0
) {
    companion object {
        /**
         * Parse face pose from JSON
         *
         * @param json JSON object
         * @return [FacePose]
         */
        fun fromJson(json: JSONObject): FacePose {
            return FacePose(
                yaw = json.optDouble("yaw", 0.0),
                pitch = json.optDouble("pitch", 0.0),
                roll = json.optDouble("roll", 0.0)
            )
        }
    }
}

/**
 * Gaze information DTO
 */
data class GazeInfoDto(
    /** Horizontal gaze direction */
    val horizontal: String? = null,
    /** Vertical gaze direction */
    val vertical: String? = null,
    /** Horizontal gaze confidence */
    val horizontalScore: Double? = null,
    /** Vertical gaze confidence */
    val verticalScore: Double? = null
) {
    companion object {
        /**
         * Parse gaze info from JSON
         *
         * @param json JSON object
         * @return [GazeInfoDto]
         */
        fun fromJson(json: JSONObject): GazeInfoDto {
            return GazeInfoDto(
                horizontal = json.optString("horizontal", "").takeIf { it.isNotEmpty() },
                vertical = json.optString("vertical", "").takeIf { it.isNotEmpty() },
                horizontalScore = json.optDouble("horizontal_score", -1.0).takeIf { it >= 0 },
                verticalScore = json.optDouble("vertical_score", -1.0).takeIf { it >= 0 }
            )
        }
    }
}

/**
 * Bounding box
 */
data class BoundingBox(
    /** Left coordinate */
    val left: Int = 0,
    /** Top coordinate */
    val top: Int = 0,
    /** Right coordinate */
    val right: Int = 0,
    /** Bottom coordinate */
    val bottom: Int = 0
) {
    companion object {
        /**
         * Parse bounding box from JSON
         *
         * @param json JSON object
         * @return [BoundingBox]
         */
        fun fromJson(json: JSONObject): BoundingBox {
            return BoundingBox(
                left = json.optInt("left", json.optInt("x1", 0)),
                top = json.optInt("top", json.optInt("y1", 0)),
                right = json.optInt("right", json.optInt("x2", 0)),
                bottom = json.optInt("bottom", json.optInt("y2", 0))
            )
        }
    }
}

/**
 * Face detection info
 */
data class FaceInfo(
    /** Face bounding box */
    val boundingBox: BoundingBox? = null,
    /** Face detection score */
    val boundingBoxScore: Double = 0.0,
    /** Face attributes */
    val attribute: FaceAttribute? = null,
    /** Face pose */
    val pose: FacePose? = null,
    /** Gaze info */
    val gaze: GazeInfoDto? = null,
    /** Whether facing away from camera */
    val isBackToCamera: Boolean = false
) {
    companion object {
        /**
         * Parse face info from JSON
         *
         * @param json JSON object
         * @return [FaceInfo]
         */
        fun fromJson(json: JSONObject): FaceInfo {
            return FaceInfo(
                boundingBox = json.optJSONObject("bounding_box")?.let { BoundingBox.fromJson(it) },
                boundingBoxScore = json.optDouble("bounding_box_score", 0.0),
                attribute = json.optJSONObject("attribute")?.let { FaceAttribute.fromJson(it) },
                pose = json.optJSONObject("pose")?.let { FacePose.fromJson(it) },
                gaze = json.optJSONObject("gaze")?.let { GazeInfoDto.fromJson(it) },
                isBackToCamera = json.optBoolean("is_back2camera", false)
            )
        }
    }
}

/**
 * Body detection info
 */
data class BodyInfo(
    /** Body bounding box */
    val boundingBox: BoundingBox? = null,
    /** Body detection score */
    val boundingBoxScore: Double = 0.0
) {
    companion object {
        /**
         * Parse body info from JSON
         *
         * @param json JSON object
         * @return [BodyInfo]
         */
        fun fromJson(json: JSONObject): BodyInfo {
            return BodyInfo(
                boundingBox = json.optJSONObject("bounding_box")?.let { BoundingBox.fromJson(it) },
                boundingBoxScore = json.optDouble("bounding_box_score", 0.0)
            )
        }
    }
}

/**
 * Behavior info
 */
data class BehaviorInfo(
    /** Whether gazing */
    val isGaze: Boolean = false,
    /** Gaze count */
    val gazeCount: Int? = null
) {
    companion object {
        /**
         * Parse behavior info from JSON
         *
         * @param json JSON object
         * @return [BehaviorInfo]
         */
        fun fromJson(json: JSONObject): BehaviorInfo {
            return BehaviorInfo(
                isGaze = json.optBoolean("is_gaze", false),
                gazeCount = json.optInt("gaze_count").takeIf { json.has("gaze_count") }
            )
        }
    }
}

/**
 * Gaze detection result
 */
data class GazeDetection(
    /** Track ID */
    val trackId: Int = 0,
    /** Body info */
    val body: BodyInfo? = null,
    /** Face info */
    val face: FaceInfo? = null,
    /** Behavior info */
    val behavior: BehaviorInfo? = null,
    /** Data status */
    val dataStatus: String? = null,
    /** Raw JSON data */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * Parse gaze detection from JSON
         *
         * @param json       JSON object
         * @param rootStatus Root-level status (fallback)
         * @return [GazeDetection]
         */
        fun fromJson(json: JSONObject, rootStatus: String? = null): GazeDetection {
            return GazeDetection(
                trackId = json.optInt("track_id", 0),
                body = json.optJSONObject("body")?.let { BodyInfo.fromJson(it) },
                face = json.optJSONObject("face")?.let { FaceInfo.fromJson(it) },
                behavior = json.optJSONObject("behavior")?.let { BehaviorInfo.fromJson(it) },
                dataStatus = json.optString("data_status").ifBlank { rootStatus },
                raw = json
            )
        }
    }
}

/**
 * Gaze payload
 */
data class GazePayload(
    /** Data status */
    val dataStatus: String? = null,
    /** Track ID */
    val trackId: Int? = null,
    /** Timestamp in microseconds */
    val timestampUs: Long? = null,
    /** Detection count */
    val detectionCount: Int? = null,
    /** Summary count */
    val summaryCount: Int? = null,
    /** Detection list */
    val detections: List<GazeDetection> = emptyList(),
    /** Raw JSON data */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * Parse gaze payload from JSON
         *
         * @param json JSON object
         * @return [GazePayload]
         */
        fun fromJson(json: JSONObject): GazePayload {
            val dataStatus = json.optString("data_status").ifBlank { null }
            val detectionsArray = json.optJSONArray("detections")
            val detections = when {
                detectionsArray != null -> fromJsonArray(detectionsArray, dataStatus)
                json.has("track_id") -> listOf(GazeDetection.fromJson(json, dataStatus))
                else -> emptyList()
            }
            return GazePayload(
                dataStatus = dataStatus,
                trackId = json.optInt("track_id").takeIf { json.has("track_id") },
                timestampUs = json.optLong("timestamp_us").takeIf { json.has("timestamp_us") },
                detectionCount = json.optInt("detection_count").takeIf { json.has("detection_count") },
                summaryCount = json.optInt("summary_count").takeIf { json.has("summary_count") },
                detections = detections,
                raw = json
            )
        }

        /**
         * Parse detection list from JSON array
         *
         * @param jsonArray  JSON array
         * @param rootStatus Root-level status
         * @return List of [GazeDetection]
         */
        private fun fromJsonArray(jsonArray: JSONArray, rootStatus: String?): List<GazeDetection> {
            val results = mutableListOf<GazeDetection>()
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(index) ?: continue
                results.add(GazeDetection.fromJson(item, rootStatus))
            }
            return results
        }
    }
}

/**
 * Inference result
 */
data class InferenceResult(
    /** Track ID */
    val trackId: String? = null,
    /** Face info */
    val face: FaceInfo? = null,
    /** Body info */
    val body: BodyInfo? = null,
    /** Bounding box */
    val boundingBox: BoundingBox? = null,
    /** Timestamp in milliseconds */
    val timestamp: Long = System.currentTimeMillis(),
    /** Raw JSON data */
    val raw: JSONObject? = null
) {
    /** Whether face is present */
    val hasFace: Boolean get() = face != null
    /** Whether body is present */
    val hasBody: Boolean get() = body != null

    /**
     * Generate display text
     */
    fun toDisplayText(): String {
        val sb = StringBuilder()
        sb.append("User Info: [ID:${trackId ?: "N/A"}]  ")

        face?.attribute?.let { attr ->
            sb.append("${attr.gender ?: "N/A"}, ${attr.age ?: "?"}yo\n")
        } ?: sb.append("\n")

        sb.append(
            String.format(
                "Metrics:   Body:%.2f  Face:%.2f\n",
                body?.boundingBoxScore ?: 0.0,
                face?.boundingBoxScore ?: 0.0
            )
        )

        face?.pose?.let { pose ->
            sb.append(String.format("Pose:      Yaw:%.1f   Pitch:%.1f", pose.yaw, pose.pitch))
        }

        return sb.toString()
    }

    companion object {
        /**
         * Parse inference result from JSON
         *
         * @param json JSON object
         * @return [InferenceResult]
         */
        fun fromJson(json: JSONObject): InferenceResult {
            return InferenceResult(
                trackId = json.optString("track_id", "").takeIf { it.isNotEmpty() },
                face = json.optJSONObject("face")?.let { FaceInfo.fromJson(it) },
                body = json.optJSONObject("body")?.let { BodyInfo.fromJson(it) },
                boundingBox = json.optJSONObject("bbox")?.let { BoundingBox.fromJson(it) },
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                raw = json
            )
        }

        /**
         * Parse inference result list from JSON array
         *
         * @param jsonArray JSON array
         * @return List of [InferenceResult]
         */
        fun fromJsonArray(jsonArray: JSONArray): List<InferenceResult> {
            val results = mutableListOf<InferenceResult>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                results.add(fromJson(item))
            }
            return results
        }
    }
}
