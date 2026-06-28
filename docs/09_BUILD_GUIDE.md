# Build Guide

AccessManager requires compiling both Kotlin (JVM) code and C++ (Native) code.

## Prerequisites
- **Android Studio:** Koala (2024.1.1) or newer.
- **Java Development Kit (JDK):** JDK 17 (embedded in modern Android Studio).
- **Android SDK:** API Level 34.
- **Android NDK:** Version `25.1.8937393`.
- **CMake:** Version `3.22.1`.

*Note: Ensure NDK and CMake are explicitly downloaded via the Android Studio SDK Manager (`Tools > SDK Manager > SDK Tools`).*

## Android Studio Setup
1. Clone the repository to your local machine.
2. Open Android Studio -> `File` -> `Open...` -> Select the `access-manager` directory.
3. Android Studio will automatically invoke Gradle to sync dependencies.
4. The Gradle sync will trigger CMake to configure the C++ project located in `app/src/main/cpp/CMakeLists.txt`.

## How Native Libraries are Built
The `build.gradle.kts` file links the native code via the `externalNativeBuild` block:
```kotlin
externalNativeBuild {
    cmake {
        path("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
    }
}
```
When you build the project, Gradle invokes CMake. CMake compiles `bridge.cpp`, `LwipBackend.cpp`, `SessionManager.cpp`, etc., along with the `lwIP` source tree, and links them into a shared library named `libaccessmanager-core.so`. 
This `.so` file is automatically packaged into the final APK in the `lib/` directory for each supported ABI (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`).

## Building APKs
To build a Debug APK (suitable for testing and debugging):
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

To build a Release APK:
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release-unsigned.apk`

## Release APK Signing
To generate a signed Release APK, you must configure a signing keystore.
1. Generate a keystore:
   ```bash
   keytool -genkey -v -keystore release.keystore -alias accessmanager -keyalg RSA -keysize 2048 -validity 10000
   ```
2. In `app/build.gradle.kts`, configure the `signingConfigs` block (or create a `keystore.properties` file locally).
3. Run `./gradlew assembleRelease` to produce a signed, production-ready APK.

## Troubleshooting Common Build Problems

**Error: `NDK not configured` or `No version of NDK matched the requested version`**
*Fix:* Open SDK Manager, check "Show Package Details" under SDK Tools, and explicitly install NDK version `25.1.8937393`.

**Error: `CMake Error: CMake was unable to find a build program corresponding to "Ninja"`**
*Fix:* Ensure CMake `3.22.1` is installed via SDK Manager.

**Error: `UnsatisfiedLinkError` at Runtime**
*Fix:* This means the C++ code failed to compile or link, but the APK was built anyway (or you're running on an unsupported architecture). Run `Build > Clean Project` and `Build > Rebuild Project`, carefully checking the Build Output tab for C++ compiler errors.
