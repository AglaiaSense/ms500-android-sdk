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

import com.ms500.usbcam.ota.OtaPackageDecoder
import java.io.File
import org.json.JSONObject

enum class PackageType(val value: Int) {
    FIRMWARE(OtaPackageDecoder.PACKAGE_TYPE_FIRMWARE),
    MODEL(OtaPackageDecoder.PACKAGE_TYPE_MODEL),
    COMBINED(OtaPackageDecoder.PACKAGE_TYPE_COMBINED),
    UNKNOWN(-1);

    companion object {
        fun fromValue(value: Int): PackageType {
            return values().firstOrNull { it.value == value } ?: UNKNOWN
        }
    }
}

data class FirmwareInfo(
    val name: String,
    val version: String,
    val versionString: String,
    val fileSize: Long,
    val md5: String,
    val isValid: Boolean
)

data class ModelInfo(
    val name: String,
    val version: String,
    val versionString: String,
    val fileSize: Long,
    val sensorId: String,
    val isValid: Boolean
)

data class PackageVerification(
    val type: PackageType,
    val firmware: FirmwareInfo? = null,
    val model: ModelInfo? = null,
    val firmwareFile: File? = null,
    val modelInfoFile: File? = null,
    val modelDataFile: File? = null,
    val modelFile: File? = modelDataFile,
    val isValid: Boolean = true,
    val error: String? = null
) {
    val hasFirmware: Boolean get() = firmware != null && firmwareFile != null
    val hasModel: Boolean get() = model != null && modelInfoFile != null && modelDataFile != null

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

enum class UpgradeState {
    IDLE,
    PREPARING,
    UPLOADING,
    VERIFYING,
    FLASHING,
    COMMITTING,
    COMPLETED,
    FAILED
}

data class UpgradeProgress(
    val state: UpgradeState = UpgradeState.IDLE,
    val percentage: Int = 0,
    val uploadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val speed: Long = 0,
    val etaSeconds: Int = 0,
    val error: String? = null,
    val isCancellable: Boolean = true
)

enum class UpgradeMode {
    NONE,
    FIRMWARE,
    MODEL,
    COMBINED
}

enum class UpgradePhase {
    IDLE,
    PREPARING,
    TRANSFER_FIRMWARE,
    TRANSFER_MODEL_INFO,
    TRANSFER_MODEL_FPK,
    VERIFYING,
    COMMITTING,
    ERROR,
    COMPLETED
}

enum class UpgradeFileType(val value: String) {
    FIRMWARE("firmware"),
    MODEL_INFO("model_info"),
    MODEL_FPK("model_fpk")
}

data class FileInfo(
    val size: Long,
    val md5: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("size", size)
            put("md5", md5)
        }
    }
}

data class UpgradeFiles(
    val firmware: FileInfo? = null,
    val modelInfo: FileInfo? = null,
    val modelFpk: FileInfo? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            firmware?.let { put("firmware", it.toJson()) }
            modelInfo?.let { put("model_info", it.toJson()) }
            modelFpk?.let { put("model_fpk", it.toJson()) }
        }
    }

    fun totalSize(): Long {
        return (firmware?.size ?: 0L) + (modelInfo?.size ?: 0L) + (modelFpk?.size ?: 0L)
    }
}

data class UnifiedUpgradeProgress(
    val phase: UpgradePhase,
    val fileType: UpgradeFileType? = null,
    val uploadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val percentage: Int = 0,
    val speed: Long = 0,
    val etaSeconds: Int = 0,
    val error: String? = null
)
