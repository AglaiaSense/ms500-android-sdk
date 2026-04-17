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
 * 推理模式 / Inference mode
 */
enum class InferenceMode {
    /** 实时模式 / Real-time mode */
    REALTIME,
    /** 批量模式 / Batch mode */
    BATCH;

    companion object {
        /**
         * 从字符串解析推理模式 / Parse inference mode from string
         *
         * @param value 模式字符串
         * @return [InferenceMode]
         */
        fun fromString(value: String): InferenceMode {
            return when (value.lowercase()) {
                "realtime" -> REALTIME
                "batch" -> BATCH
                else -> REALTIME
            }
        }
    }
}

/**
 * 推理状态 / Inference state
 */
data class InferenceState(
    /** 推理是否启用 / Whether inference is enabled */
    val enabled: Boolean = false,
    /** 原始 JSON 数据 / Raw JSON data */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * 从 JSON 解析推理状态 / Parse inference state from JSON
         *
         * @param json JSON 对象
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
 * 结果发送模式配置 / Result send mode configuration
 */
data class ResultSendModeConfig(
    /** 推理模式 / Inference mode */
    val mode: InferenceMode = InferenceMode.REALTIME,
    /** 批量帧数 / Batch frame count */
    val batchFrameCount: Int? = null,
    /** 原始 JSON 数据 / Raw JSON data */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * 从 JSON 解析发送模式配置 / Parse send mode config from JSON
         *
         * @param json JSON 对象
         * @return [ResultSendModeConfig]
         */
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

    /**
     * 转换为设置用 JSON / Convert to setting JSON
     */
    fun toSetJson(): JSONObject {
        return JSONObject().apply {
            put("mode", mode.name.lowercase())
            batchFrameCount?.let { put("batch_frame_count", it) }
        }
    }
}

/**
 * 推理配置 DTO / Inference configuration DTO
 */
data class InferenceConfigDto(
    /** 推理是否启用 / Whether inference is enabled */
    val enabled: Boolean = false,
    /** 推理模式 / Inference mode */
    val mode: InferenceMode = InferenceMode.REALTIME,
    /** 批量帧数 / Batch frame count */
    val batchFrameCount: Int? = null,
    /** 置信度阈值 / Confidence threshold */
    val confThreshold: Double = 0.32,
    /** 人脸阈值 / Face threshold */
    val faceThreshold: Double = 0.315,
    /** 凝视阈值 / Gaze threshold */
    val gazeThreshold: Double = 0.5,
    /** NMS 阈值 / NMS threshold */
    val nmsThreshold: Double = 0.3,
    /** AI 分数阈值 / AI score threshold */
    val aiScoreThreshold: Double? = null,
    /** 原始 JSON 数据 / Raw JSON data */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * 从 JSON 解析推理配置 / Parse inference config from JSON
         *
         * @param json JSON 对象
         * @return [InferenceConfigDto]
         */
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

    /**
     * 转换为设置用 JSON / Convert to setting JSON
     */
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

/**
 * ROI 配置 / Region of Interest configuration
 */
data class RoiConfig(
    /** ROI 点列表 / List of ROI points */
    val points: List<RoiPoint> = emptyList(),
    /** 是否启用 / Whether enabled */
    val enabled: Boolean = points.isNotEmpty()
) {
    /**
     * ROI 点 / ROI point
     */
    data class RoiPoint(
        /** X 坐标（0~1 归一化） / X coordinate (normalized 0~1) */
        val x: Double,
        /** Y 坐标（0~1 归一化） / Y coordinate (normalized 0~1) */
        val y: Double
    ) {
        /**
         * 转换为 Map / Convert to map
         */
        fun toMap(): Map<String, Double> = mapOf("x" to x, "y" to y)

        companion object {
            /**
             * 从 Map 解析 ROI 点 / Parse ROI point from map
             *
             * @param map 坐标 Map
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
         * 全屏 ROI / Full-screen ROI
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
         * 四边形 ROI / Quadrilateral ROI
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
         * 三角形 ROI / Triangle ROI
         */
        fun triangle(): RoiConfig {
            return RoiConfig(listOf(
                RoiPoint(0.50, 0.18),
                RoiPoint(0.80, 0.78),
                RoiPoint(0.20, 0.78)
            ))
        }

        /**
         * 八边形 ROI / Octagon ROI
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
         * 自定义 ROI（列表） / Custom ROI from list
         *
         * @param points ROI 点列表
         */
        fun custom(points: List<RoiPoint>): RoiConfig {
            return RoiConfig(points = points)
        }

        /**
         * 自定义 ROI（可变参数） / Custom ROI from varargs
         *
         * @param points ROI 点可变参数
         */
        fun custom(vararg points: RoiPoint): RoiConfig {
            return custom(points.toList())
        }

        /**
         * 从 JSON 解析 ROI 配置 / Parse ROI config from JSON
         *
         * @param json JSON 对象
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
         * 从点列表解析 ROI 配置 / Parse ROI config from point list
         *
         * @param points Map 列表
         * @return [RoiConfig]
         */
        fun fromPoints(points: List<Map<String, Double>>?): RoiConfig {
            if (points.isNullOrEmpty()) return RoiConfig()
            return RoiConfig(points.map { RoiPoint.fromMap(it) })
        }
    }

    /**
     * 转换为设置用 JSON / Convert to setting JSON
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
