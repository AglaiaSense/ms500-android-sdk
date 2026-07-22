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

import com.ms500.usbcam.ota.OtaToolstools
import java.io.File
import org.json.JSONObject

private const val OTA_PACKAGE_TYPE_FIRMWARE = 0x00000001
private const val OTA_PACKAGE_TYPE_MODEL = 0x00000002
private const val OTA_PACKAGE_TYPE_COMBINED = 0x00000003

/**
 * OTA package type
 */
enum class PackageType(val value: Int) {
    /** Firmware package */
    FIRMWARE(OTA_PACKAGE_TYPE_FIRMWARE),
    /** Model package */
    MODEL(OTA_PACKAGE_TYPE_MODEL),
    /** Combined package */
    COMBINED(OTA_PACKAGE_TYPE_COMBINED),
    /** Unknown type */
    UNKNOWN(-1);

    companion object {
        /** Firmware package type value */
        const val PACKAGE_TYPE_FIRMWARE = OTA_PACKAGE_TYPE_FIRMWARE

        /** Model package type value */
        const val PACKAGE_TYPE_MODEL = OTA_PACKAGE_TYPE_MODEL

        /** Combined package type value */
        const val PACKAGE_TYPE_COMBINED = OTA_PACKAGE_TYPE_COMBINED

        /**
         * Get package type from value
         *
         * @param value Type value
         * @return [PackageType]
         */
        fun fromValue(value: Int): PackageType {
            return values().firstOrNull { it.value == value } ?: UNKNOWN
        }
    }
}

/**
 * Firmware info
 */
data class FirmwareInfo(
    /** Firmware name */
    val name: String,
    /** Version number */
    val version: String,
    /** Version string */
    val versionString: String,
    /** File size */
    val fileSize: Long,
    /** MD5 checksum */
    val md5: String,
    /** Whether valid */
    val isValid: Boolean
)

/**
 * Model info
 */
data class ModelInfo(
    /** Model name */
    val name: String,
    /** Version number */
    val version: String,
    /** Version string */
    val versionString: String,
    /** File size */
    val fileSize: Long,
    /** Whether valid */
    val isValid: Boolean
)

/**
 * Package verification result
 */
data class PackageVerification(
    /** Package type */
    val type: PackageType,
    /** Firmware info */
    val firmware: FirmwareInfo? = null,
    /** Model info */
    val model: ModelInfo? = null,
    /** Firmware file */
    val firmwareFile: File? = null,
    /** Model info file */
    val modelInfoFile: File? = null,
    /** Model data file */
    val modelDataFile: File? = null,
    /** Model file (defaults to modelDataFile) */
    val modelFile: File? = modelDataFile,
    /** Whether valid */
    val isValid: Boolean = true,
    /** Error message */
    val error: String? = null
) {
    /** Whether firmware is present */
    val hasFirmware: Boolean get() = firmware != null && firmwareFile != null
    /** Whether model is present */
    val hasModel: Boolean get() = model != null && modelInfoFile != null && modelDataFile != null

    /**
     * Validate against device info
     *
     * @param deviceInfo Device information
     * @return List of error messages
     */
    fun validateAgainst(deviceInfo: DeviceParamDto): List<String> {
        val errors = mutableListOf<String>()

        firmware?.let { fw ->
            if (!fw.isValid) {
                errors.add("Firmware package header magic number invalid")
            }
            if (!OtaToolstools.isUsbGazeFirmware(fw.name, fw.versionString)) {
                errors.add("Firmware package must be usb_gaze firmware: name=${fw.name}, version=${fw.versionString}")
            }
        }

        model?.let { md ->
            if (!md.isValid) {
                errors.add("Model package header magic number invalid")
            }
            val packageModelName = OtaToolstools.normalizeModelName(md.name)
            val deviceModelName = OtaToolstools.extractModelNameFromVersion(deviceInfo.modelVersion)
            if (packageModelName != null && deviceModelName != null && packageModelName != deviceModelName) {
                errors.add("Model name mismatch: package=${md.name}, device=$deviceModelName")
            }
        }

        return errors
    }
}

