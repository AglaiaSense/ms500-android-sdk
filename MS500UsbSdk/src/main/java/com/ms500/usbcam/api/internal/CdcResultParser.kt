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
import org.json.JSONObject

/**
 * Generic CDC result parser.
 *
 * This is the boundary between the CDC protocol layer and the typed SDK API layer.
 * The lower layer has already decoded the CDC frame and parsed the response text
 * into [json], but it has not converted it into a DTO yet. This function keeps
 * both forms:
 * - [raw] is preserved as the original response text on [SdkResult.raw], so UI
 *   logs and automation can show exactly what the device returned.
 * - [json] is the parsed full response object, for example
 *   `{"cmd":"get_inference_state","result":{"inference_enabled":true}}`.
 * - `payload` is the business object passed to [transform]. For `result`
 *   responses this is `json.result` plus copied `cmd/meta`; for `status`
 *   responses without `result` this is the full status object.
 *
 * Supported response shapes:
 * - Control/status response: `{"cmd":"set_xxx","status":"ok","msg":"..."}`
 * - Query response: `{"cmd":"get_xxx","result":{...},"meta":{...}}`
 * - Query response with status: `{"cmd":"get_xxx","status":"ok","result":{...}}`
 *
 * DTO parsing should use only the business payload. After [transform] returns,
 * [attachFullRaw] replaces DTO `raw: JSONObject?` with the full response object
 * so callers can inspect `cmd/status/result/meta` when debugging.
 *
 * @param raw Original CDC response text from [CdcSender]. This is the preferred
 * log/debug value and is copied to [SdkResult.Success.raw] or
 * [SdkResult.Failure.raw]. It may be null for synthetic failures such as
 * connection errors or empty responses.
 * @param json Parsed full response object returned by the device. It is still a
 * [JSONObject], not a DTO. DTO conversion happens only inside [transform].
 * @param error Lower-layer transport or device error. When this is non-null,
 * parsing is skipped and a failure is returned with [raw] preserved.
 * @param transform Converts the normalized business payload into the target DTO.
 * For example, `get_inference_state` receives
 * `{"inference_enabled":true,"cmd":"get_inference_state"}` and returns
 * `InferenceState`.
 */
internal fun <T> parseCdcResult(
    raw: String?,
    json: JSONObject?,
    error: String?,
    transform: (JSONObject) -> T
): SdkResult<T> {
    if (error != null) {
        return SdkResult.failure(SdkError.DeviceRejected(errorMessage = error), raw = raw)
    }
    if (json == null) {
        return SdkResult.failure(SdkError.InvalidResponse(raw = raw, reason = "Empty response"), raw = raw)
    }

    val fullResponse = JSONObject(json.toString())

    // 1. status response: validate command/control status first.
    val status = json.optString("status").trim()
    if (status.isNotEmpty()) {
        if (!status.equals("ok", ignoreCase = true)) {
            val msg = json.optString("msg").ifBlank { "Device returned status=$status" }
            return SdkResult.failure(SdkError.DeviceRejected(errorMessage = msg), raw = raw ?: json.toString())
        }

        if (!json.has("result")) {
            return transformPayload(json, fullResponse, raw, transform)
        }
    }

    // 2. result response: query payload, optionally after status=ok validation.
    if (json.has("result")) {
        val result = json.optJSONObject("result")
            ?: return SdkResult.failure(
                SdkError.InvalidResponse(
                    raw = raw ?: json.toString(),
                    reason = "Missing or invalid result object"
                ),
                raw = raw ?: json.toString()
            )
        val payload = JSONObject(result.toString()).apply {
            json.optString("cmd").takeIf { it.isNotBlank() }?.let { put("cmd", it) }
            json.optJSONObject("meta")?.let { meta -> put("meta", meta) }
        }
        return transformPayload(payload, fullResponse, raw, transform)
    }

    // 3. Unknown response type.
    return SdkResult.failure(
        SdkError.InvalidResponse(
            raw = raw ?: json.toString(),
            reason = "Unknown CDC response format"
        ),
        raw = raw ?: json.toString()
    )
}

/**
 * Backward-compatible parser overload for code paths that only have parsed JSON.
 *
 * Prefer the overload with `raw` when calling from CDC command callbacks. This
 * overload falls back to `json.toString()`, which is valid JSON but may not be
 * byte-for-byte identical to the original response text.
 */
internal fun <T> parseCdcResult(
    json: JSONObject?,
    error: String?,
    transform: (JSONObject) -> T
): SdkResult<T> {
    return parseCdcResult(json?.toString(), json, error, transform)
}

/**
 * Runs DTO conversion and attaches both raw forms:
 * - [raw] goes to [SdkResult.raw] as the original response text.
 * - [fullResponse] goes to DTO `raw: JSONObject?` when the DTO exposes that field.
 */
private fun <T> transformPayload(
    payload: JSONObject,
    fullResponse: JSONObject,
    raw: String?,
    transform: (JSONObject) -> T
): SdkResult<T> {
    return runCatching {
        val data = attachFullRaw(transform(payload), fullResponse)
        SdkResult.success(data, raw = raw ?: fullResponse.toString())
    }.getOrElse { throwable ->
        SdkResult.failure(
            SdkError.InvalidResponse(
                raw = raw ?: fullResponse.toString(),
                reason = throwable.message ?: "Failed to parse CDC response"
            ),
            raw = raw ?: fullResponse.toString()
        )
    }
}

/**
 * Replaces a Kotlin data-class DTO's final `raw: JSONObject?` property with the
 * full CDC response object.
 *
 * DTO `fromJson(...)` methods intentionally parse only the business payload.
 * This helper avoids forcing every DTO parser to know about the outer CDC
 * envelope while still giving SDK developers access to the full raw JSON during
 * debugging. Objects without a `raw: JSONObject?` property are returned unchanged.
 */
@Suppress("UNCHECKED_CAST")
private fun <T> attachFullRaw(data: T, fullResponse: JSONObject): T {
    if (data == null || data is JSONObject) return data

    return runCatching {
        val type = data.javaClass
        val rawGetter = type.methods.firstOrNull { method ->
            method.name == "getRaw" && method.parameterCount == 0
        } ?: return data
        if (rawGetter.returnType != JSONObject::class.java) return data

        val copyMethod = type.methods.firstOrNull { method ->
            method.name == "copy" &&
                method.parameterTypes.isNotEmpty() &&
                method.parameterTypes.last() == JSONObject::class.java
        } ?: return data

        val args = Array<Any?>(copyMethod.parameterCount) { index ->
            type.getMethod("component${index + 1}").invoke(data)
        }
        args[args.lastIndex] = fullResponse
        copyMethod.invoke(data, *args) as T
    }.getOrElse {
        data
    }
}
