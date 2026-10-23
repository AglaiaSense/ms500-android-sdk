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
 * Device information DTO
 */
data class DeviceParamDto(
    /** Firmware version */
    val firmwareVersion: String,
    /** Model version */
    val modelVersion: String,
    /** Sensor ID / Sensor identifier */
    val sensorId: String? = null,
    /** Camera serial number */
    val cSn: String? = null,
    /** USB serial number */
    val uSn: String? = null,
    /** Device uptime in microseconds; resets to zero whenever the device restarts. */
    val uptimeUs: Long? = null,
    /**
     * Model this firmware is built for: `"wta"` or `"gaze"`.
     *
     * Added by firmware protocol 4.4. Older GAZE firmware (2.x) does not report this field, so a
     * null value does not mean "unknown model" - use [resolvedModelType], which falls back to the
     * firmware version prefix.
     */
    val modelType: String? = null,
    /**
     * Model actually loaded in the sensor, derived by the device from the model package input
     * tensor size (416x320 -> `"wta"`, 512x384 -> `"gaze"`, anything else -> `"unknown"`).
     *
     * Added by firmware protocol 4.4.
     */
    val loadedModel: String? = null,
    /**
     * Whether [loadedModel] matches [modelType].
     *
     * When this is `false` and the sensor holds the other model, the device runs no postprocessing
     * and reports no detection results; the matching model package must be flashed. Added by
     * firmware protocol 4.4.
     */
    val modelMatch: Boolean? = null,
    /**
     * Encrypted DRF token returned by get_device_info.result.token.
     *
     * Firmware returns Base64(IV||ciphertext||tag). When NVS has no device_token,
     * the protocol returns an empty string and the SDK exposes null.
     */
    val token: String? = null,
    /**
     * Full parsed CDC response object for debugging.
     *
     * Device fields are parsed from `get_device_info.result`. This field keeps
     * the complete response envelope for protocol inspection. Use
     * `SdkResult.raw` for the original response string.
     */
    val raw: JSONObject? = null
) {

    /**
     * Model to use when choosing between `sdk.gaze` and `sdk.wta`, resolved from the fields the
     * device actually reported.
     *
     * Resolution order, matching the firmware release notes:
     * 1. [modelType] when present - firmware 4.4 and later report it directly.
     * 2. Otherwise the firmware version prefix: `usb_gaze_3.` is WTA, `usb_gaze_2.` is GAZE, and
     *    `usb_wta_` covers WTA test builds from before 3.0.0. Both models share the `usb_gaze_`
     *    prefix because already-shipped firmware only accepts upgrades with that prefix, so the
     *    major segment - not the prefix - is what distinguishes them.
     * 3. [MODEL_UNKNOWN] when neither applies. Callers must not treat unknown as GAZE.
     *
     * Only firmware without [modelType] reaches step 2, and such firmware can only be 2.x, so
     * "missing field implies GAZE" is a consequence of the versioning rule rather than a fallback
     * guess.
     */
    val resolvedModelType: String
        get() = when {
            !modelType.isNullOrBlank() -> modelType
            else -> modelTypeFromFirmwareVersion(firmwareVersion)
        }

    /**
     * Whether the device is expected to report detection results.
     *
     * `false` only when the device explicitly reported [modelMatch] as `false`, which means the
     * sensor holds the other model and the device skips postprocessing entirely. Firmware that
     * does not report the field at all is assumed to be consistent, because mismatch detection was
     * introduced together with the field.
     */
    val reportsDetections: Boolean
        get() = modelMatch != false

    companion object {
        /** [resolvedModelType] value for the WTA model. */
        const val MODEL_WTA = "wta"

        /** [resolvedModelType] value for the original GAZE model. */
        const val MODEL_GAZE = "gaze"

        /** [resolvedModelType] value when the model cannot be determined. */
        const val MODEL_UNKNOWN = "unknown"

        /**
         * Resolves which model a firmware version string belongs to.
         *
         * **This is the only reliable discriminator.** Both models ship under the `usb_gaze`
         * prefix (so already-shipped devices, which only accept that prefix, can still upgrade),
         * and the major version separates them: `usb_gaze_2.x` = GAZE, `usb_gaze_3.x` = WTA.
         *
         * The **model package name does not work** for this. Both official combined packages carry
         * a `sony_gaze` MODL header name even though `usb_gaze_combined_v3.0.4.bin` contains the
         * WTA model (its payload is byte-identical to `sony_wta.zip`). Anything deciding GAZE vs
         * WTA from a package must go through this function with the firmware version.
         *
         * Returns [MODEL_UNKNOWN] for anything unrecognised - never defaults to [MODEL_GAZE].
         *
         * @param firmwareVersion Firmware version string, e.g. `usb_gaze_3.0.4_20261021_154039`.
         *   Test builds (`usb_gaze_3.0.4t2_...`) match the same prefixes.
         */
        fun modelTypeFromFirmwareVersion(firmwareVersion: String?): String {
            val version = firmwareVersion?.trim().orEmpty()
            return when {
                version.startsWith(FIRMWARE_PREFIX_WTA) -> MODEL_WTA
                version.startsWith(FIRMWARE_PREFIX_GAZE) -> MODEL_GAZE
                version.startsWith(FIRMWARE_PREFIX_WTA_TEST) -> MODEL_WTA
                else -> MODEL_UNKNOWN
            }
        }

        /** Firmware version prefix used by WTA builds (3.x). */
        private const val FIRMWARE_PREFIX_WTA = "usb_gaze_3."

        /** Firmware version prefix used by original GAZE builds (2.x). */
        private const val FIRMWARE_PREFIX_GAZE = "usb_gaze_2."

        /** Firmware version prefix used by WTA test builds before 3.0.0. */
        private const val FIRMWARE_PREFIX_WTA_TEST = "usb_wta_"

        /**
         * Parse device info from JSON
         *
         * @param json JSON object
         * @return [DeviceParamDto]
         */
        fun fromJson(json: JSONObject): DeviceParamDto {
            return DeviceParamDto(
                firmwareVersion = json.optString("firmware_version", "unknown"),
                modelVersion = json.optString("model_version", "unknown"),
                sensorId = json.optString("sensor_id", "").takeIf { it.isNotEmpty() },
                cSn = json.optString("c_sn", "").takeIf { it.isNotEmpty() },
                uSn = json.optString("u_sn", "").takeIf { it.isNotEmpty() },
                uptimeUs = json.optLong("uptime_us").takeIf {
                    json.has("uptime_us") && !json.isNull("uptime_us")
                },
                modelType = json.optString("model_type", "").takeIf { it.isNotEmpty() },
                loadedModel = json.optString("loaded_model", "").takeIf { it.isNotEmpty() },
                modelMatch = json.optBoolean("model_match").takeIf {
                    json.has("model_match") && !json.isNull("model_match")
                },
                token = json.optString("token", "").takeIf { it.isNotEmpty() },
                raw = json
            )
        }
    }
}

/**
 * Register value DTO
 */
data class RegisterValueDto(
    /** Register address */
    val addr: Int,
    /** Data size in bytes */
    val size: Int,
    /** Operation status */
    val status: String? = null,
    /** Numeric value */
    val value: Int? = null,
    /** Hexadecimal string */
    val hex: String? = null,
    /** Hex string from value_hex key */
    val valueHex: String? = null,
    /**
     * Full parsed CDC response object for debugging.
     *
     * Register fields are parsed from `register_get.result`. This field keeps
     * the complete response envelope for protocol inspection. Use
     * `SdkResult.raw` for the original response string.
     */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * Parse register value from JSON
         *
         * @param json JSON object
         * @param addr Default register address
         * @param size Default data size
         * @return [RegisterValueDto]
         */
        fun fromJson(json: JSONObject, addr: Int, size: Int): RegisterValueDto {
            return RegisterValueDto(
                addr = json.optInt("addr", addr),
                size = json.optInt("size", size),
                status = json.optString("status").ifBlank { null },
                value = json.optInt("value", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
                hex = json.optString("hex").ifBlank { null },
                valueHex = json.optString("value_hex").ifBlank { null },
                raw = json
            )
        }
    }
}
