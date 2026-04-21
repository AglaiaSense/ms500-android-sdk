package com.ms500.usbcam.api

import android.graphics.Bitmap
import com.ms500.usbcam.cdc.InputTensorCaptureResult
import com.ms500.usbcam.result.SdkResult

/**
 * API for capturing and decoding input tensors to Bitmap.
 */
interface InputTensorApi {

    /**
     * Captures one frame of input tensor.
     *
     * @param resetCounter Whether to reset counter, defaults to true
     * @param jpegQuality JPEG quality (0-100), defaults to 85
     * @param timeoutMs Timeout in milliseconds, defaults to 60_000
     * @param onStatus Status callback
     * @param onProgress Progress callback (current progress, total progress)
     * @return Result containing [InputTensorCaptureResult]
     */
    suspend fun capture(
        resetCounter: Boolean = true,
        jpegQuality: Int = 85,
        timeoutMs: Long = 60_000L,
        onStatus: (String) -> Unit = {},
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): SdkResult<InputTensorCaptureResult>

    /**
     * Decodes [InputTensorCaptureResult] to Bitmap.
     *
     * @param result Capture result
     * @param flipHorizontal Whether to flip horizontally, defaults to false
     * @param swapRb Whether to swap R/B channels, defaults to false
     * @return Decoded Bitmap
     */
    fun decodeToBitmap(
        result: InputTensorCaptureResult,
        flipHorizontal: Boolean = false,
        swapRb: Boolean = false
    ): Bitmap
}
