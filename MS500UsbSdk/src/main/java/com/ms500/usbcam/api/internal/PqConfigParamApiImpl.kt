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

internal class PqConfigParamApiImpl(
    private val connection: CdcConnectApiImpl
) : PqConfigParamApi {

    override suspend fun getPqFixConfig(): SdkResult<PqcongifParam> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand(
                "get_pq_settings_param"
            ) { _, json, error ->
                continuation.resume(parseCdcResult(json, error) { PqcongifParam.fromJson(it) })
            }
        }
    }

    override suspend fun setPqFixConfig(config: PqcongifParam): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("set_pq_settings_param", config.toSetJson()) { _, json, error ->
                continuation.resume(parseCdcResultDto(json, error))
            }
        }
    }

    override suspend fun resetPqFixConfig(): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("reset_pq_settings_param") { _, json, error ->
                continuation.resume(parseCdcResultDto(json, error))
            }
        }
    }

    override suspend fun setCameraImageSize(value: PqConfigCameraImageSize): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(cameraImageSize = value)))

    override suspend fun setExposureMode(value: Int): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(exposureMode = value)))

    override suspend fun setManualExposure(value: PqConfigManualExposure): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(manualExposure = value)))

    override suspend fun setManualExposureTime(value: Int): SdkResult<ResultDto> =
        setManualExposure(PqConfigManualExposure(exposureTime = value))

    override suspend fun setManualGain(value: Int): SdkResult<ResultDto> =
        setManualExposure(PqConfigManualExposure(gain = value))

    override suspend fun setWhiteBalanceMode(value: Int): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(whiteBalanceMode = value)))

    override suspend fun setManualWhiteBalancePreset(value: PqConfigManualWhiteBalancePreset): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(manualWhiteBalancePreset = value)))

    override suspend fun setManualWhiteBalanceColorTemperature(value: Int): SdkResult<ResultDto> =
        setManualWhiteBalancePreset(PqConfigManualWhiteBalancePreset(colorTemperature = value))

    override suspend fun setEvCompensation(value: Int): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(evCompensation = value)))

    override suspend fun setAeAntiFlickerMode(value: Int): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(aeAntiFlickerMode = value)))

    override suspend fun setImageCropping(value: PqConfigImageCropping): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(imageCropping = value)))

    private inline fun <T> execute(block: (CdcSender) -> SdkResult<T>): SdkResult<T> {
        val cdcSender = connection.getSender() ?: return SdkResult.failure(SdkError.NotConnected)
        return block(cdcSender)
    }

}
