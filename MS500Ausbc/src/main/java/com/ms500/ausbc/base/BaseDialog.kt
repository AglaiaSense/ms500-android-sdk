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

import android.app.Activity
import android.app.Dialog
import android.content.res.Configuration
import android.util.DisplayMetrics
import com.ms500.ausbc.R

abstract class BaseDialog(
        activity: Activity,
        portraitWidthRatio: Float = 0.67F,
        landscapeWidthRatio: Float = 0.5F
) : DialogInterface {
    private val mContext: Activity = activity
    protected val mDialog: Dialog = Dialog(mContext, R.style.CommonDialogStyle)

    init {
        mDialog.setContentView(this.getContentLayoutId())
        val orientation = mContext.resources.configuration.orientation
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE // 是否横屏
        mDialog.window?.let {
            // dialog的宽度 横屏设置为50% 竖屏设置为80%
            val dm = DisplayMetrics()
            it.windowManager?.defaultDisplay?.run {
                getMetrics(dm)
                val lp = it.attributes
                lp.width = (dm.widthPixels * if (isLandscape) landscapeWidthRatio else portraitWidthRatio).toInt()
                it.attributes = lp
            }
        }
        mDialog.setCanceledOnTouchOutside(false)
    }

    protected abstract fun getContentLayoutId(): Int

    final override fun getDialog(): Dialog = mDialog

    override fun show() {
        getDialog().show()
    }

    override fun dismiss() {
        getDialog().dismiss()
    }

    override fun isShowing(): Boolean {
        return getDialog().isShowing
    }

    override fun setCanceledOnTouchOutside(cancel: Boolean) {
        getDialog().setCanceledOnTouchOutside(cancel)
    }

    override fun setCancelable(flag: Boolean) {
        getDialog().setCancelable(flag)
    }
}
