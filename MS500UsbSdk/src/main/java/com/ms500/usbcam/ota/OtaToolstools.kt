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

import com.ms500.usbcam.dto.UnifiedUpgradeProgress
import com.ms500.usbcam.dto.UpgradeFileType
import com.ms500.usbcam.dto.UpgradePhase
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * OTA common utilities.
 *
 * This file does not orchestrate the upgrade flow; it only provides stateless utilities.
 * Designed to be reused by OtaUnifiedManage, OtaParamApiImpl, UpdateDialog, etc.
 */
object OtaToolstools {
    private const val USB_GAZE_FIRMWARE_PREFIX = "usb_gaze"
    private const val SONY_GAZE_MODEL_PREFIX = "sony_gaze"

    /** Chunk size for file data transfer, in bytes. */
    const val CHUNK_SIZE = 16 * 1024

    /** Default timeout for start phase, in ms. */
    const val TIMEOUT_START = 10_000L

    /** Default timeout for file-prepare phase, in ms. */
    const val TIMEOUT_FILE_PREPARE = 15_000L

    /** Default timeout for verify phase, in ms. */
    const val TIMEOUT_VERIFY = 30_000L

    /** Default timeout for commit phase, in ms. */
    const val TIMEOUT_COMMIT = 3_000L

    /** Small delay between chunks to give device time to process. */
    const val CHUNK_DELAY_MS = 10L

    /**
     * Calculate file MD5.
     *
     * Target file.
     * Lowercase hex MD5 string.
     */
    fun calculateMD5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Check whether a file is readable and non-empty.
     *
     * File to check.
     * True if valid.
     */
    fun isReadableFile(file: File?): Boolean {
        return file != null && file.exists() && file.isFile && file.canRead() && file.length() > 0L
    }

    /**
     * Calculate upload percentage.
     *
     * Bytes uploaded.
     * Total bytes.
     * Percentage from 0 to 100.
     */
    fun percentOf(uploaded: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((uploaded * 100L) / total).toInt().coerceIn(0, 100)
    }

    /**
     * Determine whether the device response JSON indicates success.
     *
     * Device responses are not fully standardized, so this uses a lenient but centralized check.
     *
     * Device response JSON.
     * True if successful.
     */
    fun responseLooksSuccessful(json: JSONObject): Boolean {
        val status = json.optString("status", "")
        val ret = json.optString("ret", "")
        val msg = json.optString("msg", "")
        return status.equals("ok", ignoreCase = true) ||
            status.equals("success", ignoreCase = true) ||
            ret.equals("ok", ignoreCase = true) ||
            ret.equals("success", ignoreCase = true) ||
            msg.contains("ready", ignoreCase = true) ||
            msg.contains("verified", ignoreCase = true) ||
            msg.contains("complete", ignoreCase = true) ||
            msg.contains("committed", ignoreCase = true)
    }

    /**
     * Map file type to upgrade phase.
     *
     * Current file type.
     * Corresponding upgrade phase.
     */
    fun phaseFor(fileType: UpgradeFileType): UpgradePhase {
        return when (fileType) {
            UpgradeFileType.FIRMWARE -> UpgradePhase.TRANSFER_FIRMWARE
            UpgradeFileType.MODEL_INFO -> UpgradePhase.TRANSFER_MODEL_INFO
            UpgradeFileType.MODEL_FPK -> UpgradePhase.TRANSFER_MODEL_FPK
        }
    }

    /**
     * Convert unified progress to legacy transport progress.
     *
     * Legacy UI still uses [OtaFileTransport.Progress]; this performs compatibility conversion.
     *
     * Unified progress object.
     * Legacy progress object.
     */
    fun toTransferProgress(progress: UnifiedUpgradeProgress): OtaFileTransport.Progress {
        val phase = when (progress.phase) {
            UpgradePhase.PREPARING -> OtaFileTransport.Progress.TransferPhase.PREPARING
            UpgradePhase.TRANSFER_FIRMWARE,
            UpgradePhase.TRANSFER_MODEL_INFO,
            UpgradePhase.TRANSFER_MODEL_FPK -> OtaFileTransport.Progress.TransferPhase.TRANSFERRING
            UpgradePhase.VERIFYING -> OtaFileTransport.Progress.TransferPhase.VERIFYING
            UpgradePhase.COMMITTING,
            UpgradePhase.COMPLETED -> OtaFileTransport.Progress.TransferPhase.COMPLETED
            UpgradePhase.ERROR -> OtaFileTransport.Progress.TransferPhase.ERROR
            else -> OtaFileTransport.Progress.TransferPhase.PREPARING
        }
        return OtaFileTransport.Progress(
            bytesSent = progress.uploadedBytes,
            totalBytes = progress.totalBytes,
            percentage = progress.percentage,
            speedBytesPerSecond = progress.speed,
            etaSeconds = progress.etaSeconds,
            phase = phase
        )
    }

