package com.ms500.usbcam.api.internal

import com.ms500.usbcam.api.GazeInfoApi
import com.ms500.usbcam.dto.GazePayload
import com.ms500.usbcam.dto.InferenceResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Internal implementation of gaze info API.
 * Listens to CDC message events, filters gaze data messages, parses them into InferenceResult,
 * and exposes them as a Flow.
 */
internal class GazeInfoApiImpl(
    /** CDC connection implementation, used to subscribe to message events. */
    private val connection: CdcConnectApiImpl
) : GazeInfoApi {

    /**
     * Flow of gaze inference results.
     */
    override val results: Flow<InferenceResult> = callbackFlow {
        // Trigger sender initialization to ensure message event channel is established
        connection.getSender()

        /**
         * Coroutine that collects CDC message events.
         */
        val job = launch {
            connection.messageEvents.collect { event ->
                // Filter out non-gaze data or error messages
                if (!event.isGazeData || event.isError) {
                    return@collect
                }

                // Parse raw JSON text into JSONObject
                val json = event.jsonText
                    ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
                    ?: return@collect

                // Try parsing as GazePayload first, extract results from detections
                val payload = runCatching { GazePayload.fromJson(json) }.getOrNull()
                val detections = payload?.detections.orEmpty()
                if (detections.isNotEmpty()) {
                    detections.forEach { detection ->
                        val result = detection.raw?.let { raw -> runCatching { InferenceResult.fromJson(raw) }.getOrNull() }
                        if (result != null) {
                            trySend(result)
                        }
                    }
                    return@collect
                }

                // Fallback: parse directly as InferenceResult
                runCatching { InferenceResult.fromJson(json) }
                    .onSuccess { trySend(it) }
            }
        }

        // Cancel collection coroutine when flow is closed
        awaitClose { job.cancel() }
    }
}
