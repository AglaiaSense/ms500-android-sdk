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
import com.ms500.usbcam.dto.DeviceParamDto
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
 * 升级 API 实现。
 *
 * 这个类是 OTA 的桥接层，不负责真正执行升级协议。
 * 它主要做三件事：
 * 1. 对外暴露 SDK API 给 app 调用
 * 2. 在 verify 阶段调用解包器，把升级包转换成 PackageVerification
 * 3. 在升级阶段把请求转发给 OtaUnifiedManage，并把内部进度翻译成上层能消费的模型
 */
internal class OtaParamApiImpl(
    private val connection: CdcConnectApiImpl,
    private val filesDir: File
) : OtaParamApi {

    private val decoder = OtaPackageDecoder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val decodedDir by lazy {
        File(filesDir, "decoded_packages").apply {
            if (!exists()) mkdirs()
        }
    }

    private val _stateFlow = MutableStateFlow(UpgradeState.IDLE)
    override val stateFlow: StateFlow<UpgradeState> = _stateFlow.asStateFlow()

    private val _progressFlow = MutableStateFlow(UpgradeProgress())
    override val progressFlow: Flow<UpgradeProgress> = _progressFlow

    // 整个 SDK 侧只维护一个统一执行器实例。
    private var unifiedUpgradeManager: OtaUnifiedManage? = null
    private var progressBridgeJob: Job? = null

    private fun getUnifiedUpgradeManager(): OtaUnifiedManage? {
        if (unifiedUpgradeManager == null) {
            val cdcSender = connection.getSender() ?: return null
            val cdcClient = connection.getClient() ?: return null
            unifiedUpgradeManager = OtaUnifiedManage(cdcClient, cdcSender, filesDir)
        }
        return unifiedUpgradeManager
    }

    override suspend fun verify(file: File): SdkResult<PackageVerification> {
        // verify 只做“包解析 + 元信息整理”，不做真实 OTA 传输。
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

    override fun cancel() {
        unifiedUpgradeManager?.cancelTransfer()
        stopProgressBridge()
        _stateFlow.value = UpgradeState.IDLE
        _progressFlow.value = UpgradeProgress(state = UpgradeState.IDLE)
    }

    override fun release() {
        unifiedUpgradeManager?.release()
        unifiedUpgradeManager = null
        stopProgressBridge()
        scope.coroutineContext.cancelChildren()
    }

    override fun cleanup(file: File) {
        decoder.cleanup(file)
    }

    override fun cleanupAll() {
        decodedDir.listFiles()?.forEach { file ->
            decoder.cleanup(file)
        }
    }

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
        manager.combinedUpdateFlow(firmwareFile, modelTxtFile, modelFpkFile, modelVersion, callbackWithState(callback))
    }

    private fun startProgressBridge(manager: OtaUnifiedManage) {
        // 这层桥接的目的，是把 OtaUnifiedManage 的内部进度流转换成 SDK 对外的 UpgradeProgress。
        stopProgressBridge()
        progressBridgeJob = scope.launch {
            manager.progressFlow.collect { progress ->
                progress?.let {
                    _progressFlow.value = it.toUpgradeProgress(_stateFlow.value)
                }
            }
        }
    }

    private fun stopProgressBridge() {
        progressBridgeJob?.cancel()
        progressBridgeJob = null
    }

    private fun callbackWithState(delegate: OtaFileTransport.TransferCallback): OtaFileTransport.TransferCallback {
        // callback 风格接口和 flow 风格接口并存，所以这里负责把状态统一回写到 SDK 层。
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




