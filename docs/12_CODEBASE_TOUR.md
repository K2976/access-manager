# Codebase Tour

A walkthrough of the AccessManager repository for new engineers.

## Root Directory
- `app/`: The main Android application module containing all Kotlin and C++ source code.
- `docs/`: The documentation directory (where this file lives).
- `gradle/`: Gradle wrapper and version catalog (`libs.versions.toml`).
- `build.gradle.kts` / `settings.gradle.kts`: Gradle build configuration files.

## `app/src/main/`
This is where the magic happens. It is split into `java/` (Kotlin code) and `cpp/` (Native code).

---

## The Kotlin Code (`app/src/main/java/dev/kartik/accessmanager/`)

### `di/` (Dependency Injection)
- **Why it exists:** Configures Hilt modules (`AppModule`, `DatabaseModule`) to wire up dependencies like the Room Database, DecisionEngine, and RelayEngine as Singletons.

### `data/` and `domain/` (Clean Architecture)
- **Why it exists:** Manages the SQLite database storing firewall rules.
- **Major Files:** `AppDatabase.kt` (Room definition), `PolicyRepositoryImpl.kt` (Database CRUD operations).

### `presentation/` (UI Layer)
- **Why it exists:** Jetpack Compose UI.
- **Major Files:** `HomeScreen.kt` (The main toggle switch and app list). `AppListViewModel.kt` (Bridges UI to the database).

### `vpn/` (The Core Engine)
- **Why it exists:** Everything related to the `VpnService` and packet manipulation.
- **Major Files:** 
  - `AccessManagerVpnService.kt`: The Android Service that creates the TUN interface.
  - `packet/ConnectionResolver.kt`: Parses L3/L4 headers and resolves UIDs.
  - `decision/DecisionEngine.kt`: The gatekeeper that drops blocked packets.
  - `relay/jni/JniNativeBridge.kt`: The Kotlin half of the JNI bridge.

---

## The Native Code (`app/src/main/cpp/`)

### `CMakeLists.txt`
- **Why it exists:** Instructs the Android NDK how to compile our C++ code and the lwIP source tree into `libaccessmanager-core.so`.

### `bridge/` (JNI Wrapper)
- **Major Files:** `bridge.cpp`. 
- **Why it exists:** The C++ half of the JNI bridge. It receives `jbyteArrays`, converts them to C++ pointers, and routes them to `LwipBackend`.

### `backend/` (The Relay Engine)
- **Why it exists:** The actual high-performance packet router.
- **Major Files:**
  - `LwipBackend.cpp`: The Actor thread that owns lwIP.
  - `RelayThread.cpp`: The POSIX socket `poll()` loop.
  - `SessionManager.cpp`: Manages the lifecycle of standard sockets.
  - `AddressTranslator.cpp`: Modifies IP headers to perform Destination NAT (so lwIP thinks it is the target server) and Source NAT (so the Android App receives the reply from the expected server IP).

### `third_party/lwip/`
- **Why it exists:** The full, unmodified source tree of the lwIP networking stack.
- **Who uses it:** Only `LwipBackend.cpp`.

### `port/`
- **Why it exists:** lwIP requires an "OS Port" to function. This directory implements `sys_arch.c`, which maps lwIP's internal OS abstractions (Mutexes, Mailboxes, Threads) to POSIX primitives (`pthread`, `sem_t`) supported by Android.
