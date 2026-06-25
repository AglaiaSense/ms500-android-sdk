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

import android.util.Log
import org.json.JSONObject

/**
 * CDC receiver.
 * Responsible for byte-stream decoding, JSON parsing, and message classification.
 */
class CdcReceiver {
    companion object {
        /** Log tag for the receiver chain. */
        private const val TAG = "CdcReceiver"
    }

    /** Listener interface for frames and structured messages. */
    interface Listener {
        /** Called when a complete protocol frame is received. */
        fun onFrame(frame: CdcWireFormat.Frame) {}

        /** Called when a classified message is received. */
        fun onMessage(message: CdcWireFormat.IncomingMessage) {}
    }

    /** Sole streaming decoder instance handling fragmentation and sticky packets. */
    private val decoder = CdcWireFormat.Decoder()

    /** Registered listener set. */
    private val listeners = LinkedHashSet<Listener>()

    /** Registers a new listener. */
    fun addListener(listener: Listener) {
        listeners += listener
    }

    /** Removes a registered listener. */
    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    /** Resets internal decoder state, usually called after disconnect. */
    fun reset() {
        decoder.reset()
    }

    /**
     * Processes newly received serial byte stream and dispatches decoded complete frames.
     *
     * @param data Raw bytes read from the serial port.
     * @return All complete frames decoded from the buffer in this call.
     */
    fun onBytesReceived(data: ByteArray): List<CdcWireFormat.Frame> {
        decoder.append(data)
        val frames = decoder.decodeAvailable()
        for (frame in frames) {
            dispatchFrame(frame)
        }
        return frames
    }

    /** Dispatches the raw frame, then routes by category: JSON, file, or unknown. */
    private fun dispatchFrame(frame: CdcWireFormat.Frame) {
        Log.d(TAG, "dispatchFrame: ${CdcProtocol.getTypeName(frame.type)}, payload=${frame.payload.size} bytes")
        listeners.forEach { it.onFrame(frame) }

        val category = CdcWireFormat.toMessageCategory(frame.type)
        when (category) {
            CdcWireFormat.MessageCategory.FILE_DATA -> dispatchFileData(frame, category)
            CdcWireFormat.MessageCategory.UNKNOWN -> dispatchUnknown(frame)
            else -> dispatchJson(frame, category)
        }
    }

    /** Treats file-channel payloads as binary, optionally providing a JSON view for debugging. */
    private fun dispatchFileData(frame: CdcWireFormat.Frame, category: CdcWireFormat.MessageCategory) {
        val raw = try {
            String(frame.payload, Charsets.UTF_8).trim()
        } catch (_: Exception) {
            "[Binary data: ${frame.payload.size} bytes]"
        }
        val json = if (raw.startsWith("{")) runCatching { JSONObject(raw) }.getOrNull() else null
        listeners.forEach {
            it.onMessage(
                CdcWireFormat.IncomingMessage(
                    raw = raw,
                    json = json,
                    error = null,
                    msgType = frame.type,
                    category = category
                )
            )
        }
    }

    /** Passes unknown frames as raw text with an error description. */
    private fun dispatchUnknown(frame: CdcWireFormat.Frame) {
        CdcWireFormat.logUnknownType(frame.type, frame.payload)
        val raw = String(frame.payload, Charsets.UTF_8)
        listeners.forEach {
            it.onMessage(
                CdcWireFormat.IncomingMessage(
                    raw = raw,
                    json = null,
                    error = "Unknown frame type: ${CdcProtocol.getTypeName(frame.type)}",
                    msgType = frame.type,
                    category = CdcWireFormat.MessageCategory.UNKNOWN
                )
            )
        }
    }

    /** JSON channel: performs integrity checks and parsing so upper layers don't have to. */
    private fun dispatchJson(frame: CdcWireFormat.Frame, category: CdcWireFormat.MessageCategory) {
        val raw = String(frame.payload, Charsets.UTF_8).trim()
        if (raw.isEmpty()) {
            Log.w(TAG, "Empty JSON payload")
            return
        }

        val lastBrace = raw.lastIndexOf('}')
        val lastBracket = raw.lastIndexOf(']')
        val lastValidEnd = maxOf(lastBrace, lastBracket)
        val isComplete = lastValidEnd == raw.length - 1

        Log.d(
            TAG,
            "JSON payload (${raw.length} bytes, type=${CdcProtocol.getTypeName(frame.type)}): $raw"
        )

        if (!isComplete) {
            Log.w(TAG, "JSON appears incomplete: ends at pos $lastValidEnd, length=${raw.length}")
            Log.w(TAG, "JSON tail: ...${raw.substring(maxOf(0, raw.length - 100))}")
        }

        try {
            val json = JSONObject(raw)
            CdcWireFormat.logClassification(frame.type, category, json)
            listeners.forEach {
                it.onMessage(
                    CdcWireFormat.IncomingMessage(
                        raw = raw,
                        json = json,
                        error = null,
                        msgType = frame.type,
                        category = category
                    )
                )
            }
        } catch (e: Exception) {
            val err = "JSON Parse Error: ${e.message}"
            Log.e(TAG, err)
            Log.e(TAG, "Failed JSON length: ${raw.length}")
            Log.e(TAG, "Failed JSON tail: ...${raw.substring(maxOf(0, raw.length - 50))}")
            listeners.forEach {
                it.onMessage(
                    CdcWireFormat.IncomingMessage(
                        raw = raw,
                        json = null,
                        error = err,
                        msgType = frame.type,
                        category = category
                    )
                )
            }
        }
    }
}
