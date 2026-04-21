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
 * Internal implementation of postprocess parameter APIs.
 * Translates postprocess parameter read/write operations into CDC commands.
 */
internal class PostprocessParamApiImpl(
    /** CDC connection implementation, used to obtain shared CdcSender. */
    private val connection: CdcConnectApiImpl
) : PostprocessParamApi {

    /**
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
     * Sets PPL parameter only.
     */
    override suspend fun setPplParameter(params: PplParameterDto): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(pplParameter = params))

    /**
     * Sets confidence threshold only.
     */
    override suspend fun setConfThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(confThreshold = value))

    /**
     * Sets body detection threshold only.
     */
    override suspend fun setBodyThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(bodyThreshold = value))

    /**
     * Sets face detection threshold only.
     */
    override suspend fun setFaceThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(faceThreshold = value))

    /**
     * Sets gaze detection threshold only.
     */
    override suspend fun setGazeThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(gazeThreshold = value))

    /**
     * Sets NMS threshold only.
     */
    override suspend fun setNmsThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(nmsThreshold = value))

    /**
     * Sets back-to-camera threshold only.
     */
    override suspend fun setBack2cameraThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(back2cameraThreshold = value))

    /**
     * Sets gender detection threshold only.
     */
    override suspend fun setGenderThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(genderThreshold = value))

    /**
     * Sets max body detection count only.
     */
    override suspend fun setMaxBodyDetection(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(maxBodyDetection = value))

    /**
     * Sets minimum face size only.
     */
    override suspend fun setFaceMinSize(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(faceMinSize = value))

    /**
     * Sets max age (tracking) only.
     */
    override suspend fun setMaxAge(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(maxAge = value))

    /**
     * Sets minimum hits (tracking) only.
     */
    override suspend fun setMinHits(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(minHits = value))

    /**
     * Sets IOU threshold (tracking) only.
     */
    override suspend fun setIouThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(iouThreshold = value))

    /**
     * Sets max trackers only.
     */
    override suspend fun setMaxTrackers(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(maxTrackers = value))

    /**
     * Sets minimum bbox area only.
     */
    override suspend fun setMinBboxArea(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(minBboxArea = value))

    /**
     * Sets minimum height only.
     */
    override suspend fun setMinHeight(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(minHeight = value))

    /**
     * Sets stability threshold only.
     */
    override suspend fun setStabilityThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(stabilityThreshold = value))

    /**
     * Sets body score tracking threshold only.
     */
    override suspend fun setBodyScoreTrackingThreshold(value: Double): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(bodyScoreTrackingThreshold = value))

    /**
     * Sets pitch threshold only.
     */
    override suspend fun setPitchThreshold(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(pitchThreshold = value))

    /**
     * Sets yaw threshold only.
     */
    override suspend fun setYawThreshold(value: Int): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(yawThreshold = value))

    /**
     * Sets inference enabled flag only.
     */
    override suspend fun setInferenceEnabled(value: Boolean): SdkResult<ResultDto> =
        setParams(PostprocessParamDto(inferenceEnabled = value))

    /**
     * Internal execution helper: runs block with a valid sender or returns NotConnected.
     */
    private inline fun <T> execute(block: (CdcSender) -> SdkResult<T>): SdkResult<T> {
        val cdcSender = connection.getSender() ?: return SdkResult.failure(SdkError.NotConnected)
        return block(cdcSender)
    }

}
