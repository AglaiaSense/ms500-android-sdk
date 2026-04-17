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

enum class InferenceMode {
    REALTIME,
    BATCH;

    companion object {
        fun fromString(value: String): InferenceMode {
            return when (value.lowercase()) {
                "realtime" -> REALTIME
                "batch" -> BATCH
                else -> REALTIME
            }
        }
    }
}

data class InferenceState(
    val enabled: Boolean = false,
    val raw: JSONObject? = null
) {
    companion object {
        fun fromJson(json: JSONObject): InferenceState {
            return InferenceState(
                enabled = json.optBoolean("inference_enabled", false),
                raw = json
            )
        }
    }
}

data class ResultSendModeConfig(
    val mode: InferenceMode = InferenceMode.REALTIME,
    val batchFrameCount: Int? = null,
    val raw: JSONObject? = null
) {
    companion object {
        fun fromJson(json: JSONObject): ResultSendModeConfig {
            val modeStr = json.optString("mode", "")
            val mode = if (modeStr.isNotBlank()) {
                InferenceMode.fromString(modeStr)
            } else {
                when (json.optInt("mode_code", 0)) {
                    1 -> InferenceMode.BATCH
                    else -> InferenceMode.REALTIME
                }
            }
            return ResultSendModeConfig(
                mode = mode,
                batchFrameCount = json.optInt("batch_frame_count", -1).takeIf { it > 0 },
                raw = json
            )
        }
    }

    fun toSetJson(): JSONObject {
        return JSONObject().apply {
            put("mode", mode.name.lowercase())
            batchFrameCount?.let { put("batch_frame_count", it) }
        }
    }
}

data class InferenceConfigDto(
    val enabled: Boolean = false,
    val mode: InferenceMode = InferenceMode.REALTIME,
    val batchFrameCount: Int? = null,
    val confThreshold: Double = 0.32,
    val faceThreshold: Double = 0.315,
    val gazeThreshold: Double = 0.5,
    val nmsThreshold: Double = 0.3,
    val aiScoreThreshold: Double? = null,
    val raw: JSONObject? = null
) {
    companion object {
        fun fromJson(json: JSONObject): InferenceConfigDto {
            val postprocess = json.optJSONObject("postprocess_param")
            val modeStr = json.optString("mode", "")
            val mode = if (modeStr.isNotEmpty()) {
                InferenceMode.fromString(modeStr)
            } else {
                when (json.optInt("mode_code", 0)) {
                    0 -> InferenceMode.REALTIME
                    1 -> InferenceMode.BATCH
                    else -> InferenceMode.REALTIME
                }
            }

            return InferenceConfigDto(
                enabled = json.optBoolean("inference_enabled", false),
                mode = mode,
                batchFrameCount = json.optInt("batch_frame_count", -1).takeIf { it > 0 },
                confThreshold = postprocess?.optDouble("conf_thresh", 0.32)
                    ?: json.optDouble("conf_thresh", 0.32),
                faceThreshold = postprocess?.optDouble("face_thresh", 0.315)
                    ?: json.optDouble("face_thresh", 0.315),
                gazeThreshold = postprocess?.optDouble("gaze_thresh", 0.5)
                    ?: json.optDouble("gaze_thresh", 0.5),
                nmsThreshold = postprocess?.optDouble("nms_thresh", 0.3)
                    ?: json.optDouble("nms_thresh", 0.3),
                aiScoreThreshold = json.optDouble("ai_score_threshold", -1.0).takeIf { it >= 0 },
                raw = json
            )
        }
    }

    fun toSetJson(): JSONObject {
        return JSONObject().apply {
            put("inference_enabled", enabled)
            put("mode", mode.name.lowercase())
            batchFrameCount?.let { put("batch_frame_count", it) }
            put("conf_thresh", confThreshold)
            put("face_thresh", faceThreshold)
            put("gaze_thresh", gazeThreshold)
            put("nms_thresh", nmsThreshold)
            aiScoreThreshold?.let { put("ai_score_threshold", it) }
        }
    }
}

data class RoiConfig(
    val points: List<RoiPoint> = emptyList(),
    val enabled: Boolean = points.isNotEmpty()
) {
    data class RoiPoint(
        val x: Double,
        val y: Double
    ) {
        fun toMap(): Map<String, Double> = mapOf("x" to x, "y" to y)

        companion object {
            fun fromMap(map: Map<String, Double>): RoiPoint {
                return RoiPoint(
                    x = map["x"] ?: 0.0,
                    y = map["y"] ?: 0.0
                )
            }
        }
    }

    companion object {
        fun fullScreen(): RoiConfig {
            return RoiConfig(listOf(
                RoiPoint(0.0, 0.0),
                RoiPoint(1.0, 0.0),
                RoiPoint(1.0, 1.0),
                RoiPoint(0.0, 1.0)
            ))
        }

        fun quadrilateral(): RoiConfig {
            return RoiConfig(listOf(
                RoiPoint(0.25, 0.25),
                RoiPoint(0.75, 0.25),
                RoiPoint(0.75, 0.75),
                RoiPoint(0.25, 0.75)
            ))
        }

        fun triangle(): RoiConfig {
            return RoiConfig(listOf(
                RoiPoint(0.50, 0.18),
                RoiPoint(0.80, 0.78),
                RoiPoint(0.20, 0.78)
            ))
        }

        fun octagon(): RoiConfig {
            return RoiConfig(listOf(
                RoiPoint(0.35, 0.12),
                RoiPoint(0.65, 0.12),
                RoiPoint(0.88, 0.35),
                RoiPoint(0.88, 0.65),
                RoiPoint(0.65, 0.88),
                RoiPoint(0.35, 0.88),
                RoiPoint(0.12, 0.65),
                RoiPoint(0.12, 0.35)
            ))
        }

        fun custom(points: List<RoiPoint>): RoiConfig {
            return RoiConfig(points = points)
        }

        fun custom(vararg points: RoiPoint): RoiConfig {
            return custom(points.toList())
        }

        fun fromJson(json: JSONObject): RoiConfig {
            val pointsArray = json.optJSONArray("roi_points")
            val points = mutableListOf<RoiPoint>()

            if (pointsArray != null) {
                for (i in 0 until pointsArray.length()) {
                    val pointObj = pointsArray.getJSONObject(i)
                    points.add(RoiPoint(
                        x = pointObj.optDouble("x", 0.0),
                        y = pointObj.optDouble("y", 0.0)
                    ))
                }
            }

            return RoiConfig(
                points = points,
                enabled = json.optInt("roi_point_count", 0) > 0 || points.isNotEmpty()
            )
        }

        fun fromPoints(points: List<Map<String, Double>>?): RoiConfig {
            if (points.isNullOrEmpty()) return RoiConfig()
            return RoiConfig(points.map { RoiPoint.fromMap(it) })
        }
    }

    fun toSetJson(): JSONObject {
        return JSONObject().apply {
            put("roi_points", org.json.JSONArray().apply {
                points.forEach { point ->
                    put(JSONObject().apply {
                        put("x", point.x)
                        put("y", point.y)
                    })
                }
            })
        }
    }
}

