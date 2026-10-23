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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Model identity resolution in [DeviceParamDto].
 *
 * The old-firmware fixture is the real response captured from device
 * `usb_gaze_2.0.6_20261016_143157` (see `debug_data/samples/gaze/device_info.json`), with the
 * identity fields absent exactly as that firmware sends it. Identity values are redacted in the
 * stored sample and irrelevant here.
 */
class DeviceParamDtoTest {

    @Test
    fun `old gaze firmware reports no identity fields and resolves to gaze`() {
        // Verbatim shape of the 2.0.6 response: no model_type, loaded_model or model_match.
        val json = JSONObject(
            """
            {
              "firmware_version": "usb_gaze_2.0.6_20261016_143157",
              "uptime_us": 2062238120,
              "model_version": "1.0.1",
              "sensor_id": "<redacted>",
              "c_sn": "<redacted>",
              "u_sn": "<redacted>",
              "token": "<redacted>"
            }
            """.trimIndent()
        )

        val dto = DeviceParamDto.fromJson(json)

        assertNull("2.0.6 does not report model_type", dto.modelType)
        assertNull("2.0.6 does not report loaded_model", dto.loadedModel)
        assertNull("2.0.6 does not report model_match", dto.modelMatch)
        assertEquals(DeviceParamDto.MODEL_GAZE, dto.resolvedModelType)
        assertTrue("absent model_match must not be read as a mismatch", dto.reportsDetections)
        assertEquals(2062238120L, dto.uptimeUs)
    }

    @Test
    fun `model_type wins over the firmware version prefix`() {
        // Both models ship under the usb_gaze_ prefix, so an explicit model_type must take
        // priority - the prefix alone cannot distinguish them.
        val json = JSONObject(
            """
            {
              "firmware_version": "usb_gaze_3.0.1_20261020_101458",
              "model_type": "wta",
              "loaded_model": "wta",
              "model_match": true
            }
            """.trimIndent()
        )

        val dto = DeviceParamDto.fromJson(json)

        assertEquals(DeviceParamDto.MODEL_WTA, dto.resolvedModelType)
        assertEquals("wta", dto.loadedModel)
        assertTrue(dto.reportsDetections)
    }

    @Test
    fun `version 3x without model_type resolves to wta`() {
        val dto = DeviceParamDto.fromJson(
            JSONObject("""{"firmware_version": "usb_gaze_3.0.1_20261020_101458"}""")
        )

        assertEquals(DeviceParamDto.MODEL_WTA, dto.resolvedModelType)
    }

    @Test
    fun `pre-3_0_0 wta test firmware resolves to wta`() {
        val dto = DeviceParamDto.fromJson(
            JSONObject("""{"firmware_version": "usb_wta_0.9.1_20260901_120000"}""")
        )

        assertEquals(DeviceParamDto.MODEL_WTA, dto.resolvedModelType)
    }

    @Test
    fun `unrecognised firmware resolves to unknown rather than gaze`() {
        // Defaulting an unknown firmware to GAZE would silently parse WTA results with the wrong
        // semantics, so it must stay unknown.
        val dto = DeviceParamDto.fromJson(
            JSONObject("""{"firmware_version": "experimental_1.0.0"}""")
        )

        assertEquals(DeviceParamDto.MODEL_UNKNOWN, dto.resolvedModelType)
    }

    @Test
    fun `model mismatch means the device reports no detections`() {
        // Flashing WTA firmware without the matching model package produces exactly this state.
        val json = JSONObject(
            """
            {
              "firmware_version": "usb_gaze_3.0.1_20261020_101458",
              "model_type": "wta",
              "loaded_model": "gaze",
              "model_match": false
            }
            """.trimIndent()
        )

        val dto = DeviceParamDto.fromJson(json)

        assertEquals(DeviceParamDto.MODEL_WTA, dto.resolvedModelType)
        assertEquals("gaze", dto.loadedModel)
        assertFalse("model_match=false means no results will arrive", dto.reportsDetections)
    }

    @Test
    fun `loaded_model unknown is not treated as a mismatch`() {
        // During startup the sensor may not have reported a size yet. Unknown is not evidence the
        // wrong model is loaded, so detections must not be declared unavailable.
        val json = JSONObject(
            """
            {
              "firmware_version": "usb_gaze_3.0.1_20261020_101458",
              "model_type": "wta",
              "loaded_model": "unknown"
            }
            """.trimIndent()
        )

        val dto = DeviceParamDto.fromJson(json)

        assertEquals("unknown", dto.loadedModel)
        assertNull(dto.modelMatch)
        assertTrue(dto.reportsDetections)
    }

    @Test
    fun `empty identity strings are exposed as null`() {
        // Firmware returns "" rather than omitting a field when it has no value.
        val json = JSONObject(
            """
            {
              "firmware_version": "usb_gaze_2.0.6_20261016_143157",
              "sensor_id": "",
              "c_sn": "",
              "u_sn": "",
              "token": "",
              "model_type": ""
            }
            """.trimIndent()
        )

        val dto = DeviceParamDto.fromJson(json)

        assertNull(dto.sensorId)
        assertNull(dto.cSn)
        assertNull(dto.uSn)
        assertNull(dto.token)
        assertNull("an empty model_type must not be taken as a model", dto.modelType)
        assertEquals(DeviceParamDto.MODEL_GAZE, dto.resolvedModelType)
    }
}
