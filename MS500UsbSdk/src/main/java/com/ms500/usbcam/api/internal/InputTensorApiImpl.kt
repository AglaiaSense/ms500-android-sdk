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

import com.ms500.usbcam.api.DeviceParamApi
import com.ms500.usbcam.api.InputTensorApi
import com.ms500.usbcam.api.InputTensorImageFormat
import com.ms500.usbcam.cdc.InputTensorCaptureResult
import com.ms500.usbcam.cdc.InputTensorSession
import com.ms500.usbcam.result.SdkError
import com.ms500.usbcam.result.SdkResult
import com.ms500.usbcam.result.toSdkError
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * Internal implementation of input tensor image download APIs.
 */
internal class InputTensorApiImpl(
    /** CDC connection implementation, used to create InputTensorSession. */
    private val connection: CdcConnectApiImpl,
    /** Device parameter API, used to send the underlying CDC command. */
    private val deviceParam: DeviceParamApi
) : InputTensorApi {

    companion object {
        private const val CMD_DOWNLOAD_INPUT_TENSOR = "download_input_tensor"
        private const val COMMAND_ACK_TIMEOUT_MS = 5_000L
    }

    override suspend fun capture(
        timeoutMs: Long,
        format: InputTensorImageFormat,
        onStatus: (String) -> Unit,
        onProgress: (Int, Int) -> Unit
    ): SdkResult<InputTensorCaptureResult> {
        if (!connection.isConnected) {
            return SdkResult.failure(SdkError.NotConnected)
        }

        val session = InputTensorSession(
            connection = connection,
            onStatus = onStatus,
            onProgress = onProgress
        )
        session.start()

        return try {
            onStatus("Requesting input tensor (${format.displayName} format)")
            session.markRequestStarted()

            when (val ack = executeDownloadCommand(format)) {
                is SdkResult.Failure -> {
                    session.fail("$CMD_DOWNLOAD_INPUT_TENSOR failed: ${ack.error.getMessage()}")
                    return SdkResult.failure(ack.error, ack.raw)
                }
                is SdkResult.Success -> {
                    val message = ack.data.optString("msg", "Input tensor capture started")
                    onStatus("Device response: $message")
                }
            }

            onStatus("Receiving ${format.displayName} data")
            SdkResult.success(session.awaitCapture(timeoutMs))
        } catch (t: Throwable) {
            session.fail(t.message ?: t.javaClass.simpleName)
            SdkResult.failure(t.toSdkError())
        } finally {
            session.cleanup()
        }
    }

    /** Sends {"cmd":"download_input_tensor","params":{"format":"jpg|png"}}; quality is fixed by firmware. */
    private suspend fun executeDownloadCommand(format: InputTensorImageFormat): SdkResult<JSONObject> {
        val payload = JSONObject().apply {
            put("params", JSONObject().apply { put("format", format.requestValue) })
        }
        return try {
            withTimeout(COMMAND_ACK_TIMEOUT_MS) {
                deviceParam.baseCommand(CMD_DOWNLOAD_INPUT_TENSOR, payload)
            }
        } catch (_: TimeoutCancellationException) {
            SdkResult.failure(SdkError.Timeout(CMD_DOWNLOAD_INPUT_TENSOR))
        }
    }
}
