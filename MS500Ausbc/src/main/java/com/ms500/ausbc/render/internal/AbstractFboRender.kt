/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.ausbc.render.internal

import android.content.Context
import android.opengl.GLES20
import com.ms500.ausbc.utils.Logger
import com.ms500.ausbc.utils.OpenGLUtils

/** A AbstractRender subclass, also abstract
 *       create a fbo,and draw to it instead of screen.
 *
 * Attention: Your should set your context as the current context before creating fbo,
 *      Otherwise GLES20.glCheckFramebufferStatus=0 on some other devices!
 *
 */
abstract class AbstractFboRender(context: Context) : AbstractRender(context) {
    private val mFrameBuffers by lazy {
        IntArray(1)
    }

    // texture id for draw frame
    // not mFrameBuffers, otherwise will be "clear error 0x502"
    private val mFBOTextures by lazy {
        IntArray(1)
    }

    // download image need when call
    // glBindFramebuffer()
    fun getFrameBufferId() = mFrameBuffers[0]

    fun getFrameBufferTexture() = mFBOTextures[0]

    override fun drawFrame(textureId: Int): Int {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0])
        super.drawFrame(textureId)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        // Convenient reuse of FBO
        afterDrawFBO()
        return mFBOTextures[0]
    }

    override fun setSize(width: Int, height: Int) {
        super.setSize(width, height)
        loadFBO(width, height)
    }

    protected open fun afterDrawFBO() {}

    private fun loadFBO(width: Int, height: Int) {
        destroyFrameBuffers()
        //Create FrameBuffer
        GLES20.glGenFramebuffers(mFrameBuffers.size, mFrameBuffers, 0)
        //Texture in FBO
        createTexture(mFBOTextures)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFBOTextures[0])
        //Specifies the format of the output image of the FBO texture RGBA
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height,
            0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        GLES20.glBindFramebuffer(
            GLES20.GL_FRAMEBUFFER,
            mFrameBuffers[0]
        )
        OpenGLUtils.checkGlError("glBindFramebuffer")
        //Bind fbo to 2d texture
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D,
            mFBOTextures[0], 0
        )
        OpenGLUtils.checkGlError("glFramebufferTexture2D")
        //Unbind textures and FrameBuffer
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        Logger.i(TAG, "load fbo, textures: $mFBOTextures, buffers: $mFrameBuffers")
    }

    private fun destroyFrameBuffers() {
        GLES20.glDeleteTextures(1, mFBOTextures, 0)
        GLES20.glDeleteFramebuffers(1, mFrameBuffers, 0)
    }

    companion object {
        private const val TAG = "AbstractFboRender"
    }
}