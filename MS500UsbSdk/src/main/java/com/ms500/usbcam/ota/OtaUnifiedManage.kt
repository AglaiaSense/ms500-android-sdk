package com.ms500.usbcam.ota

import android.util.Log
import com.ms500.usbcam.cdc.CdcSender
import com.ms500.usbcam.cdc.CdcPortConnection
import com.ms500.usbcam.dto.FileInfo
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
 * OTA 统一升级管理器 / Unified OTA upgrade manager.
 *
 * 负责编排完整升级流程：启动会话 → 文件准备与传输 → 校验 → 提交。
 * 对外提供协程挂起函数与基于回调的兼容 API，并通过 StateFlow 暴露实时阶段与进度。
 *
 * Orchestrates the full upgrade flow: start session → prepare & transfer files → verify → commit.
 * Provides both suspend functions and legacy callback-based APIs, exposing live phase/progress via StateFlow.
 *
 * @property cdcClient CDC 端口连接 / CDC port connection.
 * @property cdcSender CDC 命令发送器 / CDC command sender.
 * @property filesDir 应用文件目录 / Application files directory.
 */
class OtaUnifiedManage(
    private val cdcClient: CdcPortConnection,
    private val cdcSender: CdcSender,
    private val filesDir: File
) {
    companion object {
        /** Log 标签 / Log tag. */
        private const val TAG = "OtaUnifiedManage"
    }

    /**
     * 升级会话数据类 / Upgrade session data class.
     *
     * @property mode 升级模式 / Upgrade mode.
     * @property firmwareFile 固件文件（可为 null）/ Firmware file (nullable).
     * @property modelTxtFile 模型信息 TXT 文件（可为 null）/ Model info TXT file (nullable).
     * @property modelFpkFile 模型数据 FPK 文件（可为 null）/ Model data FPK file (nullable).
     * @property modelVersion 模型版本（可为 null）/ Model version (nullable).
     * @property files 各文件信息集合 / File info collection.
     * @property totalBytes 总字节数 / Total bytes.
     */
    private data class UpgradeSession(
        val mode: UpgradeMode,
        val firmwareFile: File?,
        val modelTxtFile: File?,
        val modelFpkFile: File?,
        val modelVersion: String?,
        val files: UpgradeFiles,
        val totalBytes: Long
    )

    /** 文件传输器实例 / File transport instance. */
    private val fileTransport = OtaFileTransport(cdcClient)

    /** 互斥锁，防止并发执行升级 / Mutex to prevent concurrent upgrades. */
    private val mutex = Mutex()

    /** 原子布尔值，标记是否已取消 / Atomic flag indicating cancellation. */
    private val cancelled = AtomicBoolean(false)

    /** 当前升级阶段内部流 / Internal mutable state flow for upgrade phase. */
    private val _phaseFlow = MutableStateFlow(UpgradePhase.IDLE)

    /** 当前升级阶段公开流 / Public read-only state flow for upgrade phase. */
    val phaseFlow: StateFlow<UpgradePhase> = _phaseFlow.asStateFlow()

    /** 当前升级进度内部流 / Internal mutable state flow for upgrade progress. */
    private val _progressFlow = MutableStateFlow<UnifiedUpgradeProgress?>(null)

    /** 当前升级进度公开流 / Public read-only state flow for upgrade progress. */
    val progressFlow: StateFlow<UnifiedUpgradeProgress?> = _progressFlow.asStateFlow()

    /** 当前会话（为 null 表示空闲）/ Current session (null when idle). */
    private var currentSession: UpgradeSession? = null

    /**
     * 执行完整升级流程（挂起函数）/ Execute the full upgrade flow (suspend function).
     *
     * @param mode 升级模式 / Upgrade mode.
     * @param firmwareFile 固件文件（可选）/ Firmware file (optional).
     * @param modelTxtFile 模型信息文件（可选）/ Model info file (optional).
     * @param modelFpkFile 模型数据文件（可选）/ Model data file (optional).
     * @param modelVersion 模型版本（可选）/ Model version (optional).
     * @param reboot 提交后是否重启设备 / Whether to reboot device after commit.
     * @return [SdkResult] 成功或失败结果 / Success or failure result.
     */
    suspend fun executeUpgrade(
        mode: UpgradeMode,
        firmwareFile: File? = null,
        modelTxtFile: File? = null,
        modelFpkFile: File? = null,
        modelVersion: String? = null,
        reboot: Boolean = true
    ): SdkResult<Unit> = mutex.withLock {
        if (!cdcClient.isConnected()) {
            return@withLock SdkResult.failure(SdkError.NotConnected)
        }

        val session = createSessionOrFailure(mode, firmwareFile, modelTxtFile, modelFpkFile, modelVersion)
            ?: return@withLock SdkResult.failure(SdkError.InvalidArgument("Invalid upgrade session"))

        cancelled.set(false)
        currentSession = session

        try {
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
     * 固件更新回调式入口（兼容旧 UI）/ Firmware update entry with callback (legacy UI compatible).
     *
     * @param file 固件文件 / Firmware file.
     * @param callback 传输回调 / Transfer callback.
     */
    fun firmwareUpdateFlow(file: File, callback: OtaFileTransport.TransferCallback) {
        executeWithCallback(UpgradeMode.FIRMWARE, file, null, null, null, callback)
    }

    /**
     * AI 模型更新回调式入口（兼容旧 UI）/ AI model update entry with callback (legacy UI compatible).
     *
     * @param txtFile 模型信息 TXT 文件 / Model info TXT file.
     * @param fpkFile 模型数据 FPK 文件 / Model data FPK file.
     * @param modelVersion 模型版本 / Model version.
     * @param callback 传输回调 / Transfer callback.
     */
    fun aiModelUpdateFlow(txtFile: File, fpkFile: File, modelVersion: String, callback: OtaFileTransport.TransferCallback) {
        executeWithCallback(UpgradeMode.MODEL, null, txtFile, fpkFile, modelVersion, callback)
    }

    /**
     * 组合更新回调式入口（兼容旧 UI）/ Combined update entry with callback (legacy UI compatible).
     *
     * @param firmwareFile 固件文件 / Firmware file.
     * @param modelTxtFile 模型信息 TXT 文件 / Model info TXT file.
     * @param modelFpkFile 模型数据 FPK 文件 / Model data FPK file.
     * @param modelVersion 模型版本 / Model version.
     * @param callback 传输回调 / Transfer callback.
     */
    fun combinedUpdateFlow(
        firmwareFile: File,
        modelTxtFile: File,
        modelFpkFile: File,
        modelVersion: String,
        callback: OtaFileTransport.TransferCallback
    ) {
        executeWithCallback(UpgradeMode.COMBINED, firmwareFile, modelTxtFile, modelFpkFile, modelVersion, callback)
    }

    /**
     * 取消当前传输 / Cancel current transfer.
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
     * 释放资源，内部调用取消传输 / Release resources, internally calls cancelTransfer.
     */
    fun release() {
        cancelTransfer()
    }

    /**
     * 创建升级会话，参数非法时返回 null / Create an upgrade session, or return null if parameters are invalid.
     *
     * @param mode 升级模式 / Upgrade mode.
     * @param firmwareFile 固件文件 / Firmware file.
     * @param modelTxtFile 模型信息文件 / Model info file.
     * @param modelFpkFile 模型数据文件 / Model data file.
     * @param modelVersion 模型版本 / Model version.
     * @return [UpgradeSession] 或 null.
     */
    private fun createSessionOrFailure(
        mode: UpgradeMode,
        firmwareFile: File?,
        modelTxtFile: File?,
        modelFpkFile: File?,
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

        return UpgradeSession(mode, firmwareFile, modelTxtFile, modelFpkFile, modelVersion, files, files.totalSize())
    }

    /**
     * 启动升级会话 / Start the upgrade session.
     *
     * @param session 当前会话 / Current session.
     * @return [SdkResult] 启动结果 / Start result.
     */
    private suspend fun startUpgradeSession(session: UpgradeSession): SdkResult<Unit> {
        _phaseFlow.value = UpgradePhase.PREPARING
        _progressFlow.value = UnifiedUpgradeProgress(phase = UpgradePhase.PREPARING, totalBytes = session.totalBytes)

        val request = JSONObject().apply {
            put("mode", session.mode.name.lowercase())
            put("files", session.files.toJson())
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
                    SdkResult.failure(SdkError.DeviceRejected(null, msg))
                } else {
                    SdkResult.success(Unit)
                }
            }
        }
    }

    /**
     * 准备并传输单个文件 / Prepare and transfer a single file.
     *
     * @param session 当前会话 / Current session.
     * @param file 待传输文件 / File to transfer.
     * @param fileType 文件类型 / File type.
     * @param alreadyUploaded 已上传字节数 / Bytes already uploaded.
     * @return [SdkResult] 传输结果 / Transfer result.
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
            put("size", file.length().toString())
            put("md5", md5)
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
                    SdkResult.failure(SdkError.DeviceRejected(null, msg))
                } else {
                    fileTransport.transferFile(file, fileType, alreadyUploaded, session.totalBytes, { cancelled.get() }) {
                        _progressFlow.value = it
                    }
                }
            }
        }
    }

    /**
     * 校验升级结果 / Verify upgrade result.
     *
     * @param session 当前会话 / Current session.
     * @return [SdkResult] 校验结果 / Verification result.
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
                            return SdkResult.failure(SdkError.DeviceRejected(null, msg))
                        }
                    }
                    return SdkResult.success(Unit)
                }

                if (!OtaToolstools.responseLooksSuccessful(responseJson)) {
                    val msg = responseJson.optString("msg", "Upgrade verify failed")
                    _phaseFlow.value = UpgradePhase.ERROR
                    _progressFlow.value = UnifiedUpgradeProgress(phase = UpgradePhase.ERROR, error = msg)
                    return SdkResult.failure(SdkError.DeviceRejected(null, msg))
                }

                SdkResult.success(Unit)
            }
        }
    }

    /**
     * 提交升级 / Commit the upgrade.
     *
     * @param reboot 是否重启 / Whether to reboot.
     * @return [SdkResult] 提交结果 / Commit result.
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
     * 发送命令并挂起等待响应 / Send a command and suspend awaiting response.
     *
     * @param cmd 命令名 / Command name.
     * @param params 命令参数 / Command parameters.
     * @param timeoutMs 超时时间（毫秒）/ Timeout in milliseconds.
     * @param allowTimeoutAsSuccess 超时是否视为成功 / Whether timeout should be treated as success.
     * @return [SdkResult] 包含 JSON 响应的结果 / Result containing JSON response.
     */
    private suspend fun sendCommandAwait(
        cmd: String,
        params: JSONObject,
        timeoutMs: Long,
        allowTimeoutAsSuccess: Boolean = false
    ): SdkResult<JSONObject> = suspendCancellableCoroutine { continuation ->
        cdcSender.sendCommand(cmd, params, timeoutMs) { _, response, error ->
            if (continuation.isCancelled) return@sendCommand

            if (error != null) {
                if (allowTimeoutAsSuccess && error.contains("Timeout", ignoreCase = true)) {
                    continuation.resume(
                        SdkResult.success(
                            JSONObject().apply {
                                put("cmd", cmd)
                                put("status", "ok")
                                put("msg", "Command sent; device may reboot before ACK")
                            }
                        )
                    )
                } else {
                    continuation.resume(SdkResult.failure(SdkError.DeviceRejected(null, error)))
                }
                return@sendCommand
            }

            if (response == null) {
                continuation.resume(SdkResult.failure(SdkError.InvalidResponse(reason = "Empty response")))
                return@sendCommand
            }

            continuation.resume(SdkResult.success(response))
        }
    }

    /**
     * 基于回调执行升级（内部兼容逻辑）/ Execute upgrade with callback (internal compatibility logic).
     *
     * @param mode 升级模式 / Upgrade mode.
     * @param firmwareFile 固件文件 / Firmware file.
     * @param modelTxtFile 模型信息文件 / Model info file.
     * @param modelFpkFile 模型数据文件 / Model data file.
     * @param modelVersion 模型版本 / Model version.
     * @param callback 传输回调 / Transfer callback.
     */
    private fun executeWithCallback(
        mode: UpgradeMode,
        firmwareFile: File?,
        modelTxtFile: File?,
        modelFpkFile: File?,
        modelVersion: String?,
        callback: OtaFileTransport.TransferCallback
    ) {
        val worker = Thread {
            val result = runBlocking {
                executeUpgrade(mode, firmwareFile, modelTxtFile, modelFpkFile, modelVersion, reboot = true)
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
}
