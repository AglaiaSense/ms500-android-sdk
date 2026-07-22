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

// --- Protocol Samples -------------------------------------------------------

/*
 * Protocol sample: frame message.
 * {
 *   "data_status": "frame",
 *   "timestamp_us": 61216239,
 *   "detection_count": 2,
 *   "detections": [
 *     {
 *       "upload_status": "normal",
 *       "track_id": 1,
 *       "body": {"bounding_box": {"left": 100, "top": 50, "right": 200, "bottom": 350}, "bounding_box_score": 0.95},
 *       "face": {"bounding_box": {"left": 120, "top": 80, "right": 180, "bottom": 160}, "bounding_box_score": 0.88, "attribute": {"gender": {"type": "Male", "score": 0.92}, "age": 25}, "pose": {"yaw": 15, "pitch": -5}, "is_back2camera": false},
 *       "behavior": {"is_gaze": true, "detail_gaze_info": {"stay_time": 0, "detail_gaze_time": [], "bbox_cords": {"first": null, "last": null, "conf": null, "conf_score": 0}}}
 *     }
 *   ]
 * }
 *
 * Protocol sample: summary message.
 * {
 *   "data_status": "summary",
 *   "summary_count": 1,
 *   "detections": [
 *     {
 *       "upload_status": "track_id_timeout",
 *       "track_id": 1,
 *       "frame_count": 45,
 *       "stay_time_ms": 1500,
 *       "first_seen_ms": 59600,
 *       "last_seen_ms": 61100,
 *       "gaze_ratio": 0.67,
 *       "body": {"bounding_box": {"left": 100, "top": 50, "right": 200, "bottom": 350}, "bounding_box_score": 0.88},
 *       "face": {"bounding_box": {"left": 120, "top": 80, "right": 180, "bottom": 160}, "bounding_box_score": 0.82, "attribute": {"gender": {"type": "Male", "score": 0.95}, "age": 26}, "pose": {"yaw": 12, "pitch": -3}, "is_back2camera": false, "back_ratio": 0.1},
 *       "behavior": {"is_gaze": true, "gaze_count": 30, "detail_gaze_info": {"stay_time": 1500, "detail_gaze_time": [], "bbox_cords": {"first": null, "last": {"left": 100, "top": 50, "right": 200, "bottom": 350}, "conf": null, "conf_score": 0.88}}}
 *     }
 *   ]
 * }
 */

// --- JSON Helpers -----------------------------------------------------------

private fun JSONObject.optStringOrNull(key: String): String? {
    return if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    return if (has(key) && !isNull(key)) optInt(key) else null
}

private fun JSONObject.optLongOrNull(key: String): Long? {
    return if (has(key) && !isNull(key)) optLong(key) else null
}

private fun JSONObject.optDoubleOrNull(key: String): Double? {
    return if (has(key) && !isNull(key)) optDouble(key) else null
}

private fun inferDataStatusFromUploadStatus(uploadStatus: String?): String? {
    return when (uploadStatus) {
        GazePayload.UPLOAD_STATUS_NORMAL -> GazePayload.DATA_STATUS_FRAME
        GazePayload.UPLOAD_STATUS_TRACK_ID_TIMEOUT -> GazePayload.DATA_STATUS_SUMMARY
        else -> null
    }
}

// --- Basic DTOs -------------------------------------------------------------

/** Bounding box in device image coordinates. */
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

/** Body detection info. */
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

// --- Face DTOs --------------------------------------------------------------

/** Face attributes. */
data class FaceAttribute(
    val gender: String? = null,
    val age: Int? = null,
    val genderScore: Double? = null
) {
    companion object {
        fun fromJson(json: JSONObject): FaceAttribute {
            val genderObj = json.optJSONObject("gender")
            return FaceAttribute(
                gender = genderObj?.optStringOrNull("type"),
                age = json.optIntOrNull("age"),
                genderScore = genderObj?.optDoubleOrNull("score")
            )
        }
    }
}

/** Face pose angles. */
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

/** Face detection info. */
data class FaceInfo(
    val boundingBox: BoundingBox? = null,
    val boundingBoxScore: Double = 0.0,
    val attribute: FaceAttribute? = null,
    val pose: FacePose? = null,
    val isBackToCamera: Boolean = false,
    val backRatio: Double? = null
) {
    companion object {
        fun fromJson(json: JSONObject): FaceInfo {
            return FaceInfo(
                boundingBox = json.optJSONObject("bounding_box")?.let { BoundingBox.fromJson(it) },
                boundingBoxScore = json.optDouble("bounding_box_score", 0.0),
                attribute = json.optJSONObject("attribute")?.let { FaceAttribute.fromJson(it) },
                pose = json.optJSONObject("pose")?.let { FacePose.fromJson(it) },
                isBackToCamera = json.optBoolean("is_back2camera", false),
                backRatio = json.optDoubleOrNull("back_ratio")
            )
        }
    }
}

