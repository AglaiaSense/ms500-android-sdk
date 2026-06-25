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

import com.ms500.usbcam.api.InferenceParamApi
import com.ms500.usbcam.cdc.CdcSender
import com.ms500.usbcam.dto.InferenceMode
import com.ms500.usbcam.dto.InferenceState
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.dto.ResultSendModeConfig
import com.ms500.usbcam.result.SdkResult
import com.ms500.usbcam.result.SdkError
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Internal implementation of inference parameter APIs.
 * Translates inference state, ROI, inference send mode operations into CDC commands.
 */
internal class InferenceParamApiImpl(
    /** CDC connection implementation, used to obtain shared CdcSender. */
    private val connection: CdcConnectApiImpl
) : InferenceParamApi {

    /**
     * Retrieves the current inference state.
     */
    override suspend fun getState(): SdkResult<InferenceState> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("get_inference_state") { raw, json, error ->
                continuation.resume(parseCdcResult(raw, json, error) { InferenceState.fromJson(it) })
            }
        }
    }

    /**
     * Sets the inference enabled state.
     */
    override suspend fun setState(enabled: Boolean): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand(
                "set_inference_state",
                JSONObject().apply {
                    put("params", JSONObject().apply { put("inference_enabled", enabled) })
                }
            ) { raw, json, error ->
                continuation.resume(parseCdcResult(raw, json, error) { ResultDto.fromJson(it) })
            }
        }
    }

    /**
     * Starts inference.
     */
    override suspend fun setInferenceEnabled(enabled: Boolean): SdkResult<ResultDto> = setState(enabled)

    override suspend fun startInference(): SdkResult<ResultDto> = setState(true)

    /**
     * Stops inference.
     */
    override suspend fun stopInference(): SdkResult<ResultDto> = setState(false)

    /**
     * Retrieves the inference send mode configuration.
     */
    override suspend fun getInferenceSendMode(): SdkResult<ResultSendModeConfig> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("get_inference_send_mode") { raw, json, error ->
                continuation.resume(parseCdcResult(raw, json, error) { ResultSendModeConfig.fromJson(it) })
            }
        }
    }

    /**
     * Sets the inference send mode configuration.
     */
    override suspend fun setInferenceSendMode(config: ResultSendModeConfig): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand(
                "set_inference_send_mode",
                JSONObject().apply { put("params", config.toSetJson()) }
            ) { raw, json, error ->
                continuation.resume(parseCdcResult(raw, json, error) { ResultDto.fromJson(it) })
            }
        }
    }

    /**
     * Sets the inference send mode value.
     */
    override suspend fun setInferenceSendMode(mode: InferenceMode): SdkResult<ResultDto> =
        setInferenceSendMode(ResultSendModeConfig(mode = mode))

    /**
     * Sets batch send mode.
     */
    override suspend fun setBatchMode(): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand(
                "set_inference_send_mode",
                JSONObject().apply {
                    put("params", JSONObject().apply { put("mode", 2) })
                }
            ) { raw, json, error ->
                continuation.resume(parseCdcResult(raw, json, error) { ResultDto.fromJson(it) })
            }
        }
    }

    /**
     * Sets realtime send mode.
     */
    override suspend fun setRealtimeMode(): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand(
                "set_inference_send_mode",
                JSONObject().apply {
                    put("params", JSONObject().apply { put("mode", 1) })
                }
            ) { raw, json, error ->
                continuation.resume(parseCdcResult(raw, json, error) { ResultDto.fromJson(it) })
            }
        }
    }

    /**
     * Internal execution helper: runs block with a valid sender or returns NotConnected.
     */
    private inline fun <T> execute(block: (CdcSender) -> SdkResult<T>): SdkResult<T> {
        val cdcSender = connection.getSender() ?: return SdkResult.failure(SdkError.NotConnected)
        return block(cdcSender)
    }

}