    /**
     * Normalize sensor ID.
     *
     * Removes leading/trailing and internal spaces, converts to uppercase, returns null for empty values.
     * Both UI layer and OTA verification layer need such stateless formatting functions, suitable for unified reuse in the tools layer.
     *
     * Raw sensor ID.
     * Normalized sensor ID or null.
     */
    fun normalizeSensorId(value: String?): String? {
        return value
            ?.trim()
            ?.replace(" ", "")
            ?.uppercase()
            ?.takeIf { it.isNotEmpty() }
    }

    /**
     * Extract the full firmware version string from a raw firmware binary.
     *
     * Prefers custom versions such as usb_gaze_1.1.3_20260520_172604 and
     * falls back to plain semantic versions when only x.y.z is present.
     */
    fun extractFirmwareVersion(file: File): String? {
        if (!isReadableFile(file)) return null

        return try {
            val searchBytes = FileInputStream(file).use { input ->
                val limit = 256 * 1024
                val buffer = ByteArray(limit)
                val read = input.read(buffer)
                if (read <= 0) ByteArray(0) else buffer.copyOf(read)
            }
            extractFirmwareVersion(searchBytes)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extract the full firmware version string from raw bytes.
     */
    fun extractFirmwareVersion(data: ByteArray): String? {
        if (data.isEmpty()) return null

        val text = buildString(data.size) {
            data.forEach { byte ->
                val ch = byte.toInt().toChar()
                append(if (ch.code in 32..126) ch else '\u0000')
            }
        }

        val fullMatch = Regex("""[A-Za-z_][A-Za-z0-9_]*_\d+\.\d+\.\d+_\d{8}_\d{6}""").find(text)
        if (fullMatch != null) return fullMatch.value

        val shortMatch = Regex("""\d+\.\d+\.\d+""").find(text)
        return shortMatch?.value
    }

    /**
     * Returns true only for firmware version strings that explicitly identify usb_gaze firmware.
     */
    fun isUsbGazeFirmwareVersion(value: String?): Boolean {
        val normalized = value?.trim().orEmpty()
        return normalized.startsWith("${USB_GAZE_FIRMWARE_PREFIX}_", ignoreCase = true)
    }

    /**
     * Returns true when package header fields identify usb_gaze firmware.
     */
    fun isUsbGazeFirmware(name: String?, version: String?): Boolean {
        val normalizedName = name?.trim().orEmpty()
        return normalizedName.equals(USB_GAZE_FIRMWARE_PREFIX, ignoreCase = true) ||
            normalizedName.startsWith("${USB_GAZE_FIRMWARE_PREFIX}_", ignoreCase = true) ||
            isUsbGazeFirmwareVersion(version)
    }

    /**
     * Returns true when package header fields identify a sony_gaze model.
     */
    fun isSonyGazeModel(name: String?): Boolean {
        val normalizedName = name?.trim().orEmpty()
        return normalizedName.equals(SONY_GAZE_MODEL_PREFIX, ignoreCase = true) ||
            normalizedName.startsWith("${SONY_GAZE_MODEL_PREFIX}_", ignoreCase = true)
    }

    /**
     * Compare current and target versions.
     *
     * Returns:
     * - negative when target is newer
     * - zero when equal
     * - positive when target is older
     * - null when either side cannot be parsed
     */
    fun compareVersionStrings(current: String?, target: String?): Int? {
        val currentParts = extractVersionParts(current) ?: return null
        val targetParts = extractVersionParts(target) ?: return null

        val limit = maxOf(currentParts.size, targetParts.size)
        for (index in 0 until limit) {
            val currentValue = currentParts.getOrElse(index) { 0 }
            val targetValue = targetParts.getOrElse(index) { 0 }
            if (targetValue != currentValue) {
                return if (targetValue > currentValue) -1 else 1
            }
        }
        return 0
    }

    private fun extractVersionParts(value: String?): List<Int>? {
        val match = Regex("""(\d+)\.(\d+)\.(\d+)(?:_(\d{8})_(\d{6}))?""").find(value.orEmpty()) ?: return null
        val groups = match.groupValues
        return listOf(
            groups[1].toIntOrNull() ?: return null,
            groups[2].toIntOrNull() ?: return null,
            groups[3].toIntOrNull() ?: return null,
            groups.getOrNull(4)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0,
            groups.getOrNull(5)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
        )
    }
}
