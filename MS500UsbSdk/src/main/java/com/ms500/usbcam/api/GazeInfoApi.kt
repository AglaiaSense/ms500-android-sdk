package com.ms500.usbcam.api

import com.ms500.usbcam.dto.GazeInfoSnapshot
import com.ms500.usbcam.dto.GazePayload
import kotlinx.coroutines.flow.Flow

/**
 * Gaze information API for observing parsed inference result reports.
 *
 * The implementation listens to CDC gaze/inference report messages and parses both current-frame data
 * and track-timeout summary data. Consumers can choose the complete protocol-level [payloads] stream,
 * the UI/business-oriented [results] stream, or the legacy-compatible [snapshots] stream.
 */
interface GazeInfoApi {

    /**
     * Complete parsed CDC gaze payload stream.
     *
     * Each emission preserves the protocol distinction between frame messages and summary messages.
     * Use this stream when the caller needs access to raw upload status, data status, track IDs, or all
     * parsed detections exactly as reported by the device.
     */
    val payloads: Flow<GazePayload>

    /**
     * Business result stream extracted from each gaze payload.
     *
     * This stream is intended for app screens that only need display-ready gaze results. It is derived
     * from [payloads], so callers needing protocol details should observe [payloads] directly.
     */
    val results: Flow<GazeInfoSnapshot>
        get() = snapshots

    /**
     * Legacy-compatible business snapshot stream.
     *
     * This is the same data as [results]. Keep it for existing app code that already uses snapshots.
     */
    val snapshots: Flow<GazeInfoSnapshot>
}
