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
 * Camera parameter range.
 */
data class CameraParamRange(
    /** Minimum value. */
    val min: Int,
    /** Maximum value. */
    val max: Int,
    /** Unit, for example `fps`. */
    val unit: String? = null,
    /** Optional description from device metadata. */
    val description: String? = null
) {
    companion object {
        /** Parse one range object from `meta.ranges`. */
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
 * Camera parameters from CDC `camera_param`.
 *
 * Camera parameters only expose `frame_rate`. Exposure, white balance,
 * image cropping, and image flip are managed by PQ settings.
 */
data class CameraParamDto(
    /** Frame rate in FPS. Protocol range is 5-30. */
    val frameRate: Int? = null,
    /** Parameter ranges from response `meta.ranges`. */
    val ranges: Map<String, CameraParamRange>? = null,
    /**
     * Full parsed CDC response object for debugging.
     *
     * `frameRate` and `ranges` are parsed from the normalized payload. This
     * field keeps the complete response envelope for protocol inspection. Use
     * `SdkResult.raw` for the original response string.
     */
    val raw: JSONObject? = null
) {
    companion object {
        const val FIELD_FRAME_RATE = "frame_rate"

        /**
         * Parse camera parameters from either a CDC result payload or a request params object.
         */
        fun fromJson(json: JSONObject): CameraParamDto {
            val rangesObj = json.optJSONObject("meta")?.optJSONObject("ranges")
                ?: json.optJSONObject("ranges")
            val ranges = mutableMapOf<String, CameraParamRange>()

            rangesObj?.let { obj ->
                obj.keys().forEach { key ->
                    CameraParamRange.fromJson(obj, key)?.let { range ->
                        ranges[key] = range
                    }
                }
            }

            return CameraParamDto(
                frameRate = json.optInt(FIELD_FRAME_RATE, -1).takeIf { it >= 0 },
                ranges = ranges.takeIf { it.isNotEmpty() },
                raw = json
            )
        }
    }

    /** Convert to CDC `set_camera_param.params` JSON. */
    fun toSetJson(): JSONObject {
        return JSONObject().apply {
            frameRate?.let { put(FIELD_FRAME_RATE, it) }
        }
    }

    /** Merge parameters, where non-null values in [other] override this object. */
    fun merge(other: CameraParamDto): CameraParamDto {
        return CameraParamDto(
            frameRate = other.frameRate ?: this.frameRate,
            ranges = other.ranges ?: this.ranges,
            raw = other.raw ?: this.raw
        )
    }
}
