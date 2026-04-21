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
    /** Raw JSON data (for debugging) */
    val raw: JSONObject? = null
) {
    companion object {
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
    /** Raw JSON data */
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
