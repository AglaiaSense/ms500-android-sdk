/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.usbcam.cdc


/**
 * Public CDC protocol constants.
 *
 * The frame encoder/decoder is kept in CdcWireFormat so this object can remain
 * a stable, non-obfuscated entry point for SDK users and documentation samples.
 */
object CdcProtocol {
    /** Parameter read request/response type. */
    const val MSG_TYPE_PARAM_GET = 0x0001

    /** Parameter set request/response type. */
    const val MSG_TYPE_PARAM_SET = 0x0002

    /** Register read request/response type. */
    const val MSG_TYPE_REG_GET = 0x0003

    /** Register write request/response type. */
    const val MSG_TYPE_REG_SET = 0x0004

    /** File data channel type. */
    const val MSG_TYPE_FILE_DATA = 0x0005

    /** OTA / large-file command channel type. */
    const val MSG_TYPE_OTA_UPGRADE = 0x0006

    /** Tensor export command / metadata type. */
    const val MSG_TYPE_TENSOR_EXPORT = 0x0007

    /** System control command type. */
    const val MSG_TYPE_SYS_CTRL = 0x0008

    /** Current ESP32 inference result report type. */
    const val MSG_TYPE_INFERENCE_RESULT = 0x1000

    /** Generic success response type. */
    const val MSG_TYPE_RESP_OK = 0x9000

    /** Generic error response type. */
    const val MSG_TYPE_RESP_ERROR = 0x9001

    /** Returns a human-readable name for the given msgType. */
    fun getTypeName(type: Int): String = when (type) {
        MSG_TYPE_PARAM_GET -> "MSG_TYPE_PARAM_GET(0x0001)"
        MSG_TYPE_PARAM_SET -> "MSG_TYPE_PARAM_SET(0x0002)"
        MSG_TYPE_REG_GET -> "MSG_TYPE_REG_GET(0x0003)"
        MSG_TYPE_REG_SET -> "MSG_TYPE_REG_SET(0x0004)"
        MSG_TYPE_FILE_DATA -> "MSG_TYPE_FILE_DATA(0x0005)"
        MSG_TYPE_OTA_UPGRADE -> "MSG_TYPE_OTA_UPGRADE(0x0006)"
        MSG_TYPE_TENSOR_EXPORT -> "MSG_TYPE_TENSOR_EXPORT(0x0007)"
        MSG_TYPE_SYS_CTRL -> "MSG_TYPE_SYS_CTRL(0x0008)"
        MSG_TYPE_INFERENCE_RESULT -> "MSG_TYPE_INFERENCE_RESULT(0x1000)"
        MSG_TYPE_RESP_OK -> "MSG_TYPE_RESP_OK(0x9000)"
        MSG_TYPE_RESP_ERROR -> "MSG_TYPE_RESP_ERROR(0x9001)"
        else -> "UNKNOWN(0x${type.toString(16).padStart(4, '0')})"
    }
}
