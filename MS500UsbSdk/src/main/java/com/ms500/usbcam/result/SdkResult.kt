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
 * SDK 统一结果模型 / SDK unified result model.
 *
 * 用类型安全的成功/失败结果替代历史上基于 JSONObject 的返回封装。
 * Replaces the legacy JSONObject-based return wrapper with a type-safe success/failure result.
 *
 * @param T 成功时的数据类型 / Data type on success.
 */
sealed class SdkResult<out T> {

    /**
     * 成功结果 / Success result.
     *
     * @property data 成功时返回的数据 / Payload data on success.
     * @property raw 原始响应字符串（调试用）/ Raw response string for debugging, optional.
     */
    data class Success<T>(
        val data: T,
        val raw: String? = null
    ) : SdkResult<T>()

    /**
     * 失败结果 / Failure result.
     *
     * @property error 错误信息 / Error details.
     * @property raw 原始响应字符串（调试用）/ Raw response string for debugging, optional.
     */
    data class Failure(
        val error: SdkError,
        val raw: String? = null
    ) : SdkResult<Nothing>()

    /**
     * 是否成功 / Returns `true` if this result is [Success].
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * 是否失败 / Returns `true` if this result is [Failure].
     */
    val isFailure: Boolean get() = this is Failure

    /**
     * 获取成功数据，失败时返回 `null` / Returns the success data or `null` if failed.
     *
     * @return 成功数据或 `null` / Success data or `null`.
     */
    fun getOrNull(): T? = (this as? Success)?.data

    /**
     * 获取错误，成功时返回 `null` / Returns the error or `null` if successful.
     *
     * @return [SdkError] 或 `null` / [SdkError] or `null`.
     */
    fun errorOrNull(): SdkError? = (this as? Failure)?.error

    /**
     * 获取成功数据，失败时抛出 [SdkResultException] / Returns the success data or throws [SdkResultException] if failed.
     *
     * @return 成功数据 / Success data.
     * @throws SdkResultException 当结果为 [Failure] 时抛出 / Thrown when this result is [Failure].
     */
    fun getOrThrow(): T {
        return when (this) {
            is Success -> data
            is Failure -> throw SdkResultException(error, raw)
        }
    }

    /**
     * 成功时执行操作 / Performs the given [action] if this result is [Success].
     *
     * @param action 成功数据消费回调 / Callback that consumes the success data.
     * @return 当前 [SdkResult] 实例 / The current [SdkResult] instance.
     */
    fun onSuccess(action: (T) -> Unit): SdkResult<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * 失败时执行操作 / Performs the given [action] if this result is [Failure].
     *
     * @param action 错误信息消费回调 / Callback that consumes the error.
     * @return 当前 [SdkResult] 实例 / The current [SdkResult] instance.
     */
    fun onFailure(action: (SdkError) -> Unit): SdkResult<T> {
        if (this is Failure) action(error)
        return this
    }

    /**
     * 映射成功数据 / Maps the success data to another type.
     *
     * @param transform 数据转换函数 / Function to transform the success data.
     * @return 转换后的 [SdkResult] / Transformed [SdkResult].
     */
    fun <R> map(transform: (T) -> R): SdkResult<R> {
        return when (this) {
            is Success -> Success(transform(data), raw)
            is Failure -> Failure(error, raw)
        }
    }

    /**
     * 获取成功数据或默认值 / Returns the success data or the provided [default] value.
     *
     * @param default 默认值 / Default value to return on failure.
     * @return 成功数据或默认值 / Success data or [default].
     */
    fun getOrDefault(default: @UnsafeVariance T): T = getOrNull() ?: default

    /**
     * 获取成功数据或根据错误计算默认值 / Returns the success data or computes a default from the error.
     *
     * @param onFailure 根据错误生成默认值的回调 / Callback that produces a default value from the error.
     * @return 成功数据或计算后的默认值 / Success data or computed default.
     */
    fun getOrElse(onFailure: (SdkError) -> @UnsafeVariance T): T {
        return when (this) {
            is Success -> data
            is Failure -> onFailure(error)
        }
    }

    /**
     * 伴生对象 / Companion object for factory methods.
     */
    companion object {

        /**
         * 创建成功结果 / Creates a success result.
         *
         * @param T 成功数据类型 / Success data type.
         * @param data 成功数据 / Success data.
         * @param raw 原始响应（可选）/ Raw response string, optional.
         * @return [SdkResult.Success] 实例 / A [SdkResult.Success] instance.
         */
        fun <T> success(data: T, raw: String? = null): SdkResult<T> = Success(data, raw)

        /**
         * 创建失败结果 / Creates a failure result.
         *
         * @param T 泛型占位（仅用于类型推断）/ Generic placeholder for type inference.
         * @param error 错误信息 / Error details.
         * @param raw 原始响应（可选）/ Raw response string, optional.
         * @return [SdkResult.Failure] 实例 / A [SdkResult.Failure] instance.
         */
        fun <T> failure(error: SdkError, raw: String? = null): SdkResult<T> = Failure(error, raw)
    }
}

/**
 * [SdkResult] 专用异常 / Exception thrown when unwrapping a failed [SdkResult].
 *
 * @property error 关联的 [SdkError] / Associated [SdkError].
 * @property raw 原始响应字符串（调试用）/ Raw response string for debugging, optional.
 */
class SdkResultException(
    val error: SdkError,
    val raw: String? = null
) : Exception(error.getMessage())
