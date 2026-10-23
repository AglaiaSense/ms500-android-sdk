/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.usbcam.dto

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WTA region validation and serialization.
 *
 * Validation runs before anything is sent, so these tests pin down which requests never reach the
 * device. A partial write would leave regions in a state the caller never asked for, and because
 * changing regions clears all tracking, recovering from one is not free.
 */
class WtaRegionParamDtoTest {

    @Test
    fun `parses a full-frame default response`() {
        val json = JSONObject(
            """
            {
              "frame_width": 1920,
              "frame_height": 1080,
              "watching_regions": [
                {"rect": [0, 0, 1920, 1080], "d1": 0.25, "d2": 0.25,
                 "min_yaw": -50, "max_yaw": 50, "min_pitch": -50, "max_pitch": 50}
              ],
              "counting_regions": [{"rect": [0, 0, 1920, 1080], "time": 300}]
            }
            """.trimIndent()
        )

        val dto = WtaRegionParamDto.fromJson(json)

        assertEquals(1920, dto.frameWidth)
        assertEquals(1080, dto.frameHeight)

        val watching = dto.watchingRegions?.single()
        assertEquals(WtaRectDto.fullFrame(), watching?.rect)
        assertEquals(0.25, watching?.d1 ?: 0.0, 1e-9)
        assertEquals(-50, watching?.minYaw)
        assertEquals(50, watching?.maxPitch)

        val counting = dto.countingRegions?.single()
        assertEquals(WtaRectDto.fullFrame(), counting?.rect)
        assertEquals(300, counting?.time)
    }

    @Test
    fun `a valid single-type write passes validation and omits the other array`() {
        val dto = WtaRegionParamDto(
            watchingRegions = listOf(
                WtaWatchingRegionItemDto(rect = WtaRectDto(0, 0, 960, 1080))
            )
        )

        assertNull(dto.validationError())

        val json = dto.toSetJson()
        assertTrue(json.has("watching_regions"))
        assertEquals(
            "omitting counting_regions must leave that type untouched",
            false, json.has("counting_regions")
        )
        // frame_width/frame_height are read-only and must never be sent back.
        assertEquals(false, json.has("frame_width"))
    }

    @Test
    fun `neither array provided is rejected`() {
        val error = WtaRegionParamDto().validationError()

        assertNotNull(error)
        assertTrue(error!!.contains("No watching_regions or counting_regions"))
    }

    @Test
    fun `an empty array is rejected rather than sent as a clear-all`() {
        // The protocol has no "remove every region" form: an empty array would be rejected by the
        // device, and silently turning it into a full-frame default would not be what was asked.
        val error = WtaRegionParamDto(watchingRegions = emptyList()).validationError()

        assertNotNull(error)
        assertTrue(error!!.contains("1~3"))
    }

    @Test
    fun `more than three regions is rejected`() {
        val regions = (0 until 4).map {
            WtaWatchingRegionItemDto(rect = WtaRectDto(0, 0, 100, 100))
        }

        val error = WtaRegionParamDto(watchingRegions = regions).validationError()

        assertNotNull(error)
        assertTrue(error!!.contains("got 4"))
    }

    @Test
    fun `a region without a rectangle is rejected`() {
        val error = WtaRegionParamDto(
            watchingRegions = listOf(WtaWatchingRegionItemDto(minYaw = -30))
        ).validationError()

        assertNotNull(error)
        assertTrue(error!!.contains("rect must be [x1,y1,x2,y2]"))
    }

    @Test
    fun `inverted and out-of-bounds rectangles are rejected`() {
        assertTrue(
            requireNotNull(WtaRectDto(500, 0, 100, 100).validationError())
                .contains("x1 < x2")
        )
        assertTrue(
            requireNotNull(WtaRectDto(0, 500, 100, 100).validationError())
                .contains("y1 < y2")
        )
        assertTrue(
            requireNotNull(WtaRectDto(0, 0, 2000, 100).validationError())
                .contains("x out of range")
        )
        assertTrue(
            requireNotNull(WtaRectDto(0, 0, 100, 2000).validationError())
                .contains("y out of range")
        )
        assertNull(WtaRectDto.fullFrame().validationError())
    }

    @Test
    fun `counting time outside 0 to 3600 seconds is rejected`() {
        val error = WtaRegionParamDto(
            countingRegions = listOf(
                WtaCountingRegionItemDto(rect = WtaRectDto.fullFrame(), time = 4000)
            )
        ).validationError()

        assertNotNull(error)
        assertTrue(error!!.contains("time=4000"))
        assertTrue(error.contains("[0, 3600]"))
    }

    @Test
    fun `watching angle beyond the WTA upper bound of 80 is rejected`() {
        // GAZE accepts up to 90 for the same field; WTA stops at 80.
        val error = WtaRegionParamDto(
            watchingRegions = listOf(
                WtaWatchingRegionItemDto(rect = WtaRectDto.fullFrame(), maxYaw = 90)
            )
        ).validationError()

        assertNotNull(error)
        assertTrue(error!!.contains("max_yaw=90"))
        assertTrue(error.contains("[-90, 80]"))
    }

    @Test
    fun `non-finite d1 is rejected before it can reach org_json`() {
        // org.json throws on NaN, which would surface as a JSONException from inside the SDK
        // instead of a typed failure.
        val error = WtaRegionParamDto(
            watchingRegions = listOf(
                WtaWatchingRegionItemDto(rect = WtaRectDto.fullFrame(), d1 = Double.NaN)
            )
        ).validationError()

        assertNotNull(error)
        assertTrue(error!!.contains("d1"))
    }

    @Test
    fun `an invalid region in the second array stops the whole request`() {
        // The watching array alone is fine; the counting array is not. Nothing may be sent.
        val dto = WtaRegionParamDto(
            watchingRegions = listOf(
                WtaWatchingRegionItemDto(rect = WtaRectDto(0, 0, 960, 1080))
            ),
            countingRegions = listOf(
                WtaCountingRegionItemDto(rect = WtaRectDto(0, 0, 0, 1080))
            )
        )

        val error = dto.validationError()

        assertNotNull("one bad region must fail the whole object", error)
        assertTrue(error!!.contains("counting_regions[0]"))
    }

    @Test
    fun `rect serializes as the protocol array form`() {
        val array = WtaRectDto(10, 20, 30, 40).toJson()

        assertEquals(4, array.length())
        assertEquals(10, array.getInt(0))
        assertEquals(20, array.getInt(1))
        assertEquals(30, array.getInt(2))
        assertEquals(40, array.getInt(3))
    }
}
