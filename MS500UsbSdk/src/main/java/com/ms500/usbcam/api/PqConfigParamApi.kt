package com.ms500.usbcam.api

import com.ms500.usbcam.dto.PqConfigCameraImageSize
import com.ms500.usbcam.dto.PqConfigImageCropping
import com.ms500.usbcam.dto.PqConfigManualExposure
import com.ms500.usbcam.dto.PqConfigManualWhiteBalancePreset
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.dto.PqcongifParam
import com.ms500.usbcam.result.SdkResult

/**
 * 图像质量（PQ）配置参数 API。
 * 用于获取和设置相机的图像质量固定配置，如曝光模式、白平衡、裁剪等。
 *
 * Picture Quality (PQ) configuration API for image quality settings.
 */
interface PqConfigParamApi {

    /**
     * 获取当前 PQ 固定配置。
     *
     * @return 包含 [PqcongifParam] 的结果
     */
    suspend fun getPqFixConfig(): SdkResult<PqcongifParam>

    /**
     * 设置 PQ 固定配置。
     *
     * @param config PQ 配置
     * @return 操作结果
     */
    suspend fun setPqFixConfig(config: PqcongifParam): SdkResult<ResultDto>

    /**
     * 重置 PQ 固定配置为默认值。
     *
     * @return 操作结果
     */
    suspend fun resetPqFixConfig(): SdkResult<ResultDto>

    /**
     * 设置相机图像尺寸。
     *
     * @param value 图像尺寸配置
     * @return 操作结果
     */
    suspend fun setCameraImageSize(value: PqConfigCameraImageSize): SdkResult<ResultDto>

    /**
     * 设置曝光模式。
     *
     * @param value 模式值
     * @return 操作结果
     */
    suspend fun setExposureMode(value: Int): SdkResult<ResultDto>

    /**
     * 设置手动曝光参数。
     *
     * @param value 手动曝光配置
     * @return 操作结果
     */
    suspend fun setManualExposure(value: PqConfigManualExposure): SdkResult<ResultDto>

    /**
     * 设置手动曝光时间。
     *
     * @param value 曝光时间值
     * @return 操作结果
     */
    suspend fun setManualExposureTime(value: Int): SdkResult<ResultDto>

    /**
     * 设置手动增益。
     *
     * @param value 增益值
     * @return 操作结果
     */
    suspend fun setManualGain(value: Int): SdkResult<ResultDto>

    /**
     * 设置白平衡模式。
     *
     * @param value 模式值
     * @return 操作结果
     */
    suspend fun setWhiteBalanceMode(value: Int): SdkResult<ResultDto>

    /**
     * 设置手动白平衡预设。
     *
     * @param value 白平衡预设
     * @return 操作结果
     */
    suspend fun setManualWhiteBalancePreset(value: PqConfigManualWhiteBalancePreset): SdkResult<ResultDto>

    /**
     * 设置手动白平衡色温。
     *
     * @param value 色温值
     * @return 操作结果
     */
    suspend fun setManualWhiteBalanceColorTemperature(value: Int): SdkResult<ResultDto>

    /**
     * 设置曝光补偿（EV）。
     *
     * @param value 补偿值
     * @return 操作结果
     */
    suspend fun setEvCompensation(value: Int): SdkResult<ResultDto>

    /**
     * 设置 AE 抗闪烁模式。
     *
     * @param value 模式值
     * @return 操作结果
     */
    suspend fun setAeAntiFlickerMode(value: Int): SdkResult<ResultDto>

    /**
     * 设置图像裁剪参数。
     *
     * @param value 裁剪配置
     * @return 操作结果
     */
    suspend fun setImageCropping(value: PqConfigImageCropping): SdkResult<ResultDto>
}
