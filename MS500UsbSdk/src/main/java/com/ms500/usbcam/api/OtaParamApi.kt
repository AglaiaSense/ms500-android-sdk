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
 * OTA parameters API for firmware/model upgrade lifecycle.
 */
interface OtaParamApi {

    /**
     * Upgrade state flow (StateFlow).
     */
    val stateFlow: StateFlow<UpgradeState>

    /**
     * Upgrade progress flow (Flow).
     */
    val progressFlow: Flow<UpgradeProgress>

    /**
     * Verifies upgrade package.
     *
     * @param file Upgrade package file
     * @return Verification result containing [PackageVerification]
     */
    suspend fun verify(file: File): SdkResult<PackageVerification>

    /**
     * Cancels current upgrade task.
     */
    fun cancel()

    /**
     * Releases related resources.
     */
    fun release()

    /**
     * Cleans up specified temporary file.
     *
     * @param file File to clean up
     */
    fun cleanup(file: File)

    /**
     * Cleans up all temporary files.
     */
    fun cleanupAll()

    /**
     * Firmware update (unified upgrade protocol).
     * Flow: upgrade_start -> upgrade_file -> FILE_DATA -> upgrade_verify -> upgrade_commit
     *
     * @param firmwareFile Firmware file
     * @param callback Progress callback
     */
    fun firmwareUpdate(
        firmwareFile: File,
        callback: com.ms500.usbcam.ota.OtaFileTransport.TransferCallback
    )

    /**
     * AI model update (unified upgrade protocol).
     * Flow: upgrade_start -> upgrade_file -> FILE_DATA -> upgrade_verify -> upgrade_commit
     *
     * @param txtFile Model info file
     * @param fpkFile Model data file
     * @param version Model version
     * @param callback Progress callback
     */
    fun aiModelUpdate(
        txtFile: File,
        fpkFile: File,
        version: String,
        callback: com.ms500.usbcam.ota.OtaFileTransport.TransferCallback
    )

    /**
     * Combined update (unified upgrade protocol, firmware + model, reboots only once).
     * Flow: upgrade_start -> upgrade_file -> FILE_DATA -> upgrade_verify -> upgrade_commit
     *
     * @param firmwareFile Firmware file
     * @param modelTxtFile Model info file
     * @param modelFpkFile Model data file
     * @param modelVersion Model version
     * @param callback Progress callback
     */
    fun combinedUpdate(
        firmwareFile: File,
        modelTxtFile: File,
        modelFpkFile: File,
        modelVersion: String,
        callback: com.ms500.usbcam.ota.OtaFileTransport.TransferCallback
    )
}
