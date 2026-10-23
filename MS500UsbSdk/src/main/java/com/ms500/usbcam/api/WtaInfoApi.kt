/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.usbcam.api

import com.ms500.usbcam.dto.WtaInfoSnapshot
import com.ms500.usbcam.dto.WtaPayload
import kotlinx.coroutines.flow.Flow

/**
 * WTA detection result API.
 *
 * Parses `MSG_TYPE_INFERENCE_RESULT (0x1000)` reports using **WTA** semantics: head-based tracking,
 * UVC frame coordinates, region indices, and person counting.
 *
 * Which parser runs is decided by the facade the caller subscribes to, not by the model the device
 * reports. Subscribing to this API while a GAZE device is attached parses GAZE JSON with the WTA
 * parser: shared fields come through, WTA-only fields are simply absent. Callers learn the model
 * from `deviceParam.getInfo()` and pick the matching facade.
 *
 * `0x1000` alone does not identify the model - both firmwares use it.
 */
interface WtaInfoApi {

    /**
     * Complete parsed WTA payload stream, mirroring each device JSON message.
     *
     * Preserves the frame/summary distinction and keeps placeholder fields such as the fixed
     * `is_back2camera` and all-zero body/face boxes. Use this when raw protocol fidelity matters.
     *
     * An emission with an empty `detections` list is an empty frame, which the device only sends
     * while `inference_result_filter_enable` is false. It means previously drawn boxes should be
     * cleared, not that nothing happened.
     */
    val payloads: Flow<WtaPayload>

    /**
     * Business snapshot stream, one emission per detection.
     *
     * Derived from [payloads] with the placeholders removed: no fabricated back-to-camera verdict,
     * no gender confidence (that wire slot carries a person-class score), and body/face omitted
     * when the device sent all-zero boxes. Head-only targets are preserved.
     *
     * Empty frames produce no emission, so a consumer that only observes this stream cannot tell
     * "no detections" from "no data"; observe [payloads] when that distinction matters.
     */
    val snapshots: Flow<WtaInfoSnapshot>
}
