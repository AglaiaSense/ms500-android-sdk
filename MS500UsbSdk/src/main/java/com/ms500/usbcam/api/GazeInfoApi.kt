package com.ms500.usbcam.api

import com.ms500.usbcam.dto.InferenceResult
import kotlinx.coroutines.flow.Flow

interface GazeInfoApi {
    val results: Flow<InferenceResult>
}
