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
 * Inference mode
 */
enum class InferenceMode(val value: Int) {
    /** Do not actively send inference results. */
    NONE(0),
    /** Send inference results for each frame. */
    REALTIME(1),
    /** Send summary results when a track ends. */
    BATCH(2);

    companion object {
        /** Parse result send mode from protocol value: 0=none, 1=realtime, 2=batch. */
        fun fromValue(value: Int): InferenceMode {
            return when (value) {
                0 -> NONE
                2 -> BATCH
                else -> REALTIME
            }
        }

        /** Parse result send mode from protocol string value. */
        fun fromString(value: String): InferenceMode {
            return value.trim().toIntOrNull()?.let { fromValue(it) } ?: REALTIME
        }
    }
}

/**
 * Inference state
 */
data class InferenceState(
    /** Whether inference is enabled */
    val enabled: Boolean = false,
    /**
     * Full parsed CDC response object for debugging.
     *
     * `enabled` is parsed from the response `result` payload; this field keeps
     * the full object such as `{"cmd":"get_inference_state","result":{...}}`.
     * Use `SdkResult.raw` for the original response string.
     */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * Parse inference state from JSON
         *
         * @param json JSON object
         * @return [InferenceState]
         */
        fun fromJson(json: JSONObject): InferenceState {
            return InferenceState(
                enabled = json.optBoolean("inference_enabled", false),
                raw = json
            )
        }
    }
}

/**
 * Result send mode configuration
 */
data class ResultSendModeConfig(
    /** Inference mode */
    val mode: InferenceMode = InferenceMode.REALTIME,
    /**
     * Full parsed CDC response object for debugging.
     *
     * `mode` is parsed from the response `result` payload; this field keeps the
     * full object such as `{"cmd":"get_inference_send_mode","result":{...}}`.
     * Use `SdkResult.raw` for the original response string.
     */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * Parse send mode config from JSON
         *
         * @param json JSON object
         * @return [ResultSendModeConfig]
         */
        fun fromJson(json: JSONObject): ResultSendModeConfig {
            val mode = when {
                json.has("mode") && json.opt("mode") is Number -> {
                    InferenceMode.fromValue(json.optInt("mode", 1))
                }
                json.optString("mode", "").isNotBlank() -> {
                    InferenceMode.fromString(json.optString("mode"))
                }
                else -> {
                    InferenceMode.fromValue(json.optInt("mode_code", 1))
                }
            }
            return ResultSendModeConfig(
                mode = mode,
                raw = json
            )
        }
    }

    /**
     * Convert to setting JSON
     */
    fun toSetJson(): JSONObject {
        return JSONObject().apply {
            put("mode", mode.value)
        }
    }
}

/**
 * Inference configuration DTO
 */
data class InferenceConfigDto(
    /** Whether inference is enabled */
    val enabled: Boolean = false,
    /** Inference mode */
    val mode: InferenceMode = InferenceMode.REALTIME,
    /** Confidence threshold */
    val confThreshold: Double = 0.32,
    /** Face threshold */
    val faceThreshold: Double = 0.315,
    /** Gaze threshold */
    val gazeThreshold: Double = 0.5,
    /** NMS threshold */
    val nmsThreshold: Double = 0.3,
    /** AI score threshold */
    val aiScoreThreshold: Double? = null,
    /**
     * Full parsed CDC response object for debugging.
     *
     * Typed fields are parsed from the normalized business payload. This field
     * keeps the full response envelope for protocol inspection. Use
     * `SdkResult.raw` for the original response string.
     */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * Parse inference config from JSON
         *
         * @param json JSON object
         * @return [InferenceConfigDto]
         */
        fun fromJson(json: JSONObject): InferenceConfigDto {
            val postprocess = json.optJSONObject("postprocess_param")
            val modeStr = json.optString("mode", "")
            val mode = if (modeStr.isNotEmpty()) {
                InferenceMode.fromString(modeStr)
            } else {
                when (json.optInt("mode_code", 1)) {
                    0 -> InferenceMode.NONE
                    2 -> InferenceMode.BATCH
                    else -> InferenceMode.REALTIME
                }
            }

            return InferenceConfigDto(
                enabled = json.optBoolean("inference_enabled", false),
                mode = mode,
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

    /**
     * Convert to setting JSON
     */
    fun toSetJson(): JSONObject {
        return JSONObject().apply {
            put("inference_enabled", enabled)
            put("mode", mode.name.lowercase())
            put("conf_thresh", confThreshold)
            put("face_thresh", faceThreshold)
            put("gaze_thresh", gazeThreshold)
            put("nms_thresh", nmsThreshold)
            aiScoreThreshold?.let { put("ai_score_threshold", it) }
        }
    }
}

/**
 * Region of Interest configuration
 */
data class RoiConfig(
    /** List of ROI points */
    val points: List<RoiPoint> = emptyList(),
    /** Whether enabled */
    val enabled: Boolean = points.isNotEmpty()
) {
    /**
     * ROI point
     */
    data class RoiPoint(
        /** X coordinate (normalized 0~1) */
        val x: Double,
        /** Y coordinate (normalized 0~1) */
        val y: Double
    ) {
        /**
         * Convert to map
         */
        fun toMap(): Map<String, Double> = mapOf("x" to x, "y" to y)

        companion object {
            /**
             * Parse ROI point from map
             *
             * @param map Coordinate map
             * @return [RoiPoint]
             */
            fun fromMap(map: Map<String, Double>): RoiPoint {
                return RoiPoint(
                    x = map["x"] ?: 0.0,
                    y = map["y"] ?: 0.0
                )
            }
        }
    }

    companion object {
        /**
         * Full-screen ROI
         */
        fun fullScreen(): RoiConfig {
            return RoiConfig(listOf(
                RoiPoint(0.0, 0.0),
                RoiPoint(1.0, 0.0),
                RoiPoint(1.0, 1.0),
                RoiPoint(0.0, 1.0)
            ))
        }

        /**
         * Quadrilateral ROI
         */
        fun quadrilateral(): RoiConfig {
            return RoiConfig(listOf(
                RoiPoint(0.25, 0.25),
                RoiPoint(0.75, 0.25),
                RoiPoint(0.75, 0.75),
                RoiPoint(0.25, 0.75)
            ))
        }

        /**
         * Triangle ROI
         */
        fun triangle(): RoiConfig {
            return RoiConfig(listOf(
                RoiPoint(0.50, 0.18),
                RoiPoint(0.80, 0.78),
                RoiPoint(0.20, 0.78)
            ))
        }

        /**
         * Octagon ROI
         */
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

        /**
         * Custom ROI from list
         *
         * @param points ROI point list
         */
        fun custom(points: List<RoiPoint>): RoiConfig {
            return RoiConfig(points = points)
        }

        /**
         * Custom ROI from varargs
         *
         * @param points ROI point varargs
         */
        fun custom(vararg points: RoiPoint): RoiConfig {
            return custom(points.toList())
        }

        /**
         * Parse ROI config from JSON
         *
         * @param json JSON object
         * @return [RoiConfig]
         */
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

        /**
         * Parse ROI config from point list
         *
         * @param points List of maps
         * @return [RoiConfig]
         */
        fun fromPoints(points: List<Map<String, Double>>?): RoiConfig {
            if (points.isNullOrEmpty()) return RoiConfig()
            return RoiConfig(points.map { RoiPoint.fromMap(it) })
        }
    }

    /**
     * Convert to setting JSON
     */
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
