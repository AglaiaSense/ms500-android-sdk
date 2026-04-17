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
 * 用于获取和设置 UVC 相机的图像相关参数，如曝光、增益、白平衡等。
 *
 * Camera parameters API for getting/setting UVC image-related settings.
 */
interface CameraParamApi {

    /**
     * 获取当前相机参数。
     *
     * @return 包含 [CameraParamDto] 的结果
     */
    suspend fun getParams(): SdkResult<CameraParamDto>

    /**
     * 设置相机参数。
     * 只会下发 [params] 中非 null 的字段。
     *
     * @param params 要设置的相机参数
     * @return 操作结果
     */
    suspend fun setParams(params: CameraParamDto): SdkResult<ResultDto>

    /**
     * 重置相机参数为默认值。
     *
     * @return 操作结果
     */
    suspend fun resetCamera(): SdkResult<ResultDto>

    /**
     * 设置曝光值。
     *
     * @param value 曝光值
     * @return 操作结果
     */
    suspend fun setExposure(value: Int): SdkResult<ResultDto>

    /**
     * 设置增益值。
     *
     * @param value 增益值
     * @return 操作结果
     */
    suspend fun setGain(value: Int): SdkResult<ResultDto>

    /**
     * 设置伽马值。
     *
     * @param value 伽马值
     * @return 操作结果
     */
    suspend fun setGamma(value: Int): SdkResult<ResultDto>

    /**
     * 设置白平衡值。
     *
     * @param value 白平衡值
     * @return 操作结果
     */
    suspend fun setWhiteBalance(value: Int): SdkResult<ResultDto>

    /**
     * 设置帧率。
     *
     * @param value 帧率值
     * @return 操作结果
     */
    suspend fun setFrameRate(value: Int): SdkResult<ResultDto>

    /**
     * 设置水平翻转。
     *
     * @param value 是否水平翻转（0/1）
     * @return 操作结果
     */
    suspend fun setHFlip(value: Int): SdkResult<ResultDto>

    /**
     * 设置垂直翻转。
     *
     * @param value 是否垂直翻转（0/1）
     * @return 操作结果
     */
    suspend fun setVFlip(value: Int): SdkResult<ResultDto>
}
