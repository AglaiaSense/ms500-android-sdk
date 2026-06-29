package com.ms500.usbcam.api

import com.ms500.usbcam.cdc.InputTensorCaptureResult
import com.ms500.usbcam.result.SdkResult

/** Image encoding format requested by `download_input_tensor`. */
enum class InputTensorImageFormat(
    /** Protocol request value sent as `params.format`. */
    val requestValue: String,
    /** Metadata value returned by firmware: 1 = JPEG, 2 = PNG. */
    val metadataValue: Int,
    /** File extension used when saving the captured bytes. */
    val extension: String,
    /** Human-readable label for UI/logs. */
    val displayName: String
) {
    /** JPEG/JPG format: lossy, smaller, faster. */
    JPG("jpg", 1, "jpg", "JPG"),

    /** PNG format: lossless, larger, slower. */
    PNG("png", 2, "png", "PNG");

    companion object {
        /** Parses a user/protocol string into an image format, defaulting to [JPG]. */
        fun fromRequestValue(value: String?): InputTensorImageFormat {
            return when (value?.trim()?.lowercase()) {
                "png" -> PNG
                "jpeg", "jpg" -> JPG
                else -> JPG
            }
        }

        /** Maps firmware metadata value into an image format, defaulting to [JPG]. */
        fun fromMetadataValue(value: Int): InputTensorImageFormat {
            return values().firstOrNull { it.metadataValue == value } ?: JPG
        }
    }
}

/**
 * API for capturing the current IMX500 input tensor as an image stream.
 *
 * Protocol flow:
 * 1. Android sends MSG_TYPE_TENSOR_EXPORT(0x0007):
 *    `{"cmd":"download_input_tensor","params":{"format":"jpg"}}` or `format:"png"`.
 * 2. ESP32 returns RESP_OK/RESP_ERROR.
 * 3. ESP32 sends metadata on MSG_TYPE_TENSOR_EXPORT(0x0007), where `format` is 1=JPEG or 2=PNG.
 * 4. ESP32 sends image chunks on MSG_TYPE_FILE_DATA(0x0005).
 *
 * JPEG quality is fixed by firmware; Android must not send a quality parameter. The returned
 * [InputTensorCaptureResult] contains the raw JPEG/PNG bytes and metadata needed by UI or automation code
 * to render, save, or further process the image. Inference must be enabled before calling this API,
 * otherwise the device may reject the command or no data chunk will arrive.
 */
interface InputTensorApi {

    /**
     * Captures one input tensor image frame using the `download_input_tensor` command.
     *
     * The method waits for ACK, metadata, and file-data chunks until the image is complete or [timeoutMs]
     * expires. [onStatus] is intended for user-visible progress text, while [onProgress] is intended for
     * byte progress bars or automation logs. The SDK does not decode the image here; callers can decode or
     * save the returned bytes according to [format] and the returned metadata.
     *
     * @param timeoutMs Overall timeout in milliseconds, defaults to 60_000.
     * @param format Requested image encoding format, defaults to [InputTensorImageFormat.JPG].
     * @param onStatus Status callback for ACK, metadata, first chunk, completion, and error states.
     * @param onProgress Progress callback receiving `(receivedBytes, totalBytes)`.
     * @return Result containing the captured input tensor image data and metadata.
     */
    suspend fun capture(
        timeoutMs: Long = 60_000L,
        format: InputTensorImageFormat = InputTensorImageFormat.JPG,
        onStatus: (String) -> Unit = {},
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): SdkResult<InputTensorCaptureResult>
}
