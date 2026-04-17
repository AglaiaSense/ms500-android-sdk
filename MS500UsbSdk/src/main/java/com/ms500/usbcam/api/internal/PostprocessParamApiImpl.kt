package com.ms500.usbcam.api.internal

import com.ms500.usbcam.api.PostprocessParamApi
import com.ms500.usbcam.cdc.CdcSender
import com.ms500.usbcam.dto.PostprocessParamDto
import com.ms500.usbcam.dto.PplParameterDto
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.result.SdkResult
import com.ms500.usbcam.result.SdkError
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 后处理参数 API 内部实现类。
 *
 * 负责将后处理参数（阈值、NMS、跟踪、PPL 等）的读写操作转换为 CDC 命令，
 * 并解析设备返回的 JSON。
 *
 * Internal implementation of postprocess parameter APIs.
 * Translates postprocess parameter read/write operations into CDC commands.
 */
internal class PostprocessParamApiImpl(
    /** CDC 连接实现，用于获取共享的 CdcSender。 */
    private val connection: CdcConnectApiImpl
) : PostprocessParamApi {

    /**
     * 获取当前后处理参数。
     * Retrieves the current postprocess parameters.
     */
    override suspend fun getParams(): SdkResult<PostprocessParamDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("get_postprocess_param") { _, json, error ->
                continuation.resume(parseCdcResult(json, error) { PostprocessParamDto.fromJson(it) })
            }
        }
    }

    /**
     * 设置后处理参数（支持完整或部分参数）。
     * Sets postprocess parameters (full or partial).
     */
    override suspend fun setParams(params: PostprocessParamDto): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("set_postprocess_param", params.toSetJson()) { _, response, error ->
                continuation.resume(parseCdcResultDto(response, error))
            }
        }
    }

    /**
     * 仅设置 PPL 参数。
     * Sets PPL parameter only.
     */
    override suspend fun setPplParameter(params: PplParameterDto): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(pplParameter = params))

    /**
     * 仅设置置信度阈值。
     * Sets confidence threshold only.
     */
    override suspend fun setConfThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(confThreshold = value))

    /**
     * 仅设置人体检测阈值。
     * Sets body detection threshold only.
     */
    override suspend fun setBodyThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(bodyThreshold = value))

    /**
     * 仅设置人脸检测阈值。
     * Sets face detection threshold only.
     */
    override suspend fun setFaceThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(faceThreshold = value))

    /**
     * 仅设置凝视检测阈值。
     * Sets gaze detection threshold only.
     */
    override suspend fun setGazeThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(gazeThreshold = value))

    /**
     * 仅设置 NMS 阈值。
     * Sets NMS threshold only.
     */
    override suspend fun setNmsThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(nmsThreshold = value))

    /**
     * 仅设置 back2camera 阈值。
     * Sets back-to-camera threshold only.
     */
    override suspend fun setBack2cameraThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(back2cameraThreshold = value))

    /**
     * 仅设置性别检测阈值。
     * Sets gender detection threshold only.
     */
    override suspend fun setGenderThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(genderThreshold = value))

    /**
     * 仅设置最大人体检测数量。
     * Sets max body detection count only.
     */
    override suspend fun setMaxBodyDetection(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(maxBodyDetection = value))

    /**
     * 仅设置最小人脸尺寸。
     * Sets minimum face size only.
     */
    override suspend fun setFaceMinSize(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(faceMinSize = value))

    /**
     * 仅设置最大年龄（跟踪相关）。
     * Sets max age (tracking) only.
     */
    override suspend fun setMaxAge(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(maxAge = value))

    /**
     * 仅设置最小命中次数（跟踪相关）。
     * Sets minimum hits (tracking) only.
     */
    override suspend fun setMinHits(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(minHits = value))

    /**
     * 仅设置 IOU 阈值（跟踪相关）。
     * Sets IOU threshold (tracking) only.
     */
    override suspend fun setIouThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(iouThreshold = value))

    /**
     * 仅设置最大跟踪器数量。
     * Sets max trackers only.
     */
    override suspend fun setMaxTrackers(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(maxTrackers = value))

    /**
     * 仅设置最小边界框面积。
     * Sets minimum bbox area only.
     */
    override suspend fun setMinBboxArea(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(minBboxArea = value))

    /**
     * 仅设置最小高度。
     * Sets minimum height only.
     */
    override suspend fun setMinHeight(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(minHeight = value))

    /**
     * 仅设置稳定性阈值。
     * Sets stability threshold only.
     */
    override suspend fun setStabilityThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(stabilityThreshold = value))

    /**
     * 仅设置人体评分跟踪阈值。
     * Sets body score tracking threshold only.
     */
    override suspend fun setBodyScoreTrackingThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(bodyScoreTrackingThreshold = value))

    /**
     * 仅设置俯仰角（pitch）阈值。
     * Sets pitch threshold only.
     */
    override suspend fun setPitchThreshold(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(pitchThreshold = value))

    /**
     * 仅设置偏航角（yaw）阈值。
     * Sets yaw threshold only.
     */
    override suspend fun setYawThreshold(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(yawThreshold = value))

    /**
     * 仅设置推理使能开关。
     * Sets inference enabled flag only.
     */
    override suspend fun setInferenceEnabled(value: Boolean): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(inferenceEnabled = value))

    /**
     * 内部执行模板：获取 CdcSender 后执行 block，未连接时返回失败。
     * Internal execution helper: runs block with a valid sender or returns NotConnected.
     */
    private inline fun <T> execute(block: (CdcSender) -> SdkResult<T>): SdkResult<T> {
        val cdcSender = connection.getSender() ?: return SdkResult.failure(SdkError.NotConnected)
        return block(cdcSender)
    }

}
