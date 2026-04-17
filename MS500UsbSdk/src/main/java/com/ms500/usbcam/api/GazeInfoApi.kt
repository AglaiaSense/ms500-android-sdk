package com.ms500.usbcam.api

import com.ms500.usbcam.dto.InferenceResult
import kotlinx.coroutines.flow.Flow

/**
 * 凝视（Gaze）信息 API。
 * 提供推理结果的数据流，用于获取眼部凝视等推理输出。
 *
 * Gaze info API for observing inference results such as eye gaze.
 */
interface GazeInfoApi {

    /**
     * 推理结果流。
     */
    val results: Flow<InferenceResult>
}
