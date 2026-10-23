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
 * Rectangle in UVC frame coordinates, serialized as the protocol's `[x1, y1, x2, y2]` array.
 *
 * Coordinates are integer pixels in the **1920x1080 UVC frame**, the same space the detection
 * results use. The device converts them to model coordinates using the current DNN ROI, so moving
 * the crop does not move a region on screen.
 *
 * Edges flush with the frame border (`x1 == 0`, `y1 == 0`, `x2 == FRAME_WIDTH`,
 * `y2 == FRAME_HEIGHT`) extend out to the sensor frame edge. The 16:9 UVC frame hides a band at
 * the top and bottom that the model still sees, and reported boxes get clipped to the UVC edge, so
 * a flush region covers that hidden band too.
 *
 * Parts of a region outside the DNN ROI have no effect, and a region entirely outside it matches
 * nobody.
 */
data class WtaRectDto(
    /** Left edge, 0..1920, must be less than [x2]. */
    val x1: Int,
    /** Top edge, 0..1080, must be less than [y2]. */
    val y1: Int,
    /** Right edge, 0..1920. */
    val x2: Int,
    /** Bottom edge, 0..1080. */
    val y2: Int
) {
    companion object {
        /** UVC frame width the region coordinates are expressed in. */
        const val FRAME_WIDTH = 1920

        /** UVC frame height the region coordinates are expressed in. */
        const val FRAME_HEIGHT = 1080

        /** Parses `[x1, y1, x2, y2]`, returning null when the array is missing or malformed. */
        fun fromJson(array: JSONArray?): WtaRectDto? {
            if (array == null || array.length() != 4) return null
            return WtaRectDto(
                x1 = array.optInt(0),
                y1 = array.optInt(1),
                x2 = array.optInt(2),
                y2 = array.optInt(3)
            )
        }

        /** A region covering the whole UVC frame, the firmware default. */
        fun fullFrame(): WtaRectDto = WtaRectDto(0, 0, FRAME_WIDTH, FRAME_HEIGHT)
    }

    /** Converts to the protocol's `[x1, y1, x2, y2]` array. */
    fun toJson(): JSONArray = JSONArray().apply {
        put(x1)
        put(y1)
        put(x2)
        put(y2)
    }

    /**
     * Returns null when the rectangle satisfies the protocol constraints, or a reason when not.
     *
     * Checked before sending so a malformed rectangle never reaches the device; the firmware would
     * reject the whole command anyway, and failing locally names the offending field.
     */
    fun validationError(): String? = when {
        x1 < 0 || x2 > FRAME_WIDTH -> "rect x out of range [0, $FRAME_WIDTH]: [$x1, $x2]"
        y1 < 0 || y2 > FRAME_HEIGHT -> "rect y out of range [0, $FRAME_HEIGHT]: [$y1, $y2]"
        x1 >= x2 -> "rect requires x1 < x2, got x1=$x1 x2=$x2"
        y1 >= y2 -> "rect requires y1 < y2, got y1=$y1 y2=$y2"
        else -> null
    }
}

/**
 * One watching region.
 *
 * Region 0's angles and [d1]/[d2] share storage with `get/set_postprocess_param`'s
 * `watching_region` object - writing through either command is visible from the other.
 *
 * A target is judged as gazing when its head centre falls inside this region AND the [d1]/[d2],
 * angle, and `head_min_area`/`face_min_area` conditions are all met. Overlapping regions resolve to
 * the lowest index.
 *
 * @property rect Region bounds; required when sending.
 * @property d1 Head-to-face top-left distance over head diagonal, upper bound, 0..1, default 0.25.
 * @property d2 Head-to-face bottom-right distance over head diagonal, upper bound, 0..1, default 0.25.
 * @property minYaw Lower yaw bound in degrees, -90..80, default -50.
 * @property maxYaw Upper yaw bound in degrees, -90..80, default 50.
 * @property minPitch Lower pitch bound in degrees, -90..80, default -50.
 * @property maxPitch Upper pitch bound in degrees, -90..80, default 50.
 */
