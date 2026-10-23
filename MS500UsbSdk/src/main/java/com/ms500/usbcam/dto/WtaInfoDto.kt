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

/*
 * WTA detection result DTOs.
 *
 * The WTA firmware reuses MSG_TYPE_INFERENCE_RESULT (0x1000) and the same JSON envelope as GAZE,
 * so the wire structure overlaps heavily. The semantics do not:
 *
 * - track_id follows the HEAD box, not the body box.
 * - Bounding boxes are in UVC frame coordinates (1920x1080 by default), not the 512x384 model
 *   tensor space GAZE reports.
 * - face.attribute.gender.score carries a reused person-class score, not a gender confidence.
 * - face.is_back2camera is a fixed false placeholder with no detection behind it.
 * - detail_gaze_info.bbox_cords tracks HEAD boxes rather than body boxes.
 *
 * [WtaPayload] therefore mirrors the device JSON faithfully, including the placeholder fields, so
 * protocol-level consumers see exactly what arrived. [WtaInfoSnapshot] is the business view and
 * deliberately omits the misleading values.
 *
 * Protocol sample: frame message (WTA fields marked +).
 * {
 *   "data_status": "frame",
 *   "timestamp_us": 61216239,
 *   "detection_count": 1,
 *   "detections": [
 *     {
 *       "upload_status": "normal",
 *       "track_id": 1,
 *     + "head": {"bounding_box": {"left": 900, "top": 300, "right": 1000, "bottom": 420}, "bounding_box_score": 0.91},
 *       "body": {"bounding_box": {"left": 860, "top": 300, "right": 1060, "bottom": 1000}, "bounding_box_score": 0.80},
 *       "face": {"bounding_box": {"left": 915, "top": 330, "right": 985, "bottom": 410}, "bounding_box_score": 0.77, "attribute": {"gender": {"type": "Male", "score": 0.62}, "age": 30}, "pose": {"yaw": 10, "pitch": 0}, "is_back2camera": false},
 *       "behavior": {"is_gaze": true, "detail_gaze_info": {"stay_time": 4200, "detail_gaze_time": [], "bbox_cords": {"first": null, "last": null, "conf": null, "conf_score": 0}}},
 *     + "enter_time": 57000,
 *     + "exit_time": -1,
 *     + "person_counting_time": 4200,
 *     + "counted": false,
 *     + "watching_region": 0,
 *     + "counting_region": -1
 *     }
 *   ]
 * }
 *
 * Protocol sample: summary message keeps head/enter_time/exit_time/person_counting_time/counted
 * but drops watching_region and counting_region, because a timed-out track has no current frame.
 */

// --- WTA-only basic DTOs ----------------------------------------------------

/**
 * Head detection info, the WTA tracking subject.
 *
 * @property boundingBox Head box in UVC frame coordinates. In summary messages this is the box
 * from the last frame the track was seen in.
 * @property boundingBoxScore Head detection confidence 0..1. In summary messages this is the
 * highest score observed over the whole track.
 */
data class WtaHeadInfo(
    val boundingBox: BoundingBox? = null,
    val boundingBoxScore: Double = 0.0
) {
    companion object {
        /** Parses head info from protocol JSON. */
        fun fromJson(json: JSONObject): WtaHeadInfo {
            return WtaHeadInfo(
                boundingBox = json.optJSONObject("bounding_box")?.let { BoundingBox.fromJson(it) },
                boundingBoxScore = json.optDouble("bounding_box_score", 0.0)
            )
        }
    }
}

// --- Payload DTOs -----------------------------------------------------------

