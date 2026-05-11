/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */

package com.ms500.usbcam.api

import com.ms500.usbcam.dto.CameraParamDto
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.result.SdkResult

/**
 * Camera parameters API for getting/setting UVC image-related settings.
 */
interface CameraParamApi {

    /**
     * Gets current camera parameters.
     *
     * @return Result containing [CameraParamDto]
     */
    suspend fun getParams(): SdkResult<CameraParamDto>

    /**
     * Sets camera parameters.
     * Only non-null fields in [params] will be sent.
     *
     * @param params Camera parameters to set
     * @return Operation result
     */
    suspend fun setParams(params: CameraParamDto): SdkResult<ResultDto>

    /**
     * Resets camera parameters to default values.
     *
     * @return Operation result
     */
    suspend fun resetCamera(): SdkResult<ResultDto>

    /**
     * Sets gamma value.
     *
     * @param value Gamma value
     * @return Operation result
     */
    suspend fun setGamma(value: Int): SdkResult<ResultDto>

    /**
     * Sets frame rate.
     *
     * @param value Frame rate value
     * @return Operation result
     */
    suspend fun setFrameRate(value: Int): SdkResult<ResultDto>

    /**
     * Sets horizontal flip.
     *
     * @param value Whether to flip horizontally (0/1)
     * @return Operation result
     */
    suspend fun setHFlip(value: Int): SdkResult<ResultDto>

    /**
     * Sets vertical flip.
     *
     * @param value Whether to flip vertically (0/1)
     * @return Operation result
     */
    suspend fun setVFlip(value: Int): SdkResult<ResultDto>
}
