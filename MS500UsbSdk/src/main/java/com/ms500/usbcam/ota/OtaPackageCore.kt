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

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Obfuscatable OTA package format core.
 *
 * Keep SDK-facing file handling in [OtaPackageDecoder]; this object owns the
 * binary package constants, header parsing, package parsing, and MD5 checks.
 */
internal object OtaPackageCore {
        /** Global magic number. */
        internal const val GLOBAL_MAGIC = 0x474C4F42

        /** Firmware header magic number. */
        internal const val FIRMWARE_HEADER_MAGIC = 0x46575246

        /** Model header magic number. */
        internal const val MODEL_HEADER_MAGIC = 0x4C444F4D

        /** Firmware data magic number. */
        internal const val FW_DATA_MAGIC = 0x46574441

        /** Model data magic number. */
        internal const val MODEL_DATA_MAGIC = 0x4D444154

        /** Package type: firmware only. */
        internal const val PACKAGE_TYPE_FIRMWARE = 0x00000001

        /** Package type: model only. */
        internal const val PACKAGE_TYPE_MODEL = 0x00000002

        /** Package type: combined. */
        internal const val PACKAGE_TYPE_COMBINED = 0x00000003

        /** Global header size in bytes. */
        internal const val GLOBAL_HEADER_SIZE = 8

        /** Firmware header size in bytes. */
        internal const val FIRMWARE_HEADER_SIZE = 64

        /** Model header size in bytes. */
        internal const val MODEL_HEADER_SIZE = 64

        /** Data magic field size in bytes. */
        internal const val DATA_MAGIC_SIZE = 4

        /** Firmware name field length. */
        internal const val FIRMWARE_NAME_LEN = 10

        /** Firmware reserved field length. */
        internal const val FIRMWARE_RESERVED_LEN = 26

        /** Model name field length. */
        internal const val MODEL_NAME_LEN = 10

        /** Model sensor ID field length. */
        internal const val MODEL_SENSOR_ID_LEN = 16

        /** Model reserved field length. */
        internal const val MODEL_RESERVED_LEN = 10

        /** MD5 length in bytes. */
        internal const val MD5_LEN = 16

    /**
     * Global header data class.
     *
     * Magic number.
     * Package type.
     */
    data class GlobalHeader(
        val magic: Int,
        val packageType: Int
    ) {
        /** Whether the magic number is valid. */
        val isValid: Boolean get() = magic == GLOBAL_MAGIC

        /** Whether this package contains firmware. */
        val hasFirmware: Boolean get() = packageType == PACKAGE_TYPE_FIRMWARE || packageType == PACKAGE_TYPE_COMBINED

        /** Whether this package contains model. */
        val hasModel: Boolean get() = packageType == PACKAGE_TYPE_MODEL || packageType == PACKAGE_TYPE_COMBINED
    }

    /**
     * Firmware header data class.
     *
     * Magic number.
     * Firmware name.
     * @property version Version number (integer).
     * @property fileSize Firmware data size in bytes.
     * MD5 checksum.
     * Reserved bytes.
     */
    data class FirmwareHeader(
        val magic: Int,
        val name: String,
        val version: Int,
        val fileSize: Int,
        val md5: ByteArray,
        val reserved: ByteArray = ByteArray(FIRMWARE_RESERVED_LEN)
    ) {
        /** Whether the magic number is valid. */
        val isValid: Boolean get() = magic == FIRMWARE_HEADER_MAGIC

        /** Full firmware version string, if stored directly in reserved bytes. */
        val fullVersionStringFromReserved: String?
            get() {
                val decoded = reserved.toString(Charsets.UTF_8).trim('\u0000', ' ', '\t', '\r', '\n')
                return decoded.takeIf {
                    it.isNotBlank() && Regex("""[A-Za-z_][A-Za-z0-9_]*_\d+\.\d+\.\d+_\d{8}_\d{6}""").matches(it)
                }
            }

        /** Build timestamp parsed from reserved bytes, e.g. 20260520_172604. */
        val buildTimestamp: String?
            get() {
                val decoded = reserved.toString(Charsets.UTF_8).trim('\u0000', ' ', '\t', '\r', '\n')
                return decoded.takeIf {
                    it.isNotBlank() && Regex("""\d{8}_\d{6}""").matches(it)
                }
            }

        /** Version string in major.minor.patch format. */
        val shortVersionString: String
            get() {
                val major = (version shr 16) and 0xFF
                val minor = (version shr 8) and 0xFF
                val patch = version and 0xFF
                return "$major.$minor.$patch"
            }

        /**
         * Final firmware version string.
         *
         * Preferred format:
         * name_major.minor.patch_yyyyMMdd_HHmmss
         */
        val versionString: String
            get() {
                fullVersionStringFromReserved?.let { return it }
                buildTimestamp?.let { return "${name}_${shortVersionString}_$it" }
                return shortVersionString
            }
    }