/**
 * One item inside a WTA frame or summary payload.
 *
 * This type mirrors the device JSON, placeholders included. See [WtaInfoSnapshot] for the business
 * view that drops values WTA cannot actually determine.
 *
 * @property trackId Tracker ID. WTA tracks the HEAD box, so these IDs are not comparable with GAZE
 * track IDs, nor across reconnects or postprocess/region changes - changing either rebuilds the
 * postprocess library and reassigns IDs.
 * @property trackId Tracking id of the head. Increases monotonically from boot and is **never
 * reused**: rebuilding the postprocess library (which any parameter or region change triggers)
 * clears all tracks, and the people still in frame get fresh ids that cannot collide with earlier
 * ones. Firmware before 3.0.4 restarted numbering from 1 after a rebuild, so a new person could
 * take a departed person's id - deduplicating headcount by track id was unsafe there.
 * @property head Head box; the WTA tracking subject.
 * @property body Body box in UVC coordinates. May be all-zero with a zero score when WTA could not
 * associate a body with this head; [WtaInfoSnapshot.body] filters those out.
 * @property face Face box in UVC coordinates, same all-zero caveat as [body]. Note that
 * `face.attribute.gender.score` carries a reused person-class score and `face.is_back2camera` is a
 * fixed false placeholder.
 * @property behavior Gaze behavior. `is_gaze` is true only when the head centre falls inside a
 * watching region AND that region's d1/d2, angle and minimum-area conditions are met.
 * `detail_gaze_info.bbox_cords` holds HEAD box history for WTA.
 * @property enterTimeMs First time the head appeared, in milliseconds since device boot.
 * @property exitTimeMs Fixed `-1` in frame messages (still in view); in summary messages the last
 * time the track was actually detected.
 * @property personCountingTimeMs Dwell time accumulated **in the counting region the track is
 * currently in**, in milliseconds; when in no region, the value for the region it was in most
 * recently. Note the configured threshold `counting_regions[].time` is in **seconds**.
 *
 * Firmware 3.0.4 changed this from one global counter to **per-region accumulation**: leaving and
 * returning to the same region resumes that region's count, but moving to a *different* region uses
 * that region's own counter and does **not** carry the previous region's time over. Per-region
 * values are in [countingTimesMs].
 * @property counted Whether this track has passed the threshold of **any** counting region. Once
 * true it never returns to false; which regions counted it is in [countedRegions]. The device
 * reports no headcount total: deduplicate by track ID over `counted == true`, never accumulate
 * per frame.
 * @property countingTimesMs Dwell accumulated in **each** counting region, in milliseconds.
 * **Dense**: index is the counting region index, length equals the number of counting regions in
 * effect for this frame (at most [MAX_COUNTING_REGIONS]). Empty on firmware before 3.0.4.
 * @property countedRegions Indices of the counting regions that have already counted this track,
 * for example `[0, 2]`; empty when none. **Sparse** - unlike [countingTimesMs] this is a list of
 * indices, not a value per region. Append-only. Empty on firmware before 3.0.4.
 * @property watchingRegion Zero-based index into `watching_regions[]` of the region containing the
 * head centre, or `-1` when in none. Frame messages only; null when the device omitted it. A
 * non-negative value does **not** imply `is_gaze == true`.
 * @property countingRegion Zero-based index into `counting_regions[]`, same conventions as
 * [watchingRegion].
 */
