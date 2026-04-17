/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */

package com.ms500.usbcam.dto

import java.io.File
import org.json.JSONObject

private const val OTA_PACKAGE_TYPE_FIRMWARE = 0x00000001
private const val OTA_PACKAGE_TYPE_MODEL = 0x00000002
private const val OTA_PACKAGE_TYPE_COMBINED = 0x00000003

/**
 * 升级包类型 / OTA package type
 */
enum class PackageType(val value: Int) {
    /** 固件包 / Firmware package */
    FIRMWARE(OTA_PACKAGE_TYPE_FIRMWARE),
    /** 模型包 / Model package */
    MODEL(OTA_PACKAGE_TYPE_MODEL),
    /** 组合包 / Combined package */
    COMBINED(OTA_PACKAGE_TYPE_COMBINED),
    /** 未知类型 / Unknown type */
    UNKNOWN(-1);

    companion object {
        /** 固件包类型值 / Firmware package type value */
        const val PACKAGE_TYPE_FIRMWARE = OTA_PACKAGE_TYPE_FIRMWARE

        /** 模型包类型值 / Model package type value */
        const val PACKAGE_TYPE_MODEL = OTA_PACKAGE_TYPE_MODEL

        /** 组合包类型值 / Combined package type value */
        const val PACKAGE_TYPE_COMBINED = OTA_PACKAGE_TYPE_COMBINED

        /**
         * 根据数值获取包类型 / Get package type from value
         *
         * @param value 类型数值
         * @return [PackageType]
         */
        fun fromValue(value: Int): PackageType {
            return values().firstOrNull { it.value == value } ?: UNKNOWN
        }
    }
}

/**
 * 固件信息 / Firmware info
 */
data class FirmwareInfo(
    /** 固件名称 / Firmware name */
    val name: String,
    /** 版本号 / Version number */
    val version: String,
    /** 版本字符串 / Version string */
    val versionString: String,
    /** 文件大小 / File size */
    val fileSize: Long,
    /** MD5 校验值 / MD5 checksum */
    val md5: String,
    /** 是否有效 / Whether valid */
    val isValid: Boolean
)

/**
 * 模型信息 / Model info
 */
data class ModelInfo(
    /** 模型名称 / Model name */
    val name: String,
    /** 版本号 / Version number */
    val version: String,
    /** 版本字符串 / Version string */
    val versionString: String,
    /** 文件大小 / File size */
    val fileSize: Long,
    /** Sensor ID / Sensor identifier */
    val sensorId: String,
    /** 是否有效 / Whether valid */
    val isValid: Boolean
)

/**
 * 包校验结果 / Package verification result
 */
data class PackageVerification(
    /** 包类型 / Package type */
    val type: PackageType,
    /** 固件信息 / Firmware info */
    val firmware: FirmwareInfo? = null,
    /** 模型信息 / Model info */
    val model: ModelInfo? = null,
    /** 固件文件 / Firmware file */
    val firmwareFile: File? = null,
    /** 模型信息文件 / Model info file */
    val modelInfoFile: File? = null,
    /** 模型数据文件 / Model data file */
    val modelDataFile: File? = null,
    /** 模型文件（默认指向 modelDataFile） / Model file (defaults to modelDataFile) */
    val modelFile: File? = modelDataFile,
    /** 是否有效 / Whether valid */
    val isValid: Boolean = true,
    /** 错误信息 / Error message */
    val error: String? = null
) {
    /** 是否包含固件 / Whether firmware is present */
    val hasFirmware: Boolean get() = firmware != null && firmwareFile != null
    /** 是否包含模型 / Whether model is present */
    val hasModel: Boolean get() = model != null && modelInfoFile != null && modelDataFile != null

    /**
     * 针对设备信息进行校验 / Validate against device info
     *
     * @param deviceInfo 设备信息
     * @return 错误信息列表
     */
    fun validateAgainst(deviceInfo: DeviceParamDto): List<String> {
        val errors = mutableListOf<String>()

        firmware?.let { fw ->
            if (!fw.isValid) {
                errors.add("Firmware package header magic number invalid")
            }
            val deviceFirmwareName = extractFirmwareName(deviceInfo.firmwareVersion)
            if (!fw.name.isBlank() && !deviceFirmwareName.isNullOrBlank() && !fw.name.equals(deviceFirmwareName, ignoreCase = true)) {
                errors.add("Firmware name mismatch: ${fw.name} vs $deviceFirmwareName")
            }
        }

        model?.let { md ->
            if (!md.isValid) {
                errors.add("Model package header magic number invalid")
            }
        }

        return errors
    }

    private fun extractFirmwareName(firmwareVersion: String?): String? {
        val value = firmwareVersion?.trim().orEmpty()
        if (value.isEmpty()) return null

        val versionStart = Regex("""_\d+\.\d+\.\d+""").find(value)?.range?.first ?: return null
        return value.substring(0, versionStart).takeIf { it.isNotBlank() }
    }
}

