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
 * 人脸属性 / Face attributes
 */
data class FaceAttribute(
    /** 性别 / Gender */
    val gender: String? = null,
    /** 年龄 / Age */
    val age: Int? = null,
    /** 性别置信度 / Gender confidence score */
    val genderScore: Double? = null
) {
    companion object {
        /**
         * 从 JSON 解析人脸属性 / Parse face attributes from JSON
         *
         * @param json JSON 对象
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
 * 人脸姿态 / Face pose (yaw, pitch, roll)
 */
data class FacePose(
    /** 偏航角 / Yaw angle */
    val yaw: Double = 0.0,
    /** 俯仰角 / Pitch angle */
    val pitch: Double = 0.0,
    /** 翻滚角 / Roll angle */
    val roll: Double = 0.0
) {
    companion object {
        /**
         * 从 JSON 解析人脸姿态 / Parse face pose from JSON
         *
         * @param json JSON 对象
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
 * 凝视信息 DTO / Gaze information DTO
 */
data class GazeInfoDto(
    /** 水平方向凝视状态 / Horizontal gaze direction */
    val horizontal: String? = null,
    /** 垂直方向凝视状态 / Vertical gaze direction */
    val vertical: String? = null,
    /** 水平凝视置信度 / Horizontal gaze confidence */
    val horizontalScore: Double? = null,
    /** 垂直凝视置信度 / Vertical gaze confidence */
    val verticalScore: Double? = null
) {
    companion object {
        /**
         * 从 JSON 解析凝视信息 / Parse gaze info from JSON
         *
         * @param json JSON 对象
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
 * 边界框 / Bounding box
 */
data class BoundingBox(
    /** 左边界 / Left coordinate */
    val left: Int = 0,
    /** 上边界 / Top coordinate */
    val top: Int = 0,
    /** 右边界 / Right coordinate */
    val right: Int = 0,
    /** 下边界 / Bottom coordinate */
    val bottom: Int = 0
) {
    companion object {
        /**
         * 从 JSON 解析边界框 / Parse bounding box from JSON
         *
         * @param json JSON 对象
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
 * 人脸信息 / Face detection info
 */
data class FaceInfo(
    /** 人脸边界框 / Face bounding box */
    val boundingBox: BoundingBox? = null,
    /** 人脸检测置信度 / Face detection score */
    val boundingBoxScore: Double = 0.0,
    /** 人脸属性 / Face attributes */
    val attribute: FaceAttribute? = null,
    /** 人脸姿态 / Face pose */
    val pose: FacePose? = null,
    /** 凝视信息 / Gaze info */
    val gaze: GazeInfoDto? = null,
    /** 是否背对摄像头 / Whether facing away from camera */
    val isBackToCamera: Boolean = false
) {
    companion object {
        /**
         * 从 JSON 解析人脸信息 / Parse face info from JSON
         *
         * @param json JSON 对象
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
 * 人体信息 / Body detection info
 */
data class BodyInfo(
    /** 人体边界框 / Body bounding box */
    val boundingBox: BoundingBox? = null,
    /** 人体检测置信度 / Body detection score */
    val boundingBoxScore: Double = 0.0
) {
    companion object {
        /**
         * 从 JSON 解析人体信息 / Parse body info from JSON
         *
         * @param json JSON 对象
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
 * 行为信息 / Behavior info
 */
data class BehaviorInfo(
    /** 是否在凝视 / Whether gazing */
    val isGaze: Boolean = false,
    /** 凝视次数 / Gaze count */
    val gazeCount: Int? = null
) {
    companion object {
        /**
         * 从 JSON 解析行为信息 / Parse behavior info from JSON
         *
         * @param json JSON 对象
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
 * 凝视检测结果 / Gaze detection result
 */
data class GazeDetection(
    /** 跟踪 ID / Track ID */
    val trackId: Int = 0,
    /** 人体信息 / Body info */
    val body: BodyInfo? = null,
    /** 人脸信息 / Face info */
    val face: FaceInfo? = null,
    /** 行为信息 / Behavior info */
    val behavior: BehaviorInfo? = null,
    /** 数据状态 / Data status */
    val dataStatus: String? = null,
    /** 原始 JSON 数据 / Raw JSON data */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * 从 JSON 解析凝视检测结果 / Parse gaze detection from JSON
         *
         * @param json       JSON 对象
         * @param rootStatus 根级状态（兜底）
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
 * 凝视数据载荷 / Gaze payload
 */
data class GazePayload(
    /** 数据状态 / Data status */
    val dataStatus: String? = null,
    /** 跟踪 ID / Track ID */
    val trackId: Int? = null,
    /** 时间戳（微秒） / Timestamp in microseconds */
    val timestampUs: Long? = null,
    /** 检测数量 / Detection count */
    val detectionCount: Int? = null,
    /** 汇总数量 / Summary count */
    val summaryCount: Int? = null,
    /** 检测列表 / Detection list */
    val detections: List<GazeDetection> = emptyList(),
    /** 原始 JSON 数据 / Raw JSON data */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * 从 JSON 解析凝视载荷 / Parse gaze payload from JSON
         *
         * @param json JSON 对象
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
         * 从 JSON 数组解析检测列表 / Parse detection list from JSON array
         *
         * @param jsonArray  JSON 数组
         * @param rootStatus 根级状态
         * @return [GazeDetection] 列表
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
 * 推理结果 / Inference result
 */
data class InferenceResult(
    /** 跟踪 ID / Track ID */
    val trackId: String? = null,
    /** 人脸信息 / Face info */
    val face: FaceInfo? = null,
    /** 人体信息 / Body info */
    val body: BodyInfo? = null,
    /** 边界框 / Bounding box */
    val boundingBox: BoundingBox? = null,
    /** 时间戳（毫秒） / Timestamp in milliseconds */
    val timestamp: Long = System.currentTimeMillis(),
    /** 原始 JSON 数据 / Raw JSON data */
    val raw: JSONObject? = null
) {
    /** 是否包含人脸 / Whether face is present */
    val hasFace: Boolean get() = face != null
    /** 是否包含人体 / Whether body is present */
    val hasBody: Boolean get() = body != null

    /**
     * 生成用于显示的文本 / Generate display text
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
         * 从 JSON 解析推理结果 / Parse inference result from JSON
         *
         * @param json JSON 对象
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
         * 从 JSON 数组解析推理结果列表 / Parse inference result list from JSON array
         *
         * @param jsonArray JSON 数组
         * @return [InferenceResult] 列表
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
