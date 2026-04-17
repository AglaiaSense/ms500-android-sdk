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
 * SDK 统一结果模型。
 *
 * 用类型安全的成功/失败结果替代历史上基于 JSONObject 的返回封装。
 *
 * @param T 成功时的数据类型
 */
sealed class SdkResult<out T> {

    /**
     * 成功结果
     */
    data class Success<T>(
        val data: T,
        /** 原始响应（调试用） */
        val raw: String? = null
    ) : SdkResult<T>()

    /**
     * 失败结果
     */
    data class Failure(
        val error: SdkError,
        /** 原始响应（调试用） */
        val raw: String? = null
    ) : SdkResult<Nothing>()

    /**
     * 是否成功
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * 是否失败
     */
    val isFailure: Boolean get() = this is Failure

    /**
     * 获取成功数据，失败时返回 null
     */
    fun getOrNull(): T? = (this as? Success)?.data

    /**
     * 获取错误，成功时返回 null
     */
    fun errorOrNull(): SdkError? = (this as? Failure)?.error

    /**
     * 获取成功数据，失败时抛出异常
     */
    fun getOrThrow(): T {
        return when (this) {
            is Success -> data
            is Failure -> throw SdkResultException(error, raw)
        }
    }

    /**
     * 成功时执行操作
     */
    inline fun onSuccess(action: (T) -> Unit): SdkResult<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * 失败时执行操作
     */
    inline fun onFailure(action: (SdkError) -> Unit): SdkResult<T> {
        if (this is Failure) action(error)
        return this
    }

    /**
     * 映射成功数据
     */
    inline fun <R> map(transform: (T) -> R): SdkResult<R> {
        return when (this) {
            is Success -> Success(transform(data), raw)
            is Failure -> Failure(error, raw)
        }
    }

    /**
     * 获取成功数据或默认值
     */
    fun getOrDefault(default: @UnsafeVariance T): T = getOrNull() ?: default

    /**
     * 获取成功数据或根据错误计算默认值
     */
    inline fun getOrElse(onFailure: (SdkError) -> @UnsafeVariance T): T {
        return when (this) {
            is Success -> data
            is Failure -> onFailure(error)
        }
    }

    companion object {
        /**
         * 创建成功结果
         */
        fun <T> success(data: T, raw: String? = null): SdkResult<T> = Success(data, raw)

        /**
         * 创建失败结果
         */
        fun <T> failure(error: SdkError, raw: String? = null): SdkResult<T> = Failure(error, raw)
    }
}

/**
 * SdkResult 异常
 */
class SdkResultException(
    val error: SdkError,
    val raw: String? = null
) : Exception(error.getMessage())
