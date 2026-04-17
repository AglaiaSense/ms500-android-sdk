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
 * 通用结果 DTO / Generic result DTO
 */
data class ResultDto(
    /** 命令字段 / Command field */
    val cmd: String? = null,
    /** 状态（如 ok / error） / Status (e.g. ok / error) */
    val status: String? = null,
    /** 消息或错误描述 / Message or error description */
    val msg: String? = null
) {
    companion object {
        /**
         * 从 JSON 解析通用结果 / Parse generic result from JSON
         *
         * @param json JSON 对象
         * @return [ResultDto]
         */
        fun fromJson(json: JSONObject): ResultDto {
            return ResultDto(
                cmd = json.optString("cmd").ifBlank { null },
                status = json.optString("status").ifBlank { null },
                msg = json.optString("msg")
                    .ifBlank { json.optString("message") }
                    .ifBlank { null }
            )
        }
    }
}
