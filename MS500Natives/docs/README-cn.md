# libnative 模块说明

`libnative` 模块主要提供高性能的音视频数据处理能力，通过 JNI (Java Native Interface) 调用底层的 C/C++ 代码库。主要功能包括 YUV 图像格式转换和 MP3 音频编码。

## 1. 核心类 (Java/Kotlin)

### 1.1 `YUVUtils.kt`
- **功能**: 提供 YUV 格式图像的高效处理工具。
- **Native 方法**:
  - `yuv420spToNv21`: YUV420SP 转 NV21。
  - `nv21ToYuv420sp`: NV21 转 YUV420SP。
  - `nv21ToYuv420p`: NV21 转 YUV420P。
  - `nativeRotateNV21`: 对 NV21 数据进行旋转操作 (90°, 180°, 270°)。
  - `nv21ToYuv420spWithMirror` / `nv21ToYuv420pWithMirror`: 带镜像处理的转换。

### 1.2 `LameMp3.kt`
- **功能**: 使用 LAME 库将 PCM 音频数据编码为 MP3 格式。
- **Native 方法**:
  - `lameInit`: 初始化 LAME 编码器，配置采样率、声道数、比特率和质量。
  - `lameEncode`: 将左右声道的 PCM 数据编码为 MP3 数据。
  - `lameFlush`: 刷新缓冲区，确保所有数据写入文件。
  - `lameClose`: 释放 LAME 编码器资源。

## 2. Native 实现 (C/C++)

### 2.1 `nativelib.cpp`
- **功能**: JNI 的入口文件，负责方法的动态注册。
- **核心逻辑**:
  - `JNI_OnLoad`: 加载时调用，查找 Java 类 (`com/ms500/natives/YUVUtils` 和 `com/ms500/natives/LameMp3`) 并注册 Native 方法表 (`g_yuv_methods`, `g_lame_methods`)。
  - `JNI_OnUnload`: 卸载时清理 JVM 引用。

### 2.2 `module/yuv`
- **yuv.cpp**: 实现了具体的 YUV 数据转换和旋转算法。直接操作字节数组，性能远高于 Java 实现。

### 2.3 `module/mp3`
- **mp3.cpp**: 封装了 LAME 库的调用逻辑。
- **lame/**: 包含完整的 LAME 源码 (v3.100)，用于 MP3 编码。

### 2.4 `CMakeLists.txt`
- **构建配置**:
  - 声明库名称为 `nativelib`。
  - 包含 `module/mp3/lame` 目录源码。
  - 链接 Android 系统日志库 (`log`)。
  - 编译生成共享库 (`.so`)。

## 3. AndroidManifest.xml

该模块作为一个库模块，其清单文件通常为空，或者仅包含必要的包名声明。目前主要作用是定义包名 `com.ms500.natives`。

## 4. 预编译库 (AAR)

`libnative/aar/libnative-3.2.9.aar` 是该模块的预编译产物 (Android Archive)。

- **用途**: 封装了编译后的 Java 类 (`classes.jar`) 和原生动态库 (`.so`)，以及资源文件和清单文件。
- **包含架构**: 通常包含 `arm64-v8a`, `armeabi-v7a` 等主流架构的 `.so` 文件。
- **使用场景**: 项目作为依赖被引用时，或者用于发布不公开源码的 SDK 版本。它允许应用层直接调用 native 功能而无需重复编译 C++ 源码。

## 5. 构建配置文件详解

### 5.1 `build.gradle`

这是模块的主构建脚本，配置了 Native 编译参数。

-   **Native 构建工具**:
    -   `externalNativeBuild { cmake { ... } }`: 指定使用 **CMake** 作为构建系统，而不是 `ndk-build`。
    -   `path "src/main/cpp/CMakeLists.txt"`: 指定 CMake 构建脚本的位置。
-   **ABI 过滤**:
    -   `ndk { abiFilters 'armeabi-v7a', 'arm64-v8a', "x86", "x86_64" }`: 明确指定需要生成的 CPU 架构动态库。只生成这四种架构的 `.so` 文件，以减小 APK 体积并确保兼容性。
-   **混淆传递**:
    -   `consumerProguardFiles "consumer-rules.pro"`: 同样，将混淆规则传递给上层 App。

### 5.2 `proguard-rules.pro`

-   **Native 方法保留**: 对于 `libnative`，最重要的混淆规则是保留所有 `external` (Native) 方法的 Java 类名和方法名。因为 JNI 调用依赖于方法签名的匹配。如果这些类被混淆，Native 层将无法找到 Java 方法。

## 6. JNI 实现原理 (C++ 到 Kotlin)

本项目使用 **JNI 动态注册** 的方式，将 C++ 函数映射为 Kotlin 可调用的 `external` 方法。相比静态注册（基于函数命名规则），动态注册更灵活、效率更高。

**实现流程**：

1.  **定义映射表 (`JNINativeMethod`)**:
    在 `nativelib.cpp` 中定义结构体数组，将 Kotlin 方法名、签名与 C++ 函数指针绑定。
    ```cpp
    static JNINativeMethod g_yuv_methods[] = {
        // Java方法名, 签名, C++函数指针
        {"yuv420spToNv21", "([BII[B)V", (void *) yuv420spToNv21},
        {"nv21ToYuv420sp", "([BII[B)V", (void *) nv21ToYuv420sp},
        // ...
    };
    ```

2.  **加载入口 (`JNI_OnLoad`)**:
    当 Java 层调用 `System.loadLibrary("nativelib")` 时，系统自动回调 C++ 层的 `JNI_OnLoad` 函数。

3.  **执行注册 (`RegisterNatives`)**:
    在 `JNI_OnLoad` 中，通过 `env->FindClass` 找到对应的 Java 类（如 `com/ms500/natives/YUVUtils`），然后调用 `env->RegisterNatives` 完成方法注册。

通过这种方式，Kotlin 代码中的 `external fun yuv420spToNv21(...)` 就能直接调用到底层 C++ 的 `yuv420spToNv21` 函数。
