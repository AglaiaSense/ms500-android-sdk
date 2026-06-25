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
import com.ms500.ausbc.render.internal.AbstractFboRender

/** abstract effect class, extended from AbstractFboRender
 *
 */
abstract class AbstractEffect(ctx: Context) : AbstractFboRender(ctx) {

    /**
     * Get effect id
     *
     * @return effect id
     */
    abstract fun getId(): Int

    /**
     * Get classify id
     *
     * @return effect classify id
     */
    abstract fun getClassifyId(): Int
}