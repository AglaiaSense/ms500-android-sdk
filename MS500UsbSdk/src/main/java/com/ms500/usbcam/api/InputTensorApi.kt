package com.ms500.usbcam.api

import android.graphics.Bitmap
import com.ms500.usbcam.cdc.InputTensorCaptureResult
import com.ms500.usbcam.result.SdkResult

interface InputTensorApi {
    /**
     * 抓取一帧 input tensor。
     */
    suspend fun capture(
        resetCounter: Boolean = true,
        jpegQuality: Int = 85,
        timeoutMs: Long = 60_000L,
        onStatus: (String) -> Unit = {},
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): SdkResult<InputTensorCaptureResult>

    fun decodeToBitmap(
        result: InputTensorCaptureResult,
        flipHorizontal: Boolean = false,
        swapRb: Boolean = false
    ): Bitmap
}
