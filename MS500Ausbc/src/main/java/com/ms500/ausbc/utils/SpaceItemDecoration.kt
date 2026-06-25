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

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/** RecyclerView.ItemDecoration
 *
 */
class SpaceItemDecoration(private val space: Int): RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        outRect.left = space;
        outRect.bottom = space;
        if (parent.getChildLayoutPosition(view) %3==0) {
            outRect.left = 0
        }
    }
}