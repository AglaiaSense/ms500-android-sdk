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

import com.ms500.usbcam.api.WtaPostprocessParamApi
import com.ms500.usbcam.cdc.CdcSender
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.dto.WtaPostprocessParamDto
import com.ms500.usbcam.dto.WtaWatchingRegionDto
import com.ms500.usbcam.result.SdkError
import com.ms500.usbcam.result.SdkResult
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Internal implementation of the WTA post-process parameter API.
 *
 * Mirrors [PostprocessParamApiImpl]'s shape - single-field setters build the exact protocol JSON
 * directly, while [setParams] serializes a DTO - so both parameter paths stay symmetrical and a
 * reader can diff them. The difference is the bounds: everything here validates against
 * [WtaBounds] before sending.
 */
internal class WtaPostprocessParamApiImpl(
    private val connection: CdcConnectApiImpl
) : WtaPostprocessParamApi {

    override suspend fun getParams(): SdkResult<WtaPostprocessParamDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("get_postprocess_param") { raw, json, error ->
                continuation.resume(
                    parseCdcResult(raw, json, error) { WtaPostprocessParamDto.fromJson(it) }
                )
            }
        }
    }

    override suspend fun resetPostprocess(): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("reset_postprocess_param") { raw, json, error ->
                continuation.resume(parseCdcResult(raw, json, error) { ResultDto.fromJson(it) })
            }
        }
    }

    /**
     * Validates the whole DTO before sending, so one bad field means nothing is written.
     *
     * A partial write would leave the device in a state the caller never asked for and cannot
     * easily identify, which is worse than an outright rejection.
     */
    override suspend fun setParams(params: WtaPostprocessParamDto): SdkResult<ResultDto> {
        validate(params)?.let { return it }
        return send(params.toSetJson())
    }

    // --- Mapped legacy fields -----------------------------------------------

    override suspend fun setBodyThreshold(value: Double): SdkResult<ResultDto> =
        setRatio("body_detection_threshold", value)

    override suspend fun setFaceThreshold(value: Double): SdkResult<ResultDto> =
        setRatio("face_threshold", value)

    override suspend fun setMaxBodyDetection(value: Int): SdkResult<ResultDto> {
        ParamValidation.checkRange<ResultDto>(
            "max_body_detection", value,
            WtaBounds.MAX_BODY_DETECTION_MIN, WtaBounds.MAX_BODY_DETECTION_MAX
        )?.let { return it }
        return send(JSONObject().apply { put("max_body_detection", value) })
    }

    override suspend fun setMaxAge(value: Int): SdkResult<ResultDto> {
        ParamValidation.checkRange<ResultDto>(
            "max_age", value, WtaBounds.MAX_AGE_MIN, WtaBounds.MAX_AGE_MAX
        )?.let { return it }
        return send(JSONObject().apply { put("max_age", value) })
    }

    override suspend fun setMinHits(value: Int): SdkResult<ResultDto> {
        ParamValidation.checkRange<ResultDto>(
            "min_hits", value, WtaBounds.MIN_HITS_MIN, WtaBounds.MIN_HITS_MAX
        )?.let { return it }
        return send(JSONObject().apply { put("min_hits", value) })
    }

    override suspend fun setInferenceEnabled(value: Boolean): SdkResult<ResultDto> =
        send(JSONObject().apply { put("inference_enabled", value) })

    override suspend fun setTrajectoryDbLifetime(value: Int): SdkResult<ResultDto> {
        ParamValidation.checkRange<ResultDto>(
            "trajectory_db_lifetime", value,
            WtaBounds.TRAJECTORY_LIFETIME_MIN, WtaBounds.TRAJECTORY_LIFETIME_MAX
        )?.let { return it }
        return send(JSONObject().apply { put("trajectory_db_lifetime", value) })
    }

    override suspend fun setInferenceResultFilterEnable(value: Boolean): SdkResult<ResultDto> =
        send(JSONObject().apply { put("inference_result_filter_enable", value) })

    override suspend fun setWatchingRegion(value: WtaWatchingRegionDto): SdkResult<ResultDto> {
        validateWatchingRegion(value)?.let { return it }
        return send(JSONObject().apply { put("watching_region", value.toJson()) })
    }

    override suspend fun setEnableDistributionAdjust(value: Boolean): SdkResult<ResultDto> =
        send(JSONObject().apply { put("enable_distribution_adjust", value) })

    override suspend fun setSMean(value: Double): SdkResult<ResultDto> =
        setDistribution("s_mean", value)

    override suspend fun setSStd(value: Double): SdkResult<ResultDto> =
        setDistribution("s_std", value)

    override suspend fun setTMean(value: Double): SdkResult<ResultDto> =
        setDistribution("t_mean", value)

    override suspend fun setTStd(value: Double): SdkResult<ResultDto> =
        setDistribution("t_std", value)

    override suspend fun setMBias(value: Double): SdkResult<ResultDto> =
        setBias("m_bias", value)

    override suspend fun setSBias(value: Double): SdkResult<ResultDto> =
        setBias("s_bias", value)

    // --- WTA-only fields ----------------------------------------------------

    override suspend fun setHeadScoreThresh(value: Double): SdkResult<ResultDto> =
        setRatio("head_score_thresh", value)

    override suspend fun setPersonIouThresh(value: Double): SdkResult<ResultDto> =
        setRatio("person_iou_thresh", value)

    override suspend fun setHeadIouThresh(value: Double): SdkResult<ResultDto> =
        setRatio("head_iou_thresh", value)

    override suspend fun setFaceIouThresh(value: Double): SdkResult<ResultDto> =
        setRatio("face_iou_thresh", value)

    override suspend fun setPersonWidthThresh(value: Int): SdkResult<ResultDto> {
        ParamValidation.checkRange<ResultDto>(
            "person_width_thresh", value,
            WtaBounds.PERSON_WIDTH_MIN, WtaBounds.PERSON_WIDTH_MAX
        )?.let { return it }
        return send(JSONObject().apply { put("person_width_thresh", value) })
    }

    override suspend fun setHeadWidthThresh(value: Int): SdkResult<ResultDto> =
        setHeadFaceWidth("head_width_thresh", value)

    override suspend fun setFaceWidthThresh(value: Int): SdkResult<ResultDto> =
        setHeadFaceWidth("face_width_thresh", value)

    override suspend fun setAssociationIouThresh(value: Double): SdkResult<ResultDto> =
        setRatio("association_iou_thresh", value)

    override suspend fun setGDistroAdj(value: Double): SdkResult<ResultDto> =
        setRatio("g_distro_adj", value)

    override suspend fun setMaxDist(value: Int): SdkResult<ResultDto> {
        ParamValidation.checkRange<ResultDto>(
            "max_dist", value, WtaBounds.MAX_DIST_MIN, WtaBounds.MAX_DIST_MAX
        )?.let { return it }
        return send(JSONObject().apply { put("max_dist", value) })
    }

    override suspend fun setHeadMinArea(value: Int): SdkResult<ResultDto> =
        setMinArea("head_min_area", value)

    override suspend fun setFaceMinArea(value: Int): SdkResult<ResultDto> =
        setMinArea("face_min_area", value)

    override suspend fun setAYawMin(value: Int): SdkResult<ResultDto> = setAngle("a_yaw_min", value)

    override suspend fun setAYawMax(value: Int): SdkResult<ResultDto> = setAngle("a_yaw_max", value)

    override suspend fun setAPitchMin(value: Int): SdkResult<ResultDto> =
        setAngle("a_pitch_min", value)

    override suspend fun setAPitchMax(value: Int): SdkResult<ResultDto> =
        setAngle("a_pitch_max", value)

    // --- Shared helpers -----------------------------------------------------

    private suspend fun setRatio(key: String, value: Double): SdkResult<ResultDto> {
        ParamValidation.checkRange<ResultDto>(
            key, value, WtaBounds.RATIO_MIN, WtaBounds.RATIO_MAX
        )?.let { return it }
        return send(JSONObject().apply { put(key, value) })
    }

    private suspend fun setDistribution(key: String, value: Double): SdkResult<ResultDto> {
        ParamValidation.checkRange<ResultDto>(
            key, value, WtaBounds.DISTRIBUTION_MIN, WtaBounds.DISTRIBUTION_MAX
        )?.let { return it }
        return send(JSONObject().apply { put(key, value) })
    }

    private suspend fun setBias(key: String, value: Double): SdkResult<ResultDto> {
        ParamValidation.checkRange<ResultDto>(
            key, value, WtaBounds.BIAS_MIN, WtaBounds.BIAS_MAX
        )?.let { return it }
        return send(JSONObject().apply { put(key, value) })
    }

    private suspend fun setAngle(key: String, value: Int): SdkResult<ResultDto> {
        ParamValidation.checkRange<ResultDto>(
            key, value, WtaBounds.ANGLE_MIN, WtaBounds.ANGLE_MAX
        )?.let { return it }
        return send(JSONObject().apply { put(key, value) })
    }

    private suspend fun setHeadFaceWidth(key: String, value: Int): SdkResult<ResultDto> {
        ParamValidation.checkRange<ResultDto>(
            key, value, WtaBounds.HEAD_FACE_WIDTH_MIN, WtaBounds.HEAD_FACE_WIDTH_MAX
        )?.let { return it }
        return send(JSONObject().apply { put(key, value) })
    }

    private suspend fun setMinArea(key: String, value: Int): SdkResult<ResultDto> {
        ParamValidation.checkRange<ResultDto>(
            key, value, WtaBounds.MIN_AREA_MIN, WtaBounds.MIN_AREA_MAX
        )?.let { return it }
        return send(JSONObject().apply { put(key, value) })
    }

    /** Validates every non-null field of a batch request against the WTA bounds. */
    private fun validate(params: WtaPostprocessParamDto): SdkResult<ResultDto>? {
        val ratios = listOf(
            "body_detection_threshold" to params.bodyThreshold,
            "face_threshold" to params.faceThreshold,
            "head_score_thresh" to params.headScoreThresh,
            "person_iou_thresh" to params.personIouThresh,
            "head_iou_thresh" to params.headIouThresh,
            "face_iou_thresh" to params.faceIouThresh,
            "association_iou_thresh" to params.associationIouThresh,
            "g_distro_adj" to params.gDistroAdj
        )
        for ((name, value) in ratios) {
            if (value == null) continue
            ParamValidation.checkRange<ResultDto>(
                name, value, WtaBounds.RATIO_MIN, WtaBounds.RATIO_MAX
            )?.let { return it }
        }

        val distributions = listOf(
            "s_mean" to params.sMean,
            "s_std" to params.sStd,
            "t_mean" to params.tMean,
            "t_std" to params.tStd
        )
        for ((name, value) in distributions) {
            if (value == null) continue
            ParamValidation.checkRange<ResultDto>(
                name, value, WtaBounds.DISTRIBUTION_MIN, WtaBounds.DISTRIBUTION_MAX
            )?.let { return it }
        }

        for ((name, value) in listOf("m_bias" to params.mBias, "s_bias" to params.sBias)) {
            if (value == null) continue
            ParamValidation.checkRange<ResultDto>(
                name, value, WtaBounds.BIAS_MIN, WtaBounds.BIAS_MAX
            )?.let { return it }
        }

        params.maxBodyDetection?.let { value ->
            ParamValidation.checkRange<ResultDto>(
                "max_body_detection", value,
                WtaBounds.MAX_BODY_DETECTION_MIN, WtaBounds.MAX_BODY_DETECTION_MAX
            )?.let { return it }
        }
        params.maxAge?.let { value ->
            ParamValidation.checkRange<ResultDto>(
                "max_age", value, WtaBounds.MAX_AGE_MIN, WtaBounds.MAX_AGE_MAX
            )?.let { return it }
        }
        params.minHits?.let { value ->
            ParamValidation.checkRange<ResultDto>(
                "min_hits", value, WtaBounds.MIN_HITS_MIN, WtaBounds.MIN_HITS_MAX
            )?.let { return it }
        }
        params.trajectoryDbLifetime?.let { value ->
            ParamValidation.checkRange<ResultDto>(
                "trajectory_db_lifetime", value,
                WtaBounds.TRAJECTORY_LIFETIME_MIN, WtaBounds.TRAJECTORY_LIFETIME_MAX
            )?.let { return it }
        }
        params.personWidthThresh?.let { value ->
            ParamValidation.checkRange<ResultDto>(
                "person_width_thresh", value,
                WtaBounds.PERSON_WIDTH_MIN, WtaBounds.PERSON_WIDTH_MAX
            )?.let { return it }
        }
        for ((name, value) in listOf(
            "head_width_thresh" to params.headWidthThresh,
            "face_width_thresh" to params.faceWidthThresh
        )) {
            if (value == null) continue
            ParamValidation.checkRange<ResultDto>(
                name, value, WtaBounds.HEAD_FACE_WIDTH_MIN, WtaBounds.HEAD_FACE_WIDTH_MAX
            )?.let { return it }
        }
        params.maxDist?.let { value ->
            ParamValidation.checkRange<ResultDto>(
                "max_dist", value, WtaBounds.MAX_DIST_MIN, WtaBounds.MAX_DIST_MAX
            )?.let { return it }
        }
        for ((name, value) in listOf(
            "head_min_area" to params.headMinArea,
            "face_min_area" to params.faceMinArea
        )) {
            if (value == null) continue
            ParamValidation.checkRange<ResultDto>(
                name, value, WtaBounds.MIN_AREA_MIN, WtaBounds.MIN_AREA_MAX
            )?.let { return it }
        }
        for ((name, value) in listOf(
            "a_yaw_min" to params.aYawMin,
            "a_yaw_max" to params.aYawMax,
            "a_pitch_min" to params.aPitchMin,
            "a_pitch_max" to params.aPitchMax
        )) {
            if (value == null) continue
            ParamValidation.checkRange<ResultDto>(
                name, value, WtaBounds.ANGLE_MIN, WtaBounds.ANGLE_MAX
            )?.let { return it }
        }

        params.watchingRegion?.let { region -> validateWatchingRegion(region)?.let { return it } }
        return null
    }

    /** Validates the watching-region object against the WTA angle and ratio bounds. */
    private fun validateWatchingRegion(value: WtaWatchingRegionDto): SdkResult<ResultDto>? {
        for ((name, angle) in listOf(
            "watching_region.min_yaw" to value.minYaw,
            "watching_region.max_yaw" to value.maxYaw,
            "watching_region.min_pitch" to value.minPitch,
            "watching_region.max_pitch" to value.maxPitch
        )) {
            if (angle == null) continue
            ParamValidation.checkRange<ResultDto>(
                name, angle, WtaBounds.ANGLE_MIN, WtaBounds.ANGLE_MAX
            )?.let { return it }
        }
        for ((name, ratio) in listOf(
            "watching_region.d1" to value.d1,
            "watching_region.d2" to value.d2
        )) {
            if (ratio == null) continue
            ParamValidation.checkRange<ResultDto>(
                name, ratio, WtaBounds.RATIO_MIN, WtaBounds.RATIO_MAX
            )?.let { return it }
        }
        return null
    }

    private suspend fun send(params: JSONObject): SdkResult<ResultDto> = execute { cdcSender ->
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
