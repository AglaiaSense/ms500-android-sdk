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
 * 设备参数 API 内部实现类。
 *
 * 覆盖固件已提供的设备参数类命令，如获取设备信息、重启、寄存器读写等，
 * 对上层隐藏 CDC 细节，返回类型安全的 DTO。
 *
 * Internal implementation of device parameter APIs.
 * Covers device-level commands such as info retrieval, reboot, and register read/write.
 */
internal class DeviceParamApiImpl(
    /** CDC 连接实现，用于获取共享的 CdcSender。 */
    private val connection: CdcConnectApiImpl
) : DeviceParamApi {

    /**
     * 发送自定义基础命令并返回原始 JSON。
     * Sends a custom base command and returns the raw JSON response.
     */
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

    /**
     * 获取设备信息（版本、项目名、sensor id 等）。
     * Retrieves device information such as version, project name, and sensor ID.
     */
    override suspend fun getInfo(): SdkResult<DeviceParamDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("get_device_info") { _, json, error ->
                continuation.resume(parseCdcResult(json, error) { DeviceParamDto.fromJson(it) })
            }
        }
    }

    /**
     * 请求设备重启。
     * Requests the device to reboot.
     */
    override suspend fun reboot(): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("reboot") { _, json, error ->
                continuation.resume(parseCdcResultDto(json, error))
            }
        }
    }

    /**
     * 读取寄存器值。
     * Reads a register value.
     */
    override suspend fun getRegister(addr: Int, size: Int): SdkResult<RegisterValueDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.getRegister(addr, size) { _, json, error ->
                continuation.resume(parseCdcResult(json, error) { RegisterValueDto.fromJson(it, addr, size) })
            }
        }
    }

    /**
     * 写入寄存器值。
     * Writes a register value.
     */
    override suspend fun setRegister(addr: Int, value: Int, size: Int): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.setRegister(addr, value, size) { _, json, error ->
                continuation.resume(parseCdcResultDto(json, error))
            }
        }
    }

    /**
     * 内部执行模板：获取 CdcSender 后执行 block，未连接时返回失败。
     * Internal execution helper: runs block with a valid sender or returns NotConnected.
     */
    private inline fun <T> execute(block: (CdcSender) -> SdkResult<T>): SdkResult<T> {
        val cdcSender = connection.getSender() ?: return SdkResult.failure(SdkError.NotConnected)
        return block(cdcSender)
    }

}
