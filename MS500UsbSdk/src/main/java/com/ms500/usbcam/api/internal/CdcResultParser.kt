package com.ms500.usbcam.api.internal

import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.result.SdkError
import com.ms500.usbcam.result.SdkResult
import org.json.JSONObject

/**
 * CDC 结果解析工具。
 *
 * 将底层返回的 JSON / error 字符串统一转换为 `SdkResult`，
 * 处理设备报错、空响应、状态字段等通用逻辑。
 *
 * Utility for parsing CDC responses into SdkResult.
 * Handles device errors, empty responses, and status fields uniformly.
 */

/**
 * 解析标准 CDC 结果（返回 ResultDto）。
 * Parses a standard CDC response into a ResultDto.
 */
internal fun parseCdcResultDto(
    json: JSONObject?,
    error: String?
): SdkResult<ResultDto> = parseCdcResult(json, error, ResultDto::fromJson)

/**
 * 泛型 CDC 结果解析器。
 * Generic CDC result parser.
 *
 * @param json 设备返回的 JSON 对象，可能为 null。
 * @param error 错误信息字符串，可能为 null。
 * @param transform 将成功 JSON 转换为目标类型的 lambda。
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
