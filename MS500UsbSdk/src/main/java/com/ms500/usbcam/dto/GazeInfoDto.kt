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

data class FaceAttribute(
    val gender: String? = null,
    val age: Int? = null,
    val genderScore: Double? = null
) {
    companion object {
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

data class FacePose(
    val yaw: Double = 0.0,
    val pitch: Double = 0.0,
    val roll: Double = 0.0
) {
    companion object {
        fun fromJson(json: JSONObject): FacePose {
            return FacePose(
                yaw = json.optDouble("yaw", 0.0),
                pitch = json.optDouble("pitch", 0.0),
                roll = json.optDouble("roll", 0.0)
            )
        }
    }
}

data class GazeInfoDto(
    val horizontal: String? = null,
    val vertical: String? = null,
    val horizontalScore: Double? = null,
    val verticalScore: Double? = null
) {
    companion object {
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

data class BoundingBox(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {
    companion object {
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

data class FaceInfo(
    val boundingBox: BoundingBox? = null,
    val boundingBoxScore: Double = 0.0,
    val attribute: FaceAttribute? = null,
    val pose: FacePose? = null,
    val gaze: GazeInfoDto? = null,
    val isBackToCamera: Boolean = false
) {
    companion object {
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

data class BodyInfo(
    val boundingBox: BoundingBox? = null,
    val boundingBoxScore: Double = 0.0
) {
    companion object {
        fun fromJson(json: JSONObject): BodyInfo {
            return BodyInfo(
                boundingBox = json.optJSONObject("bounding_box")?.let { BoundingBox.fromJson(it) },
                boundingBoxScore = json.optDouble("bounding_box_score", 0.0)
            )
        }
    }
}

data class BehaviorInfo(
    val isGaze: Boolean = false,
    val gazeCount: Int? = null
) {
    companion object {
        fun fromJson(json: JSONObject): BehaviorInfo {
            return BehaviorInfo(
                isGaze = json.optBoolean("is_gaze", false),
                gazeCount = json.optInt("gaze_count").takeIf { json.has("gaze_count") }
            )
        }
    }
}

data class GazeDetection(
    val trackId: Int = 0,
    val body: BodyInfo? = null,
    val face: FaceInfo? = null,
    val behavior: BehaviorInfo? = null,
    val dataStatus: String? = null,
    val raw: JSONObject? = null
) {
    companion object {
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

data class GazePayload(
    val dataStatus: String? = null,
    val trackId: Int? = null,
    val timestampUs: Long? = null,
    val detectionCount: Int? = null,
    val summaryCount: Int? = null,
    val detections: List<GazeDetection> = emptyList(),
    val raw: JSONObject? = null
) {
    companion object {
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

data class InferenceResult(
    val trackId: String? = null,
    val face: FaceInfo? = null,
    val body: BodyInfo? = null,
    val boundingBox: BoundingBox? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val raw: JSONObject? = null
) {
    val hasFace: Boolean get() = face != null
    val hasBody: Boolean get() = body != null

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
