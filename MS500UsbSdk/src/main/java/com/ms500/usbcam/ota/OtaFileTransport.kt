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
 * OTA file transporter.
 *
 * Responsibilities:
 * - Chunk splitting
 * - MSG_TYPE_FILE_DATA framing and sending
 * - Speed / ETA calculation
 * - Cancellation and connection-loss handling
 */
class OtaFileTransport(
    /** CDC port connection. */
    private val cdcClient: CdcPortConnection
) {
    companion object {
        /** Default timeout for firmware transfer, in ms. */
        const val DEFAULT_FIRMWARE_TIMEOUT = 120000L

        /** Default timeout for text-file transfer, in ms. */
        const val DEFAULT_TXT_TIMEOUT = 30000L

        /** Default timeout for FPK transfer, in ms. */
        const val DEFAULT_FPK_TIMEOUT = 300000L
    }

    /**
     * Transfer progress data class.
     *
     * Bytes already sent.
     * Total bytes.
     * @property percentage Current percentage (0-100).
     * @property speedBytesPerSecond Current speed in bytes per second.
     * @property etaSeconds Estimated time remaining in seconds.
     * Current transfer phase.
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
         * Transfer phase enumeration.
         */
        enum class TransferPhase {
            /** Preparing. */
            PREPARING,

            /** Transferring. */
            TRANSFERRING,

            /** Verifying. */
            VERIFYING,

            /** Completed. */
            COMPLETED,

            /** Error. */
            ERROR
        }
    }

    /**
     * Transfer callback interface.
     */
    interface TransferCallback {
        /**
         * Called when progress updates.
         *
         * Current progress.
         */
        fun onProgress(progress: Progress)

        /**
         * Called when transfer succeeds.
         */
        fun onSuccess()

        /**
         * Called when transfer fails.
         *
         * Error message.
         */
        fun onError(error: String)
    }

    /**
     * Perform file transfer.
     *
     * File to transfer.
     * File type.
     * @param alreadyUploaded Bytes already uploaded (for cumulative progress).
     * Total bytes.
     * Lambda to check if cancelled.
     * Progress callback.
     * Success or failure result.
     */
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
