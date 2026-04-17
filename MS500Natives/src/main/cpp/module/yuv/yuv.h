/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */

#ifndef ANDROIDUSBCAMERA_YUV_H
#define ANDROIDUSBCAMERA_YUV_H

#ifdef __cplusplus
extern "C" {
#endif
#include <cstring>

void *yuv420spToNv21Internal(char* srcData, char* destData, int width, int height);
void *nv21ToYuv420spInternal(char* srcData, char* destData, int width, int height);
void *nv21ToYuv420spWithMirrorInternal(char* srcData, char* destData, int width, int height);
void *nv21ToYuv420pInternal(char* srcData, char* destData, int width, int height);
void *nv21ToYuv420pWithMirrorInternal(char* srcData, char* destData, int width, int height);

#ifdef __cplusplus
};
#endif
#endif //ANDROIDUSBCAMERA_YUV_H
