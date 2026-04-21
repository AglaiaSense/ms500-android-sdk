package com.ms500.usbcam.ota

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Firmware / model OTA package decoder.
 *
 * Responsible for parsing the MS500 custom upgrade package format, extracting firmware/model data, and verifying MD5.
 */
internal class OtaPackageDecoder {

    companion object {
        /** Global magic number. */
        const val GLOBAL_MAGIC = 0x474C4F42

        /** Firmware header magic number. */
        const val FIRMWARE_HEADER_MAGIC = 0x46575246

        /** Model header magic number. */
        const val MODEL_HEADER_MAGIC = 0x4C444F4D

        /** Firmware data magic number. */
        const val FW_DATA_MAGIC = 0x46574441

        /** Model data magic number. */
        const val MODEL_DATA_MAGIC = 0x4D444154

        /** Package type: firmware only. */
        const val PACKAGE_TYPE_FIRMWARE = 0x00000001

        /** Package type: model only. */
        const val PACKAGE_TYPE_MODEL = 0x00000002

        /** Package type: combined. */
        const val PACKAGE_TYPE_COMBINED = 0x00000003

        /** Global header size in bytes. */
        const val GLOBAL_HEADER_SIZE = 8

        /** Firmware header size in bytes. */
        const val FIRMWARE_HEADER_SIZE = 64

        /** Model header size in bytes. */
        const val MODEL_HEADER_SIZE = 64

        /** Data magic field size in bytes. */
        const val DATA_MAGIC_SIZE = 4

        /** Firmware name field length. */
        const val FIRMWARE_NAME_LEN = 10

        /** Firmware reserved field length. */
        const val FIRMWARE_RESERVED_LEN = 26

        /** Model name field length. */
        const val MODEL_NAME_LEN = 10

        /** Model sensor ID field length. */
        const val MODEL_SENSOR_ID_LEN = 16

        /** Model reserved field length. */
        const val MODEL_RESERVED_LEN = 10

        /** MD5 length in bytes. */
        const val MD5_LEN = 16

        /** Default directory name for decoded files. */
        private const val DECODED_DIR = "decoded_packages"
    }

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
     * Decode result data class.
     *
     * Decoded firmware file.
     * Decoded model ZIP file.
     * Model info TXT file.
     * Model data FPK file.
     * Firmware header info.
     * Model header info.
     * @property error Error message (null if successful).
     */
    data class DecodeResult(
        val firmwareFile: File?,
        val modelFile: File?,
        val modelInfoFile: File?,
        val modelDataFile: File?,
        val firmwareHeader: FirmwareHeader?,
        val modelHeader: ModelHeader?,
        val error: String?
    ) {
        /** Whether decoding succeeded. */
        val isSuccess: Boolean get() = error == null

        /** Whether a valid firmware is present. */
        val hasFirmware: Boolean get() = firmwareFile != null && firmwareHeader != null

        /** Whether a valid model is present. */
        val hasModel: Boolean get() = modelFile != null && modelHeader != null
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
     * Parse global header from file.
     *
     * Upgrade package file.
     * @return [GlobalHeader] or null if file does not exist or parsing failed.
     */
    fun parseGlobalHeader(file: File): GlobalHeader? {
        if (!file.exists() || file.length() < GLOBAL_HEADER_SIZE) {
            return null
        }

        return try {
            FileInputStream(file).use { fis ->
                val headerBytes = ByteArray(GLOBAL_HEADER_SIZE)
                val read = fis.read(headerBytes)
                if (read < GLOBAL_HEADER_SIZE) {
                    return null
                }
                parseGlobalHeader(headerBytes)
            }
        } catch (_: Exception) {
            null
        }
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
                    ParseResult.Firmware(globalHeader, firmwareHeader, firmwareData)
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
                    ParseResult.Combined(globalHeader, firmwareHeader, modelHeader, firmwareData, modelData)
                }

                else -> ParseResult.Error("Unknown package type: ${globalHeader.packageType}")
            }
        } catch (e: Exception) {
            ParseResult.Error("Parse failed: ${e.message}")
        }
    }

    /**
     * Decode an upgrade package file to the output directory.
     *
     * Upgrade package file.
     * @param outputDir Output directory (uses default if null).
     * Decode result.
     */
    fun decode(
        file: File,
        outputDir: File? = null
    ): DecodeResult {
        if (!file.exists()) {
            return DecodeResult(null, null, null, null, null, null, "File does not exist: ${file.absolutePath}")
        }

        val destDir = outputDir ?: getDefaultDecodedDir(file)
        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        return try {
            val data = file.readBytes()
            when (val result = parsePackage(data)) {
                is ParseResult.Firmware -> {
                    val firmwareFile = File(destDir, "${file.nameWithoutExtension}_firmware.bin")
                    FileOutputStream(firmwareFile).use { it.write(result.firmwareData) }
                    DecodeResult(
                        firmwareFile = firmwareFile,
                        modelFile = null,
                        modelInfoFile = null,
                        modelDataFile = null,
                        firmwareHeader = result.firmwareHeader,
                        modelHeader = null,
                        error = null
                    )
                }

                is ParseResult.Model -> {
                    val modelFile = File(destDir, "${file.nameWithoutExtension}_model.zip")
                    FileOutputStream(modelFile).use { it.write(result.modelData) }
                    val extractedFiles = extractModelFiles(modelFile, destDir)
                    DecodeResult(
                        firmwareFile = null,
                        modelFile = modelFile,
                        modelInfoFile = extractedFiles.first,
                        modelDataFile = extractedFiles.second,
                        firmwareHeader = null,
                        modelHeader = result.modelHeader,
                        error = null
                    )
                }

                is ParseResult.Combined -> {
                    val firmwareFile = File(destDir, "${file.nameWithoutExtension}_firmware.bin")
                    FileOutputStream(firmwareFile).use { it.write(result.firmwareData) }
                    val modelFile = File(destDir, "${file.nameWithoutExtension}_model.zip")
                    FileOutputStream(modelFile).use { it.write(result.modelData) }
                    val extractedFiles = extractModelFiles(modelFile, destDir)
                    DecodeResult(
                        firmwareFile = firmwareFile,
                        modelFile = modelFile,
                        modelInfoFile = extractedFiles.first,
                        modelDataFile = extractedFiles.second,
                        firmwareHeader = result.firmwareHeader,
                        modelHeader = result.modelHeader,
                        error = null
                    )
                }

                is ParseResult.Error -> DecodeResult(null, null, null, null, null, null, result.message)
            }
        } catch (e: Exception) {
            DecodeResult(null, null, null, null, null, null, "Decode failed: ${e.message}")
        }
    }

    /**
     * Decode firmware-only package.
     *
     * Upgrade package file.
     * Output directory.
     * Decode result.
     */
    fun decodeFirmwarePackage(
        file: File,
        outputDir: File? = null
    ): DecodeResult {
        val result = decode(file, outputDir)
        return if (result.hasFirmware && !result.hasModel) {
            result
        } else {
            DecodeResult(null, null, null, null, null, null, "Not a valid firmware package")
        }
    }

    /**
     * Decode model-only package.
     *
     * Upgrade package file.
     * Output directory.
     * Decode result.
     */
    fun decodeModelPackage(
        file: File,
        outputDir: File? = null
    ): DecodeResult {
        val result = decode(file, outputDir)
        return if (result.hasModel && !result.hasFirmware) {
            result
        } else {
            DecodeResult(null, null, null, null, null, null, "Not a valid model package")
        }
    }

    /**
     * Decode combined package.
     *
     * Upgrade package file.
     * Output directory.
     * Decode result.
     */
    fun decodeCombinedPackage(
        file: File,
        outputDir: File? = null
    ): DecodeResult {
        val result = decode(file, outputDir)
        return if (result.hasFirmware && result.hasModel) {
            result
        } else {
            DecodeResult(null, null, null, null, null, null, "Not a valid combined package")
        }
    }

    /**
     * Extract .txt and .fpk files from model ZIP.
     *
     * Model ZIP file.
     * Destination directory.
     * First: TXT file, second: FPK file.
     */
    private fun extractModelFiles(modelZipFile: File, destDir: File): Pair<File?, File?> {
        var txtFile: File? = null
        var fpkFile: File? = null

        try {
            ZipInputStream(FileInputStream(modelZipFile)).use { input ->
                var entry = input.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val outFile = File(destDir, File(entry.name).name)
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                        when {
                            outFile.name.endsWith(".txt", ignoreCase = true) -> txtFile = outFile
                            outFile.name.endsWith(".fpk", ignoreCase = true) -> fpkFile = outFile
                        }
                    }
                    input.closeEntry()
                    entry = input.nextEntry
                }
            }
        } catch (_: IOException) {
        }

        return Pair(txtFile, fpkFile)
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
     * Get default decoded output directory.
     *
     * Source upgrade package file.
     * Default directory.
     */
    private fun getDefaultDecodedDir(sourceFile: File): File {
        return File(sourceFile.parentFile, DECODED_DIR)
    }

    /**
     * Clean up decoded temporary files.
     *
     * File to clean up.
     */
    fun cleanup(decodedFile: File?) {
        decodedFile?.let {
            try {
                if (it.exists()) {
                    it.delete()
                }
                it.parentFile?.let { parent ->
                    if (parent.name == DECODED_DIR && parent.listFiles()?.isEmpty() == true) {
                        parent.delete()
                    }
                }
            } catch (_: Exception) {
            }
        }
    }
}