data class WtaWatchingRegionItemDto(
    val rect: WtaRectDto? = null,
    val d1: Double? = null,
    val d2: Double? = null,
    val minYaw: Int? = null,
    val maxYaw: Int? = null,
    val minPitch: Int? = null,
    val maxPitch: Int? = null
) {
    companion object {
        /** Parses one watching region from protocol JSON. */
        fun fromJson(json: JSONObject): WtaWatchingRegionItemDto {
            fun int(key: String): Int? = if (json.has(key)) json.optInt(key) else null
            fun double(key: String): Double? = if (json.has(key)) json.optDouble(key) else null
            return WtaWatchingRegionItemDto(
                rect = WtaRectDto.fromJson(json.optJSONArray("rect")),
                d1 = double("d1"),
                d2 = double("d2"),
                minYaw = int("min_yaw"),
                maxYaw = int("max_yaw"),
                minPitch = int("min_pitch"),
                maxPitch = int("max_pitch")
            )
        }
    }

    /** Converts to protocol JSON; omitted fields keep their current device value. */
    fun toJson(): JSONObject = JSONObject().apply {
        rect?.let { put("rect", it.toJson()) }
        d1?.let { put("d1", it) }
        d2?.let { put("d2", it) }
        minYaw?.let { put("min_yaw", it) }
        maxYaw?.let { put("max_yaw", it) }
        minPitch?.let { put("min_pitch", it) }
        maxPitch?.let { put("max_pitch", it) }
    }
}

/**
 * One person-counting region.
 *
 * @property rect Region bounds; required when sending.
 * @property time Dwell time in **seconds** (0..3600, default 300) after which a track inside this
 * region is counted as one person. Note the reported `person_counting_time` is in
 * **milliseconds** - the two use different units.
 */
data class WtaCountingRegionItemDto(
    val rect: WtaRectDto? = null,
    val time: Int? = null
) {
    companion object {
        /** Lowest accepted [time] value, in seconds. */
        const val TIME_MIN_S = 0

        /** Highest accepted [time] value, in seconds. */
        const val TIME_MAX_S = 3600

        /** Parses one counting region from protocol JSON. */
        fun fromJson(json: JSONObject): WtaCountingRegionItemDto {
            return WtaCountingRegionItemDto(
                rect = WtaRectDto.fromJson(json.optJSONArray("rect")),
                time = if (json.has("time")) json.optInt("time") else null
            )
        }
    }

    /** Converts to protocol JSON; omitted fields keep their current device value. */
    fun toJson(): JSONObject = JSONObject().apply {
        rect?.let { put("rect", it.toJson()) }
        time?.let { put("time", it) }
    }
}

/**
 * WTA watching and counting regions.
 *
 * Returned by `get_wta_region_param` and accepted by `set_wta_region_param`. When sending, each
 * array that is provided **replaces that region type wholesale** - the array length becomes the
 * region count, which must be 1..3. Either array may be omitted to leave that type untouched, but
 * an empty array is not accepted.
 *
 * Changing regions rebuilds the postprocess library and **clears all current tracking**, so track
 * IDs are reassigned and counting state resets. Callers must not assume continuity across a write.
 *
 * @property frameWidth Frame width the coordinates use; fixed at 1920. Read-only.
 * @property frameHeight Frame height the coordinates use; fixed at 1080. Read-only.
 */
