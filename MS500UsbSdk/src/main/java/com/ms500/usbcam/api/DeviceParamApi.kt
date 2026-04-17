package com.ms500.usbcam.api

import com.ms500.usbcam.dto.DeviceParamDto
import com.ms500.usbcam.dto.RegisterValueDto
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.result.SdkResult
import org.json.JSONObject

interface DeviceParamApi {
    suspend fun getInfo(): SdkResult<DeviceParamDto>

    suspend fun reboot(): SdkResult<ResultDto>

    suspend fun baseCommand(
        cmd: String,
        payload: JSONObject = JSONObject()
    ): SdkResult<JSONObject>

    suspend fun getRegister(addr: Int, size: Int): SdkResult<RegisterValueDto>

    suspend fun setRegister(addr: Int, value: Int, size: Int): SdkResult<ResultDto>
}

