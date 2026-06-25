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
 * SDK unified result model.
 *
 * @param T Data type on success.
 */
sealed class SdkResult<out T> {

    /**
     * Successful SDK result.
     *
     * @property data Typed DTO or value parsed from the CDC response. Application
     * code should normally use this field.
     * @property raw Original CDC response text returned by the device. This is
     * intended for logs, automation traces, and protocol debugging. It may be
     * null when the result was created locally without a device response.
     */
    data class Success<T>(
        val data: T,
        val raw: String? = null
    ) : SdkResult<T>()

    /**
     * Failed SDK result.
     *
     * @property error Typed SDK error.
     * @property raw Original CDC response text when the failure came from a
     * device response or lower-layer callback. It is kept even on failures so
     * SDK developers can compare the error with the exact protocol payload.
     */
    data class Failure(
        val error: SdkError,
        val raw: String? = null
    ) : SdkResult<Nothing>()

    /** Returns true when this result is [Success]. */
    val isSuccess: Boolean get() = this is Success

    /** Performs [action] when this result is [Success]. */
    fun onSuccess(action: (T) -> Unit): SdkResult<T> {
        if (this is Success) action(data)
        return this
    }

    /** Performs [action] when this result is [Failure]. */
    fun onFailure(action: (SdkError) -> Unit): SdkResult<T> {
        if (this is Failure) action(error)
        return this
    }

    /** Maps success data while preserving [Success.raw] / [Failure.raw]. */
    fun <R> map(transform: (T) -> R): SdkResult<R> {
        return when (this) {
            is Success -> Success(transform(data), raw)
            is Failure -> Failure(error, raw)
        }
    }

    companion object {
        /** Creates a success result and optionally attaches the original CDC response text. */
        fun <T> success(data: T, raw: String? = null): SdkResult<T> = Success(data, raw)

        /** Creates a failure result and optionally attaches the original CDC response text. */
        fun <T> failure(error: SdkError, raw: String? = null): SdkResult<T> = Failure(error, raw)
    }
}
