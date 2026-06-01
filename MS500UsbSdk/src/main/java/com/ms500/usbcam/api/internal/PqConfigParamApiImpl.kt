package com.ms500.usbcam.api.internal

import com.ms500.usbcam.api.PqConfigParamApi
import com.ms500.usbcam.cdc.CdcSender
import com.ms500.usbcam.dto.PqConfigAutoExposure
import com.ms500.usbcam.dto.PqConfigAutoExposureMetering
import com.ms500.usbcam.dto.PqConfigAutoWhiteBalance
import com.ms500.usbcam.dto.PqConfigCameraImageFlip
import com.ms500.usbcam.dto.PqConfigCameraImageSize
import com.ms500.usbcam.dto.PqConfigCodecSettings
import com.ms500.usbcam.dto.PqConfigImageCropping
import com.ms500.usbcam.dto.PqConfigManualExposure
import com.ms500.usbcam.dto.PqConfigManualWhiteBalancePreset
import com.ms500.usbcam.dto.PqConfigMetadata
import com.ms500.usbcam.dto.PqConfigPortSettings
import com.ms500.usbcam.dto.PqConfigSettings
import com.ms500.usbcam.dto.PqcongifParam
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.result.SdkError
import com.ms500.usbcam.result.SdkResult
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Internal implementation of PQ configuration parameter APIs.
 * Translates PQ settings (exposure mode, white balance, cropping, image size, etc.) into CDC commands.
 */
internal class PqConfigParamApiImpl(
    /** CDC connection implementation, used to obtain shared CdcSender. */
    private val connection: CdcConnectApiImpl
) : PqConfigParamApi {

    /**
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
     * Sets camera image size only.
     */
    override suspend fun setCameraImageSize(value: PqConfigCameraImageSize): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(cameraImageSize = value)))

    /**
     * Sets camera image flip only.
     */
    override suspend fun setCameraImageFlip(value: PqConfigCameraImageFlip): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(cameraImageFlip = value)))

    /**
     * Switches to auto exposure mode.
     */
    override suspend fun setAutoExposureMode(): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(exposureMode = 0)))

    /**
     * Switches to manual exposure mode with required manual exposure parameters.
     */
    override suspend fun setManualExposureMode(value: PqConfigManualExposure): SdkResult<ResultDto> =
        setPqFixConfig(
            PqcongifParam(
                pqSettings = PqConfigSettings(exposureMode = 1, manualExposure = value)
            )
        )

    /**
     * Sets auto exposure parameters only.
     */
    override suspend fun setAutoExposure(value: PqConfigAutoExposure): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(autoExposure = value)))

    /**
     * Sets auto exposure metering parameters only.
     */
    override suspend fun setAutoExposureMetering(value: PqConfigAutoExposureMetering): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(autoExposureMetering = value)))

    /**
     * Sets manual exposure parameters only.
     */
    override suspend fun setManualExposure(value: PqConfigManualExposure): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(manualExposure = value)))

    /**
     * Sets manual exposure time only.
     */
    override suspend fun setManualExposureTime(value: Int): SdkResult<ResultDto> =
        setManualExposure(PqConfigManualExposure(exposureTime = value))

    /**
     * Sets manual gain only.
     */
    override suspend fun setManualGain(value: Int): SdkResult<ResultDto> =
        setManualExposure(PqConfigManualExposure(gain = value))

    /**
     * Sets white balance mode only.
     */
    override suspend fun setWhiteBalanceMode(value: Int): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(whiteBalanceMode = value)))

    /**
     * Sets auto white balance parameters only.
     */
    override suspend fun setAutoWhiteBalance(value: PqConfigAutoWhiteBalance): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(autoWhiteBalance = value)))

    /**
     * Sets manual white balance preset only.
     */
    override suspend fun setManualWhiteBalancePreset(value: PqConfigManualWhiteBalancePreset): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(manualWhiteBalancePreset = value)))

    /**
     * Sets manual white balance color temperature only.
     */
    override suspend fun setManualWhiteBalanceColorTemperature(value: Int): SdkResult<ResultDto> =
        setManualWhiteBalancePreset(PqConfigManualWhiteBalancePreset(colorTemperature = value))

    /**
     * Sets EV compensation only.
     */
    override suspend fun setEvCompensation(value: Int): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(evCompensation = value)))

    /**
     * Sets AE anti-flicker mode only.
     */
    override suspend fun setAeAntiFlickerMode(value: Int): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(aeAntiFlickerMode = value)))

    /**
     * Sets image cropping parameters only.
     */
    override suspend fun setImageCropping(value: PqConfigImageCropping): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(pqSettings = PqConfigSettings(imageCropping = value)))

    /**
     * Sets port settings only.
     */
    override suspend fun setPortSettings(value: PqConfigPortSettings): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(portSettings = value))

    /**
     * Sets metadata settings only.
     */
    override suspend fun setMetadata(value: PqConfigMetadata): SdkResult<ResultDto> =
        setPortSettings(PqConfigPortSettings(metadata = value))

    /**
     * Sets codec settings only.
     */
    override suspend fun setCodecSettings(value: PqConfigCodecSettings): SdkResult<ResultDto> =
        setPqFixConfig(PqcongifParam(codecSettings = value))

    /**
     * Internal execution helper: runs block with a valid sender or returns NotConnected.
     */
    private inline fun <T> execute(block: (CdcSender) -> SdkResult<T>): SdkResult<T> {
        val cdcSender = connection.getSender() ?: return SdkResult.failure(SdkError.NotConnected)
        return block(cdcSender)
    }

}
