package com.ms500.usbcam.api

import com.ms500.usbcam.dto.PqConfigCameraImageFlip
import com.ms500.usbcam.dto.PqConfigCameraImageSize
import com.ms500.usbcam.dto.PqConfigAutoExposure
import com.ms500.usbcam.dto.PqConfigAutoExposureMetering
import com.ms500.usbcam.dto.PqConfigAutoWhiteBalance
import com.ms500.usbcam.dto.PqConfigCodecSettings
import com.ms500.usbcam.dto.PqConfigImageCropping
import com.ms500.usbcam.dto.PqConfigManualExposure
import com.ms500.usbcam.dto.PqConfigManualWhiteBalancePreset
import com.ms500.usbcam.dto.PqConfigMetadata
import com.ms500.usbcam.dto.PqConfigPortSettings
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.dto.PqcongifParam
import com.ms500.usbcam.result.SdkResult

/**
 * Picture Quality (PQ) configuration API for image quality settings.
 */
interface PqConfigParamApi {

    /**
     * Gets current PQ fixed configuration.
     *
     * @return Result containing [PqcongifParam]
     */
    suspend fun getPqFixConfig(): SdkResult<PqcongifParam>

    /**
     * Sets PQ fixed configuration.
     *
     * @param config PQ configuration
     * @return Operation result
     */
    suspend fun setPqFixConfig(config: PqcongifParam): SdkResult<ResultDto>

    /**
     * Resets PQ fixed configuration to default values.
     *
     * @return Operation result
     */
    suspend fun resetPqFixConfig(): SdkResult<ResultDto>

    /**
     * Sets camera image size.
     *
     * @param value Image size configuration
     * @return Operation result
     */
    suspend fun setCameraImageSize(value: PqConfigCameraImageSize): SdkResult<ResultDto>

    /**
     * Sets camera image flip.
     *
     * @param value Image flip configuration
     * @return Operation result
     */
    suspend fun setCameraImageFlip(value: PqConfigCameraImageFlip): SdkResult<ResultDto>

    /**
     * Switches to auto exposure mode.
     *
     * @return Operation result
     */
    suspend fun setAutoExposureMode(): SdkResult<ResultDto>

    /**
     * Switches to manual exposure mode with required manual exposure parameters.
     *
     * @param value Manual exposure configuration
     * @return Operation result
     */
    suspend fun setManualExposureMode(value: PqConfigManualExposure): SdkResult<ResultDto>

    /**
     * Sets auto exposure parameters.
     *
     * @param value Auto exposure configuration
     * @return Operation result
     */
    suspend fun setAutoExposure(value: PqConfigAutoExposure): SdkResult<ResultDto>

    /**
     * Sets auto exposure metering parameters.
     *
     * @param value Auto exposure metering configuration
     * @return Operation result
     */
    suspend fun setAutoExposureMetering(value: PqConfigAutoExposureMetering): SdkResult<ResultDto>

    /**
     * Sets manual exposure parameters.
     *
     * @param value Manual exposure configuration
     * @return Operation result
     */
    suspend fun setManualExposure(value: PqConfigManualExposure): SdkResult<ResultDto>

    /**
     * Sets manual exposure time.
     *
     * @param value Exposure time value
     * @return Operation result
     */
    suspend fun setManualExposureTime(value: Int): SdkResult<ResultDto>

    /**
     * Sets manual gain.
     *
     * @param value Gain value
     * @return Operation result
     */
    suspend fun setManualGain(value: Int): SdkResult<ResultDto>

    /**
     * Sets white balance mode.
     *
     * @param value Mode value
     * @return Operation result
     */
    suspend fun setWhiteBalanceMode(value: Int): SdkResult<ResultDto>

    /**
     * Sets auto white balance parameters.
     *
     * @param value Auto white balance configuration
     * @return Operation result
     */
    suspend fun setAutoWhiteBalance(value: PqConfigAutoWhiteBalance): SdkResult<ResultDto>

    /**
     * Sets manual white balance preset.
     *
     * @param value White balance preset
     * @return Operation result
     */
    suspend fun setManualWhiteBalancePreset(value: PqConfigManualWhiteBalancePreset): SdkResult<ResultDto>

    /**
     * Sets manual white balance color temperature.
     *
     * @param value Color temperature value
     * @return Operation result
     */
    suspend fun setManualWhiteBalanceColorTemperature(value: Int): SdkResult<ResultDto>

    /**
     * Sets exposure compensation (EV).
     *
     * @param value Compensation value
     * @return Operation result
     */
    suspend fun setEvCompensation(value: Int): SdkResult<ResultDto>

    /**
     * Sets AE anti-flicker mode.
     *
     * @param value Mode value
     * @return Operation result
     */
    suspend fun setAeAntiFlickerMode(value: Int): SdkResult<ResultDto>

    /**
     * Sets image cropping parameters.
     *
     * @param value Cropping configuration
     * @return Operation result
     */
    suspend fun setImageCropping(value: PqConfigImageCropping): SdkResult<ResultDto>

    /**
     * Sets port settings.
     *
     * @param value Port settings configuration
     * @return Operation result
     */
    suspend fun setPortSettings(value: PqConfigPortSettings): SdkResult<ResultDto>

    /**
     * Sets metadata port settings only.
     *
     * @param value Metadata configuration
     * @return Operation result
     */
    suspend fun setMetadata(value: PqConfigMetadata): SdkResult<ResultDto>

    /**
     * Sets codec settings.
     *
     * @param value Codec configuration
     * @return Operation result
     */
    suspend fun setCodecSettings(value: PqConfigCodecSettings): SdkResult<ResultDto>
}
