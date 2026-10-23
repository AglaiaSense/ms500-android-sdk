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

import com.ms500.usbcam.api.WtaRegionParamApi
import com.ms500.usbcam.cdc.CdcSender
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.dto.WtaCountingRegionItemDto
import com.ms500.usbcam.dto.WtaRegionParamDto
import com.ms500.usbcam.dto.WtaWatchingRegionItemDto
import com.ms500.usbcam.result.SdkError
import com.ms500.usbcam.result.SdkResult
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Internal implementation of the WTA region API.
 *
 * Validation lives in [WtaRegionParamDto.validationError] rather than here, because the constraints
 * are structural - region counts, mandatory rectangles, coordinate ordering - and belong with the
 * data they describe. This class only maps validated objects onto CDC commands.
 */
internal class WtaRegionParamApiImpl(
    private val connection: CdcConnectApiImpl
) : WtaRegionParamApi {

    override suspend fun getParams(): SdkResult<WtaRegionParamDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("get_wta_region_param") { raw, json, error ->
                continuation.resume(
                    parseCdcResult(raw, json, error) { WtaRegionParamDto.fromJson(it) }
                )
            }
        }
    }

    override suspend fun setParams(params: WtaRegionParamDto): SdkResult<ResultDto> {
        params.validationError()?.let { return ParamValidation.invalid(it) }
        return send(params.toSetJson())
    }

    override suspend fun setWatchingRegions(
        regions: List<WtaWatchingRegionItemDto>
    ): SdkResult<ResultDto> = setParams(WtaRegionParamDto(watchingRegions = regions))

    override suspend fun setCountingRegions(
        regions: List<WtaCountingRegionItemDto>
    ): SdkResult<ResultDto> = setParams(WtaRegionParamDto(countingRegions = regions))

    override suspend fun resetRegions(): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("reset_wta_region_param") { raw, json, error ->
                continuation.resume(parseCdcResult(raw, json, error) { ResultDto.fromJson(it) })
            }
        }
    }

    private suspend fun send(params: JSONObject): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand(
                "set_wta_region_param",
                JSONObject().apply { put("params", params) }
            ) { raw, response, error ->
                continuation.resume(parseCdcResult(raw, response, error) { ResultDto.fromJson(it) })
            }
        }
    }

    private inline fun <T> execute(block: (CdcSender) -> SdkResult<T>): SdkResult<T> {
        val cdcSender = connection.getSender() ?: return SdkResult.failure(SdkError.NotConnected)
        return block(cdcSender)
    }
}
