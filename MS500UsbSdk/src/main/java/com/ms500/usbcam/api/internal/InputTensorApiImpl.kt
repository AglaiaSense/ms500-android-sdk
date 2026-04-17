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
 * 输入张量（Input Tensor）捕获 API 内部实现类。
 *
 * 负责通过 CDC 命令触发设备端输入张量 JPEG 捕获，接收二进制数据，
 * 并提供解码为 Bitmap 的辅助方法。
 *
 * Internal implementation of input tensor capture API.
 * Triggers device-side input tensor JPEG capture via CDC commands, receives binary data,
 * and provides helpers to decode into Bitmap.
 */
internal class InputTensorApiImpl(
    /** CDC 连接实现，用于创建 InputTensorSession。 */
    private val connection: CdcConnectApiImpl,
    /** 设备参数 API，用于发送底层 CDC 命令。 */
    private val deviceParam: DeviceParamApi
) : InputTensorApi {

    /**
     * 捕获输入张量 JPEG 数据。
     * Captures input tensor JPEG data from the device.
     *
     * @param resetCounter 是否先重置张量计数器。
     * @param jpegQuality JPEG 质量，范围 1-100。
     * @param timeoutMs 接收数据的超时时间（毫秒）。
     * @param onStatus 状态回调，用于反馈当前阶段。
     * @param onProgress 进度回调，用于反馈接收进度。
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
     * 将捕获结果解码为 Bitmap，并可选进行水平翻转。
     * Decodes the capture result into a Bitmap, optionally flipping horizontally.
     *
     * @param result 捕获结果。
     * @param flipHorizontal 是否水平翻转。
     * @param swapRb 是否交换 R/B 通道（当前未实现，保留参数）。
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
     * 内部命令执行辅助方法，带超时保护。
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
