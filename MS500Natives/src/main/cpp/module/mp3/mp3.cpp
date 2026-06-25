/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */

#include "mp3.h"
#include "lame/lame.h"
#include "../../utils/logger.h"

static lame_global_flags *gfp = nullptr;

void lameInitInternal(int inSampleRate, int outChannel, int outSampleRate, int outBitRate, int quality) {
    if(gfp != nullptr){
        lameCloseInternal();
    }
    gfp = lame_init();
    LOGI("init lame library success!");
    lame_set_in_samplerate(gfp,inSampleRate);
    lame_set_num_channels(gfp,outChannel);
    lame_set_out_samplerate(gfp,outSampleRate);
    lame_set_brate(gfp,outBitRate);
    lame_set_quality(gfp,quality);
    lame_init_params(gfp);
    LOGI("config lame library success!");
}

int lameEncodeInternal(short* leftBuf, short* rightBuf, int sampleRate, unsigned char* mp3Buf, int len) {
    int ret = lame_encode_buffer(gfp,leftBuf,rightBuf,sampleRate,mp3Buf,len);
    if (ret < 0) {
        LOG_E("encode pcm data failed, err = %d",ret);
    }
    return ret;
}

int lameFlushInternal(unsigned char* mp3Buf, int len) {
    int ret = lame_encode_flush(gfp,mp3Buf,len);
    if (ret <= 0) {
        LOG_E("flush lame failed, err = %d", ret);
    }
    return ret;
}

void lameCloseInternal() {
    lame_close(gfp);
    gfp = nullptr;
    LOGI("close lame success!");
}