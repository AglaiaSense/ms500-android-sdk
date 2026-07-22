package com.ms500.usbcam.api

import com.ms500.usbcam.dto.InferenceMode
import com.ms500.usbcam.dto.InferenceState
import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.dto.ResultSendModeConfig
import com.ms500.usbcam.result.SdkResult

/**
 * Inference parameter API for controlling AI inference execution and result upload mode.
 *
 * Protocol groups:
 * - Inference switch: `get_inference_state`, `set_inference_state`.
 * - Result send mode: `get_inference_send_mode`, `set_inference_send_mode`.
 *
 * The switch controls whether the device runs inference. The send mode controls how inference results
 * are uploaded to Android, for example real-time or batch reporting. These are independent settings;
 * changing one does not implicitly update the other.
 */
interface InferenceParamApi {

    /**
     * Gets current inference switch state.
     *
     * Sends `{"cmd":"get_inference_state"}` and parses the response into [InferenceState]. Use this
     * before enabling features that require device-side inference, such as gaze reports or input tensor
     * capture.
     *
     * @return Result containing current inference enabled state.
     */
    suspend fun getState(): SdkResult<InferenceState>

    /**
     * Sets inference on/off state.
     *
     * Sends `set_inference_state` with the protocol field representing whether inference is enabled.
     * This is the generic setter used by [startInference] and [stopInference].
     *
     * Range: true/false; default: true.
     *
     * @param enabled Whether to enable inference.
     * @return Operation result returned by the device.
     */
    suspend fun setState(enabled: Boolean): SdkResult<ResultDto>

    /**
     * Starts inference.
     *
     * Convenience wrapper that sets inference enabled to true. Callers that also need to configure result
     * upload behavior should call [setInferenceSendMode] separately.
     *
     * @return Operation result returned by the device.
     */
    suspend fun startInference(): SdkResult<ResultDto>

    /**
     * Stops inference.
     *
     * Convenience wrapper that sets inference enabled to false. This affects device-side inference work;
     * it does not disconnect CDC or UVC preview.
     *
     * @return Operation result returned by the device.
     */
    suspend fun stopInference(): SdkResult<ResultDto>

    /**
     * Gets inference result send mode.
     *
     * Sends `{"cmd":"get_inference_send_mode"}` and parses the response into [ResultSendModeConfig].
     * Use this when the app needs to display whether results are uploaded in real-time or batch mode.
     *
     * @return Result containing current result send mode.
     */
    suspend fun getInferenceSendMode(): SdkResult<ResultSendModeConfig>

    /**
     * Sets inference result send mode from a DTO.
     *
     * Sends `set_inference_send_mode` with [config] converted to protocol JSON. This form is useful for
     * batch-style code that already works with DTO objects.
     *
     * Mode range: 0..2; default: 1 (REALTIME).
     *
     * @param config Send mode configuration.
     * @return Operation result returned by the device.
     */
    suspend fun setInferenceSendMode(config: ResultSendModeConfig): SdkResult<ResultDto>

    /**
     * Sets inference result send mode from an enum value.
     *
     * Sends `set_inference_send_mode` with the numeric value represented by [mode]. This is the preferred
     * call shape for UI code because it avoids raw integer mode values at the call site.
     *
     * Mode range: 0..2; default: 1 (REALTIME).
     *
     * @param mode Send mode enum value.
     * @return Operation result returned by the device.
     */
    suspend fun setInferenceSendMode(mode: InferenceMode): SdkResult<ResultDto>

    /**
     * Sets batch result send mode.
     *
     * Convenience wrapper for [setInferenceSendMode] using the batch enum value. Batch mode is useful for
     * collecting summarized reports instead of receiving every result immediately.
     *
     * @return Operation result returned by the device.
     */
    suspend fun setBatchMode(): SdkResult<ResultDto>

    /**
     * Sets real-time result send mode.
     *
     * Convenience wrapper for [setInferenceSendMode] using the real-time enum value. Real-time mode is the
     * default path for UI screens that show current detection or gaze state.
     *
     * @return Operation result returned by the device.
     */
    suspend fun setRealtimeMode(): SdkResult<ResultDto>
}
