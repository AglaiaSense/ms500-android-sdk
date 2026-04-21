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
 * Replaces the legacy JSONObject-based return wrapper with a type-safe success/failure result.
 *
 * @param T Data type on success.
 */
sealed class SdkResult<out T> {

    /**
     * Success result.
     *
     * Payload data on success.
     * @property raw Raw response string for debugging (optional).
     */
    data class Success<T>(
        val data: T,
        val raw: String? = null
    ) : SdkResult<T>()

    /**
     * Failure result.
     *
     * Error details.
     * @property raw Raw response string for debugging (optional).
     */
    data class Failure(
        val error: SdkError,
        val raw: String? = null
    ) : SdkResult<Nothing>()

    /**
     * Returns `true` if this result is [Success].
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * Returns `true` if this result is [Failure].
     */
    val isFailure: Boolean get() = this is Failure

    /**
     * Returns the success data or `null` if failed.
     *
     * Success data or `null`.
     */
    fun getOrNull(): T? = (this as? Success)?.data

    /**
     * Returns the error or `null` if successful.
     *
     * [SdkError] or `null`.
     */
    fun errorOrNull(): SdkError? = (this as? Failure)?.error

    /**
     * Returns the success data or throws [SdkResultException] if failed.
     *
     * Success data.
     * Thrown when this result is [Failure].
     */
    fun getOrThrow(): T {
        return when (this) {
            is Success -> data
            is Failure -> throw SdkResultException(error, raw)
        }
    }

    /**
     * Performs the given [action] if this result is [Success].
     *
     * Callback that consumes the success data.
     * The current [SdkResult] instance.
     */
    fun onSuccess(action: (T) -> Unit): SdkResult<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * Performs the given [action] if this result is [Failure].
     *
     * Callback that consumes the error.
     * The current [SdkResult] instance.
     */
    fun onFailure(action: (SdkError) -> Unit): SdkResult<T> {
        if (this is Failure) action(error)
        return this
    }

    /**
     * Maps the success data to another type.
     *
     * Function to transform the success data.
     * Transformed [SdkResult].
     */
    fun <R> map(transform: (T) -> R): SdkResult<R> {
        return when (this) {
            is Success -> Success(transform(data), raw)
            is Failure -> Failure(error, raw)
        }
    }

    /**
     * Returns the success data or the provided [default] value.
     *
     * Default value to return on failure.
     * Success data or [default].
     */
    fun getOrDefault(default: @UnsafeVariance T): T = getOrNull() ?: default

    /**
     * Returns the success data or computes a default from the error.
     *
     * Callback that produces a default value from the error.
     * Success data or computed default.
     */
    fun getOrElse(onFailure: (SdkError) -> @UnsafeVariance T): T {
        return when (this) {
            is Success -> data
            is Failure -> onFailure(error)
        }
    }

    /**
     * Companion object for factory methods.
     */
    companion object {

        /**
         * Creates a success result.
         *
         * Success data type.
         * Success data.
         * @param raw Raw response string (optional).
         * A [SdkResult.Success] instance.
         */
        fun <T> success(data: T, raw: String? = null): SdkResult<T> = Success(data, raw)

        /**
         * Creates a failure result.
         *
         * @param T Generic placeholder（Only used for type inference）/ Generic placeholder for type inference.
         * Error details.
         * @param raw Raw response（Optional）/ Raw response string, optional.
         * A [SdkResult.Failure] instance.
         */
        fun <T> failure(error: SdkError, raw: String? = null): SdkResult<T> = Failure(error, raw)
    }
}

/**
 * Exception thrown when unwrapping a failed [SdkResult].
 *
 * Associated [SdkError].
 * @property raw Raw response string（For debugging）/ Raw response string for debugging, optional.
 */
class SdkResultException(
    val error: SdkError,
    val raw: String? = null
) : Exception(error.getMessage())
