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
 * 相机参数范围 / Camera parameter range
 */
data class CameraParamRange(
    /** 最小值 / Minimum value */
    val min: Int,
    /** 最大值 / Maximum value */
    val max: Int,
    /** 单位 / Unit */
    val unit: String? = null,
    /** 描述 / Description */
    val description: String? = null
) {
    companion object {
        /**
         * 从 JSON 解析参数范围 / Parse parameter range from JSON
         *
         * @param json JSON 对象
         * @param key  参数键名
         * @return [CameraParamRange] 或 null
         */
        fun fromJson(json: JSONObject, key: String): CameraParamRange? {
            val rangeObj = json.optJSONObject(key) ?: return null
            return CameraParamRange(
                min = rangeObj.optInt("min", 0),
                max = rangeObj.optInt("max", 0),
                unit = rangeObj.optString("unit").ifBlank { null },
                description = rangeObj.optString("description").ifBlank { null }
            )
        }
    }
}

/**
 * 相机参数 DTO / Camera parameter DTO
 */
data class CameraParamDto(
    /** 曝光值（0 表示自动） / Exposure value (0 = auto) */
    val exposure: Int? = null,
    /** 增益值（0 表示自动） / Gain value (0 = auto) */
    val gain: Int? = null,
    /** 伽马值 / Gamma value */
    val gamma: Int? = null,
    /** 白平衡模式（0 表示关闭，1 表示开启） / White balance mode (0 = off, 1 = on) */
    val whiteBalance: Int? = null,
    /** 帧率 / Frame rate */
    val frameRate: Int? = null,
    /** 水平翻转（0/1） / Horizontal flip (0/1) */
    val hflip: Int? = null,
    /** 垂直翻转（0/1） / Vertical flip (0/1) */
    val vflip: Int? = null,
    /** 检测结果主动上报开关 / Detection upload enabled */
    val detectionUploadEnabled: Boolean? = null,
    /** 各参数的可设范围 / Parameter ranges map */
    val ranges: Map<String, CameraParamRange>? = null,
    /** 原始 JSON 数据 / Raw JSON data */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * 从 JSON 解析相机参数 / Parse camera parameters from JSON
         *
         * @param json JSON 对象
         * @return [CameraParamDto]
         */
        fun fromJson(json: JSONObject): CameraParamDto {
            val rangesObj = json.optJSONObject("ranges")
            val ranges = mutableMapOf<String, CameraParamRange>()

            rangesObj?.let { obj ->
                obj.keys().forEach { key ->
                    CameraParamRange.fromJson(obj, key)?.let { range ->
                        ranges[key] = range
                    }
                }
            }

            return CameraParamDto(
                exposure = json.optInt("exposure", -1).takeIf { it >= 0 },
                gain = json.optInt("gain", -1).takeIf { it >= 0 },
                gamma = json.optInt("gamma", -1).takeIf { it >= 0 },
                whiteBalance = json.optInt("white_balance", -1).takeIf { it >= 0 },
                frameRate = json.optInt("frame_rate", -1).takeIf { it >= 0 },
                hflip = json.optInt("hflip", -1).takeIf { it >= 0 },
                vflip = json.optInt("vflip", -1).takeIf { it >= 0 },
                detectionUploadEnabled = json.takeIf { it.has("detection_upload_enabled") }
                    ?.optBoolean("detection_upload_enabled"),
                ranges = ranges.takeIf { it.isNotEmpty() },
                raw = json
            )
        }

        /**
         * 自动曝光配置 / Auto exposure configuration
         */
        fun autoExposure(): CameraParamDto {
            return CameraParamDto(exposure = 0, gain = 0)
        }

        /**
         * 手动曝光配置 / Manual exposure configuration
         *
         * @param exposure 曝光值
         * @param gain     增益值
         */
        fun manualExposure(exposure: Int, gain: Int): CameraParamDto {
            return CameraParamDto(exposure = exposure, gain = gain)
        }

        /**
         * 低光模式配置 / Low-light mode configuration
         */
        fun lowLight(): CameraParamDto {
            return CameraParamDto(
                exposure = 8000,
                gain = 200,
                gamma = 120
            )
        }
    }

    /**
     * 转换为设置用 JSON（仅包含非 null 参数）
     * Convert to setting JSON (non-null fields only)
     */
    fun toSetJson(): JSONObject {
        return JSONObject().apply {
            exposure?.let { put("exposure", it) }
            gain?.let { put("gain", it) }
            gamma?.let { put("gamma", it) }
            whiteBalance?.let { put("white_balance", it) }
            frameRate?.let { put("frame_rate", it) }
            hflip?.let { put("hflip", it) }
            vflip?.let { put("vflip", it) }
        }
    }

    /**
     * 合并参数，非 null 值覆盖当前值
     * Merge parameters, non-null values override current ones
     *
     * @param other 另一个 [CameraParamDto]
     * @return 合并后的 [CameraParamDto]
     */
    fun merge(other: CameraParamDto): CameraParamDto {
        return CameraParamDto(
            exposure = other.exposure ?: this.exposure,
            gain = other.gain ?: this.gain,
            gamma = other.gamma ?: this.gamma,
            whiteBalance = other.whiteBalance ?: this.whiteBalance,
            frameRate = other.frameRate ?: this.frameRate,
            hflip = other.hflip ?: this.hflip,
            vflip = other.vflip ?: this.vflip,
            detectionUploadEnabled = other.detectionUploadEnabled ?: this.detectionUploadEnabled,
            ranges = other.ranges ?: this.ranges,
            raw = other.raw ?: this.raw
        )
    }
}
