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

import com.ms500.usbcam.api.PostprocessParamApi
import com.ms500.usbcam.cdc.CdcSender
import com.ms500.usbcam.dto.PostprocessParamDto
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.dto.RoiPointDto
import com.ms500.usbcam.dto.WatchingRegionDto
import com.ms500.usbcam.result.SdkError
import com.ms500.usbcam.result.SdkResult
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Internal implementation of postprocess parameter APIs.
 * Translates postprocess parameter read/write operations into CDC commands.
 */
internal class PostprocessParamApiImpl(
    /** CDC connection implementation, used to obtain shared CdcSender. */
    private val connection: CdcConnectApiImpl
) : PostprocessParamApi {

    override suspend fun getParams(): SdkResult<PostprocessParamDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("get_postprocess_param") { raw, json, error ->
                continuation.resume(parseCdcResult(raw, json, error) { PostprocessParamDto.fromJson(it) })
            }
        }
    }

    override suspend fun setParams(params: PostprocessParamDto): SdkResult<ResultDto> =
        setPostprocessParams(params.toSetJson())

    override suspend fun resetPostprocess(): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("reset_postprocess_param") { raw, json, error ->
                continuation.resume(parseCdcResult(raw, json, error) { ResultDto.fromJson(it) })
            }
        }
    }

    override suspend fun setFaceDetectionRoi(points: List<RoiPointDto>): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("face_detection_roi", points.toJsonArray()) })

    override suspend fun setTrajectoryDbLifetime(value: Int): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("trajectory_db_lifetime", value) })

    override suspend fun setGazeFocusThreshold(value: Double): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("gaze_focus_threshold", value) })

    override suspend fun setEnableWatchingRegion(value: Boolean): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("enable_watching_region", value) })

    override suspend fun setWatchingRegion(value: WatchingRegionDto): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("watching_region", value.toJson()) })

    override suspend fun setInferenceResultFilterEnable(value: Boolean): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("inference_result_filter_enable", value) })

    override suspend fun setEnableGazeTimeFunctions(value: Boolean): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("enable_gaze_time_functions", value) })

    override suspend fun setEnableAgeSmooth(value: Boolean): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("enable_age_smooth", value) })

    override suspend fun setEnableAgeSmoothContinuous(value: Boolean): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("enable_age_smooth_continuous", value) })

    override suspend fun setAgeBufSize(value: Int): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("age_buf_size", value) })

    override suspend fun setAgeIdSize(value: Int): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("age_id_size", value) })

    override suspend fun setEnableGenderVote(value: Boolean): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("enable_gender_vote", value) })

    override suspend fun setEnableGenderVoteContinuous(value: Boolean): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("enable_gender_vote_continuous", value) })

    override suspend fun setGenderBufSize(value: Int): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("gender_buf_size", value) })

    override suspend fun setGenderIdSize(value: Int): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("gender_id_size", value) })

    override suspend fun setFaceMinSizePpl(value: Int): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("face_min_size_ppl", value) })

    override suspend fun setEnableZeroAge(value: Boolean): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("enable_zero_age", value) })

    override suspend fun setEnableInvalidGender(value: Boolean): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("enable_invalid_gender", value) })

    override suspend fun setEnableDistributionAdjust(value: Boolean): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("enable_distribution_adjust", value) })

    override suspend fun setSMean(value: Double): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("s_mean", value) })

    override suspend fun setSStd(value: Double): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("s_std", value) })

    override suspend fun setTMean(value: Double): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("t_mean", value) })

    override suspend fun setTStd(value: Double): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("t_std", value) })

    override suspend fun setMBias(value: Double): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("m_bias", value) })

    override suspend fun setSBias(value: Double): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("s_bias", value) })

    override suspend fun setBodyThreshold(value: Double): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("body_detection_threshold", value) })

    override suspend fun setFaceThreshold(value: Double): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("face_threshold", value) })

    override suspend fun setGazeThreshold(value: Double): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("gaze_threshold", value) })

    override suspend fun setNmsThreshold(value: Double): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("nms_threshold", value) })

    override suspend fun setBack2cameraThreshold(value: Double): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("back2camera_threshold", value) })

    override suspend fun setGenderThreshold(value: Double): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("gender_threshold", value) })

    override suspend fun setMaxBodyDetection(value: Int): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("max_body_detection", value) })

    override suspend fun setFaceMinSize(value: Int): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("face_min_size", value) })

    override suspend fun setMaxAge(value: Int): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("max_age", value) })

    override suspend fun setMinHits(value: Int): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("min_hits", value) })

    override suspend fun setIouThreshold(value: Double): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("iou_threshold", value) })

    override suspend fun setMaxTrackers(value: Int): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("max_trackers", value) })

    override suspend fun setMinBboxArea(value: Double): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("min_bbox_area", value) })

    override suspend fun setMinHeight(value: Double): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("min_height", value) })

    override suspend fun setStabilityThreshold(value: Double): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("stability_threshold", value) })

    override suspend fun setBodyScoreTrackingThreshold(value: Double): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("body_score_tracking_threshold", value) })

    override suspend fun setPitchThreshold(value: Int): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("pitch_threshold", value) })

    override suspend fun setYawThreshold(value: Int): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("yaw_threshold", value) })

    override suspend fun setBodyBoxHeightThreshold(value: Double): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("body_box_height_threshold", value) })

    override suspend fun setIdCreationThreshold(value: Double): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("id_creation_threshold", value) })

    override suspend fun setClusterRoi(points: List<RoiPointDto>): SdkResult<ResultDto> =
        setPostprocessParams(JSONObject().apply { put("cluster_roi", points.toJsonArray()) })

    private suspend fun setPostprocessParams(params: JSONObject): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand(
                "set_postprocess_param",
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

private fun List<RoiPointDto>.toJsonArray(): JSONArray = JSONArray().apply {
    forEach { put(it.toJson()) }
}
