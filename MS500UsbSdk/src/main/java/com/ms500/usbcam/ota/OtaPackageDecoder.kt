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

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Firmware / model OTA package decoder.
 *
 * This class remains the SDK-internal facade for file handling and decode entry
 * points. Binary package constants and parsing live in [OtaPackageCore], which
 * may be obfuscated independently.
 */
internal class OtaPackageDecoder {

    companion object {
        /** Default directory name for decoded files. */
        private const val DECODED_DIR = "decoded_packages"
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
        val firmwareHeader: OtaPackageCore.FirmwareHeader?,
        val modelHeader: OtaPackageCore.ModelHeader?,
        val error: String?
    ) {
        /** Whether decoding succeeded. */
        val isSuccess: Boolean get() = error == null

        /** Whether a valid firmware is present. */
        val hasFirmware: Boolean get() = firmwareFile != null && firmwareHeader != null

        /** Whether a valid model is present. */
        val hasModel: Boolean get() = modelFile != null && modelHeader != null
    }

    /** Parse global header from byte array. */
    fun parseGlobalHeader(data: ByteArray): OtaPackageCore.GlobalHeader {
        return OtaPackageCore.parseGlobalHeader(data)
    }

    /** Parse firmware header from byte array. */
    fun parseFirmwareHeader(data: ByteArray): OtaPackageCore.FirmwareHeader {
        return OtaPackageCore.parseFirmwareHeader(data)
    }

    /** Parse model header from byte array. */
    fun parseModelHeader(data: ByteArray): OtaPackageCore.ModelHeader {
        return OtaPackageCore.parseModelHeader(data)
    }

    /** Parse complete upgrade package from byte array. */
    fun parsePackage(data: ByteArray): OtaPackageCore.ParseResult {
        return OtaPackageCore.parsePackage(data)
    }

    /** Verify data MD5. */
    fun verifyMd5(data: ByteArray, expectedMd5: ByteArray): Boolean {
        return OtaPackageCore.verifyMd5(data, expectedMd5)
    }

    /**
     * Parse global header from file.
     *
     * Upgrade package file.
     * @return [OtaPackageCore.GlobalHeader] or null if file does not exist or parsing failed.
     */
    fun parseGlobalHeader(file: File): OtaPackageCore.GlobalHeader? {
        if (!file.exists() || file.length() < OtaPackageCore.GLOBAL_HEADER_SIZE) {
            return null
        }

        return try {
            FileInputStream(file).use { fis ->
                val headerBytes = ByteArray(OtaPackageCore.GLOBAL_HEADER_SIZE)
                val read = fis.read(headerBytes)
                if (read < OtaPackageCore.GLOBAL_HEADER_SIZE) {
                    return null
                }
                parseGlobalHeader(headerBytes)
            }
        } catch (_: Exception) {
            null
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
                is OtaPackageCore.ParseResult.Firmware -> {
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

                is OtaPackageCore.ParseResult.Model -> {
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

                is OtaPackageCore.ParseResult.Combined -> {
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

                is OtaPackageCore.ParseResult.Error -> DecodeResult(null, null, null, null, null, null, result.message)
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
