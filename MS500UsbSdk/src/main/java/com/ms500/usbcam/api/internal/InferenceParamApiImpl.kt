package com.ms500.usbcam.api.internal

import com.ms500.usbcam.api.InferenceParamApi
import com.ms500.usbcam.cdc.CdcSender
import com.ms500.usbcam.dto.InferenceState
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.dto.ResultSendModeConfig
import com.ms500.usbcam.dto.RoiConfig
import com.ms500.usbcam.result.SdkResult
import com.ms500.usbcam.result.SdkError
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

internal class InferenceParamApiImpl(
    private val connection: CdcConnectApiImpl
) : InferenceParamApi {

    override suspend fun getState(): SdkResult<InferenceState> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("get_inference_state") { _, json, error ->
                continuation.resume(parseCdcResult(json, error) { InferenceState.fromJson(it) })
            }
        }
    }

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

    override suspend fun startInference(): SdkResult<ResultDto> = setState(true)

    override suspend fun stopInference(): SdkResult<ResultDto> = setState(false)

    override suspend fun getResultSendMode(): SdkResult<ResultSendModeConfig> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("get_result_send_mode") { _, json, error ->
                continuation.resume(parseCdcResult(json, error) { ResultSendModeConfig.fromJson(it) })
            }
        }
    }

    override suspend fun setResultSendMode(config: ResultSendModeConfig): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("set_result_send_mode", config.toSetJson()) { _, json, error ->
                continuation.resume(parseCdcResultDto(json, error))
            }
        }
    }

    override suspend fun getRoi(): SdkResult<RoiConfig> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("get_roi") { _, json, error ->
                continuation.resume(parseCdcResult(json, error) { RoiConfig.fromJson(it) })
            }
        }
    }

    override suspend fun setRoi(config: RoiConfig): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            val json = config.toSetJson()
            cdcSender.sendCommand("set_roi", json) { _, response, error ->
                continuation.resume(parseCdcResultDto(response, error))
            }
        }
    }

    override suspend fun clearRoi(): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("clear_roi") { _, json, error ->
                continuation.resume(parseCdcResultDto(json, error))
            }
        }
    }

    override suspend fun setDetectionUpload(enabled: Boolean): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            val json = JSONObject().apply { put("enable", enabled) }
            cdcSender.sendCommand("set_detection_upload", json) { _, response, error ->
                continuation.resume(parseCdcResultDto(response, error))
            }
        }
    }

    override suspend fun startDetectionUpload(): SdkResult<ResultDto> =
        setDetectionUpload(true)

    override suspend fun stopDetectionUpload(): SdkResult<ResultDto> =
        setDetectionUpload(false)

    override suspend fun setBatchMode(frameCount: Int): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand(
                "set_result_send_mode",
                JSONObject().apply {
                    put("mode", "batch")
                    put("batch_frame_count", frameCount)
                }
            ) { _, json, error ->
                continuation.resume(parseCdcResultDto(json, error))
            }
        }
    }

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

    private inline fun <T> execute(block: (CdcSender) -> SdkResult<T>): SdkResult<T> {
        val cdcSender = connection.getSender() ?: return SdkResult.failure(SdkError.NotConnected)
        return block(cdcSender)
    }

}

