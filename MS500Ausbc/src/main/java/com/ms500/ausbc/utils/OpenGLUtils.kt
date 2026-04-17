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

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES20
import javax.microedition.khronos.opengles.GL10

/** opengl es tool
 *
 */
object OpenGLUtils {
    private const val TAG = "OpenGLUtils"

    fun glGenTextures(textures: IntArray) {
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        Logger.i(TAG, "create texture, id = ${textures[0]}")
    }

    fun isGlEsSupported(context: Context): Boolean {
        return (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.let {
            try {
                it.deviceConfigurationInfo.glEsVersion.toFloat() >= 2.0f
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    private fun glGetErrorStr(err: Int): String {
        return when (err) {
            GL10.GL_STACK_OVERFLOW -> "stack overflow"
            GL10.GL_STACK_UNDERFLOW -> "stack underflow"
            GLES20.GL_NO_ERROR -> "GL_NO_ERROR"
            GLES20.GL_INVALID_ENUM -> "GL_INVALID_ENUM"
            GLES20.GL_INVALID_VALUE -> "GL_INVALID_VALUE"
            GLES20.GL_INVALID_OPERATION -> "GL_INVALID_OPERATION"
            GLES20.GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY"
            GLES20.GL_INVALID_FRAMEBUFFER_OPERATION -> "GL_INVALID_FRAMEBUFFER_OPERATION"
            else -> "unknown"
        }
    }

    fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            val msg = op + ": glError 0x" + Integer.toHexString(error) + ":" + glGetErrorStr(error)
            Logger.e(TAG, msg)
        }
    }
}