    /**
     * Model header data class.
     *
     * Magic number.
     * Model name.
     * @property version Version number (integer).
     * @property fileSize Model data size in bytes.
     * MD5 checksum.
     * Sensor ID byte array.
     * Reserved bytes.
     */
    data class ModelHeader(
        val magic: Int,
        val name: String,
        val version: Int,
        val fileSize: Int,
        val md5: ByteArray,
        val sensorId: ByteArray,
        val reserved: ByteArray = ByteArray(MODEL_RESERVED_LEN)
    ) {
        /** Whether the magic number is valid. */
        val isValid: Boolean get() = magic == MODEL_HEADER_MAGIC

        /**
         * Version string in major.minor.patch format.
         */
        val versionString: String
            get() {
                val major = (version shr 16) and 0xFF
                val minor = (version shr 8) and 0xFF
                val patch = version and 0xFF
                return "$major.$minor.$patch"
            }

        /**
         * Sensor ID as uppercase hex string.
         */
        val sensorIdString: String
            get() = sensorId.joinToString("") { "%02X".format(it) }
    }

    /**
     * Sealed class for parse results.
     */
    sealed class ParseResult {
        /**
         * Firmware parse result.
         *
         * Global header.
         * Firmware header.
         * Raw firmware data.
         */
        data class Firmware(
            val globalHeader: GlobalHeader,
            val firmwareHeader: FirmwareHeader,
            val firmwareData: ByteArray
        ) : ParseResult()

        /**
         * Model parse result.
         *
         * Global header.
         * Model header.
         * Raw model data.
         */
        data class Model(
            val globalHeader: GlobalHeader,
            val modelHeader: ModelHeader,
            val modelData: ByteArray
        ) : ParseResult()

        /**
         * Combined parse result.
         *
         * Global header.
         * Firmware header.
         * Model header.
         * Raw firmware data.
         * Raw model data.
         */
        data class Combined(
            val globalHeader: GlobalHeader,
            val firmwareHeader: FirmwareHeader,
            val modelHeader: ModelHeader,
            val firmwareData: ByteArray,
            val modelData: ByteArray
        ) : ParseResult()

        /**
         * Parse error.
         *
         * Error message.
         */
        data class Error(val message: String) : ParseResult()
    }

    /**
     * Parse global header from byte array.
     *
     * Raw byte data.
     * @return Parsed [GlobalHeader].
     */
    fun parseGlobalHeader(data: ByteArray): GlobalHeader {
        require(data.size >= GLOBAL_HEADER_SIZE) {
            "Insufficient data length, need at least $GLOBAL_HEADER_SIZE bytes"
        }

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        return GlobalHeader(
            magic = buffer.getInt(),
            packageType = buffer.getInt()
        )
    }

    /**
     * Parse firmware header from byte array.
     *
     * Raw byte data.
     * @return Parsed [FirmwareHeader].
     */
    fun parseFirmwareHeader(data: ByteArray): FirmwareHeader {
        require(data.size >= FIRMWARE_HEADER_SIZE) {
            "Insufficient data length, need at least $FIRMWARE_HEADER_SIZE bytes"
        }

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        return FirmwareHeader(
            magic = buffer.getInt(),
            name = ByteArray(FIRMWARE_NAME_LEN).apply { buffer.get(this) }
                .toString(Charsets.UTF_8).trim('\u0000'),
            version = buffer.getInt(),
            fileSize = buffer.getInt(),
            md5 = ByteArray(MD5_LEN).apply { buffer.get(this) },
            reserved = ByteArray(FIRMWARE_RESERVED_LEN).apply { buffer.get(this) }
        )
    }

