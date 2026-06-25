/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.usbcam.api

import com.ms500.usbcam.dto.PqConfigAutoExposure
import com.ms500.usbcam.dto.PqConfigAutoExposureMetering
import com.ms500.usbcam.dto.PqConfigAutoWhiteBalance
import com.ms500.usbcam.dto.PqConfigCameraImageFlip
import com.ms500.usbcam.dto.PqConfigCameraImageSize
import com.ms500.usbcam.dto.PqConfigImageCropping
import com.ms500.usbcam.dto.PqConfigManualExposure
import com.ms500.usbcam.dto.PqConfigManualWhiteBalancePreset
import com.ms500.usbcam.dto.PqcongifParam
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.result.SdkResult

/**
 * Picture Quality (PQ) configuration API for image quality settings.
 *
 * Protocol mapping:
 * - Read: `get_pq_settings_param`, parsed from the response `result` object into [PqcongifParam].
 * - Reset: `reset_pq_settings_param`, restoring ESP32-side PQ defaults.
 * - Write: `set_pq_settings_param`, with writable fields placed under the request `params` object.
 *
 * Setter design follows the same rule as `PostprocessParamApi`: simple scalar setters build the exact
 * protocol JSON directly, while object setters send the whole protocol object. For example,
 * [setAutoExposure] sends `params.auto_exposure={...}`, and [setImageCropping] sends
 * `params.image_cropping={...}`. The SDK does not expose child-field setters such as
 * `setAutoExposureMaxGain(...)`; callers should pass the corresponding DTO object instead.
 *
 * [PqcongifParam] is used only for read parsing and batch writes through [setPqFixConfig]. It maps
 * directly to PQ business fields and does not include response wrapper fields such as `cmd` or `status`.
 * `camera_image_size` is a read-only field: it may appear in read results, but it is blocked from writes.
 */
interface PqConfigParamApi {

    /**
     * Gets current PQ fixed configuration.
     *
     * Sends `{"cmd":"get_pq_settings_param"}`. The CDC parser removes the response wrapper and passes
     * the `result` object to [PqcongifParam.fromJson]. The returned DTO may include read-only
     * `camera_image_size` together with writable PQ objects such as `auto_exposure`, `manual_exposure`,
     * `auto_white_balance`, `image_cropping`, and `camera_image_flip`.
     *
     * @return Result containing the current PQ business fields.
     */
    suspend fun getPqFixConfig(): SdkResult<PqcongifParam>

    /**
     * Resets PQ fixed configuration to protocol default values.
     *
     * Sends `{"cmd":"reset_pq_settings_param"}`. This is a device-side reset and does not require a
     * [PqcongifParam] payload. Use [getPqFixConfig] after reset when the caller needs to display the
     * actual values applied by the ESP32 firmware.
     *
     * @return Operation result returned by the device.
     */
    suspend fun resetPqFixConfig(): SdkResult<ResultDto>

    /**
     * Sets supported PQ fields in one request.
     *
     * This is the batch-setting API. It converts [config] through [PqcongifParam.toSetJson] and sends
     * the resulting object as `set_pq_settings_param.params`. Only non-null writable fields are sent.
     * Object fields keep their protocol shape, for example `auto_exposure={...}` and
     * `camera_image_flip={...}`. `camera_image_size` is read-only and is rejected at the SDK layer.
     *
     * @param config PQ fields to write; omit a field by leaving it null.
     * @return Operation result returned by the device.
     */
    suspend fun setPqFixConfig(config: PqcongifParam): SdkResult<ResultDto>

    /**
     * Attempts to set `camera_image_size`.
     *
     * This field is exposed by `get_pq_settings_param` for read display only. ESP32 currently rejects
     * `params.camera_image_size`, so the SDK returns `SdkError.UnsupportedOperation`
     * without sending a CDC command.
     *
     * Field ranges when read: width 100..10000, height 100..10000, scalingPolicy 0..2.
     *
     * @param value Image size object. It is accepted only to make the unsupported setter explicit.
     * @return Unsupported operation result.
     */
    suspend fun setCameraImageSize(value: PqConfigCameraImageSize): SdkResult<ResultDto>

    /**
     * Sets `camera_image_flip` as one protocol object.
     *
     * Sends `set_pq_settings_param` with `params.camera_image_flip=value.toJson()`, for example
     * `{"flip_horizontal":1,"flip_vertical":0}`. The SDK intentionally sends the object as a whole;
     * there are no separate flip-horizontal or flip-vertical setters.
     *
     * Field ranges/defaults:
     * - flipHorizontal: 0..1; default: 0.
     * - flipVertical: 0..1; default: 0.
     *
     * @param value Image flip configuration.
     * @return Operation result returned by the device.
     */
    suspend fun setCameraImageFlip(value: PqConfigCameraImageFlip): SdkResult<ResultDto>

    /**
     * Sets `exposure_mode` directly.
     *
     * Sends `set_pq_settings_param` with `params.exposure_mode=value`. This method only chooses which
     * exposure mode the device uses; it does not change `auto_exposure` or `manual_exposure` object
     * values. Use [setManualExposure] to update manual exposure parameters.
     *
     * Range: 0..1; default: 0 (auto). Use 0 for auto exposure and 1 for manual exposure.
     *
     * @param value Exposure mode value.
     * @return Operation result returned by the device.
     */
    suspend fun setExposureMode(value: Int): SdkResult<ResultDto>

