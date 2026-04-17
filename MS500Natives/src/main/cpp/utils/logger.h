/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */


#ifndef ANDROIDUSBCAMERA_LOGGER_H
#define ANDROIDUSBCAMERA_LOGGER_H

#ifdef __cplusplus
extern "C" {
#endif

#include <android/log.h>

#define TAG "AUSBC"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, "%s", __VA_ARGS__)
#define LOG_I(format, ...) __android_log_print(ANDROID_LOG_INFO, TAG, format, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", __VA_ARGS__)
#define LOG_E(format, ...) __android_log_print(ANDROID_LOG_ERROR, TAG, format, __VA_ARGS__)

#ifdef __cplusplus
};
#endif
#endif //ANDROIDUSBCAMERA_LOGGER_H