// --- Behavior DTOs ----------------------------------------------------------

/** First/last body boxes recorded in detailed gaze info. */
data class GazeBboxCords(
    val first: BoundingBox? = null,
    val last: BoundingBox? = null,
    val conf: BoundingBox? = null,
    val confScore: Double? = null
) {
    companion object {
        fun fromJson(json: JSONObject): GazeBboxCords {
            return GazeBboxCords(
                first = json.optJSONObject("first")?.let { BoundingBox.fromJson(it) },
                last = json.optJSONObject("last")?.let { BoundingBox.fromJson(it) },
                conf = json.optJSONObject("conf")?.let { BoundingBox.fromJson(it) },
                confScore = json.optDoubleOrNull("conf_score")
            )
        }
    }
}

/** Detailed gaze tracking info. */
data class DetailGazeInfo(
    val stayTime: Long? = null,
    val detailGazeTime: JSONArray? = null,
    val bboxCords: GazeBboxCords? = null
) {
    companion object {
        fun fromJson(json: JSONObject): DetailGazeInfo {
            return DetailGazeInfo(
                stayTime = json.optLongOrNull("stay_time"),
                detailGazeTime = json.optJSONArray("detail_gaze_time"),
                bboxCords = json.optJSONObject("bbox_cords")?.let { GazeBboxCords.fromJson(it) }
            )
        }
    }
}

/** Behavior info attached to each detection. */
data class BehaviorInfo(
    val isGaze: Boolean = false,
    val gazeCount: Int? = null,
    val detailGazeInfo: DetailGazeInfo? = null
) {
    companion object {
        fun fromJson(json: JSONObject): BehaviorInfo {
            return BehaviorInfo(
                isGaze = json.optBoolean("is_gaze", false),
                gazeCount = json.optIntOrNull("gaze_count"),
                detailGazeInfo = json.optJSONObject("detail_gaze_info")?.let { DetailGazeInfo.fromJson(it) }
            )
        }
    }
}

// --- Payload DTOs -----------------------------------------------------------

/** One item inside a frame or summary payload. */
data class GazeDetection(
    val trackId: Int = 0,
    val body: BodyInfo? = null,
    val face: FaceInfo? = null,
    val behavior: BehaviorInfo? = null,
    val dataStatus: String? = null,
    val uploadStatus: String? = null,
    val frameCount: Int? = null,
    val stayTimeMs: Long? = null,
    /** First detection time in milliseconds since device boot; summary messages only. */
    val firstSeenMs: Long? = null,
    /** Last detection time in milliseconds since device boot; summary messages only. */
    val lastSeenMs: Long? = null,
    val gazeRatio: Double? = null
) {
    companion object {
        fun fromJson(json: JSONObject, rootStatus: String? = null): GazeDetection {
            val uploadStatus = json.optStringOrNull("upload_status")
            val dataStatus = json.optStringOrNull("data_status")
                ?: rootStatus
                ?: inferDataStatusFromUploadStatus(uploadStatus)
            return GazeDetection(
                trackId = json.optInt("track_id", 0),
                body = json.optJSONObject("body")?.let { BodyInfo.fromJson(it) },
                face = json.optJSONObject("face")?.let { FaceInfo.fromJson(it) },
                behavior = json.optJSONObject("behavior")?.let { BehaviorInfo.fromJson(it) },
                dataStatus = dataStatus,
                uploadStatus = uploadStatus,
                frameCount = json.optIntOrNull("frame_count"),
                stayTimeMs = json.optLongOrNull("stay_time_ms"),
                firstSeenMs = json.optLongOrNull("first_seen_ms"),
                lastSeenMs = json.optLongOrNull("last_seen_ms"),
                gazeRatio = json.optDoubleOrNull("gaze_ratio")
            )
        }
    }
}

