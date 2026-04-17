package com.ms500.usbcam.dto

import org.json.JSONObject

data class ResultDto(
    val cmd: String? = null,
    val status: String? = null,
    val msg: String? = null
) {
    companion object {
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
