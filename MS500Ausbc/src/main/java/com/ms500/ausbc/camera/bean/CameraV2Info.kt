/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.ausbc.camera.bean

import android.hardware.camera2.CameraCharacteristics

/**
 *
 */
@kotlin.Deprecated("Deprecated since version 3.3.0")
data class CameraV2Info(override val cameraId: String) : CameraInfo(cameraId) {
    var cameraType: Int = 0
    var cameraCharacteristics: CameraCharacteristics? = null

    override fun toString(): String {
        return "CameraV2Info(cameraId='$cameraId', " +
                "cameraType=$cameraType, " +
                "cameraCharacteristics=$cameraCharacteristics)"
    }
}
