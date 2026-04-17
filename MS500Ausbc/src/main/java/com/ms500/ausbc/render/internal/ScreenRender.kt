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
import android.view.Surface
import com.ms500.ausbc.R
import com.ms500.ausbc.render.env.EGLEvn
/** Inherit from AbstractFboRender
 *      render data to screen from fbo with base_vertex.glsl and base_fragment.glsl
 *
 */
class ScreenRender(context: Context) : AbstractRender(context) {
    private var mEgl: EGLEvn? = null

    fun initEGLEvn() {
        mEgl = EGLEvn()
        mEgl?.initEgl()
    }

    fun setupSurface(surface: Surface?, surfaceWidth: Int = 0, surfaceHeight: Int = 0): Boolean {
        val success = mEgl?.setupSurface(surface, surfaceWidth, surfaceHeight) == true
        if (success) {
            mEgl?.eglMakeCurrent()
        }
        return success
    }

    fun swapBuffers(timeStamp: Long) {
        mEgl?.setPresentationTime(timeStamp)
        mEgl?.swapBuffers()
    }

    fun getCurrentContext() = mEgl?.getEGLContext()

    override fun clear() {
        mEgl?.releaseElg()
        mEgl = null
    }

    override fun getVertexSourceId(): Int = R.raw.base_vertex

    override fun getFragmentSourceId(): Int = R.raw.base_fragment
}
