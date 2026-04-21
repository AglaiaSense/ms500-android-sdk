package com.ms500.usbcam.api

import com.ms500.usbcam.dto.DeviceParamDto
import com.ms500.usbcam.dto.RegisterValueDto
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.result.SdkResult
import org.json.JSONObject

/**
 * Device parameters API for info, commands, and register access.
 */
interface DeviceParamApi {

    /**
     * Gets device information.
     *
     * @return Result containing [DeviceParamDto]
     */
    suspend fun getInfo(): SdkResult<DeviceParamDto>

    /**
     * Reboots the device.
     *
     * @return Operation result
     */
    suspend fun reboot(): SdkResult<ResultDto>

    /**
     * Sends a base command.
     *
     * @param cmd Command string
     * @param payload JSON payload attached to the command, defaults to empty object
     * @return JSON result returned by the device
     */
    suspend fun baseCommand(
        cmd: String,
        payload: JSONObject = JSONObject()
    ): SdkResult<JSONObject>

    /**
     * Reads register.
     *
     * @param addr Register address
     * @param size Read length
     * @return Result containing [RegisterValueDto]
     */
    suspend fun getRegister(addr: Int, size: Int): SdkResult<RegisterValueDto>

    /**
     * Writes register.
     *
     * @param addr Register address
     * @param value Value to write
     * @param size Write length
     * @return Operation result
     */
    suspend fun setRegister(addr: Int, value: Int, size: Int): SdkResult<ResultDto>
}
