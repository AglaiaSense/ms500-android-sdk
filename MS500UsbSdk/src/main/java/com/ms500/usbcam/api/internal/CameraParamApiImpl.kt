package com.ms500.usbcam.api.internal

import com.ms500.usbcam.api.CameraParamApi
import com.ms500.usbcam.cdc.CdcSender
import com.ms500.usbcam.dto.CameraParamDto
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.result.SdkResult
import com.ms500.usbcam.result.SdkError
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Internal implementation of camera parameter APIs.
 * Translates high-level camera operations into CDC commands and parses JSON responses into typed DTOs.
 */
internal class CameraParamApiImpl(
    /** CDC connection implementation, used to obtain shared CdcSender. */
    private val connection: CdcConnectApiImpl
) : CameraParamApi {

    /**
     * Retrieves the current camera parameters.
     */
    override suspend fun getParams(): SdkResult<CameraParamDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("get_camera_param") { _, json, error ->
                continuation.resume(parseCdcResult(json, error) { CameraParamDto.fromJson(it) })
            }
        }
    }

    /**
     * Sets camera parameters (full or partial).
     */
    override suspend fun setParams(params: CameraParamDto): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            val json = params.toSetJson()
            cdcSender.sendCommand("set_camera_param", json) { _, response, error ->
                continuation.resume(parseCdcResultDto(response, error))
            }
        }
    }

    /**
     * Resets camera parameters to device defaults.
     */
    override suspend fun resetCamera(): SdkResult<ResultDto> = execute { cdcSender ->
        suspendCancellableCoroutine { continuation ->
            cdcSender.sendCommand("reset_camera_param") { _, json, error ->
                continuation.resume(parseCdcResultDto(json, error))
            }
        }
    }

    /**
     * Sets gamma only.
     */
    override suspend fun setGamma(value: Int): SdkResult<ResultDto> = setParams(CameraParamDto(gamma = value))

    /**
     * Sets frame rate only.
     */
    override suspend fun setFrameRate(value: Int): SdkResult<ResultDto> = setParams(CameraParamDto(frameRate = value))

    /**
     * Sets horizontal flip only.
     */
    override suspend fun setHFlip(value: Int): SdkResult<ResultDto> = setParams(CameraParamDto(hflip = value))

    /**
     * Sets vertical flip only.
     */
    override suspend fun setVFlip(value: Int): SdkResult<ResultDto> = setParams(CameraParamDto(vflip = value))

    /**
     * Internal execution helper: runs block with a valid sender or returns NotConnected.
     */
    private inline fun <T> execute(block: (CdcSender) -> SdkResult<T>): SdkResult<T> {
        val cdcSender = connection.getSender() ?: return SdkResult.failure(SdkError.NotConnected)
        return block(cdcSender)
    }

}
