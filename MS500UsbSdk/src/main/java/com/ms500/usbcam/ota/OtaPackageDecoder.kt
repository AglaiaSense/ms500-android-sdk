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
 * 固件/模型升级包解码工具 / Firmware / model OTA package decoder.
 *
 * 负责解析 MS500 自定义升级包格式，提取固件、模型数据，并进行 MD5 校验。
 * Responsible for parsing the MS500 custom upgrade package format, extracting firmware/model data, and verifying MD5.
 */
internal class OtaPackageDecoder {

    companion object {
        /** 全局魔数 / Global magic number. */
        const val GLOBAL_MAGIC = 0x474C4F42

        /** 固件包头魔数 / Firmware header magic number. */
        const val FIRMWARE_HEADER_MAGIC = 0x46575246

        /** 模型包头魔数 / Model header magic number. */
        const val MODEL_HEADER_MAGIC = 0x4C444F4D

        /** 固件数据魔数 / Firmware data magic number. */
        const val FW_DATA_MAGIC = 0x46574441

        /** 模型数据魔数 / Model data magic number. */
        const val MODEL_DATA_MAGIC = 0x4D444154

        /** 纯固件包类型 / Package type: firmware only. */
        const val PACKAGE_TYPE_FIRMWARE = 0x00000001

        /** 纯模型包类型 / Package type: model only. */
        const val PACKAGE_TYPE_MODEL = 0x00000002

        /** 组合包类型（固件+模型）/ Package type: combined. */
        const val PACKAGE_TYPE_COMBINED = 0x00000003

        /** 全局包头大小（字节）/ Global header size in bytes. */
        const val GLOBAL_HEADER_SIZE = 8

        /** 固件包头大小（字节）/ Firmware header size in bytes. */
        const val FIRMWARE_HEADER_SIZE = 64

        /** 模型包头大小（字节）/ Model header size in bytes. */
        const val MODEL_HEADER_SIZE = 64

        /** 数据魔数字段大小（字节）/ Data magic field size in bytes. */
        const val DATA_MAGIC_SIZE = 4

        /** 固件名称字段长度 / Firmware name field length. */
        const val FIRMWARE_NAME_LEN = 10

        /** 固件保留字段长度 / Firmware reserved field length. */
        const val FIRMWARE_RESERVED_LEN = 26

        /** 模型名称字段长度 / Model name field length. */
        const val MODEL_NAME_LEN = 10

        /** 模型 Sensor ID 字段长度 / Model sensor ID field length. */
        const val MODEL_SENSOR_ID_LEN = 16

        /** 模型保留字段长度 / Model reserved field length. */
        const val MODEL_RESERVED_LEN = 10

        /** MD5 长度（字节）/ MD5 length in bytes. */
        const val MD5_LEN = 16

        /** 解码后文件默认存放目录名 / Default directory name for decoded files. */
        private const val DECODED_DIR = "decoded_packages"
    }

    /**
     * 全局包头数据类 / Global header data class.
     *
     * @property magic 魔数 / Magic number.
     * @property packageType 包类型 / Package type.
     */
    data class GlobalHeader(
        val magic: Int,
        val packageType: Int
    ) {
        /** 魔数是否有效 / Whether the magic number is valid. */
        val isValid: Boolean get() = magic == GLOBAL_MAGIC

        /** 是否包含固件 / Whether this package contains firmware. */
        val hasFirmware: Boolean get() = packageType == PACKAGE_TYPE_FIRMWARE || packageType == PACKAGE_TYPE_COMBINED

        /** 是否包含模型 / Whether this package contains model. */
        val hasModel: Boolean get() = packageType == PACKAGE_TYPE_MODEL || packageType == PACKAGE_TYPE_COMBINED
    }

