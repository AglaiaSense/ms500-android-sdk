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

import com.ms500.usbcam.api.WtaInfoApi
import com.ms500.usbcam.dto.WtaInfoSnapshot
import com.ms500.usbcam.dto.WtaPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform

/**
 * Internal implementation of the WTA detection result API.
 *
 * Parses the shared inference-result stream with WTA semantics. Parse failures are dropped rather
 * than raised, for the same reason [InferenceResultSource] drops malformed JSON: one bad report
 * must not tear down the caller's collector.
 */
internal class WtaInfoApiImpl(
    private val source: InferenceResultSource
) : WtaInfoApi {

    override val payloads: Flow<WtaPayload> = source.reports
        .onStart { source.ensureStarted() }
        .mapNotNull { json -> runCatching { WtaPayload.fromJson(json) }.getOrNull() }

    override val snapshots: Flow<WtaInfoSnapshot> = payloads.transform { payload ->
        WtaInfoSnapshot.fromPayload(payload).forEach { snapshot -> emit(snapshot) }
    }
}
