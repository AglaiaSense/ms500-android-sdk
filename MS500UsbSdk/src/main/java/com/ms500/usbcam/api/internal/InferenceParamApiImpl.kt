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

/**
 * 推理参数 API 内部实现类。
 *
 * 负责将推理开关、ROI、结果发送模式、检测上传等操作转换为 CDC 命令，
 * 并解析设备返回的 JSON。
 *
 * Internal implementation of inference parameter APIs.
 * Translates inference state, ROI, result send mode, and detection upload operations into CDC commands.
 */
internal class InferenceParamApiImpl(
    /** CDC 连接实现，用于获取共享的 CdcSender。 */
    private val connection: CdcConnectApiImpl
) : InferenceParamApi {

    /**
     * 获取当前推理状态。
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
     * 设置推理开关状态。
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
     * 启动推理。
     * Starts inference.
     */
    override suspend fun startInference(): SdkResult<ResultDto> = setState(true)

    /**
     * 停止推理。
     * Stops inference.
     */
    override suspend fun stopInference(): SdkResult<ResultDto> = setState(false)

    /**
     * 获取结果发送模式配置。
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
     * 设置结果发送模式配置。
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
     * 获取 ROI 配置。
     * Retrieves the ROI configuration.
     */
    override suspend fun getRoi(): SdkResult<RoiConfig> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("get_roi") { _, json, error ->
                continuation.resume(parseCdcResult(json, error) { RoiConfig.fromJson(it) })
            }
        }
    }

    /**
     * 设置 ROI 配置。
     * Sets the ROI configuration.
     */
    override suspend fun setRoi(config: RoiConfig): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            val json = config.toSetJson()
            cdcSender.sendCommand("set_roi", json) { _, response, error ->
                continuation.resume(parseCdcResultDto(response, error))
            }
        }
    }

    /**
     * 清除 ROI 配置。
     * Clears the ROI configuration.
     */
    override suspend fun clearRoi(): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("clear_roi") { _, json, error ->
                continuation.resume(parseCdcResultDto(json, error))
            }
        }
    }

    /**
     * 设置检测上传开关。
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
     * 启动检测上传。
     * Starts detection upload.
     */
    override suspend fun startDetectionUpload(): SdkResult<ResultDto> =
        setDetectionUpload(true)

    /**
     * 停止检测上传。
     * Stops detection upload.
     */
    override suspend fun stopDetectionUpload(): SdkResult<ResultDto> =
        setDetectionUpload(false)

    /**
     * 设置为批量发送模式。
     * Sets batch send mode with the specified frame count.
     */
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

    /**
     * 设置为实时发送模式。
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
     * 内部执行模板：获取 CdcSender 后执行 block，未连接时返回失败。
     * Internal execution helper: runs block with a valid sender or returns NotConnected.
     */
    private inline fun <T> execute(block: (CdcSender) -> SdkResult<T>): SdkResult<T> {
        val cdcSender = connection.getSender() ?: return SdkResult.failure(SdkError.NotConnected)
        return block(cdcSender)
    }

}
