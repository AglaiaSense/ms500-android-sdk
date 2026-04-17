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

import com.ms500.usbcam.dto.PackageVerification
import com.ms500.usbcam.dto.UpgradeProgress
import com.ms500.usbcam.dto.UpgradeState
import com.ms500.usbcam.result.SdkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * OTA 参数 API。
 * 管理固件/模型的升级流程，包括验证、升级、取消及资源清理。
 *
 * OTA parameters API for firmware/model upgrade lifecycle.
 */
interface OtaParamApi {

    /**
     * 升级状态流（StateFlow）。
     */
    val stateFlow: StateFlow<UpgradeState>

    /**
     * 升级进度流（Flow）。
     */
    val progressFlow: Flow<UpgradeProgress>

    /**
     * 验证升级包。
     *
     * @param file 升级包文件
     * @return 包含 [PackageVerification] 的验证结果
     */
    suspend fun verify(file: File): SdkResult<PackageVerification>

    /**
     * 取消当前升级任务。
     */
    fun cancel()

    /**
     * 释放相关资源。
     */
    fun release()

    /**
     * 清理指定临时文件。
     *
     * @param file 要清理的文件
     */
    fun cleanup(file: File)

    /**
     * 清理所有临时文件。
     */
    fun cleanupAll()

    /**
     * 固件升级（统一升级协议）。
     * 流程：upgrade_start → upgrade_file → FILE_DATA → upgrade_verify → upgrade_commit
     *
     * @param firmwareFile 固件文件
     * @param callback 进度回调
     */
    fun firmwareUpdate(
        firmwareFile: File,
        callback: com.ms500.usbcam.ota.OtaFileTransport.TransferCallback
    )

    /**
     * 模型升级（统一升级协议）。
     * 流程：upgrade_start → upgrade_file → FILE_DATA → upgrade_verify → upgrade_commit
     *
     * @param txtFile 模型信息文件
     * @param fpkFile 模型数据文件
     * @param version 模型版本
     * @param callback 进度回调
     */
    fun aiModelUpdate(
        txtFile: File,
        fpkFile: File,
        version: String,
        callback: com.ms500.usbcam.ota.OtaFileTransport.TransferCallback
    )

    /**
     * 组合升级（统一升级协议，固件+模型，只重启一次）。
     * 流程：upgrade_start → upgrade_file → FILE_DATA → upgrade_verify → upgrade_commit
     *
     * @param firmwareFile 固件文件
     * @param modelTxtFile 模型信息文件
     * @param modelFpkFile 模型数据文件
     * @param modelVersion 模型版本
     * @param callback 进度回调
     */
    fun combinedUpdate(
        firmwareFile: File,
        modelTxtFile: File,
        modelFpkFile: File,
        modelVersion: String,
        callback: com.ms500.usbcam.ota.OtaFileTransport.TransferCallback
    )
}
