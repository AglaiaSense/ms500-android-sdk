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

import com.ms500.usbcam.dto.ResultDto
import com.ms500.usbcam.result.SdkError
import com.ms500.usbcam.result.SdkResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Local parameter validation.
 *
 * These cover the two things the SDK still checks locally after the no-gating decision: values
 * `org.json` cannot serialise, and the range of the **facade the caller chose**. Everything else
 * goes to the device.
 *
 * The facade-range rule is the one worth pinning down, because it looks wrong until you know the
 * reasoning: the same value is accepted or rejected depending on which facade it went through, on
 * the same device, by design. It is verified on hardware too, but hardware tests cannot run on
 * every build.
 */
class ParamValidationTest {

    @Test
    fun `a value inside the range passes`() {
        assertNull(ParamValidation.checkRange<ResultDto>("x", 0.5, 0.0, 1.0))
        assertNull(ParamValidation.checkRange<ResultDto>("n", 5, 1, 10))
    }

    @Test
    fun `range bounds are inclusive`() {
        assertNull(ParamValidation.checkRange<ResultDto>("x", 0.0, 0.0, 1.0))
        assertNull(ParamValidation.checkRange<ResultDto>("x", 1.0, 0.0, 1.0))
        assertNull(ParamValidation.checkRange<ResultDto>("n", 1, 1, 10))
        assertNull(ParamValidation.checkRange<ResultDto>("n", 10, 1, 10))
    }

    @Test
    fun `an out-of-range value names the field and the bounds`() {
        // The message has to be actionable on its own - it reaches the caller as the whole
        // explanation of why nothing was sent.
        val failure = ParamValidation.checkRange<ResultDto>("max_body_detection", 20, 1, 10)

        assertNotNull(failure)
        val error = (failure as SdkResult.Failure).error
        assertTrue(error is SdkError.InvalidArgument)
        val message = error.getMessage()
        assertTrue(message, message.contains("max_body_detection"))
        assertTrue(message, message.contains("20"))
        assertTrue(message, message.contains("[1, 10]"))
    }

    @Test
    fun `non-finite doubles are rejected before they reach org_json`() {
        // org.json throws on NaN and Infinity, so an unchecked value surfaces as a JSONException
        // from inside the SDK instead of a typed failure the caller can handle.
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val failure = ParamValidation.checkRange<ResultDto>("d1", value, 0.0, 1.0)
            assertNotNull("$value must be rejected", failure)
            val message = (failure as SdkResult.Failure).error.getMessage()
            assertTrue(message, message.contains("finite"))
        }
    }

    @Test
    fun `the same value passes one facade range and fails the other`() {
        // This is the no-gating decision in one assertion: max_body_detection = 20 is valid for
        // GAZE (1..100) and invalid for WTA (1..10). Which answer you get depends on the facade
        // you called, not on the device that is attached.
        val gazeResult = ParamValidation.checkRange<ResultDto>(
            "max_body_detection", 20, 1, 100
        )
        val wtaResult = ParamValidation.checkRange<ResultDto>(
            "max_body_detection", 20,
            WtaBounds.MAX_BODY_DETECTION_MIN, WtaBounds.MAX_BODY_DETECTION_MAX
        )

        assertNull("GAZE range 1..100 accepts 20", gazeResult)
        assertNotNull("WTA range 1..10 rejects 20", wtaResult)
    }

    @Test
    fun `wta bounds match the firmware macros`() {
        // Cross-checked against MS500_SONY_USB_WTA/main/ai_process/gaze_param_config.h. These are
        // the wire bounds the command layer enforces, which are not always the postprocess
        // library's internal clamps - max_person clamps to 0..10 internally while the command
        // layer only accepts 1..10, and the wire bound is what belongs here.
        assertEquals(1, WtaBounds.MAX_BODY_DETECTION_MIN)
        assertEquals(10, WtaBounds.MAX_BODY_DETECTION_MAX)
        assertEquals(0, WtaBounds.MAX_AGE_MIN)
        assertEquals(20, WtaBounds.MAX_AGE_MAX)
        assertEquals(0, WtaBounds.MIN_HITS_MIN)
        assertEquals(10, WtaBounds.MIN_HITS_MAX)
        // Angles stop at 80 for WTA; GAZE allows 90.
        assertEquals(-90, WtaBounds.ANGLE_MIN)
        assertEquals(80, WtaBounds.ANGLE_MAX)
        // Bias is +-15 for WTA; GAZE allows +-1000.
        assertEquals(-15.0, WtaBounds.BIAS_MIN, 1e-9)
        assertEquals(15.0, WtaBounds.BIAS_MAX, 1e-9)
        assertEquals(0, WtaBounds.MAX_DIST_MIN)
        assertEquals(300, WtaBounds.MAX_DIST_MAX)
        assertEquals(0, WtaBounds.MIN_AREA_MIN)
        assertEquals(40000, WtaBounds.MIN_AREA_MAX)
        assertEquals(0, WtaBounds.PERSON_WIDTH_MIN)
        assertEquals(200, WtaBounds.PERSON_WIDTH_MAX)
        assertEquals(0, WtaBounds.HEAD_FACE_WIDTH_MIN)
        assertEquals(100, WtaBounds.HEAD_FACE_WIDTH_MAX)
        assertEquals(0, WtaBounds.TRAJECTORY_LIFETIME_MIN)
        assertEquals(3600, WtaBounds.TRAJECTORY_LIFETIME_MAX)
    }

    @Test
    fun `invalid wraps a prepared reason`() {
        // Used by DTOs that validate themselves as a whole, where the reason already carries the
        // offending index.
        val failure = ParamValidation.invalid<ResultDto>("counting_regions[0].time=4000 out of range")

        val error = (failure as SdkResult.Failure).error
        assertTrue(error is SdkError.InvalidArgument)
        assertTrue(error.getMessage().contains("counting_regions[0]"))
    }
}
