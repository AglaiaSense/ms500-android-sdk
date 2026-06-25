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

import java.nio.ByteBuffer

/** Encode data callback
 *
 */
interface IEncodeDataCallBack {
    fun onEncodeData(type: DataType, buffer:ByteBuffer, offset: Int, size: Int, timestamp: Long)
    enum class DataType {
        AAC,       // aac without ADTS header,
                   // if want adding adts, should call MediaUtils.addADTStoPacket() method
        H264_KEY,  // H.264, key frame
        H264_SPS,  // H.264, sps & pps
        H264       // H.264 not key frame
    }
}