data class WtaDetection(
    val trackId: Int = 0,
    val head: WtaHeadInfo? = null,
    val body: BodyInfo? = null,
    val face: FaceInfo? = null,
    val behavior: BehaviorInfo? = null,
    val dataStatus: String? = null,
    val uploadStatus: String? = null,
    val frameCount: Int? = null,
    val stayTimeMs: Long? = null,
    val firstSeenMs: Long? = null,
    val lastSeenMs: Long? = null,
    val gazeRatio: Double? = null,
    val enterTimeMs: Long? = null,
    val exitTimeMs: Long? = null,
    val personCountingTimeMs: Long? = null,
    val counted: Boolean = false,
    val watchingRegion: Int? = null,
    val countingRegion: Int? = null,
    val countingTimesMs: List<Long> = emptyList(),
    val countedRegions: List<Int> = emptyList()
) {

    /**
     * Age reported for this detection, or `null` when it is a sentinel rather than an age.
     *
     * Firmware 3.0.4 distinguishes two non-ages that 3.0.1 both reported as `-1`:
     * [AGE_NO_FACE] and [AGE_UNRECOGNIZED]. Use this when you want "a real age or nothing";
     * read `face.attribute.age` directly when you need to tell the two apart.
     */
    val estimatedAge: Int?
        get() = face?.attribute?.age?.takeIf { it != AGE_NO_FACE && it != AGE_UNRECOGNIZED }

    /** True when a face was found but its angle put the age estimate out of range. */
    val isAgeUnrecognized: Boolean
        get() = face?.attribute?.age == AGE_UNRECOGNIZED

    /**
     * Accumulated dwell in counting region [index], in milliseconds, or `null` when not reported.
     *
     * [countingTimesMs] is dense - its index is the counting region index - while [countedRegions]
     * is sparse. Reading one with the other's convention is the easy mistake here.
     */
    fun countingTimeMsFor(index: Int): Long? = countingTimesMs.getOrNull(index)

    /** True when this track has already been counted in counting region [index]. */
    fun isCountedIn(index: Int): Boolean = countedRegions.contains(index)

    /**
     * Whether this detection is still in view.
     *
     * Frame messages report `exit_time` as `-1` while the track is present.
     */
    val isStillInView: Boolean
        get() = exitTimeMs == null || exitTimeMs < 0

    companion object {
        /** Sentinel reported in `exit_time` while the track is still in view. */
        const val EXIT_TIME_STILL_IN_VIEW = -1L

        /** Sentinel reported in region indices when the head centre is in no region. */
        const val REGION_NONE = -1

        /**
         * `face.attribute.age` when no face was found at all.
         *
         * Firmware also reports this for special values it cannot interpret (for example the
         * distribution-adjust 997), so it means "no age", not strictly "no face".
         */
        const val AGE_NO_FACE = -1

        /**
         * `face.attribute.age` when a face was found but its angle put the estimate out of range.
         *
         * **New in firmware 3.0.4 / protocol 4.5** - 3.0.1 reported `-1` for this case too. A
         * caller that only checks `age < 0` will happily display 999 as an age, which is why
         * [estimatedAge] exists.
         *
         * In `summary` messages this appears when no frame had a valid age but at least one
         * reported 999; otherwise an age-less summary still reports [AGE_NO_FACE].
         */
        const val AGE_UNRECOGNIZED = 999

        /** Maximum number of counting regions the device reports, matching `WTA_ITEM_MAX_REGION`. */
        const val MAX_COUNTING_REGIONS = 3

        /** Parses one WTA detection from protocol JSON. */
        fun fromJson(json: JSONObject, rootStatus: String? = null): WtaDetection {
            val uploadStatus = json.optStringOrNullWta("upload_status")
            return WtaDetection(
                trackId = json.optInt("track_id", 0),
                head = json.optJSONObject("head")?.let { WtaHeadInfo.fromJson(it) },
                body = json.optJSONObject("body")?.let { BodyInfo.fromJson(it) },
                face = json.optJSONObject("face")?.let { FaceInfo.fromJson(it) },
                behavior = json.optJSONObject("behavior")?.let { BehaviorInfo.fromJson(it) },
                dataStatus = json.optStringOrNullWta("data_status") ?: rootStatus,
                uploadStatus = uploadStatus,
                frameCount = json.optIntOrNullWta("frame_count"),
                stayTimeMs = json.optLongOrNullWta("stay_time_ms"),
                firstSeenMs = json.optLongOrNullWta("first_seen_ms"),
                lastSeenMs = json.optLongOrNullWta("last_seen_ms"),
                gazeRatio = json.optDoubleOrNullWta("gaze_ratio"),
                enterTimeMs = json.optLongOrNullWta("enter_time"),
                exitTimeMs = json.optLongOrNullWta("exit_time"),
                personCountingTimeMs = json.optLongOrNullWta("person_counting_time"),
                counted = json.optBoolean("counted", false),
                watchingRegion = json.optIntOrNullWta("watching_region"),
                countingRegion = json.optIntOrNullWta("counting_region"),
                countingTimesMs = json.optLongArrayWta("counting_times"),
                countedRegions = json.optIntArrayWta("counted_regions")
            )
        }
    }
}

/**
 * Root WTA payload carried by MSG_TYPE_INFERENCE_RESULT.
 *
 * Mirrors one device JSON message. `data_status` separates per-frame reports from track-timeout
 * summaries; the two carry different field sets and must not be merged.
 *
 * @property detections Parsed detections. An empty list is meaningful: it is an empty frame, which
 * the device only emits when `inference_result_filter_enable` is false, and it means any previously
 * drawn boxes should be cleared.
 */
