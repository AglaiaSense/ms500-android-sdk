# libausbc Module Description

`libausbc` (Android USB Camera) is the core library of this project, providing high-level encapsulation for USB camera connection, preview, capture, recording, and parameter adjustment. It shields the complexity of the underlying UVC protocol and provides a simple API to the application layer.

## 1. Core Classes and Interfaces

### 1.1 `MultiCameraClient.kt`
- **Function**: Manages multi-channel USB camera connection and lifecycle.
- **Core Logic**:
  - **Device Monitoring**: Uses `USBMonitor` to listen for USB device plug/unplug events (`onAttach`, `onDetach`) and permission request results (`onConnect`, `onDisconnect`).
  - **Permission Management**: Encapsulates `requestPermission` and `hasPermission` for requesting USB device permissions.
  - **ICamera Abstract Class**: Defines common interfaces for camera operations (open, close, capture, record, etc.).
  - **WatchDog**: `CameraUVC` internally implements a watchdog mechanism to monitor video stream interruptions.

### 1.2 `CameraUVC.kt` (in `camera` package)
- **Function**: UVC camera implementation class, inheriting from `MultiCameraClient.ICamera`.
- **Core Logic**:
  - **Underlying Bridge**: Holds `UVCCamera` object, calls JNI layer for actual device control.
  - **Preview Control**: `startPreview` / `stopPreview`. Supports SurfaceView, TextureView, and other rendering targets.
  - **Parameter Adjustment**: Provides set/get methods for brightness, contrast, saturation, sharpness, gain, white balance, zoom, etc.
  - **Data Callback**: Receives underlying NV21 video frames through `IFrameCallback`, distributes to preview callbacks (`mPreviewDataCbList`), encoder (`putVideoData`), and capture queue.

### 1.3 `CameraFragment.kt` / `CameraActivity.kt` (in `base` package)
- **Function**: Base classes encapsulating camera logic, recommended for application layer inheritance.
- **Core Logic**:
  - **Automatic Management**: Automatically registers/unregisters USB listeners in lifecycle, handles permission requests.
  - **Device Recognition**: Automatically discovers and attempts to connect USB cameras meeting criteria.
  - **UI Integration**: Abstracts `getCameraView()` and `getCameraViewContainer()`, allowing subclasses to provide rendering views and containers.
  - **API Exposure**: Exposes `CameraUVC` functions (capture, record, parameter adjustment) for direct subclass calls.

### 1.4 `ICameraStateCallBack.kt` (in `callback` package)
- **Function**: Camera state callback interface.
- **States**:
  - `OPENED`: Camera successfully opened, preview started.
  - `CLOSED`: Camera closed.
  - `ERROR`: Error occurred (e.g., permission denied, device disconnected, preview failed).

## 2. Functional Module Structure

The following is categorized by package name, detailing the function of each file:

### 2.1 `base` (Base Components Package)
| File Name | Function Description |
| :--- | :--- |
| `BaseApplication.kt` | Global Application base class, responsible for initializing CrashUtils, Logger, ToastUtils, and other utilities. |
| `CameraActivity.kt` | **Core Base Class**. Implements `ICameraStateCallBack`, provides complete single-camera lifecycle management, connection monitoring, permission requests, and API encapsulation (capture/record/effects). |
| `CameraFragment.kt` | Similar to `CameraActivity`, but based on Fragment implementation for easy embedding into existing UIs. |
| `MultiCameraActivity.kt` | Activity base class supporting simultaneous preview of multiple USB cameras. |

### 2.2 `camera` (Camera Control Package)
| File Name | Function Description |
| :--- | :--- |
| `CameraUVC.kt` | **UVC Camera Implementation Class**. Inherits from `MultiCameraClient.ICamera`, encapsulates underlying `UVCCamera` operations, main entry point for application layer calls. |
| `bean/CameraRequest.kt` | **Parameter Configuration Class**. Uses Builder pattern to configure preview resolution, rendering mode (OpenGL), audio source, preview format (MJPEG/YUYV), etc. |
| `bean/PreviewSize.kt` | Simple width and height data class. |
| `CameraUvcStrategy.kt` | (Deprecated) Old UVC camera control strategy, now replaced by `CameraUVC`. |

