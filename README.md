# MS500 Android USB SDK

This repository is the minimal JitPack publishing source for the MS500 Android USB SDK.
It contains only the library modules required to build and publish the SDK artifacts.

## Repository Layout

```text
ms500-android-sdk/
├── MS500UsbSdk/
├── MS500Ausbc/
├── MS500Uvc/
├── MS500Natives/
├── MS500Usbserial/
├── gradle/
├── gradlew
├── gradlew.bat
├── build.gradle
├── gradle.properties
├── jitpack.yml
├── settings.gradle
└── README.md
```

## Keep

- `MS500UsbSdk/`
- `MS500Ausbc/`
- `MS500Uvc/`
- `MS500Natives/`
- `MS500Usbserial/`
- `gradle/`
- `gradlew`
- `gradlew.bat`
- `build.gradle`
- `settings.gradle`
- `gradle.properties`
- `jitpack.yml`
- `.gitignore`
- `README.md`

## Remove

- `app/`
- `repo/`
- `docs/`
- `.idea/`
- `.gradle/`
- `build/`
- `local.properties`
- any generated module build output

## JitPack Usage

Add JitPack in the consumer project's repositories:

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

Consume the SDK module:

```gradle
dependencies {
    implementation 'com.github.AglaiaSense.ms500-android-sdk:MS500UsbSdk:v1.0.2'
}
```
