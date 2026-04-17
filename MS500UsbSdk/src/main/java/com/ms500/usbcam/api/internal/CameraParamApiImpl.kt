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
 * 相机业务实现。
 *
 * 这一层把“相机参数”抽象成 `CameraParamApi`：
 * - 对上暴露类型安全的 `CameraParamDto`
 * - 对下统一转成 CDC `sendCommand`
 * - 负责把设备返回的原始 JSON 映射成 `SdkResult`
 *
 * 这样上层页面和 facade 不需要了解具体 CDC 命令名或 JSON 字段名。
 */
internal class CameraParamApiImpl(
    private val connection: CdcConnectApiImpl
) : CameraParamApi {

    /** 获取当前相机配置并解析为 DTO。 */
    override suspend fun getParams(): SdkResult<CameraParamDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("get_camera_param") { _, json, error ->
                continuation.resume(parseCdcResult(json, error) { CameraParamDto.fromJson(it) })
            }
        }
    }

    /** 提交完整/部分相机参数。 */
    override suspend fun setParams(params: CameraParamDto): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            val json = params.toSetJson()
            cdcSender.sendCommand("set_camera_param", json) { _, response, error ->
                continuation.resume(parseCdcResultDto(response, error))
            }
        }
    }

    /** 重置固件中的相机参数到默认值。 */
    override suspend fun resetCamera(): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("reset_camera_param") { _, json, error ->
                continuation.resume(parseCdcResultDto(json, error))
            }
        }
    }

    /** 便捷写法：仅设置曝光。 */
    override suspend fun setExposure(value: Int): SdkResult<ResultDto> = setParams(CameraParamDto(exposure = value))

    /** 便捷写法：仅设置增益。 */
    override suspend fun setGain(value: Int): SdkResult<ResultDto> = setParams(CameraParamDto(gain = value))

    /** 便捷写法：仅设置 gamma。 */
    override suspend fun setGamma(value: Int): SdkResult<ResultDto> = setParams(CameraParamDto(gamma = value))

    override suspend fun setWhiteBalance(value: Int): SdkResult<ResultDto> = setParams(CameraParamDto(whiteBalance = value))

    override suspend fun setFrameRate(value: Int): SdkResult<ResultDto> = setParams(CameraParamDto(frameRate = value))

    override suspend fun setHFlip(value: Int): SdkResult<ResultDto> = setParams(CameraParamDto(hflip = value))

    override suspend fun setVFlip(value: Int): SdkResult<ResultDto> = setParams(CameraParamDto(vflip = value))

    /** 从连接层拿到共享 CDC 通道后执行具体业务逻辑。 */
    private inline fun <T> execute(block: (CdcSender) -> SdkResult<T>): SdkResult<T> {
        val cdcSender = connection.getSender() ?: return SdkResult.failure(SdkError.NotConnected)
        return block(cdcSender)
    }

}


