package com.ms500.usbcam.api

import com.ms500.usbcam.dto.DeviceParamDto
import com.ms500.usbcam.dto.RegisterValueDto
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.result.SdkResult
import org.json.JSONObject

/**
 * 设备参数 API。
 * 用于获取设备信息、执行基础命令、读写寄存器等操作。
 *
 * Device parameters API for info, commands, and register access.
 */
interface DeviceParamApi {

    /**
     * 获取设备信息。
     *
     * @return 包含 [DeviceParamDto] 的结果
     */
    suspend fun getInfo(): SdkResult<DeviceParamDto>

    /**
     * 重启设备。
     *
     * @return 操作结果
     */
    suspend fun reboot(): SdkResult<ResultDto>

    /**
     * 发送基础命令。
     *
     * @param cmd 命令字符串
     * @param payload 命令附带的 JSON 载荷，默认为空对象
     * @return 设备返回的 JSON 结果
     */
    suspend fun baseCommand(
        cmd: String,
        payload: JSONObject = JSONObject()
    ): SdkResult<JSONObject>

    /**
     * 读取寄存器。
     *
     * @param addr 寄存器地址
     * @param size 读取长度
     * @return 包含 [RegisterValueDto] 的结果
     */
    suspend fun getRegister(addr: Int, size: Int): SdkResult<RegisterValueDto>

    /**
     * 写入寄存器。
     *
     * @param addr 寄存器地址
     * @param value 要写入的值
     * @param size 写入长度
     * @return 操作结果
     */
    suspend fun setRegister(addr: Int, value: Int, size: Int): SdkResult<ResultDto>
}
