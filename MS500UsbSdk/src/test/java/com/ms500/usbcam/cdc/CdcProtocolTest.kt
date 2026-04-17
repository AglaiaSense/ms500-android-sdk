package com.ms500.usbcam.cdc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CdcProtocolTest {

    @Test
    fun `decode standard json frame`() {
        val payload = """{"cmd":"get_device_info"}""".toByteArray(Charsets.UTF_8)
        val frameBytes = CdcProtocol.buildJsonCommandFrame(isSet = false, jsonBytes = payload)
        val decoder = CdcProtocol.Decoder()
        decoder.append(frameBytes)
        val frames = decoder.decodeAvailable()

        assertEquals(1, frames.size)
        val frame = frames.first()
        assertEquals(CdcProtocol.MSG_TYPE_PARAM_GET, frame.type)
        assertArrayEquals(payload, frame.payload)
    }

    @Test
    fun `decode with fragmented input`() {
        val payload = """{"cmd":"set_inference_state","inference_enabled":true}""".toByteArray(Charsets.UTF_8)
        val frameBytes = CdcProtocol.buildJsonCommandFrame(isSet = true, jsonBytes = payload)
        val decoder = CdcProtocol.Decoder()

        decoder.append(frameBytes.copyOfRange(0, 7))
        assertEquals(0, decoder.decodeAvailable().size)

        decoder.append(frameBytes.copyOfRange(7, frameBytes.size))
        val frames = decoder.decodeAvailable()
        assertEquals(1, frames.size)
        assertArrayEquals(payload, frames.first().payload)
    }

    @Test
    fun `decode sticky frames in one packet`() {
        val a = CdcProtocol.buildJsonCommandFrame(false, """{"cmd":"a"}""".toByteArray(Charsets.UTF_8))
        val b = CdcProtocol.buildJsonCommandFrame(false, """{"cmd":"b"}""".toByteArray(Charsets.UTF_8))
        val all = ByteArray(a.size + b.size)
        System.arraycopy(a, 0, all, 0, a.size)
        System.arraycopy(b, 0, all, a.size, b.size)

        val decoder = CdcProtocol.Decoder()
        decoder.append(all)
        val frames = decoder.decodeAvailable()
        assertEquals(2, frames.size)
    }

    @Test
    fun `resync after invalid data then decode valid frame`() {
        val badPrefix = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val good = CdcProtocol.buildJsonCommandFrame(false, """{"cmd":"ok"}""".toByteArray(Charsets.UTF_8))
        val input = ByteArray(badPrefix.size + good.size)
        System.arraycopy(badPrefix, 0, input, 0, badPrefix.size)
        System.arraycopy(good, 0, input, badPrefix.size, good.size)

        val decoder = CdcProtocol.Decoder()
        decoder.append(input)
        val frames = decoder.decodeAvailable()

        assertEquals(1, frames.size)
    }

    @Test
    fun `reject old len plus json frame`() {
        val payload = """{"track_id":1}""".toByteArray(Charsets.UTF_8)
        val legacy = ByteArray(2 + payload.size)
        legacy[0] = (payload.size and 0xFF).toByte()
        legacy[1] = ((payload.size shr 8) and 0xFF).toByte()
        System.arraycopy(payload, 0, legacy, 2, payload.size)

        val decoder = CdcProtocol.Decoder()
        decoder.append(legacy)
        val frames = decoder.decodeAvailable()
        assertEquals(0, frames.size)
    }
}
