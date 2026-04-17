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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class OtaUnifiedManage(
    private val cdcClient: CdcPortConnection,
    private val cdcSender: CdcSender,
    private val filesDir: File
) {
    companion object {
        private const val TAG = "OtaUnifiedManage"
    }

    private data class UpgradeSession(
        val mode: UpgradeMode,
        val firmwareFile: File?,
        val modelTxtFile: File?,
        val modelFpkFile: File?,
        val modelVersion: String?,
        val files: UpgradeFiles,
        val totalBytes: Long
    )

    private val fileTransport = OtaFileTransport(cdcClient)
    private val mutex = Mutex()
    private val cancelled = AtomicBoolean(false)
    private val _phaseFlow = MutableStateFlow(UpgradePhase.IDLE)
    val phaseFlow: StateFlow<UpgradePhase> = _phaseFlow.asStateFlow()
    private val _progressFlow = MutableStateFlow<UnifiedUpgradeProgress?>(null)
    val progressFlow: StateFlow<UnifiedUpgradeProgress?> = _progressFlow.asStateFlow()
    private var currentSession: UpgradeSession? = null

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

    fun firmwareUpdateFlow(file: File, callback: OtaFileTransport.TransferCallback) {
        executeWithCallback(UpgradeMode.FIRMWARE, file, null, null, null, callback)
    }

    fun aiModelUpdateFlow(txtFile: File, fpkFile: File, modelVersion: String, callback: OtaFileTransport.TransferCallback) {
        executeWithCallback(UpgradeMode.MODEL, null, txtFile, fpkFile, modelVersion, callback)
    }

    fun combinedUpdateFlow(
        firmwareFile: File,
        modelTxtFile: File,
        modelFpkFile: File,
        modelVersion: String,
        callback: OtaFileTransport.TransferCallback
    ) {
        executeWithCallback(UpgradeMode.COMBINED, firmwareFile, modelTxtFile, modelFpkFile, modelVersion, callback)
    }

    fun cancelTransfer() {
        cancelled.set(true)
        if (cdcClient.isConnected()) {
            cdcSender.sendCommand("upgrade_cancel", mapOf("reason" to "User cancelled"), 2_000L, null)
        }
        currentSession = null
        _phaseFlow.value = UpgradePhase.IDLE
        _progressFlow.value = null
    }

    fun release() {
        cancelTransfer()
    }

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

    private fun executeWithCallback(
        mode: UpgradeMode,
        firmwareFile: File?,
        modelTxtFile: File?,
        modelFpkFile: File?,
        modelVersion: String?,
        callback: OtaFileTransport.TransferCallback
    ) {
        val worker = Thread {
            val result = kotlinx.coroutines.runBlocking {
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