/** Root gaze payload sent through MSG_TYPE_INFERENCE_RESULT. */
data class GazePayload(
    val dataStatus: String? = null,
    /** Legacy top-level track ID; current summary payloads keep track_id inside detections only. */
    val trackId: Int? = null,
    val timestampUs: Long? = null,
    val detectionCount: Int? = null,
    val summaryCount: Int? = null,
    val detections: List<GazeDetection> = emptyList()
) {
    companion object {
        const val DATA_STATUS_FRAME = "frame"
        const val DATA_STATUS_SUMMARY = "summary"
        const val UPLOAD_STATUS_NORMAL = "normal"
        const val UPLOAD_STATUS_TRACK_ID_TIMEOUT = "track_id_timeout"

        fun fromJson(json: JSONObject): GazePayload {
            val dataStatus = json.optStringOrNull("data_status") ?: inferPayloadDataStatus(json)
            return GazePayload(
                dataStatus = dataStatus,
                trackId = json.optIntOrNull("track_id"),
                timestampUs = json.optLongOrNull("timestamp_us"),
                detectionCount = json.optIntOrNull("detection_count"),
                summaryCount = json.optIntOrNull("summary_count"),
                detections = parseDetections(json, dataStatus)
            )
        }

        private fun parseDetections(json: JSONObject, rootStatus: String?): List<GazeDetection> {
            val detections = json.optJSONArray("detections")
            return when {
                detections != null -> detections.toDetectionList(rootStatus)
                json.has("track_id") -> listOf(GazeDetection.fromJson(json, rootStatus))
                else -> emptyList()
            }
        }

        private fun JSONArray.toDetectionList(rootStatus: String?): List<GazeDetection> {
            val items = mutableListOf<GazeDetection>()
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                items.add(GazeDetection.fromJson(item, rootStatus))
            }
            return items
        }

        private fun inferPayloadDataStatus(json: JSONObject): String? {
            if (json.has("summary_count")) return DATA_STATUS_SUMMARY
            if (json.has("detection_count") || json.has("timestamp_us")) return DATA_STATUS_FRAME

            val detections = json.optJSONArray("detections")
            if (detections != null && detections.length() > 0) {
                val firstUploadStatus = detections.optJSONObject(0)?.optStringOrNull("upload_status")
                return inferDataStatusFromUploadStatus(firstUploadStatus)
            }
            return inferDataStatusFromUploadStatus(json.optStringOrNull("upload_status"))
        }
    }
}

// --- Public Snapshot DTO ----------------------------------------------------

/** Business-facing snapshot extracted from one gaze payload. */
data class GazeInfoSnapshot(
    val trackId: String? = null,
    val face: FaceInfo? = null,
    val body: BodyInfo? = null,
    val boundingBox: BoundingBox? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val dataStatus: String? = null,
    val uploadStatus: String? = null,
    val timestampUs: Long? = null,
    val detectionCount: Int? = null,
    val summaryCount: Int? = null,
    val frameCount: Int? = null,
    val stayTimeMs: Long? = null,
    val firstSeenMs: Long? = null,
    val lastSeenMs: Long? = null,
    val gazeRatio: Double? = null,
    val behavior: BehaviorInfo? = null
) {
    companion object {
        fun fromJson(json: JSONObject): GazeInfoSnapshot {
            return fromPayload(GazePayload.fromJson(json))
        }

        fun fromPayload(payload: GazePayload): GazeInfoSnapshot {
            return fromPayloadDetection(payload, payload.detections.firstOrNull())
        }

        fun fromPayloadDetections(payload: GazePayload): List<GazeInfoSnapshot> {
            if (payload.detections.isEmpty()) {
                return listOf(fromPayloadDetection(payload, null))
            }
            return payload.detections.map { detection ->
                fromPayloadDetection(payload, detection)
            }
        }

        private fun fromPayloadDetection(
            payload: GazePayload,
            detection: GazeDetection?
        ): GazeInfoSnapshot {
            return GazeInfoSnapshot(
                trackId = payload.trackId?.toString() ?: detection?.trackId?.toString(),
                face = detection?.face,
                body = detection?.body,
                timestamp = System.currentTimeMillis(),
                dataStatus = payload.dataStatus,
                uploadStatus = detection?.uploadStatus,
                timestampUs = payload.timestampUs,
                detectionCount = payload.detectionCount ?: payload.detections.size.takeIf { it > 0 },
                summaryCount = payload.summaryCount,
                frameCount = detection?.frameCount,
                stayTimeMs = detection?.stayTimeMs,
                firstSeenMs = detection?.firstSeenMs,
                lastSeenMs = detection?.lastSeenMs,
                gazeRatio = detection?.gazeRatio,
                behavior = detection?.behavior
            )
        }
    }
}
