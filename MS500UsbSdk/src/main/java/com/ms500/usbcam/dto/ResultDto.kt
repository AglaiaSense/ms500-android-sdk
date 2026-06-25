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
 * Generic result DTO
 */
data class ResultDto(
    /** Command field */
    val cmd: String? = null,
    /** Status (e.g. ok / error) */
    val status: String? = null,
    /** Message or error description */
    val msg: String? = null,
    /**
     * Full parsed CDC response object for debugging/log display.
     *
     * For status commands this is usually the same object used for parsing, for
     * example `{"cmd":"set_inference_state","status":"ok","msg":"..."}`.
     * Use [SdkResult.raw] when the original response string is required.
     */
    val raw: JSONObject? = null
) {
    companion object {
        /**
         * Parse generic result from JSON
         *
         * @param json JSON Object
         * @return [ResultDto]
         */
        fun fromJson(json: JSONObject): ResultDto {
            return ResultDto(
                cmd = json.optString("cmd").ifBlank { null },
                status = json.optString("status").ifBlank { null },
                msg = json.optString("msg").ifBlank { null },
                raw = json
            )
        }
    }
}