    /**
     * Sets `auto_exposure` as one protocol object.
     *
     * Sends `params.auto_exposure=value.toJson()`, for example
     * `{"max_exposure_time":30000,"min_exposure_time":160,"max_gain":230,"convergence_speed":32}`.
     * The object is sent at the `auto_exposure` level rather than through child-field setters.
     *
     * Field ranges:
     * - maxExposureTime: 100..33300; default: 30000 us.
     * - minExposureTime: 160..5100; default: 160 us.
     * - maxGain: 0..230; default: 230.
     * - convergenceSpeed: 10..100; default: 32.
     *
     * @param value Auto exposure configuration.
     * @return Operation result returned by the device.
     */
    suspend fun setAutoExposure(value: PqConfigAutoExposure): SdkResult<ResultDto>

    /**
     * Sets `auto_exposure_metering` as one protocol object.
     *
     * Sends `params.auto_exposure_metering=value.toJson()`, preserving the protocol object structure.
     * The metering rectangle values are percentages in the 0..100 range.
     *
     * Field ranges:
     * - meteringMode: 0..3.
     * - top/left/bottom/right: 0..100.
     *
     * @param value Auto exposure metering configuration.
     * @return Operation result returned by the device.
     */
    suspend fun setAutoExposureMetering(value: PqConfigAutoExposureMetering): SdkResult<ResultDto>

    /**
     * Sets `manual_exposure` as one protocol object.
     *
     * Sends `params.manual_exposure=value.toJson()`, for example `{"exposure_time":5000,"gain":32}`.
     * This method only updates manual exposure parameters; call [setExposureMode] with value 1 when the
     * caller also needs the device to use manual exposure mode.
     *
     * Field ranges/defaults: exposureTime 100..33300, default 10000 us; gain 0..978, default 32.
     *
     * @param value Manual exposure configuration.
     * @return Operation result returned by the device.
     */
    suspend fun setManualExposure(value: PqConfigManualExposure): SdkResult<ResultDto>

    /**
     * Sets `white_balance_mode` directly.
     *
     * Sends only `params.white_balance_mode=value`. Use [setAutoWhiteBalance] or
     * [setManualWhiteBalancePreset] to change the object values for the selected mode.
     *
     * Range: 0..1; default: 0 (auto).
     *
     * @param value White-balance mode value, where 0 means auto and 1 means manual preset.
     * @return Operation result returned by the device.
     */
    suspend fun setWhiteBalanceMode(value: Int): SdkResult<ResultDto>

    /**
     * Sets `auto_white_balance` as one protocol object.
     *
     * Sends `params.auto_white_balance=value.toJson()`, for example `{"convergence_speed":6}`.
     * This follows the object-level setter rule: the SDK does not expose a separate
     * `setAutoWhiteBalanceConvergenceSpeed(...)` method.
     *
     * Field range: convergenceSpeed 1..255; default: 2.
     *
     * @param value Auto white balance configuration.
     * @return Operation result returned by the device.
     */
    suspend fun setAutoWhiteBalance(value: PqConfigAutoWhiteBalance): SdkResult<ResultDto>

    /**
     * Sets `manual_white_balance_preset` as one protocol object.
     *
     * Sends `params.manual_white_balance_preset=value.toJson()`, for example
     * `{"color_temperature":6500}`. This method only writes the preset object; call
     * [setWhiteBalanceMode] if the caller also needs to switch to manual white-balance mode.
     *
     * Field range: colorTemperature 2000..10000.
     *
     * @param value Manual white balance preset.
     * @return Operation result returned by the device.
     */
    suspend fun setManualWhiteBalancePreset(value: PqConfigManualWhiteBalancePreset): SdkResult<ResultDto>

    /**
     * Sets exposure compensation (EV).
     *
     * Sends only `params.ev_compensation=value`.
     *
     * Range: -6..6; default: 0.
     *
     * @param value Exposure compensation value.
     * @return Operation result returned by the device.
     */
    suspend fun setEvCompensation(value: Int): SdkResult<ResultDto>

    /**
     * Sets AE anti-flicker mode.
     *
     * Sends only `params.ae_anti_flicker_mode=value`.
     *
     * Range: 0..7; default: 0.
     *
     * @param value Anti-flicker mode value.
     * @return Operation result returned by the device.
     */
    suspend fun setAeAntiFlickerMode(value: Int): SdkResult<ResultDto>

    /**
     * Sets `image_cropping` as one protocol object.
     *
     * Sends `params.image_cropping=value.toJson()`, for example
     * `{"left":0,"top":0,"width":4056,"height":3040}`. The object is sent as a single protocol
     * field, not as separate left/top/width/height setter commands.
     *
     * Field ranges/defaults:
     * - left: 0..4055; default: 0.
     * - top: 0..3039; default: 0.
     * - width: 1..4056; default: 4056.
     * - height: 1..3040; default: 3040.
     *
     * @param value Cropping configuration.
     * @return Operation result returned by the device.
     */
    suspend fun setImageCropping(value: PqConfigImageCropping): SdkResult<ResultDto>
}