### 2.3 `encode` (Audio/Video Encoding Package)
| File Name | Function Description |
| :--- | :--- |
| `H264EncodeProcessor.kt` | Video encoder. Uses `MediaCodec` to encode YUV data or Surface textures into H.264 streams. |
| `AACEncodeProcessor.kt` | Audio encoder. Responsible for capturing PCM audio (AudioRecord), encoding to AAC (MediaCodec) or MP3 (Lame), and supporting simultaneous recording and playback. |
| `muxer/Mp4Muxer.kt` | Media muxer. Muxes H.264 and AAC streams into MP4 files. |

### 2.4 `render` (Rendering Engine Package)
| File Name | Function Description |
| :--- | :--- |
| `RenderManager.kt` | **Rendering Scheduling Core**. Manages GL thread (`gl_render`) and encoding thread (`gl_render_codec`), responsible for texture flow, FBO effect processing, and screen display. |
| `effect/` | Contains various filter effect implementations (e.g., `EffectBlackWhite`, `EffectSoul`, `EffectZoom`). |
| `internal/` | Internal renderer implementations. `CameraRender` (draw camera texture), `ScreenRender` (screen display), `EncodeRender` (off-screen encoding). |

### 2.5 `widget` (UI Controls Package)
| File Name | Function Description |
| :--- | :--- |
| `AspectRatioTextureView.kt` | **Adaptive TextureView**. Automatically adjusts View size based on video stream aspect ratio to prevent image stretching. |
| `AspectRatioSurfaceView.kt` | Same as above, based on SurfaceView implementation. |

### 2.6 `utils` (Utilities Package)
| File Name | Function Description |
| :--- | :--- |
| `CameraUtils.kt` | Camera utility class. Provides YUV format conversion (Image to NV21), USB device filtering rules, device type identification (whether it's a UVC camera), etc. |
| `Logger.kt` | Logging utility, supports console output and file saving. |

## 3. AndroidManifest.xml

- **Permission Declarations**:
  - `android.permission.CAMERA`: Although it's a USB camera, this permission is usually declared to comply with camera application standards.
- **Feature Declarations**:
  - `android.hardware.usb.host`: **Core declaration**, indicating the App requires USB Host mode support, which is a prerequisite for connecting USB devices.

## 4. Build Configuration Files

### 4.1 `build.gradle`

This is the module's main build script, configured with **Kotlin Android** plugin.

-   **Plugin Reference**:
    -   `apply plugin: 'kotlin-android'`: Enables Kotlin language support.
-   **Kotlin Options**:
    -   `kotlinOptions { jvmTarget = '1.8' }`: Specifies JVM bytecode version 1.8 for compatibility.
-   **Dependencies**:
    -   `api project(path: ':libnative')`: Depends on Native processing library. Using `api` means projects referencing `libausbc` can also directly use `libnative` features (transitive dependency).
    -   `implementation project(path: ':libuvc')`: Depends on underlying UVC communication library. Using `implementation` means these details are hidden from upper layers.
-   **ProGuard Transfer**:
    -   `consumerProguardFiles "consumer-rules.pro"`: This configuration is critical. It declares that this library's ProGuard rules (`consumer-rules.pro`) are automatically passed to projects referencing it. This way, when the App packages with ProGuard, it automatically applies the retention rules needed by this library without manual copying.

### 4.2 `consumer-rules.pro` / `proguard-rules.pro`

-   **`consumer-rules.pro`**: ProGuard rules for consumers (Apps). Usually contains `-keep` rules to prevent reflection calls or Native methods from being obfuscated.
-   **`proguard-rules.pro`**: ProGuard rules used when building Release versions of this library itself.
