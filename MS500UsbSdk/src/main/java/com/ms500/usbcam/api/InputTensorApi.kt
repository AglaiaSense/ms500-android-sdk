package com.ms500.usbcam.api

import android.graphics.Bitmap
import com.ms500.usbcam.cdc.InputTensorCaptureResult
import com.ms500.usbcam.result.SdkResult

/**
 * Input Tensor 捕获与解码 API。
 * 用于从设备抓取一帧 input tensor 数据，并将其解码为 Bitmap。
 *
 * API for capturing and decoding input tensors to Bitmap.
 */
interface InputTensorApi {

    /**
     * 抓取一帧 input tensor。
     *
     * @param resetCounter 是否重置计数器，默认为 true
     * @param jpegQuality JPEG 质量（0-100），默认为 85
     * @param timeoutMs 超时时间（毫秒），默认为 60_000
     * @param onStatus 状态回调
     * @param onProgress 进度回调（当前进度, 总进度）
     * @return 包含 [InputTensorCaptureResult] 的结果
     */
    suspend fun capture(
        resetCounter: Boolean = true,
        jpegQuality: Int = 85,
        timeoutMs: Long = 60_000L,
        onStatus: (String) -> Unit = {},
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): SdkResult<InputTensorCaptureResult>

    /**
     * 将 [InputTensorCaptureResult] 解码为 Bitmap。
     *
     * @param result 捕获结果
     * @param flipHorizontal 是否水平翻转，默认为 false
     * @param swapRb 是否交换 R/B 通道，默认为 false
     * @return 解码后的 Bitmap
     */
    fun decodeToBitmap(
        result: InputTensorCaptureResult,
        flipHorizontal: Boolean = false,
        swapRb: Boolean = false
    ): Bitmap
}
