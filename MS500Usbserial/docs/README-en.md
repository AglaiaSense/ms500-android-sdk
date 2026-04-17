# USB-CDC Serial Communication Integration Guide

This document explains how to integrate USB-CDC (Communications Device Class) functionality on top of existing UVC video transmission to achieve **simultaneous video preview and command/file transfer**.

## 1. Composite Device Principle

Your ESP32-P4 device is configured as a USB composite device, meaning it provides multiple functional interfaces under one USB device descriptor:

-   **Function 1 (UVC)**: Contains Video Control Interface and Video Streaming Interface. Used for real-time video stream transmission.
-   **Function 2 (CDC)**: Contains Communication Interface and Data Interface. Used for simulating serial port for bidirectional data transmission (sending commands, transferring files).

**Android Support**:
Android's USB Host API allows applications to open a `UsbDeviceConnection`, then separately `claimInterface()` for different interfaces. Therefore, **the project fully allows** CDC interface communication while maintaining UVC preview.

## 2. Architecture Design

To minimize intrusion into the existing `libausbc` core library, a **"shared connection, independent drivers"** strategy is recommended.

-   **Connection Management**: Continue using `USBMonitor` from `libuvc` for USB permission requests, device monitoring, and connection opening (`openDevice`).
-   **UVC Path**: `UVCCamera` uses `UsbControlBlock` (underlying holder of `fileDescriptor`) to handle video streams.
-   **CDC Path**: Introduce third-party library `usb-serial-for-android`, reuse the same `UsbDeviceConnection` or original `UsbDevice` object to drive the serial port.

## 3. Implementation Steps

### 3.1 Add Dependencies

Recommended to use `usb-serial-for-android` library, which encapsulates tedious CDC protocol details.

Add to `app/build.gradle`:

```gradle
dependencies {
    implementation 'com.github.mik3y:usb-serial-for-android:3.7.0'
}
```

### 3.2 Recognition and Initialization

Process in `CameraActivity` or `CameraFragment`'s `IDeviceConnectCallBack` callback.

**Key Point**: Don't re-request permissions or re-open the device, reuse directly.

```kotlin
// In onConnectDev callback
override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
    // 1. Start UVC preview (existing logic)
    // ... startPreview ...

    // 2. Initialize CDC serial port (new logic)
    device?.let { dev ->
        initSerialPort(dev)
    }
}

private var mSerialPort: UsbSerialPort? = null

private fun initSerialPort(device: UsbDevice) {
    // Use usb-serial-for-android to find CDC driver
    val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
    if (driver == null) {
        Logger.e("CDC", "No serial driver found for device")
        return
    }

    // Get existing connection (Note: ensure USBMonitor exposes connection, or use USBManager to reopen,
    // but usually recommend sharing connection to avoid conflicts. If USBMonitor encapsulation is too deep,
    // can try using UsbManager to openDevice again, Android allows multiple opens for same device)
    val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
    val connection = usbManager.openDevice(device)

    if (connection == null) {
        Logger.e("CDC", "Failed to open device connection")
        return
    }

    // Get first serial port (usually CDC only has one)
    mSerialPort = driver.ports[0]

    try {
        // Open serial port
        mSerialPort?.open(connection)
        // Set parameters: baud rate 115200, 8 data bits, 1 stop bit, no parity
        mSerialPort?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

        // Start read thread
        startSerialIoManager()

        Logger.i("CDC", "Serial port opened successfully")
    } catch (e: Exception) {
        Logger.e("CDC", "Error opening serial port: ${e.message}")
    }
}
```

### 3.3 Data Read/Write (Commands and Files)

**Send Commands/Files (Write)**:

```kotlin
fun sendCommand(command: String) {
    val data = command.toByteArray()
    mSerialPort?.write(data, 1000) // 1000ms timeout
}

fun sendFile(filePath: String) {
    // Recommended to execute in child thread
    thread {
        val fileData = File(filePath).readBytes()
        // Can send in packets
        mSerialPort?.write(fileData, 5000)
    }
}
```

**Receive Response (Read)**:

Use `SerialInputOutputManager` to listen for data:

```kotlin
private fun startSerialIoManager() {
    val listener = object : SerialInputOutputManager.Listener {
        override fun onNewData(data: ByteArray) {
            // Process received data (callback in child thread)
            val message = String(data)
            Logger.i("CDC", "Received: $message")
        }

        override fun onRunError(e: Exception) {
            Logger.e("CDC", "Runner stopped: ${e.message}")
        }
    }

    val ioManager = SerialInputOutputManager(mSerialPort, listener)
    ioManager.start()
}
```

