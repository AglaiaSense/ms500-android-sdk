/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.ausbc.camera.bean

import androidx.annotation.Keep

/** Camera status
 *
 */
@kotlin.Deprecated("Deprecated since version 3.3.0")
@Keep
data class CameraStatus(val code: Int, val message: String? = null) {
    companion object {
        const val START = 1
        const val STOP = 2
        const val ERROR = -1
        const val ERROR_PREVIEW_SIZE = -2
    }
}
