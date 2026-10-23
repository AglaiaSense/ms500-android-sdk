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

import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.dto.WtaCountingRegionItemDto
import com.ms500.usbcam.dto.WtaRegionParamDto
import com.ms500.usbcam.dto.WtaWatchingRegionItemDto
import com.ms500.usbcam.result.SdkResult

/**
 * WTA watching and person-counting region API.
 *
 * Protocol mapping (added by firmware protocol 4.3, WTA firmware only):
 * - Read: `get_wta_region_param`, `MSG_TYPE_PARAM_GET (0x0001)`.
 * - Write: `set_wta_region_param`, `MSG_TYPE_PARAM_SET (0x0002)`.
 * - Reset: `reset_wta_region_param`, `MSG_TYPE_PARAM_SET (0x0002)`.
 *
 * Coordinates are integer pixels in the **1920x1080 UVC frame**, the same space detection results
 * use. 1 to 3 regions of each type are supported.
 *
 * Sending any of these to GAZE firmware returns `SdkError.DeviceRejected` with the device's own
 * message (`Unknown param_get cmd`) within about 90ms - the SDK does not pre-empt that, so callers
 * see the device's actual answer.
 */
interface WtaRegionParamApi {

    /**
     * Gets the current watching and counting regions.
     *
     * Sends `{"cmd":"get_wta_region_param"}`. The response also carries `meta.ranges`, kept in
     * [WtaRegionParamDto.raw].
     *
     * @return Result containing the current regions.
     */
    suspend fun getParams(): SdkResult<WtaRegionParamDto>

    /**
     * Replaces the regions provided in [params].
     *
     * Each array present in [params] **replaces that region type wholesale**; omit an array to
     * leave that type untouched. The whole object is validated before anything is sent, so an
     * invalid field fails locally and nothing is written to the device.
     *
     * This rebuilds the postprocess library and **clears current tracking** - track IDs are
     * reassigned and counting state resets.
     *
     * @param params Regions to write; at least one array must be non-null.
     * @return Operation result, or `SdkError.InvalidArgument` when validation fails.
     */
    suspend fun setParams(params: WtaRegionParamDto): SdkResult<ResultDto>

    /**
     * Replaces the watching regions.
     *
     * Convenience wrapper over [setParams] for the common single-type write.
     *
     * @param regions 1 to 3 watching regions, each with a mandatory rectangle.
     * @return Operation result.
     */
    suspend fun setWatchingRegions(
        regions: List<WtaWatchingRegionItemDto>
    ): SdkResult<ResultDto>

    /**
     * Replaces the person-counting regions.
     *
     * Remember `time` is in **seconds** here, while the reported `person_counting_time` is in
     * milliseconds.
     *
     * @param regions 1 to 3 counting regions, each with a mandatory rectangle.
     * @return Operation result.
     */
    suspend fun setCountingRegions(
        regions: List<WtaCountingRegionItemDto>
    ): SdkResult<ResultDto>

    /**
     * Restores both region types to firmware defaults.
     *
     * Sends `{"cmd":"reset_wta_region_param"}`, which restores one full-frame region of each type.
     * Region 0's angles and d1/d2 are shared with `postprocessParam`, so this also resets those.
     * Note the reverse is not true: `postprocessParam.resetPostprocess()` does not reset region
     * rectangles.
     *
     * @return Operation result returned by the device.
     */
    suspend fun resetRegions(): SdkResult<ResultDto>
}
