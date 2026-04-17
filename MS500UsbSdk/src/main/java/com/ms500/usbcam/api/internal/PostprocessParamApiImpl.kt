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

internal class PostprocessParamApiImpl(
    private val connection: CdcConnectApiImpl
) : PostprocessParamApi {

    override suspend fun getParams(): SdkResult<PostprocessParamDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("get_postprocess_param") { _, json, error ->
                continuation.resume(parseCdcResult(json, error) { PostprocessParamDto.fromJson(it) })
            }
        }
    }

    override suspend fun setParams(params: PostprocessParamDto): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("set_postprocess_param", params.toSetJson()) { _, response, error ->
                continuation.resume(parseCdcResultDto(response, error))
            }
        }
    }

    override suspend fun setPplParameter(params: PplParameterDto): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(pplParameter = params))

    override suspend fun setConfThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(confThreshold = value))

    override suspend fun setBodyThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(bodyThreshold = value))

    override suspend fun setFaceThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(faceThreshold = value))

    override suspend fun setGazeThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(gazeThreshold = value))

    override suspend fun setNmsThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(nmsThreshold = value))

    override suspend fun setBack2cameraThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(back2cameraThreshold = value))

    override suspend fun setGenderThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(genderThreshold = value))

    override suspend fun setMaxBodyDetection(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(maxBodyDetection = value))

    override suspend fun setFaceMinSize(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(faceMinSize = value))

    override suspend fun setMaxAge(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(maxAge = value))

    override suspend fun setMinHits(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(minHits = value))

    override suspend fun setIouThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(iouThreshold = value))

    override suspend fun setMaxTrackers(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(maxTrackers = value))

    override suspend fun setMinBboxArea(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(minBboxArea = value))

    override suspend fun setMinHeight(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(minHeight = value))

    override suspend fun setStabilityThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(stabilityThreshold = value))

    override suspend fun setBodyScoreTrackingThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(bodyScoreTrackingThreshold = value))

    override suspend fun setPitchThreshold(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(pitchThreshold = value))

    override suspend fun setYawThreshold(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(yawThreshold = value))

    override suspend fun setInferenceEnabled(value: Boolean): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(inferenceEnabled = value))

    private inline fun <T> execute(block: (CdcSender) -> SdkResult<T>): SdkResult<T> {
        val cdcSender = connection.getSender() ?: return SdkResult.failure(SdkError.NotConnected)
        return block(cdcSender)
    }

}

