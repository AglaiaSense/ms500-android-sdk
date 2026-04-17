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
 * 相机参数 API。
 *
 * 仅暴露设备当前协议已支持的参数：
 * - `exposure`
 * - `gain`
 * - `gamma`
 * - `white_balance`
 * - `frame_rate`
 * - `hflip`
 * - `vflip`
 */
interface CameraParamApi {

    /**
     * 获取当前相机参数。
     */
    suspend fun getParams(): SdkResult<CameraParamDto>

    /**
     * 设置相机参数。
     *
     * 只会下发 `params` 中非 null 的字段。
     */
    suspend fun setParams(params: CameraParamDto): SdkResult<ResultDto>

    /**
     * 重置相机参数为默认值。
     */
    suspend fun resetCamera(): SdkResult<ResultDto>

    suspend fun setExposure(value: Int): SdkResult<ResultDto>

    suspend fun setGain(value: Int): SdkResult<ResultDto>

    suspend fun setGamma(value: Int): SdkResult<ResultDto>

    suspend fun setWhiteBalance(value: Int): SdkResult<ResultDto>

    suspend fun setFrameRate(value: Int): SdkResult<ResultDto>

    suspend fun setHFlip(value: Int): SdkResult<ResultDto>

    suspend fun setVFlip(value: Int): SdkResult<ResultDto>
}
