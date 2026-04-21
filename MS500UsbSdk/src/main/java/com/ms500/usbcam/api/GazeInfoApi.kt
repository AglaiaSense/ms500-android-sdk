package com.ms500.usbcam.api

import com.ms500.usbcam.dto.InferenceResult
import kotlinx.coroutines.flow.Flow

/**
 * Gaze info API for observing inference results such as eye gaze.
 */
interface GazeInfoApi {

    /**
     * Inference result flow.
     */
    val results: Flow<InferenceResult>
}
