# libausbc 模块说明

`libausbc` (Android USB Camera) 是本项目的核心库，提供了 USB 摄像头的连接、预览、拍照、录像以及参数调节等功能的高层封装。它屏蔽了底层 UVC 协议的复杂性，向应用层提供简洁的 API。

## 1. 核心类与接口

### 1.1 `MultiCameraClient.kt`
- **功能**: 管理多路 USB 摄像头的连接与生命周期。
- **核心逻辑**:
  - **设备监听**: 使用 `USBMonitor` 监听 USB 设备的插拔 (`onAttach`, `onDetach`) 和权限申请结果 (`onConnect`, `onDisconnect`)。
  - **权限管理**: 封装了 `requestPermission` 和 `hasPermission`，用于申请 USB 设备权限。
  - **ICamera 抽象类**: 定义了相机操作的通用接口（打开、关闭、拍照、录像等）。
  - **WatchDog**: `CameraUVC` 内部实现了看门狗机制，监测视频流是否中断。

### 1.2 `CameraUVC.kt` (位于 `camera` 包)
- **功能**: UVC 相机的具体实现类，继承自 `MultiCameraClient.ICamera`。
- **核心逻辑**:
  - **底层桥接**: 持有 `UVCCamera` 对象，调用 JNI 层进行实际的设备控制。
  - **预览控制**: `startPreview` / `stopPreview`。支持 SurfaceView, TextureView 等多种渲染目标。
  - **参数调节**: 提供亮度、对比度、饱和度、清晰度、增益、白平衡、变焦等参数的 set/get 方法。
  - **数据回调**: 通过 `IFrameCallback` 接收底层 NV21 视频帧，分发给预览回调 (`mPreviewDataCbList`)、编码器 (`putVideoData`) 和拍照队列。

### 1.3 `CameraFragment.kt` / `CameraActivity.kt` (位于 `base` 包)
- **功能**: 封装了相机逻辑的基类，建议应用层继承使用。
- **核心逻辑**:
  - **自动管理**: 在生命周期中自动注册/注销 USB 监听，处理权限申请。
  - **设备识别**: 自动发现并尝试连接符合条件的 USB 摄像头。
  - **UI 集成**: 抽象了 `getCameraView()` 和 `getCameraViewContainer()`，让子类提供渲染视图和容器。
  - **API 暴露**: 将 `CameraUVC` 的功能（拍照、录像、参数调节）暴露给子类直接调用。

### 1.4 `ICameraStateCallBack.kt` (位于 `callback` 包)
- **功能**: 相机状态回调接口。
- **状态**:
  - `OPENED`: 相机成功打开，预览开始。
  - `CLOSED`: 相机关闭。
  - `ERROR`: 发生错误（如权限被拒、设备断开、预览失败）。

## 2. 功能模块结构详解

以下按包名分类，详细说明各文件的作用：

### 2.1 `base` (基础组件包)
| 文件名 | 功能描述 |
| :--- | :--- |
| `BaseApplication.kt` | 全局 Application 基类，负责初始化 CrashUtils, Logger, ToastUtils 等工具。 |
| `CameraActivity.kt` | **核心基类**。实现 `ICameraStateCallBack`，提供完整的单路相机生命周期管理、连接监听、权限申请及 API 封装 (拍照/录像/特效)。 |
| `CameraFragment.kt` | 类似于 `CameraActivity`，但基于 Fragment 实现，便于嵌入已有 UI。 |
| `MultiCameraActivity.kt` | 支持多路 USB 摄像头同时预览的 Activity 基类。 |

### 2.2 `camera` (相机控制包)
| 文件名 | 功能描述 |
| :--- | :--- |
| `CameraUVC.kt` | **UVC 相机实现类**。继承自 `MultiCameraClient.ICamera`，封装了底层的 `UVCCamera` 操作，是应用层调用的主要入口。 |
| `bean/CameraRequest.kt` | **参数配置类**。使用 Builder 模式配置预览分辨率、渲染模式(OpenGL)、音频源、预览格式(MJPEG/YUYV)等。 |
| `bean/PreviewSize.kt` | 简单的宽高数据类。 |
| `CameraUvcStrategy.kt` | (已弃用) 旧版的 UVC 相机控制策略，现已由 `CameraUVC` 替代。 |

