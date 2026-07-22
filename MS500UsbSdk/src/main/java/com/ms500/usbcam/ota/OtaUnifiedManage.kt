/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.usbcam.ota

import android.util.Log
import com.ms500.usbcam.cdc.CdcSender
import com.ms500.usbcam.cdc.CdcPortConnection
import com.ms500.usbcam.dto.FileInfo
import com.ms500.usbcam.dto.DeviceParamDto
import com.ms500.usbcam.dto.UnifiedUpgradeProgress
import com.ms500.usbcam.dto.UpgradeFileType
import com.ms500.usbcam.dto.UpgradeFiles
import com.ms500.usbcam.dto.UpgradeMode
import com.ms500.usbcam.dto.UpgradePhase
import com.ms500.usbcam.result.SdkResult
import com.ms500.usbcam.result.SdkError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Unified OTA upgrade manager.
 *
 * Orchestrates the full upgrade flow: start session → prepare & transfer files → verify → commit.
 * Provides both suspend functions and legacy callback-based APIs, exposing live phase/progress via StateFlow.
 *
 * @param cdcClient CDC port connection.
 * @param cdcSender CDC command sender.
 * @param filesDir Application files directory.
 */
class OtaUnifiedManage(
    private val cdcClient: CdcPortConnection,
    private val cdcSender: CdcSender,
    private val filesDir: File
) {
    companion object {
        /** Log tag. */
        private const val TAG = "OtaUnifiedManage"
    }

    /**
     * Upgrade session data class.
     *
     * Upgrade mode.
     * @property firmwareFile Firmware file (nullable).
     * @property modelTxtFile Model info TXT file (nullable).
     * @property modelFpkFile Model data FPK file (nullable).
     * @property modelVersion Model version (nullable).
     * File info collection.
     * Total bytes.
     */
    private data class UpgradeSession(
        val mode: UpgradeMode,
        val firmwareFile: File?,
        val modelTxtFile: File?,
        val modelFpkFile: File?,
        val firmwareVersion: String?,
        val modelVersion: String?,
        val files: UpgradeFiles,
        val totalBytes: Long
    )

    /** File transport instance. */
    private val fileTransport = OtaFileTransport(cdcClient)

    /** Mutex to prevent concurrent upgrades. */
    private val mutex = Mutex()

    /** Atomic flag indicating cancellation. */
    private val cancelled = AtomicBoolean(false)

    /** Internal mutable state flow for upgrade phase. */
    private val _phaseFlow = MutableStateFlow(UpgradePhase.IDLE)

    /** Public read-only state flow for upgrade phase. */
    val phaseFlow: StateFlow<UpgradePhase> = _phaseFlow.asStateFlow()

    /** Internal mutable state flow for upgrade progress. */
    private val _progressFlow = MutableStateFlow<UnifiedUpgradeProgress?>(null)

    /** Public read-only state flow for upgrade progress. */
    val progressFlow: StateFlow<UnifiedUpgradeProgress?> = _progressFlow.asStateFlow()

    /** Current session (null when idle). */
    private var currentSession: UpgradeSession? = null

    /**
     * Executes complete upgrade flow（Suspend function）/ Execute the full upgrade flow (suspend function).
     *
     * Upgrade mode.
     * @param firmwareFile Firmware file (optional).
     * @param modelTxtFile Model info file (optional).
     * @param modelFpkFile Model data file (optional).
     * @param modelVersion Model version (optional).
     * Whether to reboot device after commit.
     * Success or failure result.
     */
    suspend fun executeUpgrade(
        mode: UpgradeMode,
        firmwareFile: File? = null,
        modelTxtFile: File? = null,
        modelFpkFile: File? = null,
        firmwareVersion: String? = null,
        modelVersion: String? = null,
        reboot: Boolean = true
    ): SdkResult<Unit> = mutex.withLock {
        if (!cdcClient.isConnected()) {
            return@withLock SdkResult.failure(SdkError.NotConnected)
        }

        val session = createSessionOrFailure(mode, firmwareFile, modelTxtFile, modelFpkFile, firmwareVersion, modelVersion)
            ?: return@withLock SdkResult.failure(SdkError.InvalidArgument("Invalid upgrade session"))

        cancelled.set(false)
        currentSession = session

        try {
            when (val validation = validateVersionsBeforeUpgrade(session)) {
                is SdkResult.Failure -> return@withLock SdkResult.failure(validation.error)
                is SdkResult.Success -> Unit
            }

            when (val start = startUpgradeSession(session)) {
                is SdkResult.Failure -> return@withLock SdkResult.failure(start.error)
                is SdkResult.Success -> Unit
            }

            var uploadedBytes = 0L

            if (session.firmwareFile != null) {
                when (val transfer = prepareAndTransferFile(session, session.firmwareFile, UpgradeFileType.FIRMWARE, uploadedBytes)) {
                    is SdkResult.Failure -> return@withLock SdkResult.failure(transfer.error)
                    is SdkResult.Success -> uploadedBytes += session.firmwareFile.length()
                }
            }

            if (session.modelTxtFile != null) {
                when (val transfer = prepareAndTransferFile(session, session.modelTxtFile, UpgradeFileType.MODEL_INFO, uploadedBytes)) {
                    is SdkResult.Failure -> return@withLock SdkResult.failure(transfer.error)
                    is SdkResult.Success -> uploadedBytes += session.modelTxtFile.length()
                }
            }

            if (session.modelFpkFile != null) {
                when (val transfer = prepareAndTransferFile(session, session.modelFpkFile, UpgradeFileType.MODEL_FPK, uploadedBytes)) {
                    is SdkResult.Failure -> return@withLock SdkResult.failure(transfer.error)
                    is SdkResult.Success -> uploadedBytes += session.modelFpkFile.length()
                }
            }

            when (val verify = verifyUpgrade(session)) {
                is SdkResult.Failure -> return@withLock SdkResult.failure(verify.error)
                is SdkResult.Success -> Unit
            }

            when (val commit = commitUpgrade(reboot)) {
                is SdkResult.Failure -> return@withLock SdkResult.failure(commit.error)
                is SdkResult.Success -> Unit
            }

            _phaseFlow.value = UpgradePhase.COMPLETED
            _progressFlow.value = UnifiedUpgradeProgress(
                phase = UpgradePhase.COMPLETED,
                uploadedBytes = session.totalBytes,
                totalBytes = session.totalBytes,
                percentage = 100
            )
            currentSession = null
            SdkResult.success(Unit)
        } catch (e: CancellationException) {
            _phaseFlow.value = UpgradePhase.ERROR
            _progressFlow.value = UnifiedUpgradeProgress(phase = UpgradePhase.ERROR, error = "Cancelled")
            currentSession = null
            SdkResult.failure(SdkError.Upgrade.Cancelled)
        } catch (e: Exception) {
            Log.e(TAG, "executeUpgrade failed", e)
            _phaseFlow.value = UpgradePhase.ERROR
            _progressFlow.value = UnifiedUpgradeProgress(phase = UpgradePhase.ERROR, error = e.message)
            currentSession = null
            SdkResult.failure(SdkError.InternalError(e))
        }
    }

    /**
     * Firmware update entry with callback (legacy UI compatible).
     *
     * @param file Firmware file.
     * @param callback Transfer callback.
     */
    fun firmwareUpdateFlow(file: File, callback: OtaFileTransport.TransferCallback) {
        val firmwareVersion = OtaToolstools.extractFirmwareVersion(file)
        executeWithCallback(UpgradeMode.FIRMWARE, file, null, null, firmwareVersion, null, callback)
    }

    /**
     * AI model update entry with callback (legacy UI compatible).
     *
     * @param txtFile Model info TXT file.
     * @param fpkFile Model data FPK file.
     * @param modelVersion Model version.
     * @param callback Transfer callback.
     */
    fun aiModelUpdateFlow(txtFile: File, fpkFile: File, modelVersion: String, callback: OtaFileTransport.TransferCallback) {
        executeWithCallback(UpgradeMode.MODEL, null, txtFile, fpkFile, null, modelVersion, callback)
    }

    /**
     * Combined update entry with callback (legacy UI compatible).
     *
     * @param firmwareFile Firmware file.
     * @param modelTxtFile Model info TXT file.
     * @param modelFpkFile Model data FPK file.
     * @param modelVersion Model version.
     * @param callback Transfer callback.
     */
    fun combinedUpdateFlow(
        firmwareFile: File,
        modelTxtFile: File,
        modelFpkFile: File,
        firmwareVersion: String?,
        modelVersion: String,
        callback: OtaFileTransport.TransferCallback
    ) {
        executeWithCallback(UpgradeMode.COMBINED, firmwareFile, modelTxtFile, modelFpkFile, firmwareVersion, modelVersion, callback)
    }

    /**
     * Cancel current transfer.
     */
    fun cancelTransfer() {
        cancelled.set(true)
        if (cdcClient.isConnected()) {
            cdcSender.sendCommand("upgrade_cancel", mapOf("reason" to "User cancelled"), 2_000L, null)
        }
        currentSession = null
        _phaseFlow.value = UpgradePhase.IDLE
        _progressFlow.value = null
    }

    /**
     * Release resources, internally calls cancelTransfer.
     */
    fun release() {
        cancelTransfer()
    }

    /**
     * Create an upgrade session, or return null if parameters are invalid.
     *
     * Upgrade mode.
     * Firmware file.
     * Model info file.
     * Model data file.
     * Model version.
     * @return [UpgradeSession] or null.
     */
    private fun createSessionOrFailure(
        mode: UpgradeMode,
        firmwareFile: File?,
        modelTxtFile: File?,
        modelFpkFile: File?,
        firmwareVersion: String?,
        modelVersion: String?
    ): UpgradeSession? {
        if (_phaseFlow.value != UpgradePhase.IDLE && _phaseFlow.value != UpgradePhase.ERROR && currentSession != null) {
            return null
        }

        when (mode) {
            UpgradeMode.FIRMWARE -> if (!OtaToolstools.isReadableFile(firmwareFile)) return null
            UpgradeMode.MODEL -> if (!OtaToolstools.isReadableFile(modelTxtFile) || !OtaToolstools.isReadableFile(modelFpkFile) || modelVersion.isNullOrBlank()) return null
            UpgradeMode.COMBINED -> if (!OtaToolstools.isReadableFile(firmwareFile) || !OtaToolstools.isReadableFile(modelTxtFile) || !OtaToolstools.isReadableFile(modelFpkFile) || modelVersion.isNullOrBlank()) return null
            UpgradeMode.NONE -> return null
        }

        val files = UpgradeFiles(
            firmware = firmwareFile?.let { FileInfo(it.length(), OtaToolstools.calculateMD5(it)) },
            modelInfo = modelTxtFile?.let { FileInfo(it.length(), OtaToolstools.calculateMD5(it)) },
            modelFpk = modelFpkFile?.let { FileInfo(it.length(), OtaToolstools.calculateMD5(it)) }
        )

        return UpgradeSession(mode, firmwareFile, modelTxtFile, modelFpkFile, firmwareVersion, modelVersion, files, files.totalSize())
    }

    /**
     * Start the upgrade session.
     *
     * Current session.
     * Start result.
     */
    private suspend fun startUpgradeSession(session: UpgradeSession): SdkResult<Unit> {
        _phaseFlow.value = UpgradePhase.PREPARING
        _progressFlow.value = UnifiedUpgradeProgress(phase = UpgradePhase.PREPARING, totalBytes = session.totalBytes)

        val request = JSONObject().apply {
            put("mode", session.mode.name.lowercase())
            put("files", session.files.toJson().apply {
                optJSONObject("firmware")?.put("version", session.firmwareVersion)
                optJSONObject("model_fpk")?.put("version", session.modelVersion)
            })
        }

        return when (val response = sendCommandAwait("upgrade_start", request, OtaToolstools.TIMEOUT_START)) {
            is SdkResult.Failure -> {
                _phaseFlow.value = UpgradePhase.ERROR
                _progressFlow.value = UnifiedUpgradeProgress(phase = UpgradePhase.ERROR, error = response.error.getMessage())
                SdkResult.failure(response.error)
            }
            is SdkResult.Success -> {
                if (!OtaToolstools.responseLooksSuccessful(response.data)) {
                    val msg = response.data.optString("msg", "Upgrade session start failed")
                    _phaseFlow.value = UpgradePhase.ERROR
                    _progressFlow.value = UnifiedUpgradeProgress(phase = UpgradePhase.ERROR, error = msg)
                    SdkResult.failure(SdkError.DeviceRejected(msg))
                } else {
                    SdkResult.success(Unit)
                }
            }
        }
    }

    /**
     * Prepare and transfer a single file.
     *
     * Current session.
     * File to transfer.
     * File type.
     * Bytes already uploaded.
     * Transfer result.
     */
    private suspend fun prepareAndTransferFile(
        session: UpgradeSession,
        file: File,
        fileType: UpgradeFileType,
        alreadyUploaded: Long
    ): SdkResult<Unit> {
        if (cancelled.get()) {
            return SdkResult.failure(SdkError.Upgrade.Cancelled)
        }

        val phase = OtaToolstools.phaseFor(fileType)
        _phaseFlow.value = phase
        _progressFlow.value = UnifiedUpgradeProgress(
            phase = phase,
            fileType = fileType,
            uploadedBytes = alreadyUploaded,
            totalBytes = session.totalBytes,
            percentage = OtaToolstools.percentOf(alreadyUploaded, session.totalBytes)
        )

        val md5 = when (fileType) {
            UpgradeFileType.FIRMWARE -> session.files.firmware?.md5
            UpgradeFileType.MODEL_INFO -> session.files.modelInfo?.md5
            UpgradeFileType.MODEL_FPK -> session.files.modelFpk?.md5
        } ?: return SdkResult.failure(SdkError.InvalidArgument("Missing MD5 for ${fileType.value}"))

        val request = JSONObject().apply {
            put("file_type", fileType.value)
            put("size", file.length())
            put("md5", md5)
            if (fileType == UpgradeFileType.FIRMWARE && !session.firmwareVersion.isNullOrBlank()) {
                put("firmware_version", session.firmwareVersion)
            }
            if (fileType == UpgradeFileType.MODEL_FPK && !session.modelVersion.isNullOrBlank()) {
                put("model_version", session.modelVersion)
            }
        }

        return when (val prepare = sendCommandAwait("upgrade_file", request, OtaToolstools.TIMEOUT_FILE_PREPARE)) {
            is SdkResult.Failure -> {
                _phaseFlow.value = UpgradePhase.ERROR
                _progressFlow.value = UnifiedUpgradeProgress(phase = UpgradePhase.ERROR, fileType = fileType, error = prepare.error.getMessage())
                SdkResult.failure(prepare.error)
            }
            is SdkResult.Success -> {
                if (!OtaToolstools.responseLooksSuccessful(prepare.data)) {
                    val msg = prepare.data.optString("msg", "Prepare ${fileType.value} failed")
                    _phaseFlow.value = UpgradePhase.ERROR
                    _progressFlow.value = UnifiedUpgradeProgress(phase = UpgradePhase.ERROR, fileType = fileType, error = msg)
                    SdkResult.failure(SdkError.DeviceRejected(msg))
                } else {
                    fileTransport.transferFile(file, fileType, alreadyUploaded, session.totalBytes, { cancelled.get() }) {
                        _progressFlow.value = it
                    }
                }
            }
        }
    }

    /**
     * Verify upgrade result.
     *
     * Current session.
     * Verification result.
     */
    private suspend fun verifyUpgrade(session: UpgradeSession): SdkResult<Unit> {
        _phaseFlow.value = UpgradePhase.VERIFYING
        _progressFlow.value = UnifiedUpgradeProgress(
            phase = UpgradePhase.VERIFYING,
            uploadedBytes = session.totalBytes,
            totalBytes = session.totalBytes,
            percentage = 100
        )

        return when (val response = sendCommandAwait("upgrade_verify", JSONObject(), OtaToolstools.TIMEOUT_VERIFY)) {
            is SdkResult.Failure -> {
                _phaseFlow.value = UpgradePhase.ERROR
                _progressFlow.value = UnifiedUpgradeProgress(phase = UpgradePhase.ERROR, error = response.error.getMessage())
                SdkResult.failure(response.error)
            }
            is SdkResult.Success -> {
                val responseJson = response.data
                val filesJson = responseJson.optJSONObject("files")
                if (filesJson != null) {
                    for (type in UpgradeFileType.values()) {
                        val fileJson = filesJson.optJSONObject(type.value) ?: continue
                        val verified = if (fileJson.has("verified")) fileJson.optBoolean("verified", false) else true
                        val md5Match = if (fileJson.has("md5_match")) fileJson.optBoolean("md5_match", false) else true
                        if (!verified || !md5Match) {
                            val msg = "${type.value} verify failed: verified=$verified, md5_match=$md5Match"
                            _phaseFlow.value = UpgradePhase.ERROR
                            _progressFlow.value = UnifiedUpgradeProgress(phase = UpgradePhase.ERROR, error = msg)
                            return SdkResult.failure(SdkError.DeviceRejected(msg))
                        }
                    }
                    return SdkResult.success(Unit)
                }

                if (!OtaToolstools.responseLooksSuccessful(responseJson)) {
                    val msg = responseJson.optString("msg", "Upgrade verify failed")
                    _phaseFlow.value = UpgradePhase.ERROR
                    _progressFlow.value = UnifiedUpgradeProgress(phase = UpgradePhase.ERROR, error = msg)
                    return SdkResult.failure(SdkError.DeviceRejected(msg))
                }

                SdkResult.success(Unit)
            }
        }
    }

    /**
     * Commit the upgrade.
     *
     * Whether to reboot.
     * Commit result.
     */
    private suspend fun commitUpgrade(reboot: Boolean): SdkResult<Unit> {
        _phaseFlow.value = UpgradePhase.COMMITTING
        _progressFlow.value = (_progressFlow.value ?: UnifiedUpgradeProgress(phase = UpgradePhase.COMMITTING)).copy(
            phase = UpgradePhase.COMMITTING,
            percentage = 100
        )

        val request = JSONObject().apply {
            put("reboot", reboot)
        }

        return when (val response = sendCommandAwait("upgrade_commit", request, OtaToolstools.TIMEOUT_COMMIT, reboot)) {
            is SdkResult.Failure -> {
                _phaseFlow.value = UpgradePhase.ERROR
                _progressFlow.value = UnifiedUpgradeProgress(phase = UpgradePhase.ERROR, error = response.error.getMessage())
                SdkResult.failure(response.error)
            }
            is SdkResult.Success -> SdkResult.success(Unit)
        }
    }

    /**
     * Send a command and suspend awaiting response.
     *
     * Command name.
     * Command parameters.
     * @param timeoutMs Timeout (milliseconds).
     * Whether timeout should be treated as success.
     * Result containing JSON response.
     */
    private suspend fun sendCommandAwait(
        cmd: String,
        params: JSONObject,
        timeoutMs: Long,
        allowTimeoutAsSuccess: Boolean = false
    ): SdkResult<JSONObject> = suspendCancellableCoroutine { continuation ->
        val payload = if (params.length() > 0) {
            JSONObject().apply { put("params", params) }
        } else {
            params
        }
        cdcSender.sendCommand(cmd, payload, timeoutMs) { raw, response, error ->
            if (continuation.isCancelled) return@sendCommand

            if (error != null) {
                if (allowTimeoutAsSuccess && error.contains("Timeout", ignoreCase = true)) {
                    continuation.resume(
                        SdkResult.success(
                            JSONObject().apply {
                                put("cmd", cmd)
                                put("status", "ok")
                                put("msg", "Command sent; device may reboot before ACK")
                            },
                            raw = raw
                        )
                    )
                } else {
                    continuation.resume(SdkResult.failure(SdkError.DeviceRejected(error), raw = raw))
                }
                return@sendCommand
            }

            if (response == null) {
                continuation.resume(SdkResult.failure(SdkError.InvalidResponse(raw = raw, reason = "Empty response"), raw = raw))
                return@sendCommand
            }

            continuation.resume(SdkResult.success(response, raw = raw))
        }
    }

    /**
     * Execute upgrade with callback (internal compatibility logic).
     *
     * @param mode Upgrade mode.
     * @param firmwareFile Firmware file.
     * @param modelTxtFile Model info file.
     * @param modelFpkFile Model data file.
     * @param modelVersion Model version.
     * @param callback Transfer callback.
     */
    private fun executeWithCallback(
        mode: UpgradeMode,
        firmwareFile: File?,
        modelTxtFile: File?,
        modelFpkFile: File?,
        firmwareVersion: String?,
        modelVersion: String?,
        callback: OtaFileTransport.TransferCallback
    ) {
        val worker = Thread {
            val result = runBlocking {
                executeUpgrade(mode, firmwareFile, modelTxtFile, modelFpkFile, firmwareVersion, modelVersion, reboot = true)
            }
            when (result) {
                is SdkResult.Success -> callback.onSuccess()
                is SdkResult.Failure -> callback.onError(result.error.getMessage())
            }
        }
        worker.name = "ota-unified-$mode"
        worker.isDaemon = true

        val observer = Thread {
            var lastProgress: UnifiedUpgradeProgress? = null
            while (worker.isAlive) {
                val current = progressFlow.value
                if (current != null && current != lastProgress) {
                    lastProgress = current
                    callback.onProgress(OtaToolstools.toTransferProgress(current))
                }
                try {
                    Thread.sleep(50)
                } catch (_: InterruptedException) {
                    break
                }
            }
            val finalProgress = progressFlow.value
            if (finalProgress != null && finalProgress != lastProgress) {
                callback.onProgress(OtaToolstools.toTransferProgress(finalProgress))
            }
        }
        observer.name = "ota-progress-$mode"
        observer.isDaemon = true

        worker.start()
        observer.start()
    }

    /**
     * Validate target package identity before the upgrade starts.
     * Firmware and model upgrades allow any version age; firmware must explicitly be usb_gaze firmware.
     */
    private suspend fun validateVersionsBeforeUpgrade(session: UpgradeSession): SdkResult<Unit> {
        session.firmwareFile?.let {
            val targetVersion = session.firmwareVersion
            if (!OtaToolstools.isUsbGazeFirmwareVersion(targetVersion)) {
                return SdkResult.failure(
                    SdkError.InvalidArgument(
                        "Firmware package must be usb_gaze firmware: target=${targetVersion ?: "unknown"}"
                    )
                )
            }
        }

        return SdkResult.success(Unit)
    }

    /**
     * Read device info directly from CDC so OTA can validate versions before upgrade.
     */
    private suspend fun fetchDeviceInfo(): SdkResult<DeviceParamDto> {
        return when (val result = sendCommandAwait("get_device_info", JSONObject(), OtaToolstools.TIMEOUT_START)) {
            is SdkResult.Failure -> {
                Log.w(TAG, "get_device_info failed before OTA validation: ${result.error.getMessage()}")
                SdkResult.failure(result.error, raw = result.raw)
            }
            is SdkResult.Success -> {
                val payload = result.data.optJSONObject("result") ?: result.data
                Log.d(TAG, "get_device_info response=${maskDeviceInfoForLog(result.data)}")

                if (!payload.has("firmware_version") && !payload.has("model_version")) {
                    val reason = "Device info response missing version fields"
                    Log.w(TAG, "$reason: ${maskDeviceInfoForLog(result.data)}")
                    SdkResult.failure(SdkError.InvalidResponse(raw = result.raw ?: result.data.toString(), reason = reason), raw = result.raw)
                } else {
                    val deviceInfo = DeviceParamDto.fromJson(payload)
                    Log.i(TAG, "get_device_info parsed: ${formatDeviceInfoForLog(deviceInfo)}")
                    SdkResult.success(deviceInfo, raw = result.raw)
                }
            }
        }
    }

    /** Returns a compact device-info summary safe for logcat. */
    private fun formatDeviceInfoForLog(info: DeviceParamDto): String {
        return buildString {
            append("firmware=").append(info.firmwareVersion)
            append(", model=").append(info.modelVersion)
            append(", sensor_id=").append(info.sensorId.orEmpty())
            append(", c_sn=").append(info.cSn.orEmpty())
            append(", u_sn=").append(info.uSn.orEmpty())
            append(", token=").append(formatTokenForLog(info.token))
        }
    }

    /** Keeps token presence visible without printing the full credential. */
    private fun formatTokenForLog(token: String?): String {
        return if (token.isNullOrBlank()) {
            "missing"
        } else {
            "present(length=${token.length})"
        }
    }

    /** Masks token fields before logging raw get_device_info JSON. */
    private fun maskDeviceInfoForLog(json: JSONObject): JSONObject {
        val copy = JSONObject(json.toString())
        maskTokenField(copy)
        copy.optJSONObject("result")?.let { maskTokenField(it) }
        return copy
    }

    private fun maskTokenField(json: JSONObject) {
        if (json.has("token")) {
            val token = json.optString("token", "")
            json.put("token", formatTokenForLog(token))
        }
    }
}
