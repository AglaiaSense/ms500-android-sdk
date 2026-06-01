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

import com.ms500.usbcam.api.OtaParamApi
import com.ms500.usbcam.dto.FirmwareInfo
import com.ms500.usbcam.dto.ModelInfo
import com.ms500.usbcam.dto.PackageType
import com.ms500.usbcam.dto.PackageVerification
import com.ms500.usbcam.dto.UnifiedUpgradeProgress
import com.ms500.usbcam.dto.UpgradeProgress
import com.ms500.usbcam.dto.UpgradeState
import com.ms500.usbcam.ota.OtaFileTransport
import com.ms500.usbcam.ota.OtaPackageDecoder
import com.ms500.usbcam.ota.OtaToolstools
import com.ms500.usbcam.ota.OtaUnifiedManage
import com.ms500.usbcam.result.SdkResult
import com.ms500.usbcam.result.SdkError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Internal implementation of OTA upgrade APIs.
 * Bridges app-facing APIs with the underlying OtaUnifiedManage and package decoder.
 */
internal class OtaParamApiImpl(
    /** CDC connection implementation, used to get sender and client. */
    private val connection: CdcConnectApiImpl,
    /** Application private files directory for storing decoded packages. */
    private val filesDir: File
) : OtaParamApi {

    /** OTA package decoder. */
    private val decoder = OtaPackageDecoder()

    /** Coroutine scope for background tasks such as progress bridging. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Directory for decoded package files. */
    private val decodedDir by lazy {
        File(filesDir, "decoded_packages").apply {
            if (!exists()) mkdirs()
        }
    }

    /** Upgrade state state flow. */
    private val _stateFlow = MutableStateFlow(UpgradeState.IDLE)
    override val stateFlow: StateFlow<UpgradeState> = _stateFlow.asStateFlow()

    /** Upgrade progress state flow. */
    private val _progressFlow = MutableStateFlow(UpgradeProgress())
    override val progressFlow: Flow<UpgradeProgress> = _progressFlow

    /** SDK-side unified upgrade executor instance. */
    private var unifiedUpgradeManager: OtaUnifiedManage? = null

    /** Progress bridge coroutine job. */
    private var progressBridgeJob: Job? = null

    /**
     * Gets or creates the unified upgrade manager.
     */
    private fun getUnifiedUpgradeManager(): OtaUnifiedManage? {
        if (unifiedUpgradeManager == null) {
            val cdcSender = connection.getSender() ?: return null
            val cdcClient = connection.getClient() ?: return null
            unifiedUpgradeManager = OtaUnifiedManage(cdcClient, cdcSender, filesDir)
        }
        return unifiedUpgradeManager
    }

    /**
     * Verifies the upgrade package and returns metadata without performing actual transfer.
     */
    override suspend fun verify(file: File): SdkResult<PackageVerification> {
        return try {
            val header = decoder.parseGlobalHeader(file)
                ?: return SdkResult.failure(SdkError.InvalidArgument("Failed to parse package header"))

            val packageType = PackageType.fromValue(header.packageType)
            when (packageType) {
                PackageType.FIRMWARE -> buildFirmwareVerification(file)
                PackageType.MODEL -> buildModelVerification(file)
                PackageType.COMBINED -> buildCombinedVerification(file)
                else -> SdkResult.failure(SdkError.InvalidArgument("Unknown package type"))
            }
        } catch (e: Exception) {
            SdkResult.failure(SdkError.InternalError(e))
        }
    }

    /**
     * Cancels the current upgrade task.
     */
    override fun cancel() {
        unifiedUpgradeManager?.cancelTransfer()
        stopProgressBridge()
        _stateFlow.value = UpgradeState.IDLE
        _progressFlow.value = UpgradeProgress(state = UpgradeState.IDLE)
    }

    /**
     * Releases upgrade-related resources.
     */
    override fun release() {
        unifiedUpgradeManager?.release()
        unifiedUpgradeManager = null
        stopProgressBridge()
        scope.coroutineContext.cancelChildren()
    }

    /**
     * Cleans up decoded cache for the specified package file.
     */
    override fun cleanup(file: File) {
        decoder.cleanup(file)
    }

    /**
     * Cleans up decoded cache for all package files.
     */
    override fun cleanupAll() {
        decodedDir.listFiles()?.forEach { file ->
            decoder.cleanup(file)
        }
    }

    /**
     * Performs a firmware update.
     */
    override fun firmwareUpdate(
        firmwareFile: File,
        callback: OtaFileTransport.TransferCallback
    ) {
        val manager = getUnifiedUpgradeManager()
        if (manager == null) {
            callback.onError("Not connected")
            return
        }
        _stateFlow.value = UpgradeState.UPLOADING
        startProgressBridge(manager)
        manager.firmwareUpdateFlow(firmwareFile, callbackWithState(callback))
    }

    /**
     * Performs an AI model update.
     */
    override fun aiModelUpdate(
        txtFile: File,
        fpkFile: File,
        version: String,
        callback: OtaFileTransport.TransferCallback
    ) {
        val manager = getUnifiedUpgradeManager()
        if (manager == null) {
            callback.onError("Not connected")
            return
        }
        _stateFlow.value = UpgradeState.UPLOADING
        startProgressBridge(manager)
        manager.aiModelUpdateFlow(txtFile, fpkFile, version, callbackWithState(callback))
    }

    /**
     * Performs a combined firmware + model update.
     */
    override fun combinedUpdate(
        firmwareFile: File,
        modelTxtFile: File,
        modelFpkFile: File,
        modelVersion: String,
        callback: OtaFileTransport.TransferCallback
    ) {
        val manager = getUnifiedUpgradeManager()
        if (manager == null) {
            callback.onError("Not connected")
            return
        }
        _stateFlow.value = UpgradeState.UPLOADING
        startProgressBridge(manager)
        manager.combinedUpdateFlow(
            firmwareFile = firmwareFile,
            modelTxtFile = modelTxtFile,
            modelFpkFile = modelFpkFile,
            firmwareVersion = OtaToolstools.extractFirmwareVersion(firmwareFile),
            modelVersion = modelVersion,
            callback = callbackWithState(callback)
        )
    }

    /**
     * Starts bridging progress from OtaUnifiedManage to SDK-facing UpgradeProgress.
     */
    private fun startProgressBridge(manager: OtaUnifiedManage) {
        stopProgressBridge()
        progressBridgeJob = scope.launch {
            manager.progressFlow.collect { progress ->
                progress?.let {
                    _progressFlow.value = it.toUpgradeProgress(_stateFlow.value)
                }
            }
        }
    }

    /**
     * Stops the progress bridge coroutine.
     */
    private fun stopProgressBridge() {
        progressBridgeJob?.cancel()
        progressBridgeJob = null
    }

    /**
     * Wraps the delegate callback to synchronously update SDK-level state and progress.
     */
    private fun callbackWithState(delegate: OtaFileTransport.TransferCallback): OtaFileTransport.TransferCallback {
        return object : OtaFileTransport.TransferCallback {
            override fun onProgress(progress: OtaFileTransport.Progress) {
                _stateFlow.value = UpgradeState.UPLOADING
                _progressFlow.value = UpgradeProgress(
                    state = UpgradeState.UPLOADING,
                    percentage = progress.percentage,
                    uploadedBytes = progress.bytesSent,
                    totalBytes = progress.totalBytes,
                    speed = progress.speedBytesPerSecond,
                    etaSeconds = progress.etaSeconds
                )
                delegate.onProgress(progress)
            }

            override fun onSuccess() {
                _stateFlow.value = UpgradeState.COMPLETED
                _progressFlow.value = _progressFlow.value.copy(state = UpgradeState.COMPLETED, percentage = 100)
                stopProgressBridge()
                delegate.onSuccess()
            }

            override fun onError(error: String) {
                _stateFlow.value = UpgradeState.FAILED
                _progressFlow.value = _progressFlow.value.copy(state = UpgradeState.FAILED, error = error)
                stopProgressBridge()
                delegate.onError(error)
            }
        }
    }

    /**
     * Builds verification result for a firmware package.
     */
    private fun buildFirmwareVerification(file: File): SdkResult<PackageVerification> {
        val result = decoder.decodeFirmwarePackage(file, decodedDir)
        if (!result.isSuccess) {
            return SdkResult.failure(SdkError.InvalidArgument(result.error ?: "Decode failed"))
        }

        val firmwareFile = result.firmwareFile
        val firmwareHeader = result.firmwareHeader
        if (firmwareHeader == null || firmwareFile == null) {
            return SdkResult.failure(SdkError.InvalidResponse("Invalid firmware package"))
        }

        return SdkResult.success(
            PackageVerification(
                type = PackageType.FIRMWARE,
                firmware = FirmwareInfo(
                    name = firmwareHeader.name,
                    version = firmwareHeader.versionString,
                    versionString = firmwareHeader.versionString,
                    fileSize = firmwareFile.length(),
                    md5 = OtaToolstools.calculateMD5(firmwareFile),
                    isValid = true
                ),
                firmwareFile = firmwareFile,
                isValid = true
            )
        )
    }

    /**
     * Builds verification result for a model package.
     */
    private fun buildModelVerification(file: File): SdkResult<PackageVerification> {
        val result = decoder.decodeModelPackage(file, decodedDir)
        if (!result.isSuccess) {
            return SdkResult.failure(SdkError.InvalidArgument(result.error ?: "Decode failed"))
        }

        val modelFile = result.modelFile
        val modelHeader = result.modelHeader
        if (modelHeader == null || modelFile == null) {
            return SdkResult.failure(SdkError.InvalidResponse("Invalid model package"))
        }

        val txtFile = result.modelInfoFile
        val fpkFile = result.modelDataFile

        return SdkResult.success(
            PackageVerification(
                type = PackageType.MODEL,
                model = ModelInfo(
                    name = modelHeader.name,
                    version = modelHeader.versionString,
                    versionString = modelHeader.versionString,
                    fileSize = (txtFile?.length() ?: 0L) + (fpkFile?.length() ?: 0L),
                    sensorId = modelHeader.sensorIdString,
                    isValid = true
                ),
                modelInfoFile = txtFile,
                modelDataFile = fpkFile,
                isValid = true
            )
        )
    }

    /**
     * Builds verification result for a combined package.
     */
    private fun buildCombinedVerification(file: File): SdkResult<PackageVerification> {
        val result = decoder.decodeCombinedPackage(file, decodedDir)
        if (!result.isSuccess) {
            return SdkResult.failure(SdkError.InvalidArgument(result.error ?: "Decode failed"))
        }

        val firmwareFile = result.firmwareFile
        val modelFile = result.modelFile
        val firmwareHeader = result.firmwareHeader
        val modelHeader = result.modelHeader
        val txtFile = result.modelInfoFile
        val fpkFile = result.modelDataFile

        return SdkResult.success(
            PackageVerification(
                type = PackageType.COMBINED,
                firmware = firmwareHeader?.let {
                    FirmwareInfo(
                        name = it.name,
                        version = it.versionString,
                        versionString = it.versionString,
                        fileSize = firmwareFile?.length() ?: 0L,
                        md5 = firmwareFile?.let(OtaToolstools::calculateMD5).orEmpty(),
                        isValid = true
                    )
                },
                model = modelHeader?.let {
                    ModelInfo(
                        name = it.name,
                        version = it.versionString,
                        versionString = it.versionString,
                        fileSize = (txtFile?.length() ?: 0L) + (fpkFile?.length() ?: 0L),
                        sensorId = it.sensorIdString,
                        isValid = true
                    )
                },
                firmwareFile = firmwareFile,
                modelInfoFile = txtFile,
                modelDataFile = fpkFile,
                isValid = true
            )
        )
    }

    /**
     * Converts internal unified upgrade progress to the SDK-facing UpgradeProgress model.
     */
    private fun UnifiedUpgradeProgress.toUpgradeProgress(state: UpgradeState): UpgradeProgress {
        return UpgradeProgress(
            state = if (state == UpgradeState.FAILED) UpgradeState.FAILED else UpgradeState.UPLOADING,
            percentage = percentage,
            uploadedBytes = uploadedBytes,
            totalBytes = totalBytes,
            speed = speed,
            etaSeconds = etaSeconds,
            error = error
        )
    }
}
