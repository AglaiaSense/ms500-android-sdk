/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */

#ifndef ANDROIDUSBCAMERA_PROXY_MP3_H
#define ANDROIDUSBCAMERA_PROXY_MP3_H

#ifdef __cplusplus
extern "C" {
#endif

#include <jni.h>

void lameInit(JNIEnv *env, jobject instance, jint inSampleRate, jint outChannel, jint outSampleRate, jint outBitRate, jint quality);
jint lameEncode(JNIEnv *env, jobject instance, jshortArray leftBuf_, jshortArray rightBuf, jint sampleRate, jbyteArray mp3Buf);
jint lameFlush(JNIEnv *env, jobject instance, jbyteArray mp3Buf);
void lameClose(JNIEnv *env, jobject instance);

#ifdef __cplusplus
};
#endif

#endif //ANDROIDUSBCAMERA_PROXY_MP3_H
