/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.usbcam.api.internal

import com.ms500.usbcam.api.GazeInfoApi
import com.ms500.usbcam.dto.GazeInfoSnapshot
import com.ms500.usbcam.dto.GazePayload
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Internal implementation of gaze info API.
 * Exposes both complete parsed payloads and lightweight business snapshots.
 */
internal class GazeInfoApiImpl(
    /** CDC connection implementation, used to subscribe to message events. */
    private val connection: CdcConnectApiImpl
) : GazeInfoApi {

    /** Complete parsed CDC gaze payload stream. */
    override val payloads: Flow<GazePayload> = callbackFlow {
        connection.start()
        connection.getSender()

        val job = launch {
            connection.messageEvents.collect { event ->
                if (!event.isInferenceResult || event.isError) {
                    return@collect
                }

                val json = event.jsonText
                    ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
                    ?: return@collect

                runCatching { GazePayload.fromJson(json) }
                    .onSuccess { trySend(it) }
            }
        }

        awaitClose { job.cancel() }
    }

    /** Business snapshot stream extracted from each gaze payload. */
    override val snapshots: Flow<GazeInfoSnapshot> = payloads.transform { payload ->
        GazeInfoSnapshot.fromPayloadDetections(payload).forEach { snapshot ->
            emit(snapshot)
        }
    }
}
