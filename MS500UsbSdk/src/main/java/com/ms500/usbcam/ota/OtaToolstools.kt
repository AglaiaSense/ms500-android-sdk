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
 * OTA 通用函数集合 / OTA common utilities.
 *
 * 不负责升级流程编排，只提供“无状态”的公共能力。
 * 设计目标是让 OtaUnifiedManage、OtaParamApiImpl、UpdateDialog 都能复用这些基础函数。
 *
 * This file does not orchestrate the upgrade flow; it only provides stateless utilities.
 * Designed to be reused by OtaUnifiedManage, OtaParamApiImpl, UpdateDialog, etc.
 */
object OtaToolstools {
    /** 文件通路分块大小（字节）。MSG_TYPE_FILE_DATA 会按此大小循环发送。 / Chunk size for file data transfer, in bytes. */
    const val CHUNK_SIZE = 16 * 1024

    /** 启动阶段默认超时（毫秒）/ Default timeout for start phase, in ms. */
    const val TIMEOUT_START = 10_000L

    /** 文件准备阶段默认超时（毫秒）/ Default timeout for file-prepare phase, in ms. */
    const val TIMEOUT_FILE_PREPARE = 15_000L

    /** 校验阶段默认超时（毫秒）/ Default timeout for verify phase, in ms. */
    const val TIMEOUT_VERIFY = 30_000L

    /** 提交阶段默认超时（毫秒）/ Default timeout for commit phase, in ms. */
    const val TIMEOUT_COMMIT = 3_000L

    /** 两个数据块之间的小延时（毫秒），给设备侧留出处理时间。 / Small delay between chunks to give device time to process. */
    const val CHUNK_DELAY_MS = 10L

    /**
     * 计算文件 MD5 / Calculate file MD5.
     *
     * @param file 目标文件 / Target file.
     * @return 小写十六进制 MD5 字符串 / Lowercase hex MD5 string.
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
     * 判断文件是否可读且非空 / Check whether a file is readable and non-empty.
     *
     * @param file 待检查文件 / File to check.
     * @return true 表示有效文件 / True if valid.
     */
    fun isReadableFile(file: File?): Boolean {
        return file != null && file.exists() && file.isFile && file.canRead() && file.length() > 0L
    }

    /**
     * 计算上传百分比 / Calculate upload percentage.
     *
     * @param uploaded 已上传字节数 / Bytes uploaded.
     * @param total 总字节数 / Total bytes.
     * @return 0-100 的百分比 / Percentage from 0 to 100.
     */
    fun percentOf(uploaded: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((uploaded * 100L) / total).toInt().coerceIn(0, 100)
    }

    /**
     * 判断设备返回的 JSON 是否表示成功 / Determine whether the device response JSON indicates success.
     *
     * 设备侧返回字段并不完全固定，因此使用宽松但集中管理的成功判断。
     * Device responses are not fully standardized, so this uses a lenient but centralized check.
     *
     * @param json 设备返回的 JSON / Device response JSON.
     * @return true 表示成功 / True if successful.
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
     * 将文件类型映射为升级阶段 / Map file type to upgrade phase.
     *
     * @param fileType 当前文件类型 / Current file type.
     * @return 对应的 [UpgradePhase] / Corresponding upgrade phase.
     */
    fun phaseFor(fileType: UpgradeFileType): UpgradePhase {
        return when (fileType) {
            UpgradeFileType.FIRMWARE -> UpgradePhase.TRANSFER_FIRMWARE
            UpgradeFileType.MODEL_INFO -> UpgradePhase.TRANSFER_MODEL_INFO
            UpgradeFileType.MODEL_FPK -> UpgradePhase.TRANSFER_MODEL_FPK
        }
    }

    /**
     * 将统一进度模型转换为旧版传输进度 / Convert unified progress to legacy transport progress.
     *
     * 旧 UI 仍在使用 [OtaFileTransport.Progress]，这里负责兼容转换。
     * Legacy UI still uses [OtaFileTransport.Progress]; this performs compatibility conversion.
     *
     * @param progress 统一进度对象 / Unified progress object.
     * @return 旧版进度对象 / Legacy progress object.
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
     * 规范化 Sensor ID / Normalize sensor ID.
     *
     * 去除首尾空格与内部空格，转为大写，空值返回 null。
     * UI 层和 OTA 校验层都需要这类无状态格式化函数，适合放在 tools 层统一复用。
     *
     * @param value 原始 Sensor ID / Raw sensor ID.
     * @return 规范化后的 Sensor ID 或 null / Normalized sensor ID or null.
     */
    fun normalizeSensorId(value: String?): String? {
        return value
            ?.trim()
            ?.replace(" ", "")
            ?.uppercase()
            ?.takeIf { it.isNotEmpty() }
    }
}
