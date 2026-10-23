/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */
package com.ms500.usbcam.ota

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OTA package name validation.
 *
 * The WTA cases come from a real failure: flashing a `sony_wta` combined package was rejected by
 * the SDK with "Model package must be sony_gaze model" before the device ever saw it, which made
 * WTA model packages unflashable. These tests pin the fix.
 */
class OtaToolstoolsTest {

    @Test
    fun `gaze model names are accepted`() {
        assertTrue(OtaToolstools.isSonyGazeModel("sony_gaze"))
        assertTrue(OtaToolstools.isSonyGazeModel("sony_gaze_1"))
        assertTrue(OtaToolstools.isSonyGazeModel("SONY_GAZE"))
        assertTrue(OtaToolstools.isSupportedModel("sony_gaze"))
    }

    @Test
    fun `wta model names are accepted for upgrade`() {
        assertTrue(OtaToolstools.isSonyWtaModel("sony_wta"))
        assertTrue(OtaToolstools.isSonyWtaModel("sony_wta_2"))
        assertTrue(
            "a sony_wta package must be flashable - this is what blocked WTA switching",
            OtaToolstools.isSupportedModel("sony_wta")
        )
    }

    @Test
    fun `the narrow gaze check still excludes wta`() {
        // Callers that specifically mean the GAZE model must keep getting false here; only
        // upgrade validation was widened.
        assertFalse(OtaToolstools.isSonyGazeModel("sony_wta"))
    }

    @Test
    fun `unrelated names are rejected`() {
        // The check still has a job: catching a file that is not an MS500 model package at all.
        assertFalse(OtaToolstools.isSupportedModel("random_model"))
        assertFalse(OtaToolstools.isSupportedModel(""))
        assertFalse(OtaToolstools.isSupportedModel(null))
        assertFalse(
            "a prefix match must not be a substring match",
            OtaToolstools.isSupportedModel("not_sony_gaze")
        )
    }

    @Test
    fun `both firmware versions are recognised as usb_gaze`() {
        // Both models ship under the usb_gaze prefix because already-shipped firmware only accepts
        // upgrades with that prefix.
        assertTrue(OtaToolstools.isUsbGazeFirmware("usb_gaze", "usb_gaze_2.0.6_20261016_143157"))
        assertTrue(OtaToolstools.isUsbGazeFirmware("usb_gaze", "usb_gaze_3.0.1_20261020_101458"))
    }

    @Test
    fun `supported model names appear in the error text`() {
        // The message has to name what is acceptable, otherwise a rejected flash gives the caller
        // nothing to act on.
        val names = OtaToolstools.supportedModelNames()

        assertTrue(names.contains("sony_gaze"))
        assertTrue(names.contains("sony_wta"))
    }
}