/**
 * 升级状态 / Upgrade state
 */
enum class UpgradeState {
    /** 空闲 / Idle */
    IDLE,
    /** 准备中 / Preparing */
    PREPARING,
    /** 上传中 / Uploading */
    UPLOADING,
    /** 校验中 / Verifying */
    VERIFYING,
    /** 刷写中 / Flashing */
    FLASHING,
    /** 提交中 / Committing */
    COMMITTING,
    /** 已完成 / Completed */
    COMPLETED,
    /** 失败 / Failed */
    FAILED
}

/**
 * 升级进度 / Upgrade progress
 */
data class UpgradeProgress(
    /** 当前状态 / Current state */
    val state: UpgradeState = UpgradeState.IDLE,
    /** 完成百分比 / Completion percentage */
    val percentage: Int = 0,
    /** 已上传字节数 / Uploaded bytes */
    val uploadedBytes: Long = 0,
    /** 总字节数 / Total bytes */
    val totalBytes: Long = 0,
    /** 传输速度（字节/秒） / Transfer speed (bytes/sec) */
    val speed: Long = 0,
    /** 预计剩余时间（秒） / Estimated time remaining (seconds) */
    val etaSeconds: Int = 0,
    /** 错误信息 / Error message */
    val error: String? = null,
    /** 是否可取消 / Whether cancellable */
    val isCancellable: Boolean = true
)

/**
 * 升级模式 / Upgrade mode
 */
enum class UpgradeMode {
    /** 无 / None */
    NONE,
    /** 仅固件 / Firmware only */
    FIRMWARE,
    /** 仅模型 / Model only */
    MODEL,
    /** 组合升级 / Combined */
    COMBINED
}

/**
 * 升级阶段 / Upgrade phase
 */
enum class UpgradePhase {
    /** 空闲 / Idle */
    IDLE,
    /** 准备中 / Preparing */
    PREPARING,
    /** 传输固件 / Transferring firmware */
    TRANSFER_FIRMWARE,
    /** 传输模型信息 / Transferring model info */
    TRANSFER_MODEL_INFO,
    /** 传输模型 FPK / Transferring model FPK */
    TRANSFER_MODEL_FPK,
    /** 校验中 / Verifying */
    VERIFYING,
    /** 提交中 / Committing */
    COMMITTING,
    /** 错误 / Error */
    ERROR,
    /** 已完成 / Completed */
    COMPLETED
}

/**
 * 升级文件类型 / Upgrade file type
 */
enum class UpgradeFileType(val value: String) {
    /** 固件 / Firmware */
    FIRMWARE("firmware"),
    /** 模型信息 / Model info */
    MODEL_INFO("model_info"),
    /** 模型 FPK / Model FPK */
    MODEL_FPK("model_fpk")
}

/**
 * 文件信息 / File info
 */
data class FileInfo(
    /** 文件大小 / File size */
    val size: Long,
    /** MD5 校验值 / MD5 checksum */
    val md5: String
) {
    /**
     * 转换为 JSON / Convert to JSON
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("size", size)
            put("md5", md5)
        }
    }
}

/**
 * 升级文件集合 / Upgrade files collection
 */
data class UpgradeFiles(
    /** 固件文件信息 / Firmware file info */
    val firmware: FileInfo? = null,
    /** 模型信息文件信息 / Model info file info */
    val modelInfo: FileInfo? = null,
    /** 模型 FPK 文件信息 / Model FPK file info */
    val modelFpk: FileInfo? = null
) {
    /**
     * 转换为 JSON / Convert to JSON
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            firmware?.let { put("firmware", it.toJson()) }
            modelInfo?.let { put("model_info", it.toJson()) }
            modelFpk?.let { put("model_fpk", it.toJson()) }
        }
    }

    /**
     * 计算总大小 / Calculate total size
     */
    fun totalSize(): Long {
        return (firmware?.size ?: 0L) + (modelInfo?.size ?: 0L) + (modelFpk?.size ?: 0L)
    }
}

/**
 * 统一升级进度 / Unified upgrade progress
 */
data class UnifiedUpgradeProgress(
    /** 当前阶段 / Current phase */
    val phase: UpgradePhase,
    /** 当前传输的文件类型 / Current file type being transferred */
    val fileType: UpgradeFileType? = null,
    /** 已上传字节数 / Uploaded bytes */
    val uploadedBytes: Long = 0,
    /** 总字节数 / Total bytes */
    val totalBytes: Long = 0,
    /** 完成百分比 / Completion percentage */
    val percentage: Int = 0,
    /** 传输速度（字节/秒） / Transfer speed (bytes/sec) */
    val speed: Long = 0,
    /** 预计剩余时间（秒） / Estimated time remaining (seconds) */
    val etaSeconds: Int = 0,
    /** 错误信息 / Error message */
    val error: String? = null
)
