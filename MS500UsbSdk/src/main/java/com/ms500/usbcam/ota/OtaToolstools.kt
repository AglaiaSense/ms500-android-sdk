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
 * OTA 通用函数集合。
 *
 * 这个文件不负责升级流程编排，只提供“无状态”的公共能力。
 * 设计目标是让 OtaUnifiedManage、OtaParamApiImpl、UpdateDialog 都能复用这些基础函数。
 */
object OtaToolstools {
    // 文件通路分块大小。MSG_TYPE_FILE_DATA 会按这个大小循环发送。
    const val CHUNK_SIZE = 16 * 1024

    // 命令通路各阶段的默认超时。
    const val TIMEOUT_START = 10_000L
    const val TIMEOUT_FILE_PREPARE = 15_000L
    const val TIMEOUT_VERIFY = 30_000L
    const val TIMEOUT_COMMIT = 3_000L

    // 两个数据块之间的小延时，给设备侧留出处理时间。
    const val CHUNK_DELAY_MS = 10L

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

    fun isReadableFile(file: File?): Boolean {
        return file != null && file.exists() && file.isFile && file.canRead() && file.length() > 0L
    }

    fun percentOf(uploaded: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((uploaded * 100L) / total).toInt().coerceIn(0, 100)
    }

    fun responseLooksSuccessful(json: JSONObject): Boolean {
        // 设备侧返回字段并不完全固定，所以这里做一个宽松但集中管理的成功判断。
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

    fun phaseFor(fileType: UpgradeFileType): UpgradePhase {
        // 把“当前文件类型”映射成“当前 OTA 阶段”。
        return when (fileType) {
            UpgradeFileType.FIRMWARE -> UpgradePhase.TRANSFER_FIRMWARE
            UpgradeFileType.MODEL_INFO -> UpgradePhase.TRANSFER_MODEL_INFO
            UpgradeFileType.MODEL_FPK -> UpgradePhase.TRANSFER_MODEL_FPK
        }
    }

    fun toTransferProgress(progress: UnifiedUpgradeProgress): OtaFileTransport.Progress {
        // 旧 UI 还在使用 OtaFileTransport.Progress，这里负责把统一进度模型做一次兼容转换。
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

    fun normalizeSensorId(value: String?): String? {
        // UI 层和 OTA 校验层都需要这类无状态格式化函数，适合放在 tools 层统一复用。
        return value
            ?.trim()
            ?.replace(" ", "")
            ?.uppercase()
            ?.takeIf { it.isNotEmpty() }
    }
}
