/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.usbcam.api.internal

import com.ms500.usbcam.result.SdkError
import com.ms500.usbcam.result.SdkResult

/**
 * Local parameter validation, applied before a command is built.
 *
 * Scope is deliberately narrow. The SDK does **not** decide what the device supports - commands go
 * out and the device's own answer is reported back, because the SDK's idea of device capability
 * goes stale as firmware evolves, and a wrong guess produces a failure the caller cannot explain.
 *
 * What is checked here is only what would otherwise produce a malformed request:
 *
 * - **Non-finite floats.** `org.json` throws on `NaN` and `Infinity`, so an unchecked value
 *   surfaces as a `JSONException` from inside the SDK rather than a typed failure.
 * - **Range of the chosen facade.** `sdk.gaze` validates against GAZE bounds, `sdk.wta` against WTA
 *   bounds - the facade the caller picked, never the model the device happens to be running. So
 *   `max_body_detection = 20` passes through `sdk.gaze` (1..100) and fails through `sdk.wta`
 *   (1..10), on the same device, by design.
 *
 * Range failures are returned as [SdkError.InvalidArgument] with the field name and bounds, so the
 * message reads the same whether it came from here or from the device.
 */
internal object ParamValidation {

    /**
     * Checks a double against an inclusive range.
     *
     * @return null when acceptable, otherwise a failure carrying the field name and bounds.
     */
    fun <T> checkRange(
        name: String,
        value: Double,
        min: Double,
        max: Double
    ): SdkResult<T>? {
        if (!value.isFinite()) {
            return SdkResult.failure(
                SdkError.InvalidArgument("$name must be a finite number, got $value")
            )
        }
        if (value < min || value > max) {
            return SdkResult.failure(
                SdkError.InvalidArgument("$name=$value out of range [$min, $max]")
            )
        }
        return null
    }

    /**
     * Checks an int against an inclusive range.
     *
     * @return null when acceptable, otherwise a failure carrying the field name and bounds.
     */
    fun <T> checkRange(
        name: String,
        value: Int,
        min: Int,
        max: Int
    ): SdkResult<T>? {
        if (value < min || value > max) {
            return SdkResult.failure(
                SdkError.InvalidArgument("$name=$value out of range [$min, $max]")
            )
        }
        return null
    }

    /**
     * Wraps a pre-built failure reason into [SdkError.InvalidArgument].
     *
     * Used by DTOs that validate themselves as a whole, such as the region parameters, where the
     * reason string is assembled with the offending index already in it.
     */
    fun <T> invalid(reason: String): SdkResult<T> =
        SdkResult.failure(SdkError.InvalidArgument(reason))
}

/**
 * WTA parameter bounds, taken from the WTA firmware's `gaze_param_config.h` macros and
 * `wta_param.c` field table.
 *
 * These are the **wire** bounds the firmware enforces, which are not always the postprocess
 * library's internal clamps - `max_person` for instance clamps to 0..10 internally while the
 * command layer only accepts 1..10. The wire bound is what belongs here.
 *
 * Cross-checked against `docs/sync/cdc_protocol/GAZE_WTA_FIELD_MATRIX.md`; both must be updated
 * together when firmware changes.
 */
internal object WtaBounds {
    /** Score thresholds and IoU thresholds share the 0..1 range. */
    const val RATIO_MIN = 0.0
    const val RATIO_MAX = 1.0

    /** `max_body_detection` -> `max_person`. GAZE allows 1..100. */
    const val MAX_BODY_DETECTION_MIN = 1
    const val MAX_BODY_DETECTION_MAX = 10

    /** `max_age`. */
    const val MAX_AGE_MIN = 0
    const val MAX_AGE_MAX = 20

    /** `min_hits` -> `min_hit`. */
    const val MIN_HITS_MIN = 0
    const val MIN_HITS_MAX = 10

    /** `trajectory_db_lifetime`, in seconds. */
    const val TRAJECTORY_LIFETIME_MIN = 0
    const val TRAJECTORY_LIFETIME_MAX = 3600

    /** Age distribution mean/std fields. */
    const val DISTRIBUTION_MIN = 0.0
    const val DISTRIBUTION_MAX = 100.0

    /** `m_bias` / `s_bias`. GAZE allows -1000..1000. */
    const val BIAS_MIN = -15.0
    const val BIAS_MAX = 15.0

    /** Watching and age-validity angles. GAZE allows -90..90. */
    const val ANGLE_MIN = -90
    const val ANGLE_MAX = 80

    /** `person_width_thresh`, in model pixels. */
    const val PERSON_WIDTH_MIN = 0
    const val PERSON_WIDTH_MAX = 200

    /** `head_width_thresh` / `face_width_thresh`, in model pixels. */
    const val HEAD_FACE_WIDTH_MIN = 0
    const val HEAD_FACE_WIDTH_MAX = 100

    /** `max_dist`, in model pixels. */
    const val MAX_DIST_MIN = 0
    const val MAX_DIST_MAX = 300

    /** `head_min_area` / `face_min_area`, in model pixels squared. */
    const val MIN_AREA_MIN = 0
    const val MIN_AREA_MAX = 40000
}
