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

import android.content.Context
import android.widget.Toast
import androidx.annotation.MainThread

/** Simple Toast
 *
 * @author Created by jiangdongguo on 2022/1/21
 */
object ToastUtils {

    private var applicationCtx: Context ?= null

    @MainThread
    fun init(ctx: Context) {
        if (applicationCtx != null) {
            return
        }
        this.applicationCtx = ctx.applicationContext
    }

    @JvmStatic
    fun show(msg: String) {
        applicationCtx?.let { ctx ->
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }

    @JvmStatic
    fun show(resId: Int) {
        applicationCtx?.let { ctx ->
            Toast.makeText(ctx, ctx.getString(resId), Toast.LENGTH_LONG).show()
        }
    }

}