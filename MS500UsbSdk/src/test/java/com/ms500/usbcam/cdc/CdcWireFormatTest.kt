/*
 * Copyright 2026-2027 AglaiaSense Technology Co., Ltd
 *
 * This is UNPUBLISHED PROPRIETARY SOURCE CODE of AglaiaSense Technology Co., Ltd
 * No part of this file may be copied, modified, sold, and distributed in any
 * form or by any means without prior explicit permission in writing from
 * AglaiaSense Technology Co., Ltd
 *
 */

package com.ms500.usbcam.cdc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for command-to-msgType classification.
 *
 * The thing worth protecting here is that an **unregistered** write command does not silently go
 * out as a read. That bug is invisible in code review - the call succeeds, the frame is well
 * formed, and only the device notices, answering `Unknown param_get cmd`.
 */
class CdcWireFormatTest {

    @Test
    fun `registered commands keep their explicit type`() {
        assertEquals(CdcProtocol.MSG_TYPE_PARAM_GET, CdcWireFormat.getMessageType("get_camera_param"))
        assertEquals(CdcProtocol.MSG_TYPE_PARAM_SET, CdcWireFormat.getMessageType("set_camera_param"))
        assertEquals(CdcProtocol.MSG_TYPE_PARAM_SET, CdcWireFormat.getMessageType("reset_postprocess_param"))
        assertEquals(CdcProtocol.MSG_TYPE_PARAM_SET, CdcWireFormat.getMessageType("clear_roi"))
    }

    @Test
    fun `explicit non-parameter types are not overridden by the prefix rule`() {
        // `factory_reset` contains "reset" but is a system control command, and `register_set`
        // ends in "set" - neither may be reclassified as a parameter write.
        assertEquals(CdcProtocol.MSG_TYPE_SYS_CTRL, CdcWireFormat.getMessageType("reboot"))
        assertEquals(CdcProtocol.MSG_TYPE_SYS_CTRL, CdcWireFormat.getMessageType("factory_reset"))
        assertEquals(CdcProtocol.MSG_TYPE_REG_SET, CdcWireFormat.getMessageType("register_set"))
        assertEquals(CdcProtocol.MSG_TYPE_REG_GET, CdcWireFormat.getMessageType("register_get"))
        assertEquals(CdcProtocol.MSG_TYPE_OTA_UPGRADE, CdcWireFormat.getMessageType("upgrade_start"))
    }

    @Test
    fun `unregistered write commands are classified as writes, not reads`() {
        // Real case: firmware 4.4 has set_detection_draw_state, and the SDK deliberately exposes
        // no typed wrapper for it because it is a device-internal debug capability. Sent through
        // deviceParam.baseCommand() it used to go out as PARAM_GET and always fail.
        assertEquals(
            CdcProtocol.MSG_TYPE_PARAM_SET,
            CdcWireFormat.getMessageType("set_detection_draw_state")
        )
        assertEquals(
            CdcProtocol.MSG_TYPE_PARAM_SET,
            CdcWireFormat.getMessageType("reset_something_not_yet_wrapped")
        )
        assertEquals(
            CdcProtocol.MSG_TYPE_PARAM_SET,
            CdcWireFormat.getMessageType("clear_something_not_yet_wrapped")
        )
        assertTrue(CdcWireFormat.isSetCommand("set_detection_draw_state"))
    }

    @Test
    fun `unregistered read commands stay reads`() {
        assertEquals(
            CdcProtocol.MSG_TYPE_PARAM_GET,
            CdcWireFormat.getMessageType("get_detection_draw_state")
        )
        assertFalse(CdcWireFormat.isSetCommand("get_detection_draw_state"))
    }

    @Test
    fun `a command matching no prefix still defaults to read`() {
        // Preserves the previous behaviour for names that are neither get_ nor a write prefix.
        assertEquals(CdcProtocol.MSG_TYPE_PARAM_GET, CdcWireFormat.getMessageType("something_odd"))
        assertEquals(CdcProtocol.MSG_TYPE_PARAM_GET, CdcWireFormat.getMessageType(""))
    }

    @Test
    fun `prefix match is anchored at the start`() {
        // "offset_x" contains "set_" but does not start with it, so it must stay a read.
        assertEquals(CdcProtocol.MSG_TYPE_PARAM_GET, CdcWireFormat.getMessageType("offset_x"))
        assertEquals(CdcProtocol.MSG_TYPE_PARAM_GET, CdcWireFormat.getMessageType("preset_mode"))
    }
}
