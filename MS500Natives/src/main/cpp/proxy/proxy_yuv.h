/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */

#ifndef ANDROIDUSBCAMERA_PROXY_YUV_H
#define ANDROIDUSBCAMERA_PROXY_YUV_H
#ifdef __cplusplus
extern "C" {
#endif

#include <jni.h>
#include <cstdlib>
#include "../module/yuv/yuv.h"
#include "../utils/logger.h"

void yuv420spToNv21(JNIEnv *env, jobject instance, jbyteArray data, jint width, jint height);
void nv21ToYuv420sp(JNIEnv *env, jobject instance, jbyteArray data, jint width, jint height);
void nv21ToYuv420spWithMirror(JNIEnv *env, jobject instance, jbyteArray data, jint width, jint height);
void nv21ToYuv420p(JNIEnv *env, jobject instance, jbyteArray data, jint width, jint height);
void nv21ToYuv420pWithMirror(JNIEnv *env, jobject instance, jbyteArray data, jint width, jint height);
void nativeRotateNV21(JNIEnv *env, jobject instance, jbyteArray data, jint width, jint height, jint degree);

#ifdef __cplusplus
};
#endif
#endif //ANDROIDUSBCAMERA_PROXY_YUV_H
