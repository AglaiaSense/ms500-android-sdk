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

/**
 * WTA model facade, reached through `sdk.wta`.
 *
 * Groups the APIs specific to the WTA model: head-based detection results, WTA parameter ranges and
 * WTA-only fields, plus the watching and person-counting regions. Shared capabilities stay at the
 * SDK root.
 *
 * **The facade selects the semantics, not the device.** See [GazeApi] for the full rationale. In
 * short: calling this facade against GAZE firmware is allowed; the region commands come back as
 * `SdkError.DeviceRejected` with the device's `Unknown param_get cmd` message in about 90ms, and
 * `postprocessParam` reads succeed but contain none of the WTA-only fields.
 *
 * Java callers should prefer the flat accessors on the SDK root
 * (`sdk.wtaInfoApi()`, `sdk.wtaPostprocessParamApi()`, `sdk.wtaRegionParamApi()`).
 */
interface WtaApi {

    /**
     * WTA detection result streams.
     *
     * Boxes are in **UVC frame coordinates** (1920x1080 by default), so an overlay maps them
     * straight from the actual UVC frame size - the GAZE 512x384 transform must not be applied.
     */
    val info: WtaInfoApi

    /**
     * WTA post-process parameters.
     *
     * Covers the mapped legacy fields at WTA ranges plus the 18 WTA-only fields. Legacy fields that
     * WTA algorithms ignore are deliberately absent; reach them through `sdk.gaze.postprocessParam`
     * if the raw wire behaviour is needed.
     */
    val postprocessParam: WtaPostprocessParamApi

    /**
     * WTA watching and person-counting regions.
     *
     * WTA firmware only - these three commands do not exist on GAZE firmware.
     */
    val regionParam: WtaRegionParamApi
}