### 3.4 Resource Release

Close serial port in `onDisconnectDec` or `onDestroy`, **be careful not to close connection** (if it's shared and managed by `USBMonitor`), or if reopened by yourself, it needs to be closed.

```kotlin
private fun closeSerial() {
    try {
        mSerialPort?.close()
        mSerialPort = null
    } catch (e: Exception) {
        // ignore
    }
}
```

## 4. Common Issues and Solutions

1.  **Permission Conflict**:
    `USBMonitor` requests USB permission once. After obtaining permission, it is valid for the entire `UsbDevice`. Therefore, you don't need to request separate permission for CDC, use it directly.

2.  **Interface Preemption (Claim Interface)**:
    -   `libuvc` (Native layer) claims Video Interface (usually Interface 0 and 1).
    -   `usb-serial-for-android` attempts to claim CDC Data Interface (usually Interface 2 and 3).
    -   **No Conflict**: As long as Interface IDs don't overlap, both can coexist perfectly.

3.  **Device Filtering**:
    Check `res/xml/default_device_filter.xml`. Ensure filtering rules don't filter out your composite device due to `class` or `subclass` mismatches. Recommend using only `vendor-id` and `product-id` for filtering, don't add `class` restrictions.

4.  **Bandwidth Competition**:
    UVC video stream occupies significant USB bandwidth (Isochronous or Bulk transfer). CDC usually uses Bulk transfer. During high-resolution video preview, CDC file transfer speed may be affected. Recommend appropriately reducing video frame rate or pausing preview when transferring large files.

## 5. Project File Details

### 5.1 Source Directory (`src/main/java/com/ms500/usbserial`)

This directory contains core implementation classes for USB serial communication.

#### **`driver/` (Driver Implementation Package)**
- **`UsbSerialPort.java`**: Defines generic interface for serial communication (`read`, `write`, `open`, `close`, `setParameters`, etc.).
- **`UsbSerialDriver.java`**: Defines driver program interface, one driver can contain multiple Ports.
- **`CdcAcmSerialDriver.java`**: **Core Driver**. Implements CDC-ACM (USB Communications Device Class) protocol. This is the most commonly used standard virtual serial port protocol for ESP32, Arduino, and other devices.
- **`Ch34xSerialDriver.java`, `Cp21xxSerialDriver.java`, `FtdiSerialDriver.java`, `ProlificSerialDriver.java`**: Dedicated driver implementations for specific vendor chips (CH340, CP2102, FT232, PL2303).
- **`UsbSerialProber.java`**: **Core Utility**. Responsible for automatically matching appropriate driver programs based on USB device's VID (Vendor ID) and PID (Product ID).
- **`ProbeTable.java`**: Maintains a mapping table of VID/PID to driver class.
- **`CommonUsbSerialPort.java`**: Generic serial port implementation base class, encapsulates underlying `UsbDeviceConnection` operations.

#### **`util/` (Utility Package)**
- **`SerialInputOutputManager.java`**: **Recommended**. Provides an asynchronous I/O manager. It starts a background thread to continuously read serial port data and passes data back through `Listener` callback, greatly simplifying application layer reading logic.
- **`HexDump.java`**: Debug utility for converting binary data to hexadecimal string display.

## 6. Build Files and Configuration

The following are detailed functions and notes for three special configuration files in the `libusbserial` directory.

| File Name | Function | Special Reference | Notes |
| :--- | :--- | :--- | :--- |
| **`build.gradle`** | Module main build script | `consumerProguardFiles 'proguard-rules.pro'` | 1. Automatically packages ProGuard rules for dependents (app), preventing app obfuscation from causing library failure.<br>2. **Not enabled** `coverage.gradle` by default, only compiles as ordinary library. |
| **`coverage.gradle`** | Test coverage configuration | `apply plugin: 'jacoco'`<br>`flavorDimensions += 'device'` | 1. **Not enabled by default**: Unless `apply from: 'coverage.gradle'` is added to `build.gradle`.<br>2. **Flavor trap**: Once enabled, introduces `device` dimension (e.g., cp2102, ft232). At this point, `app` modules depending on this library **must** add `missingDimensionStrategy 'device', 'anyDevice'` in `defaultConfig`, otherwise build will fail. |
| **`proguard-rules.pro`** | ProGuard rules file | `-keep class com.ms500.usbserial.driver.* { *; }` | **Do not delete**. This library uses **reflection** mechanism (`Class.forName`) to load drivers. If driver class names are obfuscated, `UsbSerialProber` won't be able to find drivers, causing inability to recognize any device. |
