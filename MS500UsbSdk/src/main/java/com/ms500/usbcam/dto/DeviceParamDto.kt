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
 * 设备信息 DTO / Device information DTO
 */
data class DeviceParamDto(
    /** 固件版本 / Firmware version */
    val firmwareVersion: String,
    /** 模型版本 / Model version */
    val modelVersion: String,
    /** Sensor ID / Sensor identifier */
    val sensorId: String? = null,
    /** 原始 JSON 数据（调试用） / Raw JSON data (for debugging) */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * 从 JSON 解析设备信息 / Parse device info from JSON
         *
         * @param json JSON 对象
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
 * 寄存器值 DTO / Register value DTO
 */
data class RegisterValueDto(
    /** 寄存器地址 / Register address */
    val addr: Int,
    /** 数据大小（字节） / Data size in bytes */
    val size: Int,
    /** 操作状态 / Operation status */
    val status: String? = null,
    /** 数值 / Numeric value */
    val value: Int? = null,
    /** 十六进制字符串 / Hexadecimal string */
    val hex: String? = null,
    /** 带 value_hex 键的十六进制字符串 / Hex string from value_hex key */
    val valueHex: String? = null,
    /** 原始 JSON 数据 / Raw JSON data */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * 从 JSON 解析寄存器值 / Parse register value from JSON
         *
         * @param json JSON 对象
         * @param addr 默认寄存器地址
         * @param size 默认数据大小
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
