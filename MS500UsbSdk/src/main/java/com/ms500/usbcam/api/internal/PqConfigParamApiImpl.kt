package com.ms500.usbcam.api.internal

import com.ms500.usbcam.api.PqConfigParamApi
import com.ms500.usbcam.cdc.CdcSender
import com.ms500.usbcam.dto.PqConfigCameraImageSize
import com.ms500.usbcam.dto.PqConfigImageCropping
import com.ms500.usbcam.dto.PqConfigManualExposure
import com.ms500.usbcam.dto.PqConfigManualWhiteBalancePreset
import com.ms500.usbcam.dto.PqConfigSettings
import com.ms500.usbcam.dto.PqcongifParam
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.result.SdkError
import com.ms500.usbcam.result.SdkResult
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * PQ（图像质量）配置参数 API 内部实现类。
 *
 * 负责将 PQ 设置（曝光模式、白平衡、裁剪、图像尺寸等）的读写操作转换为 CDC 命令，
 * 并解析设备返回的 JSON。
 *
 * Internal implementation of PQ configuration parameter APIs.
 * Translates PQ settings (exposure mode, white balance, cropping, image size, etc.) into CDC commands.
 */
internal class PqConfigParamApiImpl(
    /** CDC 连接实现，用于获取共享的 CdcSender。 */
    private val connection: CdcConnectApiImpl
) : PqConfigParamApi {

    /**
     * 获取当前 PQ 配置参数。
     * Retrieves the current PQ configuration parameters.
     */
    override suspend fun getPqFixConfig(): SdkResult<PqcongifParam> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand(
                "get_pq_settings_param"
            ) { _, json, error ->
                continuation.resume(parseCdcResult(json, error) { PqcongifParam.fromJson(it) })
            }
        }
    }

    /**
     * 设置 PQ 配置参数。
     * Sets the PQ configuration parameters.
     */
    override suspend fun setPqFixConfig(config: PqcongifParam): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("set_pq_settings_param", config.toSetJson()) { _, json, error ->
                continuation.resume(parseCdcResultDto(json, error))
            }
        }
    }

    /**
     * 重置 PQ 配置参数为设备默认值。
     * Resets PQ configuration parameters to device defaults.
     */
    override suspend fun resetPqFixConfig(): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("reset_pq_settings_param") { _, json, error ->
                continuation.resume(parseCdcResultDto(json, error))
            }
        }
    }

    /**
     * 仅设置相机图像尺寸。
     * Sets camera image size only.
     */
    override suspend fun setCameraImageSize(value: PqConfigCameraImageSize): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(cameraImageSize = value)))

    /**
     * 仅设置曝光模式。
     * Sets exposure mode only.
     */
    override suspend fun setExposureMode(value: Int): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(exposureMode = value)))

    /**
     * 仅设置手动曝光参数。
     * Sets manual exposure parameters only.
     */
    override suspend fun setManualExposure(value: PqConfigManualExposure): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(manualExposure = value)))

    /**
     * 仅设置手动曝光时间。
     * Sets manual exposure time only.
     */
    override suspend fun setManualExposureTime(value: Int): SdkResult<ResultDto> =
        setManualExposure(PqConfigManualExposure(exposureTime = value))

    /**
     * 仅设置手动增益。
     * Sets manual gain only.
     */
    override suspend fun setManualGain(value: Int): SdkResult<ResultDto> =
        setManualExposure(PqConfigManualExposure(gain = value))

    /**
     * 仅设置白平衡模式。
     * Sets white balance mode only.
     */
    override suspend fun setWhiteBalanceMode(value: Int): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(whiteBalanceMode = value)))

    /**
     * 仅设置手动白平衡预设。
     * Sets manual white balance preset only.
     */
    override suspend fun setManualWhiteBalancePreset(value: PqConfigManualWhiteBalancePreset): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(manualWhiteBalancePreset = value)))

    /**
     * 仅设置手动白平衡色温。
     * Sets manual white balance color temperature only.
     */
    override suspend fun setManualWhiteBalanceColorTemperature(value: Int): SdkResult<ResultDto> =
        setManualWhiteBalancePreset(PqConfigManualWhiteBalancePreset(colorTemperature = value))

    /**
     * 仅设置曝光补偿。
     * Sets EV compensation only.
     */
    override suspend fun setEvCompensation(value: Int): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(evCompensation = value)))

    /**
     * 仅设置 AE 抗闪烁模式。
     * Sets AE anti-flicker mode only.
     */
    override suspend fun setAeAntiFlickerMode(value: Int): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(aeAntiFlickerMode = value)))

    /**
     * 仅设置图像裁剪参数。
     * Sets image cropping parameters only.
     */
    override suspend fun setImageCropping(value: PqConfigImageCropping): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(imageCropping = value)))

    /**
     * 内部执行模板：获取 CdcSender 后执行 block，未连接时返回失败。
     * Internal execution helper: runs block with a valid sender or returns NotConnected.
     */
    private inline fun <T> execute(block: (CdcSender) -> SdkResult<T>): SdkResult<T> {
        val cdcSender = connection.getSender() ?: return SdkResult.failure(SdkError.NotConnected)
        return block(cdcSender)
    }

}
