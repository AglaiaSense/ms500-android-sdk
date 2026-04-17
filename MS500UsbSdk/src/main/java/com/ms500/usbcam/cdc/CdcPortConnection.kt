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
 * CDC 端口连接层。
 *
 * 只负责 USB/Serial 连接生命周期、字节收发和连接状态。
 * 协议解码和消息分类由 [CdcReceiver] 处理。
 *
 * CDC port connection layer.
 * Manages USB/Serial lifecycle, raw byte I/O, and connection state.
 * Protocol decoding is delegated to [CdcReceiver].
 */
class CdcPortConnection(
    /** Android 上下文，用于获取 [UsbManager] 和注册广播。 / Android [Context] for [UsbManager] and broadcast registration. */
    private val context: Context,
    /** 状态监听器，用于向外同步连接状态和文本状态。 / Status listener for connection and text status updates. */
    private val listener: StatusListener,
    /** USB 权限申请广播 action。 / Broadcast action for USB permission requests. */
    private val permissionAction: String = "com.ms500.usbcam.USB_PERMISSION"
) : SerialInputOutputManager.Listener {

    companion object {
        /** 连接层日志 tag。 / Log tag for this layer. */
        private const val TAG = "CdcPortConnection"

        /** 目标 ESP32 设备 VID。 / Target ESP32 USB VID. */
        private const val ESP32_VID = 0x303A

        /** 目标 ESP32 设备主 PID。 / Target ESP32 USB PID. */
        private const val ESP32_PID = 0x8000

        /** 串口默认波特率。 / Default serial baud rate. */
        private const val BAUD_RATE = 115200

        /** 自动重连延迟（毫秒）。 / Auto-reconnect delay in milliseconds. */
        private const val RECONNECT_DELAY_MS = 3000L

        /** 自动重连最大尝试次数。 / Maximum auto-reconnect attempts. */
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }

    /** 连接状态机。 / Connection state machine. */
    enum class ConnectionState {
        /** 已断开。 / Disconnected. */
        DISCONNECTED,
        /** 正在连接。 / Connecting. */
        CONNECTING,
        /** 已连接。 / Connected. */
        CONNECTED,
        /** 正在重连。 / Reconnecting. */
        RECONNECTING
    }

    /** 客户端对外暴露的状态监听接口。 / Public status listener interface. */
    interface StatusListener {
        /** 文本状态更新回调。 / Text status update callback. */
        fun onStatus(status: String)

        /** 连接状态变更回调。 / Connection state change callback. */
        fun onConnectionStateChanged(state: ConnectionState)
    }

    /** CDC 消息监听接口。 / CDC message listener interface. */
    interface MessageListener {
        /** 解出完整 frame 时回调。 / Called when a complete frame is decoded. */
        fun onFrame(frame: CdcProtocol.Frame) {}

        /** 收到结构化消息时回调。 / Called when a structured message is received. */
        fun onMessage(raw: String, json: JSONObject?, error: String?, msgType: Int)

        /** 连接状态变化时回调。 / Called when connection state changes. */
        fun onConnectionStateChanged(state: ConnectionState) {}
    }

    /** 主线程 handler，用于统一状态和消息回调线程。 / Main thread [Handler] for consistent callbacks. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 外部注册的 CDC 消息监听器列表。 / Registered CDC message listeners. */
    private val messageListeners = CopyOnWriteArrayList<MessageListener>()

    /** 内部接收器，负责把原始字节流解码成结构化消息。 / Internal receiver that decodes raw bytes into structured messages. */
    private val receiver = CdcReceiver().also { cdcReceiver ->
        cdcReceiver.addListener(object : CdcReceiver.Listener {
            override fun onFrame(frame: CdcProtocol.Frame) {
                messageListeners.forEach { it.onFrame(frame) }
            }

            override fun onMessage(message: CdcProtocol.IncomingMessage) {
                messageListeners.forEach {
                    it.onMessage(message.raw, message.json, message.error, message.msgType)
                }
            }
        })
    }

    /** 当前已打开的串口对象。 / Currently opened USB serial port. */
    private var usbSerialPort: UsbSerialPort? = null

    /** 串口读写管理器。 / Serial I/O manager. */
    private var usbIoManager: SerialInputOutputManager? = null

    /** USB 权限广播是否已经注册。 / Whether the USB permission broadcast receiver is registered. */
    private var receiverRegistered = false

    /** 当前连接状态，使用原子值便于跨线程读取。 / Atomic connection state for thread-safe reads. */
    private val connectionState = AtomicInteger(ConnectionState.DISCONNECTED.ordinal)

    /** 是否启用自动重连。 / Whether auto-reconnect is enabled. */
    private val autoReconnectEnabled = AtomicBoolean(false)

    /** 当前已执行的重连次数。 / Current reconnect attempt count. */
    private var reconnectAttempts = 0

    /** 已计划的重连任务。 / Scheduled reconnect runnable. */
    private var reconnectRunnable: Runnable? = null

    /** 当前连接统计信息。 / Current connection statistics. */
    private var stats = ConnectionStats()

    /**
     * 连接统计。
     *
     * @property bytesSent 累计发送字节数。 / Total bytes sent.
     * @property bytesReceived 累计接收字节数。 / Total bytes received.
     * @property framesDecoded 累计解码出的帧数。 / Total decoded frames.
     * @property connectTime 最近一次成功连接时间戳。 / Timestamp of the last successful connection.
     */
    data class ConnectionStats(
        var bytesSent: Long = 0,
        var bytesReceived: Long = 0,
        var framesDecoded: Long = 0,
        var connectTime: Long = 0
    )

    /** 处理 USB 权限申请结果的广播接收器。 / Broadcast receiver for USB permission results. */
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

    /** 添加一个消息监听器，并立即同步当前连接状态。 / Adds a message listener and immediately syncs the current connection state. */
    fun addMessageListener(listener: MessageListener) {
        messageListeners.add(listener)
        listener.onConnectionStateChanged(getConnectionState())
    }

    /** 移除一个消息监听器。 / Removes a message listener. */
    fun removeMessageListener(listener: MessageListener) {
        messageListeners.remove(listener)
    }

    /** 注册 USB 权限广播接收器。 / Registers the USB permission broadcast receiver. */
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

    /** 停止客户端并释放所有连接资源。 / Stops the client and releases all connection resources. */
    fun stop() {
        disableAutoReconnect()
        disconnect()
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(usbReceiver) }
            receiverRegistered = false
        }
        Log.d(TAG, "Client stopped")
    }

    /** 扫描目标设备并启动连接或权限申请。 / Scans for the target device and initiates connection or permission request. */
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

    /** 主动断开当前连接，并重置接收状态。 / Actively disconnects and resets the receiver state. */
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

    /** 先断开后延迟重新连接。 / Disconnects and reconnects after a short delay. */
    fun reconnect() {
        disconnect()
        mainHandler.postDelayed({ connect() }, 500)
    }

    /** 向当前串口发送一段原始 CDC 数据。 / Sends raw CDC data to the current serial port. */
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

    /** 当前是否处于已连接状态。 / Returns whether currently connected. */
    fun isConnected(): Boolean = getConnectionState() == ConnectionState.CONNECTED

    /** 返回当前连接状态快照。 / Returns the current connection state snapshot. */
    fun getConnectionState(): ConnectionState = ConnectionState.values()[connectionState.get()]

    /** 返回连接统计信息副本。 / Returns a copy of the connection statistics. */
    fun getStats(): ConnectionStats = stats.copy()

    /** 启用断线自动重连。 / Enables auto-reconnect on disconnect. */
    fun enableAutoReconnect() {
        autoReconnectEnabled.set(true)
        Log.d(TAG, "Auto reconnect enabled")
    }

    /** 关闭自动重连并取消已有重连任务。 / Disables auto-reconnect and cancels pending reconnect tasks. */
    fun disableAutoReconnect() {
        autoReconnectEnabled.set(false)
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        reconnectRunnable = null
        reconnectAttempts = 0
    }

    /** 向系统申请指定 USB 设备的访问权限。 / Requests system permission for the given USB device. */
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

    /** 只保留默认 prober，驱动识别逻辑统一收口，避免重复 fallback。 / Probes and opens the device using the default prober. */
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
                usbSerialPort?.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
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

    /** 在允许的情况下计划下一次自动重连。 / Schedules the next auto-reconnect attempt if enabled. */
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

    /** 向外同步一条文本状态。 / Posts a text status update to the listener. */
    private fun updateStatus(status: String) {
        mainHandler.post {
            listener.onStatus("Status: $status")
        }
    }

    /** 更新连接状态，并把变化广播给所有监听器。 / Updates connection state and broadcasts the change to all listeners. */
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

    /** 判断一个 USB 设备是否属于目标 ESP32 CDC 设备。 / Checks whether a USB device is the target ESP32 CDC device. */
    private fun isEsp32Device(vid: Int, pid: Int): Boolean {
        return vid == ESP32_VID && (pid == ESP32_PID || pid == 0x0800 || pid == 8000)
    }

    /** 串口线程回来的数据统一切回主线程再解码，保持监听器回调线程一致。 / Receives new data from the serial thread and decodes it on the main thread. */
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

    /** 串口读写线程发生异常时回调。 / Called when the serial I/O thread encounters an error. */
    override fun onRunError(e: Exception?) {
        Log.e(TAG, "Read error: ${e?.message}")
        mainHandler.post {
            updateStatus("Read Error: ${e?.message}")
            updateConnectionState(ConnectionState.DISCONNECTED)
            disconnect()
        }
    }
}
