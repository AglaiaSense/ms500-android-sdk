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
 * OTA parameter API for firmware and AI model upgrade lifecycle.
 *
 * Upgrade operations use the unified CDC file-transfer protocol:
 * `upgrade_start -> upgrade_file -> FILE_DATA chunks -> upgrade_verify -> upgrade_commit`.
 * The API exposes state and progress flows for UI/automation observers, while the update methods start
 * the actual transfer work through the OTA transport layer.
 *
 * Firmware package validation must confirm a `usb_gaze` firmware identity before upgrade. Repeated
 * firmware stability tests may upgrade to the same, newer, or older `usb_gaze` firmware package.
 */
interface OtaParamApi {

    /**
     * Current upgrade state flow.
     *
     * Emits high-level lifecycle states such as idle, verifying, transferring, committing, completed, or
     * failed. UI code should observe this state rather than inferring lifecycle from progress values only.
     */
    val stateFlow: StateFlow<UpgradeState>

    /**
     * Upgrade progress flow.
     *
     * Emits byte/file progress during transfer. Automation code can observe this flow to record long-run
     * upgrade stability tests, including repeated firmware/model upgrade loops.
     */
    val progressFlow: Flow<UpgradeProgress>

    /**
     * Verifies an upgrade package before starting transfer.
     *
     * The verification result identifies whether [file] is firmware, model metadata, model payload, or an
     * unsupported package. Firmware verification enforces the `usb_gaze` identity rule used by upgrade
     * flows.
     *
     * @param file Upgrade package file to inspect.
     * @return Verification result containing [PackageVerification].
     */
    suspend fun verify(file: File): SdkResult<PackageVerification>

    /**
     * Cancels the current upgrade task.
     *
     * This stops the active SDK-side transfer if possible. Device-side behavior depends on which protocol
     * stage was already reached, so callers should observe [stateFlow] after cancellation.
     */
    fun cancel()

    /**
     * Releases OTA resources.
     *
     * Call this when the owning screen or service is being destroyed. It should not be used as a normal
     * per-file cleanup operation; use [cleanup] or [cleanupAll] for temporary files.
     */
    fun release()

    /**
     * Cleans up a specified temporary file.
     *
     * @param file Temporary file to remove.
     */
    fun cleanup(file: File)

    /**
     * Cleans up all OTA temporary files managed by this API.
     */
    fun cleanupAll()

    /**
     * Starts firmware update through the unified upgrade protocol.
     *
     * The firmware file must pass firmware package verification and must identify as `usb_gaze`. Version
     * comparison does not block same-version or downgrade firmware packages, which allows repeated and
     * rollback stability tests.
     *
     * @param firmwareFile Firmware package file.
     * @param callback Low-level transport progress callback.
     */
    fun firmwareUpdate(
        firmwareFile: File,
        callback: com.ms500.usbcam.ota.OtaFileTransport.TransferCallback
    )

    /**
     * Starts AI model update through the unified upgrade protocol.
     *
     * The model update requires both the model info text file and model data file. [version] is the model
     * version reported to the device during the upgrade flow.
     *
     * @param txtFile Model info file.
     * @param fpkFile Model data file.
     * @param version Model version string.
     * @param callback Low-level transport progress callback.
     */
    fun aiModelUpdate(
        txtFile: File,
        fpkFile: File,
        version: String,
        callback: com.ms500.usbcam.ota.OtaFileTransport.TransferCallback
    )

    /**
     * Starts combined firmware and model update through one upgrade session.
     *
     * This flow transfers firmware and model files before committing, so the device only needs to reboot
     * once after both package groups are accepted.
     *
     * @param firmwareFile Firmware package file.
     * @param modelTxtFile Model info file.
     * @param modelFpkFile Model data file.
     * @param modelVersion Model version string.
     * @param callback Low-level transport progress callback.
     */
    fun combinedUpdate(
        firmwareFile: File,
        modelTxtFile: File,
        modelFpkFile: File,
        modelVersion: String,
        callback: com.ms500.usbcam.ota.OtaFileTransport.TransferCallback
    )
}
