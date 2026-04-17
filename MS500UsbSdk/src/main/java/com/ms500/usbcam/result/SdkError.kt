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
 * SDK 错误分类
 * 
 * 提供清晰的错误分类，便于业务层做统一错误处理和重试策略
 */
sealed class SdkError {
    
    /**
     * 获取错误描述
     */
    abstract fun getMessage(): String
    
    /**
     * 设备未连接
     */
    object NotConnected : SdkError() {
        override fun getMessage(): String = "Device not connected"
    }
    
    /**
     * 连接中（操作过于频繁）
     */
    object Connecting : SdkError() {
        override fun getMessage(): String = "Device is connecting"
    }
    
    /**
     * 请求超时
     */
    data class Timeout(val operation: String? = null) : SdkError() {
        override fun getMessage(): String = 
            operation?.let { "Operation '$it' timeout" } ?: "Request timeout"
    }
    
    /**
     * 设备拒绝请求
     */
    data class DeviceRejected(
        val code: Int? = null,
        val errorMessage: String? = null
    ) : SdkError() {
        override fun getMessage(): String = 
            errorMessage ?: code?.let { "Device rejected with code $it" } ?: "Device rejected request"
    }
    
    /**
     * 响应解析失败
     */
    data class InvalidResponse(
        val raw: String? = null,
        val reason: String? = null
    ) : SdkError() {
        override fun getMessage(): String = 
            reason ?: "Invalid response format"
    }
    
    /**
     * 参数无效
     */
    data class InvalidArgument(
        val errorMessage: String
    ) : SdkError() {
        override fun getMessage(): String = errorMessage
    }
    
    /**
     * 不支持的操作
     */
    data class UnsupportedOperation(
        val errorMessage: String
    ) : SdkError() {
        override fun getMessage(): String = errorMessage
    }
    
    /**
     * 升级相关错误
     */
    sealed class Upgrade : SdkError() {
        /**
         * 包验证失败
         */
        data class PackageInvalid(val reason: String) : Upgrade() {
            override fun getMessage(): String = "Package invalid: $reason"
        }
        
        /**
         * 版本验证失败
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
         * Sensor ID 不匹配
         */
        data class SensorIdMismatch(
            val expected: String,
            val actual: String
        ) : Upgrade() {
            override fun getMessage(): String = 
                "Sensor ID mismatch: expected=$expected, actual=$actual"
        }
        
        /**
         * 传输失败
         */
        data class TransferFailed(val reason: String) : Upgrade() {
            override fun getMessage(): String = "Transfer failed: $reason"
        }
        
        /**
         * 用户取消
         */
        object Cancelled : Upgrade() {
            override fun getMessage(): String = "Upgrade cancelled by user"
        }
    }
    
    /**
     * 统一升级错误
     */
    data class UpgradeError(
        val errorMessage: String
    ) : SdkError() {
        override fun getMessage(): String = "Upgrade error: $errorMessage"
    }
    
    /**
     * 内部错误
     */
    data class InternalError(
        val cause: Throwable? = null
    ) : SdkError() {
        override fun getMessage(): String = 
            cause?.message ?: "Internal error"
    }
    
    /**
     * 通信错误
     */
    data class CommunicationError(
        val reason: String
    ) : SdkError() {
        override fun getMessage(): String = "Communication error: $reason"
    }
    
    /**
     * 未知错误
     */
    data class Unknown(
        val errorMessage: String? = null
    ) : SdkError() {
        override fun getMessage(): String = errorMessage ?: "Unknown error"
    }
}

/**
 * 将异常转换为 SdkError
 */
fun Throwable.toSdkError(): SdkError {
    return when (this) {
        is SdkErrorException -> this.error
        else -> SdkError.InternalError(this)
    }
}

/**
 * 带 SdkError 的异常
 */
class SdkErrorException(
    val error: SdkError,
    cause: Throwable? = null
) : Exception(error.getMessage(), cause)
