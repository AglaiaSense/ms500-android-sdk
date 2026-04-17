/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */


#ifndef ANDROIDUSBCAMERA_MP3_H
#define ANDROIDUSBCAMERA_MP3_H

#ifdef __cplusplus
extern "C" {
#endif
void lameInitInternal(int inSampleRate, int outChannel, int outSampleRate, int outBitRate, int quality);
int lameEncodeInternal(short* leftBuf, short* rightBuf, int sampleRate, unsigned char* mp3Buf, int len);
int lameFlushInternal(unsigned char* mp3Buf, int len);
void lameCloseInternal();
#ifdef __cplusplus
};
#endif
#endif //ANDROIDUSBCAMERA_MP3_H
