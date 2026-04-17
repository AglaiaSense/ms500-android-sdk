# libuvc Component Description

## Introduction
`libuvc` is the underlying core module of this project, mainly responsible for communication between Android devices and USB cameras (UVC devices). It is based on `libusb` or Linux native USB file system implementation, providing efficient video stream data acquisition capabilities for upper-layer Kotlin/Java code through JNI (Java Native Interface).

## Core Functions
1.  **USB Device Enumeration and Permission Management**:
    - Identify connected USB devices, especially UVC standard-compliant cameras (such as ESP32 simulated UVC devices).
    - Handle Android USB Host permission requests.
2.  **UVC Protocol Control (Control Transfer)**:
    - Send and receive UVC standard control commands, such as setting exposure, white balance, gain, etc.
    - Get device descriptors, configuration descriptors, and other metadata.
3.  **Video Stream Transmission (Isochronous/Bulk Transfer)**:
    - Establish video stream transmission pipeline.
    - Receive raw data streams from cameras (usually MJPEG or YUV format).
    - Parse UVC payload headers, extract valid video frame data.
4.  **JNI Bridging**:
    - Encapsulate C/C++ interfaces, expose `startPreview`, `stopPreview`, `setParams`, and other methods to Java layer.
    - Callback video frame data received from Native layer to Java layer for rendering or processing.

## Technical Details
- **Underlying Dependencies**: Depends on NDK for compilation, usually links `libusb` library or directly operates `/dev/bus/usb` nodes.
- **Data Formats**: Focuses on supporting MJPEG (Motion JPEG) and YUV (such as YUY2, NV21) format parsing.
- **Performance Optimization**: Optimized for bandwidth-constrained devices like ESP32, handles packet loss and frame synchronization issues.

## Directory Structure (Example)
- `src/main/jni/`: C/C++ source code, containing UVC protocol implementation.
- `src/main/java/`: JNI interface definition classes.
- `CMakeLists.txt` or `Android.mk`: Native build scripts.

## Related Modules
- **Upper Layer**: Provides data to `libausbc` module for encapsulation and UI display.
- **Lower Layer**: Directly interacts with Android Linux kernel USB drivers.

## Build Configuration Files

This module's build configuration differs significantly from other modules because it uses traditional `ndk-build` approach.

### 1. `build.gradle`

This is the module's main build script.

-   **Special NDK Build Task**:
    This module **does not use** the common `externalNativeBuild { cmake }` approach, but defines a custom Gradle task `task ndkBuild(type: Exec)`.
    -   **Principle**: It directly calls the `ndk-build` command from NDK toolchain ( `ndk-build.cmd` on Windows).
    -   **Path Lookup**: Script contains logic (`getNdkBuildPath`) that automatically finds NDK installation path from system environment variable `NDK_HOME` or project's `local.properties` file.
    -   **Compile Command**: `ndk-build -j8 -C src/main`. This means it reads `src/main/jni/Android.mk` and `Application.mk` for compilation.

-   **Lint Options**:
    -   `abortOnError false`: Continue building even if Lint check finds errors. This is useful when dealing with projects containing old code or specific warnings.

### 2. `proguard-rules.pro` (if exists)

This is the code obfuscation rules file.

-   **Function**: Specifies which classes or methods should not be obfuscated when packaging Release versions.
-   **JNI Notes**: For classes containing JNI methods (such as Native interface classes in this module), **must retain** their class names and method names, otherwise C++ layer cannot find corresponding Java methods, causing runtime crashes (`UnsatisfiedLinkError`).
