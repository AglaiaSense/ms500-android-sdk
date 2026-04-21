package com.ms500.usbcam.api.internal

import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.result.SdkError
import com.ms500.usbcam.result.SdkResult
import org.json.JSONObject

/**
 * Utility for parsing CDC responses into SdkResult.
 * Handles device errors, empty responses, and status fields uniformly.
 */

/**
 * Parses a standard CDC response into a ResultDto.
 */
internal fun parseCdcResultDto(
    json: JSONObject?,
    error: String?
): SdkResult<ResultDto> = parseCdcResult(json, error, ResultDto::fromJson)

/**
 * Generic CDC result parser.
 *
 * @param json JSON object returned by device, may be null.
 * @param error Error string, may be null.
 * @param transform Lambda to convert successful JSON to target type.
 */
internal fun <T> parseCdcResult(
    json: JSONObject?,
    error: String?,
    transform: (JSONObject) -> T
): SdkResult<T> {
    return when {
        error != null -> SdkResult.failure(SdkError.DeviceRejected(null, error))
        json == null -> SdkResult.failure(SdkError.InvalidResponse(reason = "Empty response"))
        json.optString("status") == "error" -> {
            val code = json.optInt("code", -1)
            val message = json.optString("message").ifBlank { json.optString("msg", "Unknown error") }
            SdkResult.failure(SdkError.DeviceRejected(code, message))
        }
        else -> SdkResult.success(transform(json))
    }
}
