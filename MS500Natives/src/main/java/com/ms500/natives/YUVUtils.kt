/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */

package com.ms500.natives

/** YUV format transform
 *
 */
object YUVUtils {
    init {
        System.loadLibrary("nativelib")
    }

    external fun yuv420spToNv21(data: ByteArray, width: Int, height: Int)
    external fun nv21ToYuv420sp(data: ByteArray, width: Int, height: Int)
    external fun nv21ToYuv420spWithMirror(data: ByteArray, width: Int, height: Int)
    external fun nv21ToYuv420p(data: ByteArray, width: Int, height: Int)
    external fun nv21ToYuv420pWithMirror(data: ByteArray, width: Int, height: Int)
    external fun nativeRotateNV21(data: ByteArray, width: Int, height: Int, degree: Int)
}