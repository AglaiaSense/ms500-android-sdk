package com.ms500.usbcam.api

import com.ms500.usbcam.dto.PostprocessParamDto
import com.ms500.usbcam.dto.PplParameterDto
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.result.SdkResult

/**
 * 后处理参数 API。
 * 用于获取和设置 AI 推理后处理阶段的各项阈值与参数。
 *
 * Post-processing parameters API for inference threshold and tracker settings.
 */
interface PostprocessParamApi {

    /**
     * 获取当前后处理参数。
     *
     * @return 包含 [PostprocessParamDto] 的结果
     */
    suspend fun getParams(): SdkResult<PostprocessParamDto>

    /**
     * 设置后处理参数。
     *
     * @param params 后处理参数
     * @return 操作结果
     */
    suspend fun setParams(params: PostprocessParamDto): SdkResult<ResultDto>

    /**
     * 设置 PPL 参数。
     *
     * @param params PPL 参数
     * @return 操作结果
     */
    suspend fun setPplParameter(params: PplParameterDto): SdkResult<ResultDto>

    /**
     * 设置置信度阈值。
     *
     * @param value 阈值
     * @return 操作结果
     */
    suspend fun setConfThreshold(value: Double): SdkResult<ResultDto>

    /**
     * 设置人体检测阈值。
     *
     * @param value 阈值
     * @return 操作结果
     */
    suspend fun setBodyThreshold(value: Double): SdkResult<ResultDto>

    /**
     * 设置人脸检测阈值。
     *
     * @param value 阈值
     * @return 操作结果
     */
    suspend fun setFaceThreshold(value: Double): SdkResult<ResultDto>

    /**
     * 设置凝视检测阈值。
     *
     * @param value 阈值
     * @return 操作结果
     */
    suspend fun setGazeThreshold(value: Double): SdkResult<ResultDto>

    /**
     * 设置 NMS 阈值。
     *
     * @param value 阈值
     * @return 操作结果
     */
    suspend fun setNmsThreshold(value: Double): SdkResult<ResultDto>

    /**
     * 设置背对相机阈值。
     *
     * @param value 阈值
     * @return 操作结果
     */
    suspend fun setBack2cameraThreshold(value: Double): SdkResult<ResultDto>

    /**
     * 设置性别检测阈值。
     *
     * @param value 阈值
     * @return 操作结果
     */
    suspend fun setGenderThreshold(value: Double): SdkResult<ResultDto>

    /**
     * 设置最大人体检测数量。
     *
     * @param value 数量
     * @return 操作结果
     */
    suspend fun setMaxBodyDetection(value: Int): SdkResult<ResultDto>

    /**
     * 设置人脸最小尺寸。
     *
     * @param value 尺寸（像素）
     * @return 操作结果
     */
    suspend fun setFaceMinSize(value: Int): SdkResult<ResultDto>

    /**
     * 设置最大跟踪年龄。
     *
     * @param value 年龄
     * @return 操作结果
     */
    suspend fun setMaxAge(value: Int): SdkResult<ResultDto>

    /**
     * 设置最小命中次数。
     *
     * @param value 次数
     * @return 操作结果
     */
    suspend fun setMinHits(value: Int): SdkResult<ResultDto>

    /**
     * 设置 IOU 阈值。
     *
     * @param value 阈值
     * @return 操作结果
     */
    suspend fun setIouThreshold(value: Double): SdkResult<ResultDto>

    /**
     * 设置最大跟踪器数量。
     *
     * @param value 数量
     * @return 操作结果
     */
    suspend fun setMaxTrackers(value: Int): SdkResult<ResultDto>

    /**
     * 设置最小 BBox 面积。
     *
     * @param value 面积
     * @return 操作结果
     */
    suspend fun setMinBboxArea(value: Double): SdkResult<ResultDto>

    /**
     * 设置最小高度。
     *
     * @param value 高度
     * @return 操作结果
     */
    suspend fun setMinHeight(value: Double): SdkResult<ResultDto>

    /**
     * 设置稳定性阈值。
     *
     * @param value 阈值
     * @return 操作结果
     */
    suspend fun setStabilityThreshold(value: Double): SdkResult<ResultDto>

    /**
     * 设置人体跟踪得分阈值。
     *
     * @param value 阈值
     * @return 操作结果
     */
    suspend fun setBodyScoreTrackingThreshold(value: Double): SdkResult<ResultDto>

    /**
     * 设置俯仰角（Pitch）阈值。
     *
     * @param value 阈值
     * @return 操作结果
     */
    suspend fun setPitchThreshold(value: Int): SdkResult<ResultDto>

    /**
     * 设置偏航角（Yaw）阈值。
     *
     * @param value 阈值
     * @return 操作结果
     */
    suspend fun setYawThreshold(value: Int): SdkResult<ResultDto>

    /**
     * 设置推理启用开关。
     *
     * @param value 是否启用
     * @return 操作结果
     */
    suspend fun setInferenceEnabled(value: Boolean): SdkResult<ResultDto>
}
