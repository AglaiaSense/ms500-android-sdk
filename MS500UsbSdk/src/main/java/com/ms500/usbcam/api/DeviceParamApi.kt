package com.ms500.usbcam.api

import com.ms500.usbcam.dto.DeviceParamDto
import com.ms500.usbcam.dto.RegisterValueDto
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.result.SdkResult
import org.json.JSONObject

/**
 * Device parameter API for device information, control commands, and register access.
 *
 * This API covers CDC commands that are not parameter-specific:
 * - Device information: `get_device_info`.
 * - Device lifecycle commands: `reboot`, `factory_reset`.
 * - Generic command passthrough through [baseCommand].
 * - Register read/write helpers through [getRegister] and [setRegister].
 *
 * Prefer the named helpers when they exist. [baseCommand] is intended for advanced protocol testing or
 * newly added firmware commands that do not yet have a typed SDK method.
 */
interface DeviceParamApi {

    /**
     * Gets device information.
     *
     * Sends `{"cmd":"get_device_info"}` and parses firmware/model/device identity fields, device uptime,
     * and the encrypted device token into [DeviceParamDto]. [DeviceParamDto.uptimeUs] uses the device's
     * microsecond boot-time clock and can be used to align wall-clock time with gaze `timestamp_us`,
     * `first_seen_ms`, and `last_seen_ms`. It resets when the device restarts. The `token` field is
     * `get_device_info.result.token` from firmware and may be blank when NVS has no token.
     *
     * @return Result containing current device information.
     */
    suspend fun getInfo(): SdkResult<DeviceParamDto>

    /**
     * Reboots the device.
     *
     * Sends the typed `reboot` command. The CDC connection may disconnect while the device restarts;
     * callers should observe [CdcConnectApi.stateFlow] or reconnect after the device is ready.
     *
     * @return Operation result returned before the device restarts.
     */
    suspend fun reboot(): SdkResult<ResultDto>

    /**
     * Restores factory defaults.
     *
     * Sends the typed `factory_reset` command. This is intentionally exposed as a named method so UI and
     * automation code do not have to call [baseCommand] for a destructive operation.
     *
     * @return Operation result returned by the device.
     */
    suspend fun factoryReset(): SdkResult<ResultDto>

    /**
     * Sends a generic CDC command.
     *
     * The SDK builds a JSON object with `cmd` and all fields from [payload], then sends it through the
     * same CDC request/response path as typed APIs. This method can be used for any command and parameter
     * combination, but typed APIs should be preferred for stable product flows because they document the
     * expected DTO and protocol field names.
     *
     * @param cmd Command string, for example `get_device_info` or a firmware test command.
     * @param payload JSON payload attached to the command, defaults to an empty object.
     * @return Raw JSON result returned by the device after CDC response validation.
     */
    suspend fun baseCommand(
        cmd: String,
        payload: JSONObject = JSONObject()
    ): SdkResult<JSONObject>

    /**
     * Reads a device register.
     *
     * Sends the register read protocol using [addr] and [size]. The response is parsed into
     * [RegisterValueDto], including numeric and hexadecimal representations when provided by the device.
     *
     * @param addr Register address.
     * @param size Read length in bytes.
     * @return Result containing the register value.
     */
    suspend fun getRegister(addr: Int, size: Int): SdkResult<RegisterValueDto>

    /**
     * Writes a device register.
     *
     * Sends the register write protocol using [addr], [value], and [size]. This is the typed replacement
     * for manually building register JSON through [baseCommand].
     *
     * @param addr Register address.
     * @param value Value to write.
     * @param size Write length in bytes.
     * @return Operation result returned by the device.
     */
    suspend fun setRegister(addr: Int, value: Int, size: Int): SdkResult<ResultDto>
}
