/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.ausbc.render.effect

import android.content.Context
import android.opengl.GLES20
import com.ms500.ausbc.R
import com.ms500.ausbc.render.effect.bean.CameraEffect

/** Soul effect
 *
 */
class EffectSoul(context: Context): AbstractEffect(context) {

    private var mTimeStampsHandler = -1
    private var mTimeCount = 0

    override fun getId(): Int = ID

    override fun getClassifyId(): Int = CameraEffect.CLASSIFY_ID_ANIMATION

    override fun init() {
        mTimeStampsHandler = GLES20.glGetUniformLocation(mProgram, "timeStamps")
    }

    override fun beforeDraw() {
        if (mTimeCount > 65535) {
            mTimeCount = 0
        }
        GLES20.glUniform1f(mTimeStampsHandler, (++mTimeCount % 9).toFloat())
    }

    override fun getVertexSourceId(): Int = R.raw.base_vertex

    override fun getFragmentSourceId(): Int = R.raw.effect_soul_fragment

    companion object {
        const val ID = 200
    }
}