/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.ausbc.render.effect.bean

import androidx.annotation.Keep
import com.ms500.ausbc.R
import com.ms500.ausbc.render.effect.AbstractEffect

/** camera filter info
 *
 */
@Keep
data class CameraEffect(
    val id: Int,
    val name: String,
    val classifyId: Int,
    val effect: AbstractEffect? = null,
    val coverResId: Int? = null,
    val coverUrl: String? = null
) {
    companion object {
        val NONE_FILTER by lazy {
            CameraEffect(
                ID_NONE_FILTER,
                NAME_NONE,
                CLASSIFY_ID_FILTER,
                coverResId = R.drawable.effect_none
            )
        }

        val NONE_ANIMATION by lazy {
            CameraEffect(
                ID_NONE_ANIMATION,
                NAME_NONE,
                CLASSIFY_ID_ANIMATION,
                coverResId = R.drawable.effect_none
            )
        }

        const val CLASSIFY_ID_FILTER = 1
        const val CLASSIFY_ID_ANIMATION = 2
        const val ID_NONE_FILTER = -1
        const val ID_NONE_ANIMATION = -2
        private const val NAME_NONE = "None"
    }
}