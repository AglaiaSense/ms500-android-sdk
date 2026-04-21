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

/**
 * SDK error taxonomy.
 *
 * Provides clear error categories to help the business layer perform unified error handling and retry strategies.
 */
sealed class SdkError {

    /**
     * Gets the error description.
     *
     * Human-readable error message.
     */
    abstract fun getMessage(): String

    /**
     * Device not connected.
     *
     * Usually occurs when the USB device is not plugged in or permission is not granted.
     */
    object NotConnected : SdkError() {
        override fun getMessage(): String = "Device not connected"
    }

    /**
     * Device is connecting.
     *
     * Indicates that connection is currently being established; repeated operation calls will return this error.
     */
    object Connecting : SdkError() {
        override fun getMessage(): String = "Device is connecting"
    }

    /**
     * Request timeout.
     *
     * @property operation Operation name where timeout occurred (optional).
     */
    data class Timeout(val operation: String? = null) : SdkError() {
        override fun getMessage(): String =
            operation?.let { "Operation '$it' timeout" } ?: "Request timeout"
    }

    /**
     * Device rejected the request.
     *
     * @property code Error code returned by device (optional).
     * @property errorMessage Error message returned by device (optional).
     */
    data class DeviceRejected(
        val code: Int? = null,
        val errorMessage: String? = null
    ) : SdkError() {
        override fun getMessage(): String =
            errorMessage ?: code?.let { "Device rejected with code $it" } ?: "Device rejected request"
    }

    /**
     * Invalid response format.
     *
     * @property raw Raw response string (optional).
     * @property reason Reason why the response is invalid (optional).
     */
    data class InvalidResponse(
        val raw: String? = null,
        val reason: String? = null
    ) : SdkError() {
        override fun getMessage(): String =
            reason ?: "Invalid response format"
    }

    /**
     * Invalid argument.
     *
     * Detailed parameter error description.
     */
    data class InvalidArgument(
        val errorMessage: String
    ) : SdkError() {
        override fun getMessage(): String = errorMessage
    }

    /**
     * Unsupported operation.
     *
     * Explanation of why the operation is unsupported.
     */
    data class UnsupportedOperation(
        val errorMessage: String
    ) : SdkError() {
        override fun getMessage(): String = errorMessage
    }

    /**
     * Upgrade-related errors.
     *
     * Various sub-errors that may occur during firmware/software upgrade.
     */
    sealed class Upgrade : SdkError() {

        /**
         * Upgrade package validation failed.
         *
         * Reason why the package is invalid.
         */
        data class PackageInvalid(val reason: String) : Upgrade() {
            override fun getMessage(): String = "Package invalid: $reason"
        }

        /**
         * Version mismatch.
         *
         * Current version.
         * Target version.
         * Reason for the mismatch.
         */
        data class VersionMismatch(
            val current: String,
            val target: String,
            val reason: String
        ) : Upgrade() {
            override fun getMessage(): String =
                "Version mismatch: current=$current, target=$target, $reason"
        }

        /**
         * Sensor ID mismatch.
         *
         * Expected Sensor ID.
         * Actual Sensor ID.
         */
        data class SensorIdMismatch(
            val expected: String,
            val actual: String
        ) : Upgrade() {
            override fun getMessage(): String =
                "Sensor ID mismatch: expected=$expected, actual=$actual"
        }

        /**
         * Upgrade transfer failed.
         *
         * Reason why the transfer failed.
         */
        data class TransferFailed(val reason: String) : Upgrade() {
            override fun getMessage(): String = "Transfer failed: $reason"
        }

        /**
         * Upgrade cancelled by user.
         */
        object Cancelled : Upgrade() {
            override fun getMessage(): String = "Upgrade cancelled by user"
        }
    }

    /**
     * Generic upgrade error.
     *
     * Upgrade error description.
     */
    data class UpgradeError(
        val errorMessage: String
    ) : SdkError() {
        override fun getMessage(): String = "Upgrade error: $errorMessage"
    }

    /**
     * Internal error.
     *
     * @property cause Underlying throwable (optional).
     */
    data class InternalError(
        val cause: Throwable? = null
    ) : SdkError() {
        override fun getMessage(): String =
            cause?.message ?: "Internal error"
    }

    /**
     * Communication error.
     *
     * Reason for the communication failure.
     */
    data class CommunicationError(
        val reason: String
    ) : SdkError() {
        override fun getMessage(): String = "Communication error: $reason"
    }

    /**
     * Unknown error.
     *
     * @property errorMessage Error description (optional).
     */
    data class Unknown(
        val errorMessage: String? = null
    ) : SdkError() {
        override fun getMessage(): String = errorMessage ?: "Unknown error"
    }
}

/**
 * Converts a [Throwable] to an [SdkError].
 *
 * Corresponding [SdkError] instance.
 */
fun Throwable.toSdkError(): SdkError {
    return when (this) {
        is SdkErrorException -> this.error
        else -> SdkError.InternalError(this)
    }
}

/**
 * Exception that carries an [SdkError].
 *
 * Associated SDK error.
 * @param cause Original throwable (optional).
 */
class SdkErrorException(
    val error: SdkError,
    cause: Throwable? = null
) : Exception(error.getMessage(), cause)