### 2.3 `encode` (音视频编码包)
| 文件名 | 功能描述 |
| :--- | :--- |
| `H264EncodeProcessor.kt` | 视频编码器。使用 `MediaCodec` 将 YUV 数据或 Surface 纹理编码为 H.264 码流。 |
| `AACEncodeProcessor.kt` | 音频编码器。负责采集 PCM 音频 (AudioRecord)，编码为 AAC (MediaCodec) 或 MP3 (Lame)，并支持边录边播。 |
| `muxer/Mp4Muxer.kt` | 媒体合成器。将 H.264 和 AAC 码流混合封装成 MP4 文件。 |

### 2.4 `render` (渲染引擎包)
| 文件名 | 功能描述 |
| :--- | :--- |
| `RenderManager.kt` | **渲染调度核心**。管理 GL 线程 (`gl_render`) 和 编码线程 (`gl_render_codec`)，负责纹理流转、FBO 特效处理及画面上屏。 |
| `effect/` | 包含各种滤镜特效实现 (如 `EffectBlackWhite`, `EffectSoul`, `EffectZoom`)。 |
| `internal/` | 内部渲染器实现。`CameraRender`(绘制相机纹理), `ScreenRender`(上屏绘制), `EncodeRender`(离屏编码绘制)。 |

### 2.5 `widget` (UI 控件包)
| 文件名 | 功能描述 |
| :--- | :--- |
| `AspectRatioTextureView.kt` | **自适应 TextureView**。根据视频流的宽高比自动调整自身 View 的尺寸，防止画面拉伸变形。 |
| `AspectRatioSurfaceView.kt` | 同上，基于 SurfaceView 实现。 |

### 2.6 `utils` (工具包)
| 文件名 | 功能描述 |
| :--- | :--- |
| `CameraUtils.kt` | 相机工具类。提供 YUV 格式转换 (Image转NV21)、USB 设备过滤规则判断、设备类型识别(是否为 UVC 相机)等。 |
| `Logger.kt` | 日志工具，支持控制台输出及文件保存。 |

## 3. AndroidManifest.xml

- **权限声明**:
  - `android.permission.CAMERA`: 虽然是 USB 相机，但通常也声明此权限以符合相机应用规范。
- **特性声明**:
  - `android.hardware.usb.host`: **核心声明**，表明 App 需要 USB Host 模式支持，这是连接 USB 设备的前提。

## 4. 构建配置文件详解

### 4.1 `build.gradle`

这是模块的主构建脚本，采用 **Kotlin Android** 插件配置。

-   **插件引用**:
    -   `apply plugin: 'kotlin-android'`: 启用 Kotlin 语言支持。
-   **Kotlin 选项**:
    -   `kotlinOptions { jvmTarget = '1.8' }`: 指定编译生成的 JVM 字节码版本为 1.8，确保兼容性。
-   **依赖关系**:
    -   `api project(path: ':libnative')`: 依赖 Native 处理库。使用 `api` 意味着引用 `libausbc` 的项目也能直接使用 `libnative` 的功能（依赖传递）。
    -   `implementation project(path: ':libuvc')`: 依赖底层 UVC 通信库。使用 `implementation` 表示这些细节对上层隐藏。
-   **混淆传递**:
    -   `consumerProguardFiles "consumer-rules.pro"`: 此配置非常关键。它声明本库的混淆规则（`consumer-rules.pro`）会自动传递给引用它的 App。这样 App 在打包混淆时，会自动应用本库需要的保留规则，无需开发者手动复制粘贴。

### 4.2 `consumer-rules.pro` / `proguard-rules.pro`

-   **`consumer-rules.pro`**: 供消费者（App）使用的混淆规则。通常包含 `-keep` 规则，防止反射调用或 Native 方法被混淆。
-   **`proguard-rules.pro`**: 本库自身构建 Release 版本时使用的混淆规则。