    /**
     * Parse model header from byte array.
     *
     * Raw byte data.
     * @return Parsed [ModelHeader].
     */
    fun parseModelHeader(data: ByteArray): ModelHeader {
        require(data.size >= MODEL_HEADER_SIZE) {
            "Insufficient data length, need at least $MODEL_HEADER_SIZE bytes"
        }

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        return ModelHeader(
            magic = buffer.getInt(),
            name = ByteArray(MODEL_NAME_LEN).apply { buffer.get(this) }
                .toString(Charsets.UTF_8).trim('\u0000'),
            version = buffer.getInt(),
            fileSize = buffer.getInt(),
            md5 = ByteArray(MD5_LEN).apply { buffer.get(this) },
            sensorId = ByteArray(MODEL_SENSOR_ID_LEN).apply { buffer.get(this) },
            reserved = ByteArray(MODEL_RESERVED_LEN).apply { buffer.get(this) }
        )
    }

    /**
     * Parse complete upgrade package from byte array.
     *
     * Raw byte data.
     * Parse result.
     */
    fun parsePackage(data: ByteArray): ParseResult {
        return try {
            var offset = 0
            if (data.size < GLOBAL_HEADER_SIZE) {
                return ParseResult.Error("Data length insufficient, cannot parse global header")
            }
            val globalHeader = parseGlobalHeader(data.copyOfRange(offset, offset + GLOBAL_HEADER_SIZE))
            offset += GLOBAL_HEADER_SIZE

            if (!globalHeader.isValid) {
                return ParseResult.Error("Invalid magic number: 0x${globalHeader.magic.toString(16)}")
            }

            when (globalHeader.packageType) {
                PACKAGE_TYPE_FIRMWARE -> {
                    if (data.size < offset + FIRMWARE_HEADER_SIZE) {
                        return ParseResult.Error("Insufficient data length, cannot parse firmware header")
                    }
                    val firmwareHeader = parseFirmwareHeader(data.copyOfRange(offset, offset + FIRMWARE_HEADER_SIZE))
                    offset += FIRMWARE_HEADER_SIZE
                    if (!firmwareHeader.isValid) {
                        return ParseResult.Error("Firmware header magic error: 0x${firmwareHeader.magic.toString(16)}")
                    }
                    if (data.size < offset + DATA_MAGIC_SIZE) {
                        return ParseResult.Error("Insufficient data length, cannot parse firmware data_magic")
                    }
                    val fwMagic = ByteBuffer.wrap(data.copyOfRange(offset, offset + DATA_MAGIC_SIZE)).order(ByteOrder.LITTLE_ENDIAN).int
                    if (fwMagic != FW_DATA_MAGIC) {
                        return ParseResult.Error("Firmware data_magic error: 0x${fwMagic.toString(16)}")
                    }
                    offset += DATA_MAGIC_SIZE
                    if (data.size < offset + firmwareHeader.fileSize) {
                        return ParseResult.Error("Insufficient data length, firmware data incomplete")
                    }
                    val firmwareData = data.copyOfRange(offset, offset + firmwareHeader.fileSize)
                    if (!verifyMd5(firmwareData, firmwareHeader.md5)) {
                        return ParseResult.Error("Firmware MD5 verification failed")
                    }
                    ParseResult.Firmware(globalHeader, firmwareHeader.withResolvedVersion(firmwareData), firmwareData)
                }

                PACKAGE_TYPE_MODEL -> {
                    if (data.size < offset + MODEL_HEADER_SIZE) {
                        return ParseResult.Error("Insufficient data length, cannot parse model header")
                    }
                    val modelHeader = parseModelHeader(data.copyOfRange(offset, offset + MODEL_HEADER_SIZE))
                    offset += MODEL_HEADER_SIZE
                    if (!modelHeader.isValid) {
                        return ParseResult.Error("Model header magic error: 0x${modelHeader.magic.toString(16)}")
                    }
                    if (data.size < offset + DATA_MAGIC_SIZE) {
                        return ParseResult.Error("Insufficient data length, cannot parse model data_magic")
                    }
                    val mdMagic = ByteBuffer.wrap(data.copyOfRange(offset, offset + DATA_MAGIC_SIZE)).order(ByteOrder.LITTLE_ENDIAN).int
                    if (mdMagic != MODEL_DATA_MAGIC) {
                        return ParseResult.Error("Model data_magic error: 0x${mdMagic.toString(16)}")
                    }
                    offset += DATA_MAGIC_SIZE
                    if (data.size < offset + modelHeader.fileSize) {
                        return ParseResult.Error("Insufficient data length, model data incomplete")
                    }
                    val modelData = data.copyOfRange(offset, offset + modelHeader.fileSize)
                    if (!verifyMd5(modelData, modelHeader.md5)) {
                        return ParseResult.Error("Model MD5 verification failed")
                    }
                    ParseResult.Model(globalHeader, modelHeader, modelData)
                }

                PACKAGE_TYPE_COMBINED -> {
                    if (data.size < offset + FIRMWARE_HEADER_SIZE) {
                        return ParseResult.Error("Data length insufficient, cannot parse firmware header")
                    }
                    val firmwareHeader = parseFirmwareHeader(data.copyOfRange(offset, offset + FIRMWARE_HEADER_SIZE))
                    offset += FIRMWARE_HEADER_SIZE
                    if (!firmwareHeader.isValid) {
                        return ParseResult.Error("Firmware header magic Error: 0x${firmwareHeader.magic.toString(16)}")
                    }
                    if (data.size < offset + MODEL_HEADER_SIZE) {
                        return ParseResult.Error("Data length insufficient, cannot parse model header")
                    }
                    val modelHeader = parseModelHeader(data.copyOfRange(offset, offset + MODEL_HEADER_SIZE))
                    offset += MODEL_HEADER_SIZE
                    if (!modelHeader.isValid) {
                        return ParseResult.Error("Model header magic Error: 0x${modelHeader.magic.toString(16)}")
                    }
                    if (data.size < offset + DATA_MAGIC_SIZE) {
                        return ParseResult.Error("Data length insufficient, cannot parse firmware data_magic")
                    }
                    val fwMagic = ByteBuffer.wrap(data.copyOfRange(offset, offset + DATA_MAGIC_SIZE)).order(ByteOrder.LITTLE_ENDIAN).int
                    if (fwMagic != FW_DATA_MAGIC) {
                        return ParseResult.Error("Firmware data_magic Error: 0x${fwMagic.toString(16)}")
                    }
                    offset += DATA_MAGIC_SIZE
                    if (data.size < offset + firmwareHeader.fileSize) {
                        return ParseResult.Error("Data length insufficient, firmware data incomplete")
                    }
                    val firmwareData = data.copyOfRange(offset, offset + firmwareHeader.fileSize)
                    offset += firmwareHeader.fileSize
                    if (data.size < offset + DATA_MAGIC_SIZE) {
                        return ParseResult.Error("Data length insufficient, cannot parse model data_magic")
                    }
                    val mdMagic = ByteBuffer.wrap(data.copyOfRange(offset, offset + DATA_MAGIC_SIZE)).order(ByteOrder.LITTLE_ENDIAN).int
                    if (mdMagic != MODEL_DATA_MAGIC) {
                        return ParseResult.Error("Model data_magic Error: 0x${mdMagic.toString(16)}")
                    }
                    offset += DATA_MAGIC_SIZE
                    if (data.size < offset + modelHeader.fileSize) {
                        return ParseResult.Error("Data length insufficient, model data incomplete")
                    }
                    val modelData = data.copyOfRange(offset, offset + modelHeader.fileSize)
                    if (!verifyMd5(firmwareData, firmwareHeader.md5)) {
                        return ParseResult.Error("Firmware MD5 verification failed")
                    }
                    if (!verifyMd5(modelData, modelHeader.md5)) {
                        return ParseResult.Error("Model MD5 verification failed")
                    }
                    ParseResult.Combined(globalHeader, firmwareHeader.withResolvedVersion(firmwareData), modelHeader, firmwareData, modelData)
                }

                else -> ParseResult.Error("Unknown package type: ${globalHeader.packageType}")
            }
        } catch (e: Exception) {
            ParseResult.Error("Parse failed: ${e.message}")
        }
    }

    /**
     * Verify data MD5.
     *
     * Data to verify.
     * Expected MD5 value.
     * Whether they match.
     */
    fun verifyMd5(data: ByteArray, expectedMd5: ByteArray): Boolean {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val actualMd5 = md.digest(data)
            actualMd5.contentEquals(expectedMd5)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Prefer the full version from the header; otherwise recover it from firmware content.
     */
    private fun FirmwareHeader.withResolvedVersion(firmwareData: ByteArray): FirmwareHeader {
        if (fullVersionStringFromReserved != null || buildTimestamp != null) return this

        val extracted = OtaToolstools.extractFirmwareVersion(firmwareData) ?: return this
        val encoded = Regex("""\d{8}_\d{6}""").find(extracted)
            ?.value
            ?.toByteArray(Charsets.UTF_8)
            ?.copyOf(FIRMWARE_RESERVED_LEN)
            ?: extracted.toByteArray(Charsets.UTF_8).copyOf(FIRMWARE_RESERVED_LEN)
        return copy(reserved = encoded)
    }
}
