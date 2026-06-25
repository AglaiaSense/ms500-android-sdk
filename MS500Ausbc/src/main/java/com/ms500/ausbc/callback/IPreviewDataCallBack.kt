/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.ausbc.callback

/** Camera preview data callback
 *
 */
interface IPreviewDataCallBack {
    fun onPreviewData(data: ByteArray?, width: Int, height: Int, format: DataFormat)

    enum class DataFormat {
        NV21, RGBA
    }
}