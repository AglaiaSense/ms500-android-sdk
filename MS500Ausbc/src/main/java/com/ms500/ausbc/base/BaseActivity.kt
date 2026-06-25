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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.ms500.ausbc.utils.ActivityStackUtils

/** Base Activity
 *  Extended from AppCompatActivity which implemented LifecycleOwner.
 *
 */
abstract class BaseActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getRootView(layoutInflater))
        initView()
        initData()
        ActivityStackUtils.pushActivity(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        clear()
        ActivityStackUtils.removeActivity(this)
    }

    protected abstract fun getRootView(layoutInflater: LayoutInflater): View?
    protected open fun initView() {}
    protected open fun initData() {}
    protected open fun clear() {}
}