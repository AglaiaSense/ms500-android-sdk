package com.ms500.usbcam.api.internal

import com.ms500.usbcam.api.CameraParamApi
import com.ms500.usbcam.cdc.CdcSender
import com.ms500.usbcam.dto.CameraParamDto
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.result.SdkResult
import com.ms500.usbcam.result.SdkError
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 相机参数 API 内部实现类。
 *
 * 负责将 `CameraParamApi` 定义的相机参数操作转换为底层 CDC 命令，
 * 并将设备返回的原始 JSON 解析为类型安全的 DTO。
 *
 * Internal implementation of camera parameter APIs.
 * Translates high-level camera operations into CDC commands and parses JSON responses into typed DTOs.
 */
internal class CameraParamApiImpl(
    /** CDC 连接实现，用于获取共享的 CdcSender。 */
    private val connection: CdcConnectApiImpl
) : CameraParamApi {

    /**
     * 获取当前相机参数。
     * Retrieves the current camera parameters.
     */
    override suspend fun getParams(): SdkResult<CameraParamDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("get_camera_param") { _, json, error ->
                continuation.resume(parseCdcResult(json, error) { CameraParamDto.fromJson(it) })
            }
        }
    }

    /**
     * 设置相机参数（支持完整或部分参数）。
     * Sets camera parameters (full or partial).
     */
    override suspend fun setParams(params: CameraParamDto): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            val json = params.toSetJson()
            cdcSender.sendCommand("set_camera_param", json) { _, response, error ->
                continuation.resume(parseCdcResultDto(response, error))
            }
        }
    }

    /**
     * 将相机参数重置为设备默认值。
     * Resets camera parameters to device defaults.
     */
    override suspend fun resetCamera(): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("reset_camera_param") { _, json, error ->
                continuation.resume(parseCdcResultDto(json, error))
            }
        }
    }

    /**
     * 仅设置曝光值。
     * Sets exposure only.
     */
    override suspend fun setExposure(value: Int): SdkResult<ResultDto> = setParams(CameraParamDto(exposure = value))

    /**
     * 仅设置增益值。
     * Sets gain only.
     */
    override suspend fun setGain(value: Int): SdkResult<ResultDto> = setParams(CameraParamDto(gain = value))

    /**
     * 仅设置 gamma 值。
     * Sets gamma only.
     */
    override suspend fun setGamma(value: Int): SdkResult<ResultDto> = setParams(CameraParamDto(gamma = value))

    /**
     * 仅设置白平衡值。
     * Sets white balance only.
     */
    override suspend fun setWhiteBalance(value: Int): SdkResult<ResultDto> = setParams(CameraParamDto(whiteBalance = value))

    /**
     * 仅设置帧率。
     * Sets frame rate only.
     */
    override suspend fun setFrameRate(value: Int): SdkResult<ResultDto> = setParams(CameraParamDto(frameRate = value))

    /**
     * 仅设置水平翻转。
     * Sets horizontal flip only.
     */
    override suspend fun setHFlip(value: Int): SdkResult<ResultDto> = setParams(CameraParamDto(hflip = value))

    /**
     * 仅设置垂直翻转。
     * Sets vertical flip only.
     */
    override suspend fun setVFlip(value: Int): SdkResult<ResultDto> = setParams(CameraParamDto(vflip = value))

    /**
     * 内部执行模板：获取 CdcSender 后执行 block，未连接时返回失败。
     * Internal execution helper: runs block with a valid sender or returns NotConnected.
     */
    private inline fun <T> execute(block: (CdcSender) -> SdkResult<T>): SdkResult<T> {
        val cdcSender = connection.getSender() ?: return SdkResult.failure(SdkError.NotConnected)
        return block(cdcSender)
    }

}
