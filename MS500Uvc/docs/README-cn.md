# libuvc 组件说明

## 简介
`libuvc` 是本项目的底层核心模块，主要负责 Android 设备与 USB 摄像头（UVC 设备）之间的通信。它基于 `libusb` 或 Linux 原生 USB 文件系统实现，通过 JNI (Java Native Interface) 为上层 Kotlin/Java 代码提供高效的视频流数据采集能力。

## 核心功能
1.  **USB 设备枚举与权限管理**:
    - 识别连接的 USB 设备，特别是符合 UVC 标准的摄像头（如 ESP32 模拟的 UVC 设备）。
    - 处理 Android USB Host 权限请求。
2.  **UVC 协议控制 (Control Transfer)**:
    - 发送和接收 UVC 标准控制指令，如设置曝光、白平衡、增益等。
    - 获取设备描述符、配置描述符等元数据。
3.  **视频流传输 (Isochronous/Bulk Transfer)**:
    - 建立视频流传输管道。
    - 接收来自摄像头的原始数据流（通常为 MJPEG 或 YUV 格式）。
    - 解析 UVC 负载头 (Payload Header)，提取有效的视频帧数据。
4.  **JNI 桥接**:
    - 封装 C/C++ 接口，向 Java 层暴露 `startPreview`、`stopPreview`、`setParams` 等方法。
    - 将 Native 层接收到的视频帧数据回调给 Java 层进行渲染或处理。

## 技术细节
- **底层依赖**: 依赖 NDK 进行编译，通常链接 `libusb` 库或直接操作 `/dev/bus/usb` 节点。
- **数据格式**: 重点支持 MJPEG (Motion JPEG) 和 YUV (如 YUY2, NV21) 格式的解析。
- **性能优化**: 针对 ESP32 等带宽受限设备进行了优化，处理丢包和帧同步问题。

## 目录结构 (示例)
- `src/main/jni/`: C/C++ 源码，包含 UVC 协议实现。
- `src/main/java/`: JNI 接口定义类。
- `CMakeLists.txt` 或 `Android.mk`: Native 构建脚本。

## 关联模块
- **上层**: 提供数据给 `libausbc` 模块进行封装和 UI 展示。
- **下层**: 直接与 Android Linux 内核 USB 驱动交互。

## 构建配置文件详解

本模块的构建配置与其他模块有显著不同，因为它使用传统的 `ndk-build` 方式。

### 1. `build.gradle`

这是模块的主构建脚本。

-   **特殊的 NDK 构建任务**:
    本模块**不使用**常见的 `externalNativeBuild { cmake }` 方式，而是定义了自定义的 Gradle 任务 `task ndkBuild(type: Exec)`。
    -   **原理**: 它直接调用 NDK 工具链中的 `ndk-build` 命令（Windows下为 `ndk-build.cmd`）。
    -   **路径查找**: 脚本中包含一段逻辑 (`getNdkBuildPath`)，会自动从系统环境变量 `NDK_HOME` 或项目的 `local.properties` 文件中查找 NDK 的安装路径。
    -   **编译命令**: `ndk-build -j8 -C src/main`。这意味着它会读取 `src/main/jni/Android.mk` 和 `Application.mk` 进行编译。

-   **Lint 选项**:
    -   `abortOnError false`: 即使 Lint 检查发现错误，也继续构建。这在处理包含旧代码或特定警告的项目时很有用。

### 2. `proguard-rules.pro` (如果存在)

这是代码混淆规则文件。

-   **作用**: 指定在打包 Release 版本时，哪些类或方法不应被混淆。
-   **JNI 注意事项**: 对于包含 JNI 方法的类（如本模块中的 Native 接口类），**必须保留**其类名和方法名，否则 C++ 层无法找到对应的 Java 方法，导致运行时崩溃 (`UnsatisfiedLinkError`)。
