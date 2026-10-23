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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform

/**
 * Internal implementation of the GAZE detection result API.
 *
 * Reads from the shared [InferenceResultSource] rather than subscribing to `messageEvents`
 * directly, so a device report is turned into JSON once regardless of how many facades observe it.
 *
 * Parse failures are dropped rather than raised: a corrupt report says nothing about the reports
 * around it, and letting it propagate would tear down the caller's collector.
 */
internal class GazeInfoApiImpl(
    private val source: InferenceResultSource
) : GazeInfoApi {

    override val payloads: Flow<GazePayload> = source.reports
        .onStart { source.ensureStarted() }
        .mapNotNull { json -> runCatching { GazePayload.fromJson(json) }.getOrNull() }

    override val snapshots: Flow<GazeInfoSnapshot> = payloads.transform { payload ->
        GazeInfoSnapshot.fromPayloadDetections(payload).forEach { snapshot -> emit(snapshot) }
    }
}