    /**
     * 固件包头数据类 / Firmware header data class.
     *
     * @property magic 魔数 / Magic number.
     * @property name 固件名称 / Firmware name.
     * @property version 版本号（整型）/ Version number (integer).
     * @property fileSize 固件数据大小（字节）/ Firmware data size in bytes.
     * @property md5 MD5 校验值 / MD5 checksum.
     * @property reserved 保留字段 / Reserved bytes.
     */
    data class FirmwareHeader(
        val magic: Int,
        val name: String,
        val version: Int,
        val fileSize: Int,
        val md5: ByteArray,
        val reserved: ByteArray = ByteArray(FIRMWARE_RESERVED_LEN)
    ) {
        /** 魔数是否有效 / Whether the magic number is valid. */
        val isValid: Boolean get() = magic == FIRMWARE_HEADER_MAGIC

        /**
         * 版本号字符串，格式：major.minor.patch / Version string in major.minor.patch format.
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
     * 模型包头数据类 / Model header data class.
     *
     * @property magic 魔数 / Magic number.
     * @property name 模型名称 / Model name.
     * @property version 版本号（整型）/ Version number (integer).
     * @property fileSize 模型数据大小（字节）/ Model data size in bytes.
     * @property md5 MD5 校验值 / MD5 checksum.
     * @property sensorId Sensor ID 字节数组 / Sensor ID byte array.
     * @property reserved 保留字段 / Reserved bytes.
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
        /** 魔数是否有效 / Whether the magic number is valid. */
        val isValid: Boolean get() = magic == MODEL_HEADER_MAGIC

        /**
         * 版本号字符串，格式：major.minor.patch / Version string in major.minor.patch format.
         */
        val versionString: String
            get() {
                val major = (version shr 16) and 0xFF
                val minor = (version shr 8) and 0xFF
                val patch = version and 0xFF
                return "$major.$minor.$patch"
            }

        /**
         * Sensor ID 十六进制字符串 / Sensor ID as uppercase hex string.
         */
        val sensorIdString: String
            get() = sensorId.joinToString("") { "%02X".format(it) }
    }

    /**
     * 解析结果密封类 / Sealed class for parse results.
     */
    sealed class ParseResult {
        /**
         * 固件包解析结果 / Firmware parse result.
         *
         * @property globalHeader 全局包头 / Global header.
         * @property firmwareHeader 固件包头 / Firmware header.
         * @property firmwareData 固件原始数据 / Raw firmware data.
         */
        data class Firmware(
            val globalHeader: GlobalHeader,
            val firmwareHeader: FirmwareHeader,
            val firmwareData: ByteArray
        ) : ParseResult()

        /**
         * 模型包解析结果 / Model parse result.
         *
         * @property globalHeader 全局包头 / Global header.
         * @property modelHeader 模型包头 / Model header.
         * @property modelData 模型原始数据 / Raw model data.
         */
        data class Model(
            val globalHeader: GlobalHeader,
            val modelHeader: ModelHeader,
            val modelData: ByteArray
        ) : ParseResult()

        /**
         * 组合包解析结果 / Combined parse result.
         *
         * @property globalHeader 全局包头 / Global header.
         * @property firmwareHeader 固件包头 / Firmware header.
         * @property modelHeader 模型包头 / Model header.
         * @property firmwareData 固件原始数据 / Raw firmware data.
         * @property modelData 模型原始数据 / Raw model data.
         */
        data class Combined(
            val globalHeader: GlobalHeader,
            val firmwareHeader: FirmwareHeader,
            val modelHeader: ModelHeader,
            val firmwareData: ByteArray,
            val modelData: ByteArray
        ) : ParseResult()

        /**
         * 解析错误 / Parse error.
         *
         * @property message 错误信息 / Error message.
         */
        data class Error(val message: String) : ParseResult()
    }

    /**
     * 解码结果数据类 / Decode result data class.
     *
     * @property firmwareFile 解码后的固件文件 / Decoded firmware file.
     * @property modelFile 解码后的模型 ZIP 文件 / Decoded model ZIP file.
     * @property modelInfoFile 模型信息 TXT 文件 / Model info TXT file.
     * @property modelDataFile 模型数据 FPK 文件 / Model data FPK file.
     * @property firmwareHeader 固件包头信息 / Firmware header info.
     * @property modelHeader 模型包头信息 / Model header info.
     * @property error 错误信息（成功时为 null）/ Error message (null if successful).
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
        /** 是否解码成功 / Whether decoding succeeded. */
        val isSuccess: Boolean get() = error == null

        /** 是否包含有效固件 / Whether a valid firmware is present. */
        val hasFirmware: Boolean get() = firmwareFile != null && firmwareHeader != null

