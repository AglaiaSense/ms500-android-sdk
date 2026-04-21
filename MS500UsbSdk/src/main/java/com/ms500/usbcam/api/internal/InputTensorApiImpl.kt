package com.ms500.usbcam.api.internal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.ms500.usbcam.api.DeviceParamApi
import com.ms500.usbcam.api.InputTensorApi
import com.ms500.usbcam.cdc.InputTensorCaptureResult
import com.ms500.usbcam.cdc.InputTensorSession
import com.ms500.usbcam.result.SdkError
import com.ms500.usbcam.result.SdkResult
import com.ms500.usbcam.result.toSdkError
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * Internal implementation of input tensor capture API.
 * Triggers device-side input tensor JPEG capture via CDC commands, receives binary data,
 * and provides helpers to decode into Bitmap.
 */
internal class InputTensorApiImpl(
    /** CDC connection implementation, used to create InputTensorSession. */
    private val connection: CdcConnectApiImpl,
    /** Device parameter API, used to send underlying CDC commands. */
    private val deviceParam: DeviceParamApi
) : InputTensorApi {

    /**
     * Captures input tensor JPEG data from the device.
     *
     * @param resetCounter Whether to reset tensor counter first.
     * @param jpegQuality JPEG quality, range 1-100.
     * @param timeoutMs Timeout for receiving data in milliseconds.
     * @param onStatus Status callback for current phase feedback.
     * @param onProgress Progress callback for receive progress feedback.
     */
    override suspend fun capture(
        resetCounter: Boolean,
        jpegQuality: Int,
        timeoutMs: Long,
        onStatus: (String) -> Unit,
        onProgress: (Int, Int) -> Unit
    ): SdkResult<InputTensorCaptureResult> {
        if (!connection.isConnected) {
            return SdkResult.failure(SdkError.NotConnected)
        }

        val session = InputTensorSession(
            connection = connection,
            onStatus = onStatus,
            onProgress = onProgress
        )
        session.start()

        return try {
            if (resetCounter) {
                onStatus("reset")
                when (val result = executeCommand("reset_input_tensor_counter", timeoutMs = 5_000L)) {
                    is SdkResult.Failure -> {
                        session.fail("reset_input_tensor_counter failed: ${result.error.getMessage()}")
                        return SdkResult.failure(result.error, result.raw)
                    }
                    is SdkResult.Success -> Unit
                }
            }

            onStatus("request")
            session.markRequestStarted()
            when (
                val result = executeCommand(
                    "get_input_tensor_jpg_via_cdc",
                    payload = JSONObject().apply { put("quality", jpegQuality.coerceIn(1, 100)) },
                    timeoutMs = 5_000L
                )
            ) {
                is SdkResult.Failure -> {
                    session.fail("get_input_tensor_jpg_via_cdc failed: ${result.error.getMessage()}")
                    return SdkResult.failure(result.error, result.raw)
                }
                is SdkResult.Success -> Unit
            }

            onStatus("receive")
            SdkResult.success(session.awaitCapture(timeoutMs))
        } catch (t: Throwable) {
            session.fail(t.message ?: t.javaClass.simpleName)
            SdkResult.failure(t.toSdkError())
        } finally {
            session.cleanup()
        }
    }

    /**
     * Decodes the capture result into a Bitmap, optionally flipping horizontally.
     *
     * @param result Capture result.
     * @param flipHorizontal Whether to flip horizontally.
     * @param swapRb Whether to swap R/B channels (not yet implemented, reserved parameter).
     */
    override fun decodeToBitmap(
        result: InputTensorCaptureResult,
        flipHorizontal: Boolean,
        swapRb: Boolean
    ): Bitmap {
        val decoded = BitmapFactory.decodeByteArray(result.bytes, 0, result.bytes.size)
            ?: error("Failed to decode JPG bytes to bitmap")

        if (!flipHorizontal) return decoded

        val matrix = Matrix().apply { postScale(-1f, 1f) }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    /**
     * Internal helper to execute a command with timeout protection.
     */
    private suspend fun executeCommand(
        cmd: String,
        payload: JSONObject = JSONObject(),
        timeoutMs: Long
    ): SdkResult<JSONObject> {
        return try {
            withTimeout(timeoutMs) {
                deviceParam.baseCommand(cmd, payload)
            }
        } catch (_: TimeoutCancellationException) {
            SdkResult.failure(SdkError.Timeout(cmd))
        }
    }
}
