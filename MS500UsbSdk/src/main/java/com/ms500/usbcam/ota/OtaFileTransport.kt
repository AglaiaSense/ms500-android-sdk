package com.ms500.usbcam.ota

import com.ms500.usbcam.cdc.CdcProtocol
import com.ms500.usbcam.cdc.CdcPortConnection
import com.ms500.usbcam.dto.UnifiedUpgradeProgress
import com.ms500.usbcam.dto.UpgradeFileType
import com.ms500.usbcam.result.SdkResult
import com.ms500.usbcam.result.SdkError
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException

/**
 * OTA 文件传输器。
 *
 * 这里只负责真实文件传输：
 * - chunk 切分
 * - MSG_TYPE_FILE_DATA 封包发送
 * - 速度/ETA 计算
 * - 传输取消与连接中断处理
 */
class OtaFileTransport(
    private val cdcClient: CdcPortConnection
) {
    companion object {
        const val DEFAULT_FIRMWARE_TIMEOUT = 120000L
        const val DEFAULT_TXT_TIMEOUT = 30000L
        const val DEFAULT_FPK_TIMEOUT = 300000L
    }

    data class Progress(
        val bytesSent: Long,
        val totalBytes: Long,
        val percentage: Int,
        val speedBytesPerSecond: Long,
        val etaSeconds: Int,
        val phase: TransferPhase
    ) {
        enum class TransferPhase {
            PREPARING,
            TRANSFERRING,
            VERIFYING,
            COMPLETED,
            ERROR
        }
    }

    interface TransferCallback {
        fun onProgress(progress: Progress)
        fun onSuccess()
        fun onError(error: String)
    }

    suspend fun transferFile(
        file: File,
        fileType: UpgradeFileType,
        alreadyUploaded: Long,
        totalBytes: Long,
        isCancelled: () -> Boolean,
        onProgress: (UnifiedUpgradeProgress) -> Unit
    ): SdkResult<Unit> {
        var bytesSentForCurrentFile = 0L
        val startTime = System.currentTimeMillis()
        var lastProgressUpdateTime = 0L

        return try {
            file.inputStream().use { input ->
                val buffer = ByteArray(OtaToolstools.CHUNK_SIZE)
                var read: Int

                while (input.read(buffer).also { read = it } != -1) {
                    if (isCancelled()) {
                        throw CancellationException("Transfer cancelled")
                    }
                    if (!cdcClient.isConnected()) {
                        throw IOException("CDC connection lost")
                    }

                    val chunk = if (read == OtaToolstools.CHUNK_SIZE) buffer else buffer.copyOf(read)
                    cdcClient.send(CdcProtocol.buildFrame(CdcProtocol.MSG_TYPE_FILE_DATA, chunk))

                    bytesSentForCurrentFile += read
                    val overallUploaded = alreadyUploaded + bytesSentForCurrentFile
                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdateTime >= 100 || overallUploaded == totalBytes) {
                        val elapsedMs = (now - startTime).coerceAtLeast(1L)
                        val speed = (bytesSentForCurrentFile * 1000L) / elapsedMs
                        val remaining = (totalBytes - overallUploaded).coerceAtLeast(0L)
                        val eta = if (speed > 0) (remaining / speed).toInt() else 0

                        onProgress(
                            UnifiedUpgradeProgress(
                                phase = OtaToolstools.phaseFor(fileType),
                                fileType = fileType,
                                uploadedBytes = overallUploaded,
                                totalBytes = totalBytes,
                                percentage = OtaToolstools.percentOf(overallUploaded, totalBytes),
                                speed = speed,
                                etaSeconds = eta
                            )
                        )
                        lastProgressUpdateTime = now
                    }

                    if (OtaToolstools.CHUNK_DELAY_MS > 0) {
                        Thread.sleep(OtaToolstools.CHUNK_DELAY_MS)
                    }
                }
            }

            SdkResult.success(Unit)
        } catch (e: CancellationException) {
            SdkResult.failure(SdkError.Upgrade.Cancelled)
        } catch (e: IOException) {
            SdkResult.failure(SdkError.CommunicationError(e.message ?: "Transfer failed"))
        } catch (e: Exception) {
            SdkResult.failure(SdkError.InternalError(e))
        }
    }
}





