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

import com.ms500.usbcam.cdc.InputTensorMetadata
import com.ms500.usbcam.dto.DeviceParamDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for per-model input tensor geometry.
 *
 * The property worth protecting: an unknown model never silently acquires GAZE's tensor size.
 * Reporting 512x384 for a device that exports something else is indistinguishable from a real
 * measurement once it reaches the caller.
 */
class InputTensorModelTest {

    @Test
    fun `tensor sizes match the two models`() {
        assertEquals(512, InputTensorModel.GAZE.tensorWidth)
        assertEquals(384, InputTensorModel.GAZE.tensorHeight)
        assertEquals(416, InputTensorModel.WTA.tensorWidth)
        assertEquals(320, InputTensorModel.WTA.tensorHeight)
    }

    @Test
    fun `model types line up with DeviceParamDto`() {
        // resolvedModelType feeds fromModelType, so the strings have to be the same ones.
        assertEquals(DeviceParamDto.MODEL_GAZE, InputTensorModel.GAZE.modelType)
        assertEquals(DeviceParamDto.MODEL_WTA, InputTensorModel.WTA.modelType)
        assertEquals(InputTensorModel.GAZE, InputTensorModel.fromModelType(DeviceParamDto.MODEL_GAZE))
        assertEquals(InputTensorModel.WTA, InputTensorModel.fromModelType(DeviceParamDto.MODEL_WTA))
    }

    @Test
    fun `an unknown model resolves to null, never to GAZE`() {
        assertNull(InputTensorModel.fromModelType(DeviceParamDto.MODEL_UNKNOWN))
        assertNull(InputTensorModel.fromModelType(null))
        assertNull(InputTensorModel.fromModelType(""))
        assertNull(InputTensorModel.fromModelType("GAZE"))
    }

    @Test
    fun `the larger tensor gets the longer timeout`() {
        for (format in InputTensorImageFormat.values()) {
            val gaze = InputTensorModel.GAZE.recommendedTimeoutMs(format)
            val wta = InputTensorModel.WTA.recommendedTimeoutMs(format)
            assertTrue(
                "${format.displayName}: GAZE ${gaze}ms should exceed WTA ${wta}ms",
                gaze > wta
            )
            // 512*384 / (416*320) = 1.477
            assertEquals(gaze.toDouble(), wta * 1.477, wta * 0.01)
        }
    }

    @Test
    fun `PNG gets more time than JPG for the same model`() {
        for (model in InputTensorModel.values()) {
            assertTrue(
                model.name,
                model.recommendedTimeoutMs(InputTensorImageFormat.PNG) >
                    model.recommendedTimeoutMs(InputTensorImageFormat.JPG)
            )
        }
    }

    @Test
    fun `WTA keeps the measured baseline timeouts`() {
        // Measured on usb_gaze_3.0.1: JPG 47626 bytes / 47 chunks, PNG 205022 bytes / 201 chunks,
        // first chunk about 4 s. The baseline is the smaller model's, so it must stay unscaled.
        assertEquals(60_000L, InputTensorModel.WTA.recommendedTimeoutMs(InputTensorImageFormat.JPG))
        assertEquals(120_000L, InputTensorModel.WTA.recommendedTimeoutMs(InputTensorImageFormat.PNG))
    }

    @Test
    fun `reported size is checked against the model that asked`() {
        assertTrue(InputTensorModel.WTA.matchesReportedSize(416, 320))
        assertFalse(InputTensorModel.WTA.matchesReportedSize(512, 384))
        assertTrue(InputTensorModel.GAZE.matchesReportedSize(512, 384))
        assertFalse(InputTensorModel.GAZE.matchesReportedSize(416, 320))
        // Swapped dimensions are not a match either.
        assertFalse(InputTensorModel.WTA.matchesReportedSize(320, 416))
    }

    @Test
    fun `geometry label reads as width x height`() {
        assertEquals("512x384", InputTensorModel.GAZE.describeGeometry())
        assertEquals("416x320", InputTensorModel.WTA.describeGeometry())
    }

    @Test
    fun `unreported dimensions are flagged, not filled in`() {
        val unreported = InputTensorMetadata(
            width = InputTensorMetadata.UNKNOWN_DIMENSION,
            height = InputTensorMetadata.UNKNOWN_DIMENSION,
            format = InputTensorImageFormat.JPG.metadataValue,
            size = 47626
        )
        assertFalse(unreported.hasDimensions)
        // The sentinel must not equal either model's size, or "unknown" would read as a real value.
        assertFalse(InputTensorModel.GAZE.matchesReportedSize(unreported.width, unreported.height))
        assertFalse(InputTensorModel.WTA.matchesReportedSize(unreported.width, unreported.height))

        val reported = unreported.copy(width = 416, height = 320)
        assertTrue(reported.hasDimensions)
        assertTrue(InputTensorModel.WTA.matchesReportedSize(reported.width, reported.height))
    }
}
