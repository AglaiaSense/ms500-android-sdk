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
 * OTA 文件传输器 / OTA file transporter.
 *
 * 负责真实的文件数据传输：
 * - 按 chunk 切分文件
 * - 使用 MSG_TYPE_FILE_DATA 封包发送
 * - 计算实时速度与预计剩余时间（ETA）
 * - 处理用户取消与连接中断异常
 *
 * Responsibilities:
 * - Chunk splitting
 * - MSG_TYPE_FILE_DATA framing and sending
 * - Speed / ETA calculation
 * - Cancellation and connection-loss handling
 */
class OtaFileTransport(
    /** CDC 端口连接 / CDC port connection. */
    private val cdcClient: CdcPortConnection
) {
    companion object {
        /** 固件传输默认超时（毫秒）/ Default timeout for firmware transfer, in ms. */
        const val DEFAULT_FIRMWARE_TIMEOUT = 120000L

        /** 文本文件传输默认超时（毫秒）/ Default timeout for text-file transfer, in ms. */
        const val DEFAULT_TXT_TIMEOUT = 30000L

        /** FPK 文件传输默认超时（毫秒）/ Default timeout for FPK transfer, in ms. */
        const val DEFAULT_FPK_TIMEOUT = 300000L
    }

    /**
     * 传输进度数据类 / Transfer progress data class.
     *
     * @property bytesSent 已发送字节数 / Bytes already sent.
     * @property totalBytes 总字节数 / Total bytes.
     * @property percentage 当前百分比（0-100）/ Current percentage.
     * @property speedBytesPerSecond 当前速度（字节/秒）/ Current speed in bytes per second.
     * @property etaSeconds 预计剩余时间（秒）/ Estimated time remaining in seconds.
     * @property phase 当前传输阶段 / Current transfer phase.
     */
    data class Progress(
        val bytesSent: Long,
        val totalBytes: Long,
        val percentage: Int,
        val speedBytesPerSecond: Long,
        val etaSeconds: Int,
        val phase: TransferPhase
    ) {
        /**
         * 传输阶段枚举 / Transfer phase enumeration.
         */
        enum class TransferPhase {
            /** 准备中 / Preparing. */
            PREPARING,

            /** 传输中 / Transferring. */
            TRANSFERRING,

            /** 校验中 / Verifying. */
            VERIFYING,

            /** 已完成 / Completed. */
            COMPLETED,

            /** 出错 / Error. */
            ERROR
        }
    }

    /**
     * 传输回调接口 / Transfer callback interface.
     */
    interface TransferCallback {
        /**
         * 进度更新 / Called when progress updates.
         *
         * @param progress 当前进度 / Current progress.
         */
        fun onProgress(progress: Progress)

        /**
         * 传输成功 / Called when transfer succeeds.
         */
        fun onSuccess()

        /**
         * 传输失败 / Called when transfer fails.
         *
         * @param error 错误信息 / Error message.
         */
        fun onError(error: String)
    }

    /**
     * 执行文件传输 / Perform file transfer.
     *
     * @param file 待传输文件 / File to transfer.
     * @param fileType 文件类型 / File type.
     * @param alreadyUploaded 已上传字节数（用于多文件累计进度）/ Bytes already uploaded (for cumulative progress).
     * @param totalBytes 总字节数 / Total bytes.
     * @param isCancelled 取消判断 lambda / Lambda to check if cancelled.
     * @param onProgress 进度回调 / Progress callback.
     * @return [SdkResult] 成功或失败结果 / Success or failure result.
     */
    suspend fun transferFile(
        file: File,
        fileType: UpgradeFileType,
        alreadyUploaded: Long,
        totalBytes: Long,
        isCancelled: () -> Boolean,
        onProgress: (UnifiedUpgradeProgress) -> Unit
    ): SdkResult<Unit> {
        /** 当前文件已发送字节数 / Bytes sent for the current file. */
        var bytesSentForCurrentFile = 0L

        /** 传输开始时间 / Transfer start time. */
        val startTime = System.currentTimeMillis()

        /** 上次进度更新时间 / Last progress update time. */
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
