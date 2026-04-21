package com.ms500.usbcam.api

import com.ms500.usbcam.dto.PqConfigCameraImageSize
import com.ms500.usbcam.dto.PqConfigImageCropping
import com.ms500.usbcam.dto.PqConfigManualExposure
import com.ms500.usbcam.dto.PqConfigManualWhiteBalancePreset
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
     * Sets exposure mode.
     *
     * @param value Mode value
     * @return Operation result
     */
    suspend fun setExposureMode(value: Int): SdkResult<ResultDto>

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
}
