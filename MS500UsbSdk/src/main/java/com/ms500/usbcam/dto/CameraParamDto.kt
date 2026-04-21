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
 * Camera parameter range
 */
data class CameraParamRange(
    /** Minimum value */
    val min: Int,
    /** Maximum value */
    val max: Int,
    /** Unit */
    val unit: String? = null,
    /** Description */
    val description: String? = null
) {
    companion object {
        /**
         * Parse parameter range from JSON
         *
         * @param json JSON object
         * @param key  Parameter key name
         * @return [CameraParamRange] or null
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
 * Camera parameter DTO
 */
data class CameraParamDto(
    /** Exposure value (0 = auto) */
    val exposure: Int? = null,
    /** Gain value (0 = auto) */
    val gain: Int? = null,
    /** Gamma value */
    val gamma: Int? = null,
    /** White balance mode (0 = off, 1 = on) */
    val whiteBalance: Int? = null,
    /** Frame rate */
    val frameRate: Int? = null,
    /** Horizontal flip (0/1) */
    val hflip: Int? = null,
    /** Vertical flip (0/1) */
    val vflip: Int? = null,
    /** Detection upload enabled */
    val detectionUploadEnabled: Boolean? = null,
    /** Parameter ranges map */
    val ranges: Map<String, CameraParamRange>? = null,
    /** Raw JSON data */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * Parse camera parameters from JSON
         *
         * @param json JSON Object
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
         * Auto exposure configuration
         */
        fun autoExposure(): CameraParamDto {
            return CameraParamDto(exposure = 0, gain = 0)
        }

        /**
         * Manual exposure configuration
         *
         * @param exposure Exposure value
         * @param gain     Gain value
         */
        fun manualExposure(exposure: Int, gain: Int): CameraParamDto {
            return CameraParamDto(exposure = exposure, gain = gain)
        }

        /**
         * Low-light mode configuration
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
     * Merge parameters, non-null values override current ones
     *
     * @param other Another [CameraParamDto]
     * @return Merged [CameraParamDto]
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
