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

import android.app.Activity
import java.util.*

/** Activity stack manager
 *
 */
object ActivityStackUtils {
    private const val TAG = "ActivityStackUtils"
    private val mStack: Stack<Activity> = Stack()

    fun pushActivity(activity: Activity) {
        mStack.push(activity)
        Logger.d(TAG, "push stack: ${activity.localClassName}")
    }

    fun popActivity() {
        if (!mStack.empty()) {
            val activity: Activity = mStack.pop()
            activity.finish()
            Logger.d(TAG, "pop stack: ${activity.localClassName}")
        }
    }

    fun removeActivity(activity: Activity) {
        if (!mStack.empty()) {
            mStack.remove(activity)
            Logger.d(TAG, "remove stack: ${activity.localClassName}")
        }
    }

    fun getStackTop(): Activity? {
        var activity: Activity? = null
        if (!mStack.empty()) {
            activity = mStack.peek()
            Logger.d(TAG, "stack top: ${activity.localClassName}")
        }
        return activity
    }

    fun popAllActivity() {
        if (!mStack.empty()) {
            val size: Int = mStack.size
            for (i in 0 until size) {
                popActivity()
            }
        }
    }

    fun hasActivity() = !mStack.isEmpty()
}