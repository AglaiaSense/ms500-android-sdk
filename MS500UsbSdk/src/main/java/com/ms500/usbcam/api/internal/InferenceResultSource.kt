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

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import org.json.JSONObject

/**
 * Shared source of parsed inference-result JSON.
 *
 * Both model facades observe `MSG_TYPE_INFERENCE_RESULT (0x1000)`. Routing them through one source
 * means a report is turned into a [JSONObject] once, no matter how many facades and how many
 * collectors are attached - parsing the same payload per subscriber would multiply work on the
 * hot path for no benefit.
 *
 * The underlying `messageEvents` flow already drops oldest on overflow
 * ([BufferOverflow.DROP_OLDEST]), which is the right policy here: detection reports are a realtime
 * stream where a stale frame is worth less than a fresh one, and a slow UI collector must never
 * stall the CDC read thread or delay command responses.
 *
 * Malformed JSON is dropped rather than raised. A corrupt report says nothing about the ones
 * around it, and surfacing it as an exception would tear down the caller's collector - the frame
 * protocol layer already handles resynchronisation.
 */
internal class InferenceResultSource(
    private val connection: CdcConnectApiImpl
) {

    /**
     * Parsed JSON of every inference report, in arrival order.
     *
     * Starts the CDC client and sender on first collection so a caller can subscribe before
     * connecting, which is the normal order for UI code that binds flows in `onCreate`.
     */
    val reports: Flow<JSONObject> = connection.messageEvents
        .filter { event -> event.isInferenceResult && !event.isError }
        .mapNotNull { event ->
            event.jsonText?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
        }

    /** Ensures the CDC lower layer is running before results can arrive. */
    fun ensureStarted() {
        connection.start()
        connection.getSender()
    }
}
