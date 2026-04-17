package com.ms500.usbcam.api.internal

import com.ms500.usbcam.api.DeviceParamApi
import com.ms500.usbcam.cdc.CdcSender
import com.ms500.usbcam.dto.DeviceParamDto
import com.ms500.usbcam.dto.RegisterValueDto
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.result.SdkResult
import com.ms500.usbcam.result.SdkError
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * 设备业务实现。
 *
 * 当前覆盖固件已经稳定提供的设备参数类命令：
 * - `get_device_info`
 * - `reboot`
 * - `reg_get`
 * - `reg_set`
 *
 * 这里不暴露 CDC 细节，只返回业务层需要的 DTO / 基本类型。
 */
internal class DeviceParamApiImpl(
    private val connection: CdcConnectApiImpl
) : DeviceParamApi {

    override suspend fun baseCommand(
        cmd: String,
        payload: JSONObject
    ): SdkResult<JSONObject> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand(cmd, payload) { _, response, error ->
                continuation.resume(parseCdcResult(response, error) { it })
            }
        }
    }

    /** 获取设备版本、项目名、sensor id 等信息。 */
    override suspend fun getInfo(): SdkResult<DeviceParamDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("get_device_info") { _, json, error ->
                continuation.resume(parseCdcResult(json, error) { DeviceParamDto.fromJson(it) })
            }
        }
    }

    /** 请求设备重启。 */
    override suspend fun reboot(): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("reboot") { _, json, error ->
                continuation.resume(parseCdcResultDto(json, error))
            }
        }
    }

    override suspend fun getRegister(addr: Int, size: Int): SdkResult<RegisterValueDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.getRegister(addr, size) { _, json, error ->
                continuation.resume(parseCdcResult(json, error) { RegisterValueDto.fromJson(it, addr, size) })
            }
        }
    }

    override suspend fun setRegister(addr: Int, value: Int, size: Int): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.setRegister(addr, value, size) { _, json, error ->
                continuation.resume(parseCdcResultDto(json, error))
            }
        }
    }

    private inline fun <T> execute(block: (CdcSender) -> SdkResult<T>): SdkResult<T> {
        val cdcSender = connection.getSender() ?: return SdkResult.failure(SdkError.NotConnected)
        return block(cdcSender)
    }

}



