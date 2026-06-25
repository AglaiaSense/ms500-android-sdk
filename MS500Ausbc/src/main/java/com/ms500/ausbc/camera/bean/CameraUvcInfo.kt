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

/** uvc camera info
 *
 */
@kotlin.Deprecated("Deprecated since version 3.3.0")
class CameraUvcInfo(override val cameraId: String) : CameraInfo(cameraId) {
    var cameraName: String = ""
    var cameraProductName: String? = null
    var cameraManufacturerName: String? = null
    var cameraProtocol: Int = 0
    var cameraClass: Int = 0
    var cameraSubClass: Int = 0

    override fun toString(): String {
        return "CameraUvcInfo(cameraId='$cameraId', " +
                "cameraName='$cameraName', " +
                "cameraProductName='$cameraProductName', " +
                "cameraManufacturerName='$cameraManufacturerName', " +
                "cameraProtocol=$cameraProtocol, " +
                "cameraClass=$cameraClass, " +
                "cameraSubClass=$cameraSubClass, " +
                "cameraVid=$cameraVid, " +
                "cameraPid=$cameraPid, " +
                "cameraPreviewSizes=$cameraPreviewSizes)"

    }
}
