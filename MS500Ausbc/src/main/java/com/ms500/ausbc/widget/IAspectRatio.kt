/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.ausbc.widget

import android.view.Surface

/** aspect ratio setting func interface
 *
 */
interface IAspectRatio {
    fun setAspectRatio(width: Int, height: Int)
    fun getSurfaceWidth(): Int
    fun getSurfaceHeight(): Int
    fun getSurface(): Surface?
    fun postUITask(task: ()->Unit)
}