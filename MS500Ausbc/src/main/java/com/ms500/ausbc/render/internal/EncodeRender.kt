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
import android.opengl.EGLContext
import android.view.Surface
import com.ms500.ausbc.R
import com.ms500.ausbc.render.env.EGLEvn

/** Inherit from AbstractFboRender
 *      render data to EGL from fbo and encode it
 *
 */
class EncodeRender(context: Context): AbstractRender(context) {

    private var mEgl: EGLEvn? = null

    fun initEGLEvn(glContext: EGLContext) {
        mEgl = EGLEvn()
        mEgl?.initEgl(glContext)
    }

    fun setupSurface(surface: Surface) {
        mEgl?.setupSurface(surface)
        mEgl?.eglMakeCurrent()
    }

    fun swapBuffers(timeStamp: Long) {
        mEgl?.setPresentationTime(timeStamp)
        mEgl?.swapBuffers()
    }

    override fun clear() {
        mEgl?.releaseElg()
        mEgl = null
    }

    override fun getVertexSourceId(): Int = R.raw.base_vertex

    override fun getFragmentSourceId(): Int = R.raw.base_fragment
}