data class WtaRegionParamDto(
    val watchingRegions: List<WtaWatchingRegionItemDto>? = null,
    val countingRegions: List<WtaCountingRegionItemDto>? = null,
    val frameWidth: Int? = null,
    val frameHeight: Int? = null,
    /**
     * Full parsed CDC response object for debugging, including `meta.ranges`.
     */
    val raw: JSONObject? = null
) {
    companion object {
        /** Minimum regions per type. */
        const val REGION_COUNT_MIN = 1

        /** Maximum regions per type. */
        const val REGION_COUNT_MAX = 3

        /** Parses region parameters from `get_wta_region_param.result`. */
        fun fromJson(json: JSONObject): WtaRegionParamDto {
            return WtaRegionParamDto(
                watchingRegions = json.optJSONArray("watching_regions")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        array.optJSONObject(index)?.let { WtaWatchingRegionItemDto.fromJson(it) }
                    }
                },
                countingRegions = json.optJSONArray("counting_regions")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        array.optJSONObject(index)?.let { WtaCountingRegionItemDto.fromJson(it) }
                    }
                },
                frameWidth = if (json.has("frame_width")) json.optInt("frame_width") else null,
                frameHeight = if (json.has("frame_height")) json.optInt("frame_height") else null,
                raw = json
            )
        }
    }

    /**
     * Converts the provided region arrays to `set_wta_region_param.params` JSON.
     *
     * `frame_width` / `frame_height` are read-only and never sent.
     */
    fun toSetJson(): JSONObject = JSONObject().apply {
        watchingRegions?.let { regions ->
            put("watching_regions", JSONArray().apply { regions.forEach { put(it.toJson()) } })
        }
        countingRegions?.let { regions ->
            put("counting_regions", JSONArray().apply { regions.forEach { put(it.toJson()) } })
        }
    }

    /**
     * Returns null when this object can be sent, or the reason it cannot.
     *
     * Validates the whole object before anything goes out, so a bad field never results in a
     * partial write. Checks region counts, mandatory rectangles, rectangle bounds, and the
     * counting dwell time.
     */
    fun validationError(): String? {
        if (watchingRegions == null && countingRegions == null) {
            return "No watching_regions or counting_regions provided"
        }
        watchingRegions?.let { regions ->
            countError("watching_regions", regions.size)?.let { return it }
            regions.forEachIndexed { index, region ->
                val rect = region.rect
                    ?: return "watching_regions[$index].rect must be [x1,y1,x2,y2]"
                rect.validationError()?.let { return "watching_regions[$index].$it" }
                angleError("watching_regions[$index].min_yaw", region.minYaw)?.let { return it }
                angleError("watching_regions[$index].max_yaw", region.maxYaw)?.let { return it }
                angleError("watching_regions[$index].min_pitch", region.minPitch)?.let { return it }
                angleError("watching_regions[$index].max_pitch", region.maxPitch)?.let { return it }
                ratioError("watching_regions[$index].d1", region.d1)?.let { return it }
                ratioError("watching_regions[$index].d2", region.d2)?.let { return it }
            }
        }
        countingRegions?.let { regions ->
            countError("counting_regions", regions.size)?.let { return it }
            regions.forEachIndexed { index, region ->
                val rect = region.rect
                    ?: return "counting_regions[$index].rect must be [x1,y1,x2,y2]"
                rect.validationError()?.let { return "counting_regions[$index].$it" }
                region.time?.let { time ->
                    if (time < WtaCountingRegionItemDto.TIME_MIN_S ||
                        time > WtaCountingRegionItemDto.TIME_MAX_S
                    ) {
                        return "counting_regions[$index].time=$time out of range " +
                            "[${WtaCountingRegionItemDto.TIME_MIN_S}, " +
                            "${WtaCountingRegionItemDto.TIME_MAX_S}]"
                    }
                }
            }
        }
        return null
    }

    private fun countError(key: String, size: Int): String? {
        return if (size < REGION_COUNT_MIN || size > REGION_COUNT_MAX) {
            "$key must be an array of $REGION_COUNT_MIN~$REGION_COUNT_MAX regions, got $size"
        } else {
            null
        }
    }

    private fun angleError(label: String, value: Int?): String? {
        if (value == null) return null
        return if (value < ANGLE_MIN || value > ANGLE_MAX) {
            "$label=$value out of range [$ANGLE_MIN, $ANGLE_MAX]"
        } else {
            null
        }
    }

    private fun ratioError(label: String, value: Double?): String? {
        if (value == null) return null
        return if (!value.isFinite() || value < 0.0 || value > 1.0) {
            "$label=$value out of range [0.0, 1.0]"
        } else {
            null
        }
    }
}

/** Lowest accepted watching-region angle, in degrees. */
private const val ANGLE_MIN = -90

/** Highest accepted watching-region angle, in degrees. WTA stops at 80, unlike GAZE's 90. */
private const val ANGLE_MAX = 80
