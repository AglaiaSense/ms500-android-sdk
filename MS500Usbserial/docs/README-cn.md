# USB-CDC 串口通信集成指南

本文档解释如何在现有的 UVC 视频传输基础上，集成 USB-CDC (Communications Device Class) 功能，以实现**同时进行视频预览和命令/文件传输**。

## 1. 复合设备原理 (Composite Device)

你的 ESP32-P4 设备配置为 USB 复合设备，这意味着它在一个 USB 设备描述符下提供了多个功能的接口集合：

-   **Function 1 (UVC)**: 包含 Video Control Interface 和 Video Streaming Interface。用于传输实时视频流。
-   **Function 2 (CDC)**: 包含 Communication Interface 和 Data Interface。用于模拟串口进行双向数据传输（发送命令、传输文件）。

**Android 端的支持**：
Android 的 USB Host API 允许应用打开一个 `UsbDeviceConnection`，然后针对不同的 Interface 分别进行 `claimInterface()`。因此，**项目完全允许**在保持 UVC 预览的同时，通过 CDC 接口通信。

## 2. 架构设计

为了最小化对现有 `libausbc` 核心库的侵入，建议采用 **"共享连接，独立驱动"** 的策略。

-   **连接管理**: 继续使用 `libuvc` 中的 `USBMonitor` 负责 USB 权限申请、设备监听和连接打开 (`openDevice`)。
-   **UVC 路径**: `UVCCamera` 使用 `UsbControlBlock`（底层持有 `fileDescriptor`）处理视频流。
-   **CDC 路径**: 引入第三方库 `usb-serial-for-android`，复用同一个 `UsbDeviceConnection` 或原始的 `UsbDevice` 对象来驱动串口。

## 3. 实现步骤

### 3.1 引入依赖

推荐使用 `usb-serial-for-android` 库，它封装了繁琐的 CDC 协议细节。

在 `app/build.gradle` 中添加：

```gradle
dependencies {
    implementation 'com.github.mik3y:usb-serial-for-android:3.7.0'
}
```

### 3.2 识别与初始化

在 `CameraActivity` 或 `CameraFragment` 的 `IDeviceConnectCallBack` 回调中进行处理。

**关键点**：不要重新申请权限或重新打开设备，直接复用。

```kotlin
// 在 onConnectDev 回调中
override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
    // 1. 启动 UVC 预览 (现有逻辑)
    // ... startPreview ...

    // 2. 初始化 CDC 串口 (新增逻辑)
    device?.let { dev ->
        initSerialPort(dev)
    }
}

private var mSerialPort: UsbSerialPort? = null

private fun initSerialPort(device: UsbDevice) {
    // 使用 usb-serial-for-android 查找 CDC 驱动
    val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
    if (driver == null) {
        Logger.e("CDC", "No serial driver found for device")
        return
    }

    // 获取现有连接 (注意：这里需要确保 USBMonitor 暴露 connection，或者使用 USBManager 重新 open，
    // 但通常建议共享 connection 以避免冲突。如果 USBMonitor 封装太深，
    // 可以尝试使用 UsbManager 再次 openDevice，Android 允许对同一设备多次 open)
    val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
    val connection = usbManager.openDevice(device)

    if (connection == null) {
        Logger.e("CDC", "Failed to open device connection")
        return
    }

    // 获取第一个串口端口 (通常 CDC 只有一个)
    mSerialPort = driver.ports[0]

    try {
        // 打开串口
        mSerialPort?.open(connection)
        // 设置参数: 波特率 115200, 8位数据, 1位停止, 无校验
        mSerialPort?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

        // 启动读取线程
        startSerialIoManager()

        Logger.i("CDC", "Serial port opened successfully")
    } catch (e: Exception) {
        Logger.e("CDC", "Error opening serial port: ${e.message}")
    }
}
```

### 3.3 数据读写 (命令与文件)

**发送命令/文件 (Write)**:

```kotlin
fun sendCommand(command: String) {
    val data = command.toByteArray()
    mSerialPort?.write(data, 1000) // 1000ms timeout
}

fun sendFile(filePath: String) {
    // 建议在子线程执行
    thread {
        val fileData = File(filePath).readBytes()
        // 可以分包发送
        mSerialPort?.write(fileData, 5000)
    }
}
```

**接收响应 (Read)**:

使用 `SerialInputOutputManager` 监听数据：

