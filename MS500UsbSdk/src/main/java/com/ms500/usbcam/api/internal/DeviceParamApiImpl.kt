/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
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
 * Internal implementation of device parameter APIs.
 * Covers device-level commands such as info retrieval, reboot, and register read/write.
 */
internal class DeviceParamApiImpl(
    /** CDC connection implementation, used to obtain shared CdcSender. */
    private val connection: CdcConnectApiImpl
) : DeviceParamApi {

    /**
     * Sends a custom base command and returns the raw JSON response.
     */
    override suspend fun baseCommand(
        cmd: String,
        payload: JSONObject
    ): SdkResult<JSONObject> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand(cmd, payload) { raw, response, error ->
                continuation.resume(parseCdcResult(raw, response, error) { it })
            }
        }
    }

    /**
     * Retrieves device information such as version, project name, and sensor ID.
     */
    override suspend fun getInfo(): SdkResult<DeviceParamDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("get_device_info") { raw, json, error ->
                continuation.resume(parseCdcResult(raw, json, error) { DeviceParamDto.fromJson(it) })
            }
        }
    }

    /**
     * Requests the device to reboot.
     */
    override suspend fun reboot(): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("reboot") { raw, json, error ->
                continuation.resume(parseCdcResult(raw, json, error) { ResultDto.fromJson(it) })
            }
        }
    }

    /**
     * Restores factory defaults.
     */
    override suspend fun factoryReset(): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("factory_reset") { raw, json, error ->
                continuation.resume(parseCdcResult(raw, json, error) { ResultDto.fromJson(it) })
            }
        }
    }
    /**
     * Reads a register value.
     */
    override suspend fun getRegister(addr: Int, size: Int): SdkResult<RegisterValueDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.getRegister(addr, size) { raw, json, error ->
                continuation.resume(parseCdcResult(raw, json, error) { RegisterValueDto.fromJson(it, addr, size) })
            }
        }
    }

    /**
     * Writes a register value.
     */
    override suspend fun setRegister(addr: Int, value: Int, size: Int): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.setRegister(addr, value, size) { raw, json, error ->
                continuation.resume(parseCdcResult(raw, json, error) { ResultDto.fromJson(it) })
            }
        }
    }
    /**
     * Internal execution helper: runs block with a valid sender or returns NotConnected.
     */
    private inline fun <T> execute(block: (CdcSender) -> SdkResult<T>): SdkResult<T> {
        val cdcSender = connection.getSender() ?: return SdkResult.failure(SdkError.NotConnected)
        return block(cdcSender)
    }

}
