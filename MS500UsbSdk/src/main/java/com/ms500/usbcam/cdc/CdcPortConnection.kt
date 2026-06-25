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

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ms500.usbserial.driver.UsbSerialPort
import com.ms500.usbserial.driver.UsbSerialProber
import com.ms500.usbserial.util.SerialInputOutputManager
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * CDC port connection layer.
 * Manages USB/Serial lifecycle, raw byte I/O, and connection state.
 * Protocol decoding is delegated to [CdcReceiver].
 */
class CdcPortConnection(
    /** Android [Context] for [UsbManager] and broadcast registration. */
    private val context: Context,
    /** Status listener for connection and text status updates. */
    private val listener: StatusListener,
    /** Broadcast action for USB permission requests. */
    private val permissionAction: String = "com.ms500.usbcam.USB_PERMISSION"
) : SerialInputOutputManager.Listener {

    companion object {
        /** Log tag for this layer. */
        private const val TAG = "CdcPortConnection"

        /** Auto-reconnect delay in milliseconds. */
        private const val RECONNECT_DELAY_MS = 3000L

        /** Maximum auto-reconnect attempts. */
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }

    /** Connection state machine. */
    enum class ConnectionState {
        /** Disconnected. */
        DISCONNECTED,
        /** Connecting. */
        CONNECTING,
        /** Connected. */
        CONNECTED,
        /** Reconnecting. */
        RECONNECTING
    }

    /** Public status listener interface. */
    interface StatusListener {
        /** Text status update callback. */
        fun onStatus(status: String)

        /** Connection state change callback. */
        fun onConnectionStateChanged(state: ConnectionState)
    }

    /** CDC message listener interface. */
    interface MessageListener {
        /** Called when a complete frame is decoded. */
        fun onFrame(frame: CdcWireFormat.Frame) {}

        /** Called when a structured message is received. */
        fun onMessage(raw: String, json: JSONObject?, error: String?, msgType: Int)

        /** Called when connection state changes. */
        fun onConnectionStateChanged(state: ConnectionState) {}
    }

    /** Main thread [Handler] for consistent callbacks. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Registered CDC message listeners. */
    private val messageListeners = CopyOnWriteArrayList<MessageListener>()

    /** Internal receiver that decodes raw bytes into structured messages. */
    private val receiver = CdcReceiver().also { cdcReceiver ->
        cdcReceiver.addListener(object : CdcReceiver.Listener {
            override fun onFrame(frame: CdcWireFormat.Frame) {
                messageListeners.forEach { it.onFrame(frame) }
            }

            override fun onMessage(message: CdcWireFormat.IncomingMessage) {
                messageListeners.forEach {
                    it.onMessage(message.raw, message.json, message.error, message.msgType)
                }
            }
        })
    }

    /** Currently opened USB serial port. */
    private var usbSerialPort: UsbSerialPort? = null

    /** Serial I/O manager. */
    private var usbIoManager: SerialInputOutputManager? = null

    /** Whether the USB permission broadcast receiver is registered. */
    private var receiverRegistered = false

    /** Atomic connection state for thread-safe reads. */
    private val connectionState = AtomicInteger(ConnectionState.DISCONNECTED.ordinal)

    /** Whether auto-reconnect is enabled. */
    private val autoReconnectEnabled = AtomicBoolean(false)

    /** Current reconnect attempt count. */
    private var reconnectAttempts = 0

    /** Scheduled reconnect runnable. */
    private var reconnectRunnable: Runnable? = null

    /** Current connection statistics. */
    private var stats = ConnectionStats()

    /**
     * Connection statistics.
     *
     * @property bytesSent Total bytes sent.
     * @property bytesReceived Total bytes received.
     * @property framesDecoded Total decoded frames.
     * @property connectTime Timestamp of the last successful connection.
     */
    data class ConnectionStats(
        var bytesSent: Long = 0,
        var bytesReceived: Long = 0,
        var framesDecoded: Long = 0,
        var connectTime: Long = 0
    )

    /** Broadcast receiver for USB permission results. */
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (permissionAction != intent.action) return
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                device?.let { connectToDevice(it) }
            } else {
                updateStatus("USB permission denied")
                updateConnectionState(ConnectionState.DISCONNECTED)
            }
        }
    }

    /** Adds a message listener and immediately syncs the current connection state. */
    fun addMessageListener(listener: MessageListener) {
        messageListeners.add(listener)
        listener.onConnectionStateChanged(getConnectionState())
    }

    /** Removes a message listener. */
    fun removeMessageListener(listener: MessageListener) {
        messageListeners.remove(listener)
    }

    /** Registers the USB permission broadcast receiver. */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun start() {
        if (receiverRegistered) return
        val filter = IntentFilter(permissionAction)
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val exportedFlag = Context::class.java.getField("RECEIVER_EXPORTED").getInt(null)
                context.registerReceiver(usbReceiver, filter, exportedFlag)
            } catch (_: Exception) {
                context.registerReceiver(usbReceiver, filter)
            }
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
        receiverRegistered = true
        Log.d(TAG, "Client started")
    }

    /** Stops the client and releases all connection resources. */
    fun stop() {
        disableAutoReconnect()
        disconnect()
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(usbReceiver) }
            receiverRegistered = false
        }
        Log.d(TAG, "Client stopped")
    }

    /** Scans for the target device and initiates connection or permission request. */
    fun connect() {
        if (getConnectionState() != ConnectionState.DISCONNECTED) {
            Log.w(TAG, "Already connected or connecting")
            return
        }

        updateConnectionState(ConnectionState.CONNECTING)
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        var espDevice: UsbDevice? = null
        val allDevicesInfo = StringBuilder()

        for (device in usbManager.deviceList.values) {
            val vid = device.vendorId
            val pid = device.productId
            allDevicesInfo.append(String.format("VID:0x%04X PID:0x%04X\n", vid, pid))
            if (isEsp32Device(vid, pid)) {
                espDevice = device
            }
        }

        if (espDevice == null) {
            val msg = if (allDevicesInfo.isEmpty()) {
                "No USB devices connected"
            } else {
                "ESP32 not found. Connected:\n$allDevicesInfo"
            }
            updateStatus(msg)
            updateConnectionState(ConnectionState.DISCONNECTED)
            return
        }

        if (usbManager.hasPermission(espDevice)) {
            connectToDevice(espDevice)
        } else {
            requestPermission(espDevice)
        }
    }

    /** Actively disconnects and resets the receiver state. */
    fun disconnect() {
        disableAutoReconnect()

        usbIoManager?.stop()
        usbIoManager = null

        runCatching {
            usbSerialPort?.close()
        }.onFailure { e ->
            Log.w(TAG, "Error closing port: ${e.message}")
        }
        usbSerialPort = null

        receiver.reset()
        updateConnectionState(ConnectionState.DISCONNECTED)
        updateStatus("Disconnected")
    }

    /** Disconnects and reconnects after a short delay. */
    fun reconnect() {
        disconnect()
        mainHandler.postDelayed({ connect() }, 500)
    }

    /** Sends raw CDC data to the current serial port. */
    fun send(data: ByteArray) {
        val port = usbSerialPort
        if (port == null) {
            Log.e(TAG, "Send failed: usbSerialPort is null")
            updateStatus("CDC not connected")
            return
        }

        if (getConnectionState() != ConnectionState.CONNECTED) {
            Log.e(TAG, "Send failed: not connected, state=${getConnectionState()}")
            updateStatus("Not connected")
            return
        }

        try {
            port.write(data, 1000)
            stats.bytesSent += data.size
//            Log.d(TAG, "Write completed: ${data.size} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Send failed: ${e.message}", e)
            updateStatus("Send failed: ${e.message}")
            if (autoReconnectEnabled.get()) {
                scheduleReconnect()
            }
        }
    }

    /** Returns whether currently connected. */
    fun isConnected(): Boolean = getConnectionState() == ConnectionState.CONNECTED

    /** Returns the current connection state snapshot. */
    fun getConnectionState(): ConnectionState = ConnectionState.values()[connectionState.get()]

    /** Returns a copy of the connection statistics. */
    fun getStats(): ConnectionStats = stats.copy()

    /** Enables auto-reconnect on disconnect. */
    fun enableAutoReconnect() {
        autoReconnectEnabled.set(true)
        Log.d(TAG, "Auto reconnect enabled")
    }

    /** Disables auto-reconnect and cancels pending reconnect tasks. */
    fun disableAutoReconnect() {
        autoReconnectEnabled.set(false)
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        reconnectRunnable = null
        reconnectAttempts = 0
    }

    /** Requests system permission for the given USB device. */
    private fun requestPermission(device: UsbDevice) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0, Intent(permissionAction), flags
        )
        usbManager.requestPermission(device, permissionIntent)
        updateStatus("Requesting USB permission...")
    }

    /** Probes and opens the device using the default prober. */
    private fun connectToDevice(device: UsbDevice) {
        val vid = device.vendorId
        val pid = device.productId

        if (!isEsp32Device(vid, pid)) {
            updateStatus("Ignored non-ESP32 device (VID:$vid PID:$pid)")
            updateConnectionState(ConnectionState.DISCONNECTED)
            return
        }

        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver == null) {
            updateStatus("Driver not found")
            updateConnectionState(ConnectionState.DISCONNECTED)
            return
        }
        if (driver.ports.isEmpty()) {
            updateStatus("No serial port")
            updateConnectionState(ConnectionState.DISCONNECTED)
            return
        }

        try {
            val portIndex = 0
            Log.d(TAG, "Using port index: $portIndex (total ports: ${driver.ports.size})")
            val connection = usbManager.openDevice(driver.device)

            if (connection == null) {
                updateStatus("Failed to open device connection")
                updateConnectionState(ConnectionState.DISCONNECTED)
                return
            }

            usbSerialPort = driver.ports[portIndex]
            usbSerialPort?.open(connection)

            runCatching {
                usbSerialPort?.setParameters(CdcWireFormat.BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                usbSerialPort?.dtr = true
                usbSerialPort?.rts = true
            }

            usbIoManager = SerialInputOutputManager(usbSerialPort, this)
            usbIoManager?.start()

            reconnectAttempts = 0
            stats.connectTime = System.currentTimeMillis()
            updateConnectionState(ConnectionState.CONNECTED)
            updateStatus("Connected: ${device.deviceName}")
            Log.i(TAG, "Connected to ${device.deviceName}, port $portIndex")
        } catch (e: IOException) {
            Log.e(TAG, "Connection error: ${e.message}")
            updateStatus("Connection Error: ${e.message}")
            updateConnectionState(ConnectionState.DISCONNECTED)
            disconnect()
        }
    }

    /** Schedules the next auto-reconnect attempt if enabled. */
    private fun scheduleReconnect() {
        if (!autoReconnectEnabled.get()) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnect attempts reached")
            updateStatus("Max reconnect attempts reached")
            return
        }

        reconnectAttempts++
        updateConnectionState(ConnectionState.RECONNECTING)
        updateStatus("Reconnecting... (attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)")

        reconnectRunnable = Runnable {
            if (autoReconnectEnabled.get()) {
                Log.d(TAG, "Auto reconnect attempt $reconnectAttempts")
                connect()
            }
        }
        mainHandler.postDelayed(reconnectRunnable!!, RECONNECT_DELAY_MS)
    }

    /** Posts a text status update to the listener. */
    private fun updateStatus(status: String) {
        mainHandler.post {
            listener.onStatus("Status: $status")
        }
    }

    /** Updates connection state and broadcasts the change to all listeners. */
    private fun updateConnectionState(state: ConnectionState) {
        val oldState = ConnectionState.values()[connectionState.getAndSet(state.ordinal)]
        if (oldState != state) {
            Log.d(TAG, "Connection state: $oldState -> $state")
            mainHandler.post {
                listener.onConnectionStateChanged(state)
                messageListeners.forEach { it.onConnectionStateChanged(state) }
            }

            if (state == ConnectionState.DISCONNECTED && autoReconnectEnabled.get()) {
                scheduleReconnect()
            }
        }
    }

    /** Checks whether a USB device is the target ESP32 CDC device. */
    private fun isEsp32Device(vid: Int, pid: Int): Boolean {
        return CdcWireFormat.isEsp32Device(vid, pid)
    }

    /** Receives new data from the serial thread and decodes it on the main thread. */
    override fun onNewData(data: ByteArray?) {
        if (data == null || data.isEmpty()) return

        stats.bytesReceived += data.size

        mainHandler.post {
            val frames = receiver.onBytesReceived(data)
            if (frames.isNotEmpty()) {
                stats.framesDecoded += frames.size
                Log.d(TAG, "Decoded ${frames.size} frames")
            }
        }
    }

    /** Called when the serial I/O thread encounters an error. */
    override fun onRunError(e: Exception?) {
        Log.e(TAG, "Read error: ${e?.message}")
        mainHandler.post {
            updateStatus("Read Error: ${e?.message}")
            updateConnectionState(ConnectionState.DISCONNECTED)
            disconnect()
        }
    }
}
