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

import com.ms500.usbcam.api.PqConfigParamApi
import com.ms500.usbcam.cdc.CdcSender
import com.ms500.usbcam.dto.PqConfigAutoExposure
import com.ms500.usbcam.dto.PqConfigAutoExposureMetering
import com.ms500.usbcam.dto.PqConfigAutoWhiteBalance
import com.ms500.usbcam.dto.PqConfigCameraImageFlip
import com.ms500.usbcam.dto.PqConfigCameraImageSize
import com.ms500.usbcam.dto.PqConfigImageCropping
import com.ms500.usbcam.dto.PqConfigManualExposure
import com.ms500.usbcam.dto.PqConfigManualWhiteBalancePreset
import com.ms500.usbcam.dto.PqcongifParam
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.result.SdkError
import com.ms500.usbcam.result.SdkResult
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Internal implementation of PQ configuration parameter APIs.
 * Translates PQ settings into CDC protocol field-level commands.
 */
internal class PqConfigParamApiImpl(
    /** CDC connection implementation, used to obtain shared CdcSender. */
    private val connection: CdcConnectApiImpl
) : PqConfigParamApi {

    /** Retrieves the current PQ configuration parameters. */
    override suspend fun getPqFixConfig(): SdkResult<PqcongifParam> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("get_pq_settings_param") { raw, json, error ->
                continuation.resume(parseCdcResult(raw, json, error) { PqcongifParam.fromJson(it) })
            }
        }
    }

    /** Resets PQ configuration parameters to device defaults. */
    override suspend fun resetPqFixConfig(): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("reset_pq_settings_param") { raw, json, error ->
                continuation.resume(parseCdcResult(raw, json, error) { ResultDto.fromJson(it) })
            }
        }
    }

    /** Sets supported PQ configuration parameters. Batch setting is the only path that uses [PqcongifParam]. */
    override suspend fun setPqFixConfig(config: PqcongifParam): SdkResult<ResultDto> = execute { cdcSender ->
        config.unsupportedSetField()?.let { field ->
            return@execute SdkResult.failure(SdkError.UnsupportedOperation("$field is read-only"))
        }
        sendPqSettings(cdcSender, config.toSetJson())
    }

    /** Attempts to set camera image size. This field is read-only. */
    override suspend fun setCameraImageSize(value: PqConfigCameraImageSize): SdkResult<ResultDto> =
        SdkResult.failure(SdkError.UnsupportedOperation("camera_image_size is read-only"))

    /** Sets camera image flip as one protocol object. */
    override suspend fun setCameraImageFlip(value: PqConfigCameraImageFlip): SdkResult<ResultDto> = execute { cdcSender ->
        sendPqSettings(cdcSender, JSONObject().apply { put("camera_image_flip", value.toJson()) })
    }

    /** Sets exposure mode value. */
    override suspend fun setExposureMode(value: Int): SdkResult<ResultDto> = execute { cdcSender ->
        sendPqSettings(cdcSender, JSONObject().apply { put("exposure_mode", value) })
    }

    /** Sets auto exposure as one protocol object. */
    override suspend fun setAutoExposure(value: PqConfigAutoExposure): SdkResult<ResultDto> = execute { cdcSender ->
        sendPqSettings(cdcSender, JSONObject().apply { put("auto_exposure", value.toJson()) })
    }

    /** Sets auto exposure metering as one protocol object. */
    override suspend fun setAutoExposureMetering(value: PqConfigAutoExposureMetering): SdkResult<ResultDto> = execute { cdcSender ->
        sendPqSettings(cdcSender, JSONObject().apply { put("auto_exposure_metering", value.toJson()) })
    }

    /** Sets manual exposure as one protocol object. */
    override suspend fun setManualExposure(value: PqConfigManualExposure): SdkResult<ResultDto> = execute { cdcSender ->
        sendPqSettings(cdcSender, JSONObject().apply { put("manual_exposure", value.toJson()) })
    }

    /** Sets white balance mode. */
    override suspend fun setWhiteBalanceMode(value: Int): SdkResult<ResultDto> = execute { cdcSender ->
        sendPqSettings(cdcSender, JSONObject().apply { put("white_balance_mode", value) })
    }

    /** Sets auto white balance as one protocol object. */
    override suspend fun setAutoWhiteBalance(value: PqConfigAutoWhiteBalance): SdkResult<ResultDto> = execute { cdcSender ->
        sendPqSettings(cdcSender, JSONObject().apply { put("auto_white_balance", value.toJson()) })
    }

    /** Sets manual white balance preset as one protocol object. */
    override suspend fun setManualWhiteBalancePreset(value: PqConfigManualWhiteBalancePreset): SdkResult<ResultDto> = execute { cdcSender ->
        sendPqSettings(cdcSender, JSONObject().apply { put("manual_white_balance_preset", value.toJson()) })
    }

    /** Sets EV compensation only. */
    override suspend fun setEvCompensation(value: Int): SdkResult<ResultDto> = execute { cdcSender ->
        sendPqSettings(cdcSender, JSONObject().apply { put("ev_compensation", value) })
    }

    /** Sets AE anti-flicker mode only. */
    override suspend fun setAeAntiFlickerMode(value: Int): SdkResult<ResultDto> = execute { cdcSender ->
        sendPqSettings(cdcSender, JSONObject().apply { put("ae_anti_flicker_mode", value) })
    }

    /** Sets image cropping as one protocol object. */
    override suspend fun setImageCropping(value: PqConfigImageCropping): SdkResult<ResultDto> = execute { cdcSender ->
        sendPqSettings(cdcSender, JSONObject().apply { put("image_cropping", value.toJson()) })
    }

    /** Sends the exact protocol fields under params for set_pq_settings_param. */
    private suspend fun sendPqSettings(cdcSender: CdcSender, params: JSONObject): SdkResult<ResultDto> {
        return suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand(
                "set_pq_settings_param",
                JSONObject().apply { put("params", params) }
            ) { raw, json, error ->
                continuation.resume(parseCdcResult(raw, json, error) { ResultDto.fromJson(it) })
            }
        }
    }

    /** Internal execution helper: runs block with a valid sender or returns NotConnected. */
    private inline fun <T> execute(block: (CdcSender) -> SdkResult<T>): SdkResult<T> {
        val cdcSender = connection.getSender() ?: return SdkResult.failure(SdkError.NotConnected)
        return block(cdcSender)
    }
}
