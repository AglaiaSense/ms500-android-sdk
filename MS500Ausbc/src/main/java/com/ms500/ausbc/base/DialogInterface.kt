/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.ausbc.base

import android.app.Dialog

interface DialogInterface {
    fun getDialog(): Dialog

    fun show()

    fun dismiss()

    fun isShowing(): Boolean

    fun setCanceledOnTouchOutside(cancel: Boolean)

    fun setCancelable(flag: Boolean)
}