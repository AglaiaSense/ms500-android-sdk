package com.ms500.usbcam.api.internal

import com.ms500.usbcam.api.InferenceParamApi
import com.ms500.usbcam.cdc.CdcSender
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
 * Translates inference state, ROI, result send mode, and detection upload operations into CDC commands.
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
            cdcSender.sendCommand("get_inference_state") { _, json, error ->
                continuation.resume(parseCdcResult(json, error) { InferenceState.fromJson(it) })
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
                JSONObject().apply { put("inference_enabled", enabled) }
            ) { _, json, error ->
                continuation.resume(parseCdcResultDto(json, error))
            }
        }
    }

    /**
     * Starts inference.
     */
    override suspend fun startInference(): SdkResult<ResultDto> = setState(true)

    /**
     * Stops inference.
     */
    override suspend fun stopInference(): SdkResult<ResultDto> = setState(false)

    /**
     * Retrieves the result send mode configuration.
     */
    override suspend fun getResultSendMode(): SdkResult<ResultSendModeConfig> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("get_result_send_mode") { _, json, error ->
                continuation.resume(parseCdcResult(json, error) { ResultSendModeConfig.fromJson(it) })
            }
        }
    }

    /**
     * Sets the result send mode configuration.
     */
    override suspend fun setResultSendMode(config: ResultSendModeConfig): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("set_result_send_mode", config.toSetJson()) { _, json, error ->
                continuation.resume(parseCdcResultDto(json, error))
            }
        }
    }

    /**
     * Sets the detection upload enabled state.
     */
    override suspend fun setDetectionUpload(enabled: Boolean): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            val json = JSONObject().apply { put("enable", enabled) }
            cdcSender.sendCommand("set_detection_upload", json) { _, response, error ->
                continuation.resume(parseCdcResultDto(response, error))
            }
        }
    }

    /**
     * Starts detection upload.
     */
    override suspend fun startDetectionUpload(): SdkResult<ResultDto> =
        setDetectionUpload(true)

    /**
     * Stops detection upload.
     */
    override suspend fun stopDetectionUpload(): SdkResult<ResultDto> =
        setDetectionUpload(false)

    /**
     * Sets batch send mode.
     */
    override suspend fun setBatchMode(): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand(
                "set_result_send_mode",
                JSONObject().apply {
                    put("mode", "batch")
                }
            ) { _, json, error ->
                continuation.resume(parseCdcResultDto(json, error))
            }
        }
    }

    /**
     * Sets realtime send mode.
     */
    override suspend fun setRealtimeMode(): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand(
                "set_result_send_mode",
                JSONObject().apply { put("mode", "realtime") }
            ) { _, json, error ->
                continuation.resume(parseCdcResultDto(json, error))
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
