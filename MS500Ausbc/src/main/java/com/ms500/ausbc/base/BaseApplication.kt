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

import android.app.Application
import com.ms500.ausbc.utils.CrashUtils
import com.ms500.ausbc.utils.Logger
import com.ms500.ausbc.utils.ToastUtils

/** Base Application
 *
 */
open class BaseApplication: Application() {

    override fun onCreate() {
        super.onCreate()
        CrashUtils.init(this)
        Logger.init(this)
        ToastUtils.init(this)
    }
}