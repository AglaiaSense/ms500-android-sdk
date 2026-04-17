package com.ms500.usbcam.api

import com.ms500.usbcam.dto.InferenceState
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.dto.ResultSendModeConfig
import com.ms500.usbcam.dto.RoiConfig
import com.ms500.usbcam.result.SdkResult

/**
 * 推理参数 API。
 * 用于控制 AI 推理的启停、结果发送模式、ROI 区域以及检测上传等配置。
 *
 * Inference parameters API for controlling AI inference settings.
 */
interface InferenceParamApi {

    /**
     * 获取当前推理状态。
     *
     * @return 包含 [InferenceState] 的结果
     */
    suspend fun getState(): SdkResult<InferenceState>

    /**
     * 设置推理开关状态。
     *
     * @param enabled 是否启用推理
     * @return 操作结果
     */
    suspend fun setState(enabled: Boolean): SdkResult<ResultDto>

    /**
     * 启动推理。
     *
     * @return 操作结果
     */
    suspend fun startInference(): SdkResult<ResultDto>

    /**
     * 停止推理。
     *
     * @return 操作结果
     */
    suspend fun stopInference(): SdkResult<ResultDto>

    /**
     * 获取结果发送模式。
     *
     * @return 包含 [ResultSendModeConfig] 的结果
     */
    suspend fun getResultSendMode(): SdkResult<ResultSendModeConfig>

    /**
     * 设置结果发送模式。
     *
     * @param config 发送模式配置
     * @return 操作结果
     */
    suspend fun setResultSendMode(config: ResultSendModeConfig): SdkResult<ResultDto>

    /**
     * 设置为批量发送模式。
     *
     * @param frameCount 每批包含的帧数
     * @return 操作结果
     */
    suspend fun setBatchMode(frameCount: Int): SdkResult<ResultDto>

    /**
     * 设置为实时发送模式。
     *
     * @return 操作结果
     */
    suspend fun setRealtimeMode(): SdkResult<ResultDto>

    /**
     * 获取 ROI 配置。
     *
     * @return 包含 [RoiConfig] 的结果
     */
    suspend fun getRoi(): SdkResult<RoiConfig>

    /**
     * 设置 ROI 配置。
     *
     * @param config ROI 配置
     * @return 操作结果
     */
    suspend fun setRoi(config: RoiConfig): SdkResult<ResultDto>

    /**
     * 清除 ROI 配置。
     *
     * @return 操作结果
     */
    suspend fun clearRoi(): SdkResult<ResultDto>

    /**
     * 设置检测上传开关。
     *
     * @param enabled 是否启用检测上传
     * @return 操作结果
     */
    suspend fun setDetectionUpload(enabled: Boolean): SdkResult<ResultDto>

    /**
     * 启动检测上传。
     *
     * @return 操作结果
     */
    suspend fun startDetectionUpload(): SdkResult<ResultDto>

    /**
     * 停止检测上传。
     *
     * @return 操作结果
     */
    suspend fun stopDetectionUpload(): SdkResult<ResultDto>
}