```kotlin
private fun startSerialIoManager() {
    val listener = object : SerialInputOutputManager.Listener {
        override fun onNewData(data: ByteArray) {
            // 处理接收到的数据 (在子线程回调)
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

### 3.4 资源释放

在 `onDisconnectDec` 或 `onDestroy` 中关闭串口，**注意不要关闭 connection** (如果它是被 `USBMonitor` 共享管理的)，或者如果由自己再次 `open` 的则需要关闭。

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

## 4. 常见问题与解决方案

1.  **权限冲突**：
    `USBMonitor` 会请求一次 USB 权限。获取权限后，该权限对整个 `UsbDevice` 有效。因此，你不需要为 CDC 单独申请权限，直接使用即可。

2.  **接口抢占 (Claim Interface)**：
    -   `libuvc` (Native层) 会 claim Video Interface (通常是 Interface 0 和 1)。
    -   `usb-serial-for-android` 会尝试 claim CDC Data Interface (通常是 Interface 2 和 3)。
    -   **互不冲突**：只要 Interface ID 不重叠，两者可以完美共存。

3.  **设备过滤**：
    检查 `res/xml/default_device_filter.xml`。确保过滤规则不会因为 `class` 或 `subclass` 不匹配而过滤掉你的复合设备。建议仅使用 `vendor-id` 和 `product-id` 进行过滤，不要加 `class` 限制。

4.  **带宽竞争**：
    UVC 视频流占用大量 USB 带宽（Isochronous 或 Bulk 传输）。CDC 通常使用 Bulk 传输。在高分辨率视频预览时，CDC 的文件传输速度可能会受到影响，建议在传输大文件时适当降低视频帧率或暂停预览。

## 5. 项目文件详解

### 5.1 源码目录 (`src/main/java/com/ms500/usbserial`)

该目录包含了 USB 串口通信的核心实现类。

#### **`driver/` (驱动实现包)**
- **`UsbSerialPort.java`**: 定义了串口通信的通用接口（`read`, `write`, `open`, `close`, `setParameters` 等）。
- **`UsbSerialDriver.java`**: 定义了驱动程序的接口，一个驱动可以包含多个 Port。
- **`CdcAcmSerialDriver.java`**: **核心驱动**。实现了 CDC-ACM (USB Communications Device Class) 协议。这是 ESP32、Arduino 等设备最常用的标准虚拟串口协议。
- **`Ch34xSerialDriver.java`, `Cp21xxSerialDriver.java`, `FtdiSerialDriver.java`, `ProlificSerialDriver.java`**: 针对特定厂商芯片（CH340, CP2102, FT232, PL2303）的专用驱动实现。
- **`UsbSerialProber.java`**: **核心工具**。负责根据 USB 设备的 VID (Vendor ID) 和 PID (Product ID) 自动匹配合适的驱动程序。
- **`ProbeTable.java`**: 维护了一张 VID/PID 到 驱动类 的映射表。
- **`CommonUsbSerialPort.java`**: 通用的串口实现基类，封装了底层的 `UsbDeviceConnection` 操作。

#### **`util/` (工具包)**
- **`SerialInputOutputManager.java`**: **推荐使用**。提供了一个异步 I/O 管理器。它启动一个后台线程持续读取串口数据，并通过 `Listener` 回调将数据传回，极大简化了应用层的读取逻辑。
- **`HexDump.java`**: 调试工具，用于将二进制数据转为十六进制字符串显示。

## 6. 构建文件与配置说明

以下是 `libusbserial` 目录下三个特殊配置文件的详细作用与注意事项。

| 文件名 | 作用 | 特殊引用 | 注意事项 |
| :--- | :--- | :--- | :--- |
| **`build.gradle`** | 模块主构建脚本 | `consumerProguardFiles 'proguard-rules.pro'` | 1. 自动将混淆规则打包给依赖方(app)，防止 app 混淆导致本库失效。<br>2. 默认**未启用** `coverage.gradle`，仅作为普通库编译。 |
| **`coverage.gradle`** | 测试覆盖率配置 | `apply plugin: 'jacoco'`<br>`flavorDimensions += 'device'` | 1. **默认不生效**：除非在 `build.gradle` 中添加 `apply from: 'coverage.gradle'`。<br>2. **Flavor 陷阱**：一旦启用，引入了 `device` 维度 (如 cp2102, ft232)。此时依赖该库的 `app` 模块**必须**在 `defaultConfig` 中添加 `missingDimensionStrategy 'device', 'anyDevice'`，否则构建报错。 |
| **`proguard-rules.pro`** | 混淆规则文件 | `-keep class com.ms500.usbserial.driver.* { *; }` | **严禁删除**。本库使用**反射**机制 (`Class.forName`) 来加载驱动。如果驱动类名被混淆，`UsbSerialProber` 将无法找到驱动，导致无法识别任何设备。 |
