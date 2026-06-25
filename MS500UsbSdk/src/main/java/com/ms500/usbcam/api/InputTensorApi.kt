package com.ms500.usbcam.api

import com.ms500.usbcam.cdc.InputTensorCaptureResult
import com.ms500.usbcam.result.SdkResult

/**
 * API for capturing the current IMX500 input tensor as a JPEG stream.
 *
 * Protocol flow:
 * 1. Android sends MSG_TYPE_TENSOR_EXPORT(0x0007): `{"cmd":"download_input_tensor"}`.
 * 2. ESP32 returns RESP_OK/RESP_ERROR.
 * 3. ESP32 sends metadata on MSG_TYPE_TENSOR_EXPORT(0x0007).
 * 4. ESP32 sends JPEG chunks on MSG_TYPE_FILE_DATA(0x0005).
 *
 * The JPEG quality is fixed by firmware; Android must not send a quality parameter. The returned
 * [InputTensorCaptureResult] contains the raw JPEG bytes and metadata needed by UI or automation code to
 * render, save, or further process the image. Inference must be enabled before calling this API,
 * otherwise the device may reject the command or no data chunk will arrive.
 */
interface InputTensorApi {

    /**
     * Captures one input tensor JPEG frame using the `download_input_tensor` command.
     *
     * The method waits for ACK, metadata, and file-data chunks until the image is complete or [timeoutMs]
     * expires. [onStatus] is intended for user-visible progress text, while [onProgress] is intended for
     * byte progress bars or automation logs. The SDK does not decode the JPEG here; callers can decode or
     * save the returned bytes according to their own workflow.
     *
     * @param timeoutMs Overall timeout in milliseconds, defaults to 60_000.
     * @param onStatus Status callback for ACK, metadata, first chunk, completion, and error states.
     * @param onProgress Progress callback receiving `(receivedBytes, totalBytes)`.
     * @return Result containing the captured input tensor JPEG data and metadata.
     */
    suspend fun capture(
        timeoutMs: Long = 60_000L,
        onStatus: (String) -> Unit = {},
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): SdkResult<InputTensorCaptureResult>
}
