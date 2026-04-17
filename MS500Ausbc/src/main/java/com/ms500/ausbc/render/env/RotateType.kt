/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.ausbc.render.env

/** rotate angle type
 *
 */
enum class RotateType {
    ANGLE_0,        // default, do nothing
    ANGLE_90,
    ANGLE_180,
    ANGLE_270,
    FLIP_UP_DOWN,    // flip vertically
    FLIP_LEFT_RIGHT  // horizontal flip(mirror)
}