# libnative Module Description

The `libnative` module primarily provides high-performance audio/video data processing capabilities through JNI (Java Native Interface) calls to underlying C/C++ code libraries. Main functions include YUV image format conversion and MP3 audio encoding.

## 1. Core Classes (Java/Kotlin)

### 1.1 `YUVUtils.kt`
- **Function**: Provides efficient YUV format image processing tools.
- **Native Methods**:
  - `yuv420spToNv21`: YUV420SP to NV21 conversion.
  - `nv21ToYuv420sp`: NV21 to YUV420SP conversion.
  - `nv21ToYuv420p`: NV21 to YUV420P conversion.
  - `nativeRotateNV21`: Rotate NV21 data (90°, 180°, 270°).
  - `nv21ToYuv420spWithMirror` / `nv21ToYuv420pWithMirror`: Conversion with mirror processing.

### 1.2 `LameMp3.kt`
- **Function**: Uses LAME library to encode PCM audio data to MP3 format.
- **Native Methods**:
  - `lameInit`: Initialize LAME encoder, configure sample rate, channels, bit rate, and quality.
  - `lameEncode`: Encode left and right channel PCM data to MP3 data.
  - `lameFlush`: Flush buffer to ensure all data is written to file.
  - `lameClose`: Release LAME encoder resources.

## 2. Native Implementation (C/C++)

### 2.1 `nativelib.cpp`
- **Function**: JNI entry file, responsible for dynamic method registration.
- **Core Logic**:
  - `JNI_OnLoad`: Called when loading, finds Java classes (`com/ms500/natives/YUVUtils` and `com/ms500/natives/LameMp3`) and registers Native method tables (`g_yuv_methods`, `g_lame_methods`).
  - `JNI_OnUnload`: Cleans up JVM references when unloading.

### 2.2 `module/yuv`
- **yuv.cpp**: Implements specific YUV data conversion and rotation algorithms. Directly operates on byte arrays, performance far exceeds Java implementation.

### 2.3 `module/mp3`
- **mp3.cpp**: Encapsulates LAME library call logic.
- **lame/**: Contains complete LAME source code (v3.100) for MP3 encoding.

### 2.4 `CMakeLists.txt`
- **Build Configuration**:
  - Declares library name as `nativelib`.
  - Includes `module/mp3/lame` directory source code.
  - Links Android system log library (`log`).
  - Compiles to generate shared library (`.so`).

## 3. AndroidManifest.xml

As a library module, its manifest file is usually empty or only contains necessary package name declarations. Currently mainly serves to define package name `com.ms500.natives`.

## 4. Precompiled Library (AAR)

`libnative/aar/libnative-3.2.9.aar` is the precompiled output of this module (Android Archive).

- **Purpose**: Encapsulates compiled Java classes (`classes.jar`) and native dynamic libraries (`.so`), as well as resource files and manifest files.
- **Included Architectures**: Usually contains `.so` files for mainstream architectures like `arm64-v8a`, `armeabi-v7a`.
- **Usage Scenarios**: When the project is referenced as a dependency, or for releasing SDK versions without source code disclosure. Allows application layer to directly call native features without recompiling C++ source code.

## 5. Build Configuration Files

### 5.1 `build.gradle`

This is the module's main build script, configured with Native compilation parameters.

-   **Native Build Tool**:
    -   `externalNativeBuild { cmake { ... } }`: Specifies using **CMake** as build system, instead of `ndk-build`.
    -   `path "src/main/cpp/CMakeLists.txt"`: Specifies CMake build script location.
-   **ABI Filtering**:
    -   `ndk { abiFilters 'armeabi-v7a', 'arm64-v8a', "x86", "x86_64" }`: Explicitly specifies CPU architectures for dynamic library generation. Only generates `.so` files for these four architectures to reduce APK size and ensure compatibility.
-   **ProGuard Transfer**:
    -   `consumerProguardFiles "consumer-rules.pro"`: Similarly, passes ProGuard rules to upper-level App.

### 5.2 `proguard-rules.pro`

-   **Native Method Retention**: For `libnative`, the most important ProGuard rule is to retain all `external` (Native) method Java class names and method names. Because JNI calls depend on method signature matching. If these classes are obfuscated, the Native layer won't be able to find Java methods.

## 6. JNI Implementation Principle (C++ to Kotlin)

This project uses **JNI dynamic registration** to map C++ functions to Kotlin-callable `external` methods. Compared to static registration (based on function naming rules), dynamic registration is more flexible and efficient.

**Implementation Process**:

1.  **Define Mapping Table (`JNINativeMethod`)**:
    Define structure array in `nativelib.cpp`, binding Kotlin method names, signatures with C++ function pointers.
    ```cpp
    static JNINativeMethod g_yuv_methods[] = {
        // Java method name, signature, C++ function pointer
        {"yuv420spToNv21", "([BII[B)V", (void *) yuv420spToNv21},
        {"nv21ToYuv420sp", "([BII[B)V", (void *) nv21ToYuv420sp},
        // ...
    };
    ```

2.  **Load Entry (`JNI_OnLoad`)**:
    When Java layer calls `System.loadLibrary("nativelib")`, the system automatically callbacks C++ layer's `JNI_OnLoad` function.

3.  **Execute Registration (`RegisterNatives`)**:
    In `JNI_OnLoad`, find corresponding Java class (e.g., `com/ms500/natives/YUVUtils`) through `env->FindClass`, then call `env->RegisterNatives` to complete method registration.

Through this approach, `external fun yuv420spToNv21(...)` in Kotlin code can directly call the underlying C++ `yuv420spToNv21` function.