/**
 * Upgrade state
 */
enum class UpgradeState {
    /** Idle */
    IDLE,
    /** Preparing */
    PREPARING,
    /** Uploading */
    UPLOADING,
    /** Verifying */
    VERIFYING,
    /** Flashing */
    FLASHING,
    /** Committing */
    COMMITTING,
    /** Completed */
    COMPLETED,
    /** Failed */
    FAILED
}

/**
 * Upgrade progress
 */
data class UpgradeProgress(
    /** Current state */
    val state: UpgradeState = UpgradeState.IDLE,
    /** Completion percentage */
    val percentage: Int = 0,
    /** Uploaded bytes */
    val uploadedBytes: Long = 0,
    /** Total bytes */
    val totalBytes: Long = 0,
    /** Transfer speed (bytes/sec) */
    val speed: Long = 0,
    /** Estimated time remaining (seconds) */
    val etaSeconds: Int = 0,
    /** Error message */
    val error: String? = null,
    /** Whether cancellable */
    val isCancellable: Boolean = true
)

/**
 * Upgrade mode
 */
enum class UpgradeMode {
    /** None */
    NONE,
    /** Firmware only */
    FIRMWARE,
    /** Model only */
    MODEL,
    /** Combined */
    COMBINED
}

/**
 * Upgrade phase
 */
enum class UpgradePhase {
    /** Idle */
    IDLE,
    /** Preparing */
    PREPARING,
    /** Transferring firmware */
    TRANSFER_FIRMWARE,
    /** Transferring model info */
    TRANSFER_MODEL_INFO,
    /** Transferring model FPK */
    TRANSFER_MODEL_FPK,
    /** Verifying */
    VERIFYING,
    /** Committing */
    COMMITTING,
    /** Error */
    ERROR,
    /** Completed */
    COMPLETED
}

/**
 * Upgrade file type
 */
enum class UpgradeFileType(val value: String) {
    /** Firmware */
    FIRMWARE("firmware"),
    /** Model info */
    MODEL_INFO("model_info"),
    /** Model FPK */
    MODEL_FPK("model_fpk")
}

/**
 * File info
 */
data class FileInfo(
    /** File size */
    val size: Long,
    /** MD5 checksum */
    val md5: String
) {
    /**
     * Convert to JSON
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("size", size)
            put("md5", md5)
        }
    }
}

/**
 * Upgrade files collection
 */
data class UpgradeFiles(
    /** Firmware file info */
    val firmware: FileInfo? = null,
    /** Model info file info */
    val modelInfo: FileInfo? = null,
    /** Model FPK file info */
    val modelFpk: FileInfo? = null
) {
    /**
     * Convert to JSON
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            firmware?.let { put("firmware", it.toJson()) }
            modelInfo?.let { put("model_info", it.toJson()) }
            modelFpk?.let { put("model_fpk", it.toJson()) }
        }
    }

    /**
     * Calculate total size
     */
    fun totalSize(): Long {
        return (firmware?.size ?: 0L) + (modelInfo?.size ?: 0L) + (modelFpk?.size ?: 0L)
    }
}

/**
 * Unified upgrade progress
 */
data class UnifiedUpgradeProgress(
    /** Current phase */
    val phase: UpgradePhase,
    /** Current file type being transferred */
    val fileType: UpgradeFileType? = null,
    /** Uploaded bytes */
    val uploadedBytes: Long = 0,
    /** Total bytes */
    val totalBytes: Long = 0,
    /** Completion percentage */
    val percentage: Int = 0,
    /** Transfer speed (bytes/sec) */
    val speed: Long = 0,
    /** Estimated time remaining (seconds) */
    val etaSeconds: Int = 0,
    /** Error message */
    val error: String? = null
)
