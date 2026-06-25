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

import com.ms500.usbcam.api.CameraParamApi
import com.ms500.usbcam.cdc.CdcSender
import com.ms500.usbcam.dto.CameraParamDto
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.result.SdkResult
import com.ms500.usbcam.result.SdkError
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Internal implementation of camera parameter APIs.
 * Translates high-level camera operations into CDC commands and parses JSON responses into typed DTOs.
 */
internal class CameraParamApiImpl(
    /** CDC connection implementation, used to obtain shared CdcSender. */
    private val connection: CdcConnectApiImpl
) : CameraParamApi {

    /**
     * Retrieves the current camera parameters.
     */
    override suspend fun getParams(): SdkResult<CameraParamDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("get_camera_param") { raw, json, error ->
                continuation.resume(parseCdcResult(raw, json, error) { CameraParamDto.fromJson(it) })
            }
        }
    }

    /**
     * Sets camera parameters (full or partial).
     */
    override suspend fun setParams(params: CameraParamDto): SdkResult<ResultDto> = execute { cdcSender ->
        val setJson = params.toSetJson()
        if (setJson.length() == 0) {
            return@execute SdkResult.failure(
                SdkError.UnsupportedOperation("Only frame_rate is supported by camera parameters")
            )
        }
        suspendCancellableCoroutine { continuation ->
            val json = JSONObject().apply { put("params", setJson) }
            cdcSender.sendCommand("set_camera_param", json) { raw, response, error ->
                continuation.resume(parseCdcResult(raw, response, error) { ResultDto.fromJson(it) })
            }
        }
    }
    /**
     * Sets frame rate only. Valid range: 5-30 fps.
     */
    override suspend fun setFrameRate(value: Int): SdkResult<ResultDto> = setParams(CameraParamDto(frameRate = value))
    /**
     * Internal execution helper: runs block with a valid sender or returns NotConnected.
     */
    private inline fun <T> execute(block: (CdcSender) -> SdkResult<T>): SdkResult<T> {
        val cdcSender = connection.getSender() ?: return SdkResult.failure(SdkError.NotConnected)
        return block(cdcSender)
    }

}
