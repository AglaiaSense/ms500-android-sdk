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
 * SDK 错误分类 / SDK error taxonomy.
 *
 * 提供清晰的错误分类，便于业务层做统一错误处理和重试策略。
 * Provides clear error categories to help the business layer perform unified error handling and retry strategies.
 */
sealed class SdkError {

    /**
     * 获取错误描述 / Gets the error description.
     *
     * @return 可读的错误信息 / Human-readable error message.
     */
    abstract fun getMessage(): String

    /**
     * 设备未连接 / Device not connected.
     *
     * 通常在 USB 设备未插入或权限未授予时出现。
     */
    object NotConnected : SdkError() {
        override fun getMessage(): String = "Device not connected"
    }

    /**
     * 连接中（操作过于频繁）/ Device is connecting.
     *
     * 表示当前正在建立连接，重复调用操作会返回此错误。
     */
    object Connecting : SdkError() {
        override fun getMessage(): String = "Device is connecting"
    }

    /**
     * 请求超时 / Request timeout.
     *
     * @property operation 发生超时的操作名称（可选）/ Name of the timed-out operation, optional.
     */
    data class Timeout(val operation: String? = null) : SdkError() {
        override fun getMessage(): String =
            operation?.let { "Operation '$it' timeout" } ?: "Request timeout"
    }

    /**
     * 设备拒绝请求 / Device rejected the request.
     *
     * @property code 设备返回的错误码（可选）/ Error code returned by the device, optional.
     * @property errorMessage 设备返回的错误信息（可选）/ Error message returned by the device, optional.
     */
    data class DeviceRejected(
        val code: Int? = null,
        val errorMessage: String? = null
    ) : SdkError() {
        override fun getMessage(): String =
            errorMessage ?: code?.let { "Device rejected with code $it" } ?: "Device rejected request"
    }

    /**
     * 响应解析失败 / Invalid response format.
     *
     * @property raw 原始响应字符串（可选）/ Raw response string for debugging, optional.
     * @property reason 解析失败原因（可选）/ Reason why the response is invalid, optional.
     */
    data class InvalidResponse(
        val raw: String? = null,
        val reason: String? = null
    ) : SdkError() {
        override fun getMessage(): String =
            reason ?: "Invalid response format"
    }

    /**
     * 参数无效 / Invalid argument.
     *
     * @property errorMessage 具体的参数错误说明 / Detailed parameter error description.
     */
    data class InvalidArgument(
        val errorMessage: String
    ) : SdkError() {
        override fun getMessage(): String = errorMessage
    }

    /**
     * 不支持的操作 / Unsupported operation.
     *
     * @property errorMessage 不支持的原因说明 / Explanation of why the operation is unsupported.
     */
    data class UnsupportedOperation(
        val errorMessage: String
    ) : SdkError() {
        override fun getMessage(): String = errorMessage
    }

    /**
     * 升级相关错误 / Upgrade-related errors.
     *
     * 包含固件/软件升级过程中可能出现的各类子错误。
     */
    sealed class Upgrade : SdkError() {

        /**
         * 升级包验证失败 / Upgrade package validation failed.
         *
         * @property reason 验证失败原因 / Reason why the package is invalid.
         */
        data class PackageInvalid(val reason: String) : Upgrade() {
            override fun getMessage(): String = "Package invalid: $reason"
        }

        /**
         * 版本验证失败 / Version mismatch.
         *
         * @property current 当前版本 / Current version.
         * @property target 目标版本 / Target version.
         * @property reason 不匹配原因 / Reason for the mismatch.
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
         * Sensor ID 不匹配 / Sensor ID mismatch.
         *
         * @property expected 预期的 Sensor ID / Expected Sensor ID.
         * @property actual 实际的 Sensor ID / Actual Sensor ID.
         */
        data class SensorIdMismatch(
            val expected: String,
            val actual: String
        ) : Upgrade() {
            override fun getMessage(): String =
                "Sensor ID mismatch: expected=$expected, actual=$actual"
        }

        /**
         * 升级传输失败 / Upgrade transfer failed.
         *
         * @property reason 传输失败原因 / Reason why the transfer failed.
         */
        data class TransferFailed(val reason: String) : Upgrade() {
            override fun getMessage(): String = "Transfer failed: $reason"
        }

        /**
         * 用户取消升级 / Upgrade cancelled by user.
         */
        object Cancelled : Upgrade() {
            override fun getMessage(): String = "Upgrade cancelled by user"
        }
    }

    /**
     * 统一升级错误（通用）/ Generic upgrade error.
     *
     * @property errorMessage 升级错误描述 / Upgrade error description.
     */
    data class UpgradeError(
        val errorMessage: String
    ) : SdkError() {
        override fun getMessage(): String = "Upgrade error: $errorMessage"
    }

    /**
     * 内部错误 / Internal error.
     *
     * @property cause 底层异常（可选）/ Underlying throwable, optional.
     */
    data class InternalError(
        val cause: Throwable? = null
    ) : SdkError() {
        override fun getMessage(): String =
            cause?.message ?: "Internal error"
    }

    /**
     * 通信错误 / Communication error.
     *
     * @property reason 通信失败原因 / Reason for the communication failure.
     */
    data class CommunicationError(
        val reason: String
    ) : SdkError() {
        override fun getMessage(): String = "Communication error: $reason"
    }

    /**
     * 未知错误 / Unknown error.
     *
     * @property errorMessage 错误描述（可选）/ Error description, optional.
     */
    data class Unknown(
        val errorMessage: String? = null
    ) : SdkError() {
        override fun getMessage(): String = errorMessage ?: "Unknown error"
    }
}

/**
 * 将异常转换为 [SdkError] / Converts a [Throwable] to an [SdkError].
 *
 * @return 对应的 [SdkError] 实例 / Corresponding [SdkError] instance.
 */
fun Throwable.toSdkError(): SdkError {
    return when (this) {
        is SdkErrorException -> this.error
        else -> SdkError.InternalError(this)
    }
}

/**
 * 携带 [SdkError] 的异常 / Exception that carries an [SdkError].
 *
 * @property error 关联的 SDK 错误 / Associated SDK error.
 * @param cause 原始异常（可选）/ Original throwable, optional.
 */
class SdkErrorException(
    val error: SdkError,
    cause: Throwable? = null
) : Exception(error.getMessage(), cause)
