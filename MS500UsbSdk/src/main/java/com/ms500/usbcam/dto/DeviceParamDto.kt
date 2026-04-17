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
 * 设备信息
 */
data class DeviceParamDto(
    /** 固件版本 */
    val firmwareVersion: String,
    /** 模型版本 */
    val modelVersion: String,
    /** Sensor ID */
    val sensorId: String? = null,
    /** 原始数据（调试用） */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * 从 JSON 解析
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

data class RegisterValueDto(
    val addr: Int,
    val size: Int,
    val status: String? = null,
    val value: Int? = null,
    val hex: String? = null,
    val valueHex: String? = null,
    val raw: JSONObject? = null
) {
    companion object {
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
