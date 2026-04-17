/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.ausbc.utils

import android.os.Environment
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception

/** save h264 data for test
 *
 */
object H264TestUtil {
    private var fos: FileOutputStream? = null

    fun createFile() {
        try {
            // /sdcard root path/file_xxxx.264
            fos = FileOutputStream(Environment.getExternalStorageDirectory().path + "/file_" + System.currentTimeMillis() + ".264")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun writeFile(data: ByteArray) {
        try {
            fos?.write(data)
            fos?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun closeFile() {
        try {
            fos?.close()
            fos = null
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}