data class WtaPayload(
    val dataStatus: String? = null,
    val timestampUs: Long? = null,
    val detectionCount: Int? = null,
    val summaryCount: Int? = null,
    val detections: List<WtaDetection> = emptyList()
) {

    /** Whether this payload is a per-frame report. */
    val isFrame: Boolean
        get() = dataStatus == DATA_STATUS_FRAME

    /** Whether this payload is a track-timeout summary. */
    val isSummary: Boolean
        get() = dataStatus == DATA_STATUS_SUMMARY

    companion object {
        /** `data_status` value for per-frame reports. */
        const val DATA_STATUS_FRAME = "frame"

        /** `data_status` value for track-timeout summaries. */
        const val DATA_STATUS_SUMMARY = "summary"

        /** `upload_status` value on per-frame detections. */
        const val UPLOAD_STATUS_NORMAL = "normal"

        /** `upload_status` value on track-timeout detections. */
        const val UPLOAD_STATUS_TRACK_ID_TIMEOUT = "track_id_timeout"

        /** Parses a WTA payload from protocol JSON. */
        fun fromJson(json: JSONObject): WtaPayload {
            val dataStatus = json.optStringOrNullWta("data_status") ?: inferDataStatus(json)
            return WtaPayload(
                dataStatus = dataStatus,
                timestampUs = json.optLongOrNullWta("timestamp_us"),
                detectionCount = json.optIntOrNullWta("detection_count"),
                summaryCount = json.optIntOrNullWta("summary_count"),
                detections = parseDetections(json, dataStatus)
            )
        }

        private fun parseDetections(json: JSONObject, rootStatus: String?): List<WtaDetection> {
            val array = json.optJSONArray("detections") ?: return emptyList()
            val items = mutableListOf<WtaDetection>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                items.add(WtaDetection.fromJson(item, rootStatus))
            }
            return items
        }

        /** Falls back to the per-message counters when `data_status` is absent. */
        private fun inferDataStatus(json: JSONObject): String? {
            if (json.has("summary_count")) return DATA_STATUS_SUMMARY
            if (json.has("detection_count") || json.has("timestamp_us")) return DATA_STATUS_FRAME

            val first = json.optJSONArray("detections")?.optJSONObject(0) ?: return null
            return when (first.optStringOrNullWta("upload_status")) {
                UPLOAD_STATUS_NORMAL -> DATA_STATUS_FRAME
                UPLOAD_STATUS_TRACK_ID_TIMEOUT -> DATA_STATUS_SUMMARY
                else -> null
            }
        }
    }
}

// --- Public snapshot DTO ----------------------------------------------------

/**
 * Business-facing snapshot of one WTA detection.
 *
 * Unlike [WtaDetection] this view drops what WTA cannot actually determine, so screens and
 * analytics never present placeholder values as findings:
 *
 * - `is_back2camera` is absent, because WTA always reports a fixed false.
 * - Gender confidence is absent, because that wire slot carries a person-class score.
 * - [body] and [face] are null when the device sent all-zero placeholder boxes.
 *
 * A head-only target - head box present, body and face unavailable - is a valid detection and is
 * kept. Use [WtaInfoApi.payloads] when the raw placeholders are needed.
 *
 * @property boundingBoxSpace Coordinate space of every box in this snapshot. WTA reports UVC frame
 * coordinates, so overlays must map from the actual UVC frame size and must NOT apply the GAZE
 * 512x384 model-tensor transform.
 */
