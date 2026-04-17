package com.ms500.usbcam.api.internal

import com.ms500.usbcam.api.GazeInfoApi
import com.ms500.usbcam.cdc.CdcProtocol
import com.ms500.usbcam.dto.GazePayload
import com.ms500.usbcam.dto.InferenceResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

internal class GazeInfoApiImpl(
    private val connection: CdcConnectApiImpl
) : GazeInfoApi {

    override val results: Flow<InferenceResult> = callbackFlow {
        connection.getSender()

        val job = launch {
            connection.messageEvents.collect { event ->
                if (event.msgType != CdcProtocol.MSG_TYPE_GAZE_DATA || event.isError) {
                    return@collect
                }

                val json = event.jsonText
                    ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
                    ?: return@collect

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

                runCatching { InferenceResult.fromJson(json) }
                    .onSuccess { trySend(it) }
            }
        }

        awaitClose { job.cancel() }
    }
}
