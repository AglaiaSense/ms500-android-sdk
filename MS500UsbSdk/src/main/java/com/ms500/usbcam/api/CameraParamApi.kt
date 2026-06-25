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
 * Camera parameter API for CDC camera settings.
 *
 * Protocol mapping:
 * - Read: `get_camera_param`, parsed from the response `result` object into [CameraParamDto].
 * - Write: `set_camera_param`, with writable fields placed under the request `params` object.
 *
 * Current ESP32 firmware accepts only `params.frame_rate` for `set_camera_param`. Other image quality
 * controls such as flip, exposure, white balance, and cropping belong to [PqConfigParamApi].
 */
interface CameraParamApi {

    /**
     * Gets current camera parameters.
     *
     * Sends `{"cmd":"get_camera_param"}`. The result contains the current `frame_rate` value and may
     * contain range metadata such as `frame_rate.min=5`, `frame_rate.max=30`, and `unit="fps"`.
     *
     * @return Result containing current camera parameters.
     */
    suspend fun getParams(): SdkResult<CameraParamDto>

    /**
     * Sets camera parameters in one request.
     *
     * Converts [params] through [CameraParamDto.toSetJson] and sends it as `set_camera_param.params`.
     * Only non-null fields are sent. With the current firmware this effectively means only
     * `params.frame_rate` is emitted.
     *
     * Field range/default from appendix 8: frameRate 5..30 fps; default: 30 fps.
     *
     * @param params Camera parameters to set.
     * @return Operation result returned by the device.
     */
    suspend fun setParams(params: CameraParamDto): SdkResult<ResultDto>

    /**
     * Sets frame rate directly.
     *
     * Sends `set_camera_param` with `params.frame_rate=value`. This is the preferred simple setter for
     * frame rate because it makes the exact protocol field obvious at the call site.
     *
     * Range: 5..30 fps; default: 30 fps.
     *
     * @param value Frame rate value in fps.
     * @return Operation result returned by the device.
     */
    suspend fun setFrameRate(value: Int): SdkResult<ResultDto>
}