data class WtaInfoSnapshot(
    val trackId: Int = 0,
    val head: WtaHeadInfo? = null,
    val body: BodyInfo? = null,
    val face: FaceInfo? = null,
    val age: Int? = null,
    val gender: String? = null,
    val yaw: Double? = null,
    val pitch: Double? = null,
    val isGaze: Boolean = false,
    val dataStatus: String? = null,
    val uploadStatus: String? = null,
    val timestampUs: Long? = null,
    val frameCount: Int? = null,
    val stayTimeMs: Long? = null,
    val firstSeenMs: Long? = null,
    val lastSeenMs: Long? = null,
    val gazeRatio: Double? = null,
    val enterTimeMs: Long? = null,
    val exitTimeMs: Long? = null,
    val personCountingTimeMs: Long? = null,
    val counted: Boolean = false,
    val watchingRegion: Int? = null,
    val countingRegion: Int? = null,
    val countingTimesMs: List<Long> = emptyList(),
    val countedRegions: List<Int> = emptyList(),
    val boundingBoxSpace: String = BOX_SPACE_UVC,
    val receivedAtMs: Long = System.currentTimeMillis()
) {
    companion object {
        /** [boundingBoxSpace] value: boxes are in UVC frame coordinates. */
        const val BOX_SPACE_UVC = "uvc"

        /**
         * Builds one snapshot per detection in [payload].
         *
         * An empty payload yields an empty list rather than a placeholder snapshot: callers clear
         * their overlays on an empty frame, and a synthetic empty snapshot would be drawn as a
         * detection that never existed.
         */
        fun fromPayload(payload: WtaPayload): List<WtaInfoSnapshot> {
            return payload.detections.map { detection -> fromDetection(payload, detection) }
        }

        private fun fromDetection(payload: WtaPayload, detection: WtaDetection): WtaInfoSnapshot {
            return WtaInfoSnapshot(
                trackId = detection.trackId,
                head = detection.head,
                body = detection.body?.takeIf { it.isUsable() },
                face = detection.face?.takeIf { it.isUsable() },
                // Snapshot exposes a real age or nothing. Firmware 3.0.4 reports -1 for "no face"
                // and 999 for "face found but angle out of range"; both are sentinels, and 999 in
                // particular survives any `age < 0` check. Callers needing the distinction read
                // WtaDetection.isAgeUnrecognized on the payload.
                age = detection.estimatedAge,
                gender = detection.face?.attribute?.gender,
                yaw = detection.face?.pose?.yaw,
                pitch = detection.face?.pose?.pitch,
                isGaze = detection.behavior?.isGaze == true,
                dataStatus = payload.dataStatus,
                uploadStatus = detection.uploadStatus,
                timestampUs = payload.timestampUs,
                frameCount = detection.frameCount,
                stayTimeMs = detection.stayTimeMs,
                firstSeenMs = detection.firstSeenMs,
                lastSeenMs = detection.lastSeenMs,
                gazeRatio = detection.gazeRatio,
                enterTimeMs = detection.enterTimeMs,
                exitTimeMs = detection.exitTimeMs,
                personCountingTimeMs = detection.personCountingTimeMs,
                countingTimesMs = detection.countingTimesMs,
                countedRegions = detection.countedRegions,
                counted = detection.counted,
                watchingRegion = detection.watchingRegion,
                countingRegion = detection.countingRegion
            )
        }
    }
}

// --- Internal helpers -------------------------------------------------------

/*
 * These mirror the private helpers in GazeInfoDto.kt. They are duplicated rather than shared
 * because making them internal/public would widen the SDK surface for no caller benefit, and
 * because the two files are expected to evolve independently as the two protocols diverge.
 */

/** Whether a body/face box carries real data rather than an all-zero WTA placeholder. */
private fun BodyInfo.isUsable(): Boolean = boundingBox?.isNonEmpty() == true || boundingBoxScore > 0.0

/** Whether a face box carries real data rather than an all-zero WTA placeholder. */
private fun FaceInfo.isUsable(): Boolean = boundingBox?.isNonEmpty() == true || boundingBoxScore > 0.0

/** Whether a box has a positive area. */
private fun BoundingBox.isNonEmpty(): Boolean = right > left && bottom > top

private fun JSONObject.optStringOrNullWta(key: String): String? {
    return if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
}

private fun JSONObject.optIntOrNullWta(key: String): Int? {
    return if (has(key) && !isNull(key)) optInt(key) else null
}

private fun JSONObject.optLongOrNullWta(key: String): Long? {
    return if (has(key) && !isNull(key)) optLong(key) else null
}

/**
 * Reads a JSON array of milliseconds, returning an empty list when the key is missing.
 *
 * Missing means "this firmware does not report it" (anything before 3.0.4), which is a normal
 * state, not an error - callers get an empty list and the surrounding logic is unchanged.
 */
private fun JSONObject.optLongArrayWta(key: String): List<Long> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).map { array.optLong(it) }
}

/** Reads a JSON array of region indices; see [optLongArrayWta] for the missing-key contract. */
private fun JSONObject.optIntArrayWta(key: String): List<Int> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).map { array.optInt(it) }
}

private fun JSONObject.optDoubleOrNullWta(key: String): Double? {
    return if (has(key) && !isNull(key)) optDouble(key) else null
}
