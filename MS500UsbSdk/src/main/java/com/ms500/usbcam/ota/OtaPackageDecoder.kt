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
 * 固件/模型升级包解码工具
 */
class OtaPackageDecoder {

    companion object {
        const val GLOBAL_MAGIC = 0x474C4F42
        const val FIRMWARE_HEADER_MAGIC = 0x46575246
        const val MODEL_HEADER_MAGIC = 0x4C444F4D
        const val FW_DATA_MAGIC = 0x46574441
        const val MODEL_DATA_MAGIC = 0x4D444154

        const val PACKAGE_TYPE_FIRMWARE = 0x00000001
        const val PACKAGE_TYPE_MODEL = 0x00000002
        const val PACKAGE_TYPE_COMBINED = 0x00000003

        const val GLOBAL_HEADER_SIZE = 8
        const val FIRMWARE_HEADER_SIZE = 64
        const val MODEL_HEADER_SIZE = 64
        const val DATA_MAGIC_SIZE = 4

        const val FIRMWARE_NAME_LEN = 10
        const val FIRMWARE_RESERVED_LEN = 26

        const val MODEL_NAME_LEN = 10
        const val MODEL_SENSOR_ID_LEN = 16
        const val MODEL_RESERVED_LEN = 10

        const val MD5_LEN = 16

        private const val DECODED_DIR = "decoded_packages"
    }

    data class GlobalHeader(
        val magic: Int,
        val packageType: Int
    ) {
        val isValid: Boolean get() = magic == GLOBAL_MAGIC
        val hasFirmware: Boolean get() = packageType == PACKAGE_TYPE_FIRMWARE || packageType == PACKAGE_TYPE_COMBINED
        val hasModel: Boolean get() = packageType == PACKAGE_TYPE_MODEL || packageType == PACKAGE_TYPE_COMBINED
    }

    data class FirmwareHeader(
        val magic: Int,
        val name: String,
        val version: Int,
        val fileSize: Int,
        val md5: ByteArray,
        val reserved: ByteArray = ByteArray(FIRMWARE_RESERVED_LEN)
    ) {
        val isValid: Boolean get() = magic == FIRMWARE_HEADER_MAGIC

        val versionString: String
            get() {
                val major = (version shr 16) and 0xFF
                val minor = (version shr 8) and 0xFF
                val patch = version and 0xFF
                return "$major.$minor.$patch"
            }
    }

    data class ModelHeader(
        val magic: Int,
        val name: String,
        val version: Int,
        val fileSize: Int,
        val md5: ByteArray,
        val sensorId: ByteArray,
        val reserved: ByteArray = ByteArray(MODEL_RESERVED_LEN)
    ) {
        val isValid: Boolean get() = magic == MODEL_HEADER_MAGIC

        val versionString: String
            get() {
                val major = (version shr 16) and 0xFF
                val minor = (version shr 8) and 0xFF
                val patch = version and 0xFF
                return "$major.$minor.$patch"
            }

        val sensorIdString: String
            get() = sensorId.joinToString("") { "%02X".format(it) }
    }

    sealed class ParseResult {
        data class Firmware(
            val globalHeader: GlobalHeader,
            val firmwareHeader: FirmwareHeader,
            val firmwareData: ByteArray
        ) : ParseResult()

        data class Model(
            val globalHeader: GlobalHeader,
            val modelHeader: ModelHeader,
            val modelData: ByteArray
        ) : ParseResult()

        data class Combined(
            val globalHeader: GlobalHeader,
            val firmwareHeader: FirmwareHeader,
            val modelHeader: ModelHeader,
            val firmwareData: ByteArray,
            val modelData: ByteArray
        ) : ParseResult()

        data class Error(val message: String) : ParseResult()
    }

    data class DecodeResult(
        val firmwareFile: File?,
        val modelFile: File?,
        val modelInfoFile: File?,
        val modelDataFile: File?,
        val firmwareHeader: FirmwareHeader?,
        val modelHeader: ModelHeader?,
        val error: String?
    ) {
        val isSuccess: Boolean get() = error == null
        val hasFirmware: Boolean get() = firmwareFile != null && firmwareHeader != null
        val hasModel: Boolean get() = modelFile != null && modelHeader != null
    }

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

    fun verifyMd5(data: ByteArray, expectedMd5: ByteArray): Boolean {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val actualMd5 = md.digest(data)
            actualMd5.contentEquals(expectedMd5)
        } catch (_: Exception) {
            false
        }
    }

    private fun getDefaultDecodedDir(sourceFile: File): File {
        return File(sourceFile.parentFile, DECODED_DIR)
    }

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
