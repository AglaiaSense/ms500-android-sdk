/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */

package com.ms500.usbcam.result

/** SDK error taxonomy. */
sealed class SdkError {

    /** Gets the human-readable error description. */
    abstract fun getMessage(): String

    /** Device is not connected. */
    object NotConnected : SdkError() {
        override fun getMessage(): String = "Device not connected"
    }

    /** Request timeout. */
    data class Timeout(val operation: String? = null) : SdkError() {
        override fun getMessage(): String =
            operation?.let { "Operation '$it' timeout" } ?: "Request timeout"
    }

    /** Device rejected the request. */
    data class DeviceRejected(
        val errorMessage: String? = null
    ) : SdkError() {
        override fun getMessage(): String = errorMessage ?: "Device rejected request"
    }

    /**
     * Invalid response format.
     *
     * @property raw Original CDC response text that failed parsing, when
     * available. This is diagnostic data, not part of the stable business API.
     * @property reason Human-readable parse failure reason.
     */
    data class InvalidResponse(
        val raw: String? = null,
        val reason: String? = null
    ) : SdkError() {
        override fun getMessage(): String = reason ?: "Invalid response format"
    }

    /** Invalid argument. */
    data class InvalidArgument(
        val errorMessage: String
    ) : SdkError() {
        override fun getMessage(): String = errorMessage
    }

    /** Unsupported operation. */
    data class UnsupportedOperation(
        val errorMessage: String
    ) : SdkError() {
        override fun getMessage(): String = errorMessage
    }

    /** Upgrade-related errors. */
    sealed class Upgrade : SdkError() {

        /** Version mismatch. */
        data class VersionMismatch(
            val current: String,
            val target: String,
            val reason: String
        ) : Upgrade() {
            override fun getMessage(): String =
                "Version mismatch: current=$current, target=$target, $reason"
        }

        /** Upgrade cancelled by user. */
        object Cancelled : Upgrade() {
            override fun getMessage(): String = "Upgrade cancelled by user"
        }
    }

    /** Internal error. */
    data class InternalError(
        val cause: Throwable? = null
    ) : SdkError() {
        override fun getMessage(): String = cause?.message ?: "Internal error"
    }

    /** Communication error. */
    data class CommunicationError(
        val reason: String
    ) : SdkError() {
        override fun getMessage(): String = "Communication error: $reason"
    }

    /** Unknown error. */
    data class Unknown(
        val errorMessage: String? = null
    ) : SdkError() {
        override fun getMessage(): String = errorMessage ?: "Unknown error"
    }
}

/** Converts a [Throwable] to an [SdkError]. */
fun Throwable.toSdkError(): SdkError = SdkError.InternalError(this)