        /** 是否包含有效模型 / Whether a valid model is present. */
        val hasModel: Boolean get() = modelFile != null && modelHeader != null
    }

    /**
     * 从字节数组解析全局包头 / Parse global header from byte array.
     *
     * @param data 原始字节数据 / Raw byte data.
     * @return 解析后的 [GlobalHeader].
     */
    fun parseGlobalHeader(data: ByteArray): GlobalHeader {
        require(data.size >= GLOBAL_HEADER_SIZE) {
            "数据长度不足，需要至少 $GLOBAL_HEADER_SIZE 字节"
        }

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        return GlobalHeader(
            magic = buffer.getInt(),
            packageType = buffer.getInt()
        )
    }

    /**
     * 从字节数组解析固件包头 / Parse firmware header from byte array.
     *
     * @param data 原始字节数据 / Raw byte data.
     * @return 解析后的 [FirmwareHeader].
     */
    fun parseFirmwareHeader(data: ByteArray): FirmwareHeader {
        require(data.size >= FIRMWARE_HEADER_SIZE) {
            "数据长度不足，需要至少 $FIRMWARE_HEADER_SIZE 字节"
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
     * 从字节数组解析模型包头 / Parse model header from byte array.
     *
     * @param data 原始字节数据 / Raw byte data.
     * @return 解析后的 [ModelHeader].
     */
    fun parseModelHeader(data: ByteArray): ModelHeader {
        require(data.size >= MODEL_HEADER_SIZE) {
            "数据长度不足，需要至少 $MODEL_HEADER_SIZE 字节"
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
     * 从文件解析全局包头 / Parse global header from file.
     *
     * @param file 升级包文件 / Upgrade package file.
     * @return [GlobalHeader] 或 null（文件不存在或解析失败）/ Global header or null if not exists or parse failed.
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
     * 解析完整升级包字节数据 / Parse complete upgrade package from byte array.
     *
     * @param data 原始字节数据 / Raw byte data.
     * @return [ParseResult] 解析结果 / Parse result.
     */
    fun parsePackage(data: ByteArray): ParseResult {
        return try {
            var offset = 0
            if (data.size < GLOBAL_HEADER_SIZE) {
                return ParseResult.Error("数据长度不足，无法解析全局包头")
            }
            val globalHeader = parseGlobalHeader(data.copyOfRange(offset, offset + GLOBAL_HEADER_SIZE))
            offset += GLOBAL_HEADER_SIZE

            if (!globalHeader.isValid) {
                return ParseResult.Error("无效的魔法数: 0x${globalHeader.magic.toString(16)}")
            }

            when (globalHeader.packageType) {
                PACKAGE_TYPE_FIRMWARE -> {
                    if (data.size < offset + FIRMWARE_HEADER_SIZE) {
                        return ParseResult.Error("数据长度不足，无法解析固件包头")
                    }
                    val firmwareHeader = parseFirmwareHeader(data.copyOfRange(offset, offset + FIRMWARE_HEADER_SIZE))
                    offset += FIRMWARE_HEADER_SIZE
                    if (!firmwareHeader.isValid) {
                        return ParseResult.Error("固件包头 magic 错误: 0x${firmwareHeader.magic.toString(16)}")
                    }
                    if (data.size < offset + DATA_MAGIC_SIZE) {
                        return ParseResult.Error("数据长度不足，无法解析固件 data_magic")
                    }
                    val fwMagic = ByteBuffer.wrap(data.copyOfRange(offset, offset + DATA_MAGIC_SIZE)).order(ByteOrder.LITTLE_ENDIAN).int
                    if (fwMagic != FW_DATA_MAGIC) {
                        return ParseResult.Error("固件 data_magic 错误: 0x${fwMagic.toString(16)}")
                    }
                    offset += DATA_MAGIC_SIZE
                    if (data.size < offset + firmwareHeader.fileSize) {
                        return ParseResult.Error("数据长度不足，固件数据不完整")
                    }
                    val firmwareData = data.copyOfRange(offset, offset + firmwareHeader.fileSize)
                    if (!verifyMd5(firmwareData, firmwareHeader.md5)) {
                        return ParseResult.Error("固件 MD5 校验失败")
                    }
                    ParseResult.Firmware(globalHeader, firmwareHeader, firmwareData)
                }

                PACKAGE_TYPE_MODEL -> {
                    if (data.size < offset + MODEL_HEADER_SIZE) {
                        return ParseResult.Error("数据长度不足，无法解析模型包头")
                    }
                    val modelHeader = parseModelHeader(data.copyOfRange(offset, offset + MODEL_HEADER_SIZE))
                    offset += MODEL_HEADER_SIZE
                    if (!modelHeader.isValid) {
                        return ParseResult.Error("模型包头 magic 错误: 0x${modelHeader.magic.toString(16)}")
                    }
                    if (data.size < offset + DATA_MAGIC_SIZE) {
                        return ParseResult.Error("数据长度不足，无法解析模型 data_magic")
                    }
                    val mdMagic = ByteBuffer.wrap(data.copyOfRange(offset, offset + DATA_MAGIC_SIZE)).order(ByteOrder.LITTLE_ENDIAN).int
                    if (mdMagic != MODEL_DATA_MAGIC) {
                        return ParseResult.Error("模型 data_magic 错误: 0x${mdMagic.toString(16)}")
                    }
                    offset += DATA_MAGIC_SIZE
                    if (data.size < offset + modelHeader.fileSize) {
                        return ParseResult.Error("数据长度不足，模型数据不完整")
                    }
                    val modelData = data.copyOfRange(offset, offset + modelHeader.fileSize)
                    if (!verifyMd5(modelData, modelHeader.md5)) {
                        return ParseResult.Error("模型 MD5 校验失败")
                    }
                    ParseResult.Model(globalHeader, modelHeader, modelData)
                }

                PACKAGE_TYPE_COMBINED -> {
                    if (data.size < offset + FIRMWARE_HEADER_SIZE) {
                        return ParseResult.Error("数据长度不足，无法解析固件包头")
                    }
                    val firmwareHeader = parseFirmwareHeader(data.copyOfRange(offset, offset + FIRMWARE_HEADER_SIZE))
                    offset += FIRMWARE_HEADER_SIZE
                    if (!firmwareHeader.isValid) {
                        return ParseResult.Error("固件包头 magic 错误: 0x${firmwareHeader.magic.toString(16)}")
                    }
                    if (data.size < offset + MODEL_HEADER_SIZE) {
                        return ParseResult.Error("数据长度不足，无法解析模型包头")
                    }
                    val modelHeader = parseModelHeader(data.copyOfRange(offset, offset + MODEL_HEADER_SIZE))
                    offset += MODEL_HEADER_SIZE
                    if (!modelHeader.isValid) {
                        return ParseResult.Error("模型包头 magic 错误: 0x${modelHeader.magic.toString(16)}")
                    }
                    if (data.size < offset + DATA_MAGIC_SIZE) {
                        return ParseResult.Error("数据长度不足，无法解析固件 data_magic")
                    }
                    val fwMagic = ByteBuffer.wrap(data.copyOfRange(offset, offset + DATA_MAGIC_SIZE)).order(ByteOrder.LITTLE_ENDIAN).int
                    if (fwMagic != FW_DATA_MAGIC) {
                        return ParseResult.Error("固件 data_magic 错误: 0x${fwMagic.toString(16)}")
                    }
                    offset += DATA_MAGIC_SIZE
                    if (data.size < offset + firmwareHeader.fileSize) {
                        return ParseResult.Error("数据长度不足，固件数据不完整")
                    }
                    val firmwareData = data.copyOfRange(offset, offset + firmwareHeader.fileSize)
                    offset += firmwareHeader.fileSize
                    if (data.size < offset + DATA_MAGIC_SIZE) {
                        return ParseResult.Error("数据长度不足，无法解析模型 data_magic")
                    }
                    val mdMagic = ByteBuffer.wrap(data.copyOfRange(offset, offset + DATA_MAGIC_SIZE)).order(ByteOrder.LITTLE_ENDIAN).int
                    if (mdMagic != MODEL_DATA_MAGIC) {
                        return ParseResult.Error("模型 data_magic 错误: 0x${mdMagic.toString(16)}")
                    }
                    offset += DATA_MAGIC_SIZE
                    if (data.size < offset + modelHeader.fileSize) {
                        return ParseResult.Error("数据长度不足，模型数据不完整")
                    }
                    val modelData = data.copyOfRange(offset, offset + modelHeader.fileSize)
                    if (!verifyMd5(firmwareData, firmwareHeader.md5)) {
                        return ParseResult.Error("固件 MD5 校验失败")
                    }
                    if (!verifyMd5(modelData, modelHeader.md5)) {
                        return ParseResult.Error("模型 MD5 校验失败")
                    }
                    ParseResult.Combined(globalHeader, firmwareHeader, modelHeader, firmwareData, modelData)
                }

                else -> ParseResult.Error("未知的包类型: ${globalHeader.packageType}")
            }
        } catch (e: Exception) {
            ParseResult.Error("解析失败: ${e.message}")
        }
    }

    /**
     * 解码升级包文件到输出目录 / Decode an upgrade package file to the output directory.
     *
     * @param file 升级包文件 / Upgrade package file.
     * @param outputDir 输出目录（为 null 时使用默认目录）/ Output directory (uses default if null).
     * @return [DecodeResult] 解码结果 / Decode result.
     */
    fun decode(
        file: File,
        outputDir: File? = null
    ): DecodeResult {
        if (!file.exists()) {
            return DecodeResult(null, null, null, null, null, null, "文件不存在: ${file.absolutePath}")
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
            DecodeResult(null, null, null, null, null, null, "解码失败: ${e.message}")
        }
    }

    /**
     * 仅解码固件包 / Decode firmware-only package.
     *
     * @param file 升级包文件 / Upgrade package file.
     * @param outputDir 输出目录 / Output directory.
     * @return [DecodeResult] 解码结果 / Decode result.
     */
    fun decodeFirmwarePackage(
        file: File,
        outputDir: File? = null
    ): DecodeResult {
        val result = decode(file, outputDir)
        return if (result.hasFirmware && !result.hasModel) {
            result
        } else {
            DecodeResult(null, null, null, null, null, null, "不是有效的固件包")
        }
    }

    /**
     * 仅解码模型包 / Decode model-only package.
     *
     * @param file 升级包文件 / Upgrade package file.
     * @param outputDir 输出目录 / Output directory.
     * @return [DecodeResult] 解码结果 / Decode result.
     */
    fun decodeModelPackage(
        file: File,
        outputDir: File? = null
    ): DecodeResult {
        val result = decode(file, outputDir)
        return if (result.hasModel && !result.hasFirmware) {
            result
        } else {
            DecodeResult(null, null, null, null, null, null, "不是有效的模型包")
        }
    }

    /**
     * 仅解码组合包 / Decode combined package.
     *
     * @param file 升级包文件 / Upgrade package file.
     * @param outputDir 输出目录 / Output directory.
     * @return [DecodeResult] 解码结果 / Decode result.
     */
    fun decodeCombinedPackage(
        file: File,
        outputDir: File? = null
    ): DecodeResult {
        val result = decode(file, outputDir)
        return if (result.hasFirmware && result.hasModel) {
            result
        } else {
            DecodeResult(null, null, null, null, null, null, "不是有效的组合包")
        }
    }

    /**
     * 从模型 ZIP 中提取 .txt 与 .fpk 文件 / Extract .txt and .fpk files from model ZIP.
     *
     * @param modelZipFile 模型 ZIP 文件 / Model ZIP file.
     * @param destDir 目标目录 / Destination directory.
     * @return [Pair] 第一个为 TXT 文件，第二个为 FPK 文件 / First: TXT file, second: FPK file.
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
     * 校验数据 MD5 / Verify data MD5.
     *
     * @param data 待校验数据 / Data to verify.
     * @param expectedMd5 期望的 MD5 值 / Expected MD5 value.
     * @return 是否一致 / Whether they match.
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
     * 获取默认解码输出目录 / Get default decoded output directory.
     *
     * @param sourceFile 源升级包文件 / Source upgrade package file.
     * @return 默认目录 / Default directory.
     */
    private fun getDefaultDecodedDir(sourceFile: File): File {
        return File(sourceFile.parentFile, DECODED_DIR)
    }

    /**
     * 清理解码产生的临时文件 / Clean up decoded temporary files.
     *
     * @param decodedFile 待清理的文件 / File to clean up.
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
