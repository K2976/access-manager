# API Reference

This document outlines the major public interfaces and classes across the AccessManager architecture.

---

## Kotlin Domain Models

### `AccessState`
- **Purpose:** Represents the firewall policy for a specific application. Enum: `ALLOWED`, `BLOCKED`, `UNMETERED_ONLY`.
- **Owner:** Domain Layer (`dev.kartik.accessmanager.domain.model`)

### `AppPolicy`
- **Purpose:** Maps a `packageName` to an `AccessState`.
- **Owner:** Domain Layer (`dev.kartik.accessmanager.domain.model`)

---

## Kotlin Interfaces

### `DecisionEngine`
- **Purpose:** Evaluates incoming packets against the policy database.
- **Owner:** VPN Layer (`dev.kartik.accessmanager.vpn.decision`)
- **Callers:** `AccessManagerVpnService`
- **Future Extension:** Can be expanded to factor in time-of-day rules, active network type (Wi-Fi vs Mobile), or IP-based rules.

### `ConnectionResolver`
- **Purpose:** Parses raw IP packets to extract source/dest IPs and ports, then uses `ConnectivityManager` to resolve the UID.
- **Owner:** VPN Layer (`dev.kartik.accessmanager.vpn.packet`)

### `RelayEngine`
- **Purpose:** The high-level abstraction for the native C++ networking stack. Provides methods to `start()`, `stop()`, and `enqueueUplink()`, and exposes a `downlinkPackets` Flow.
- **Owner:** VPN Layer (`dev.kartik.accessmanager.vpn.relay`)
- **Dependencies:** `NativeBridge`

### `NativeBridge`
- **Purpose:** The strict JNI boundary interface. Only passes primitives and byte arrays.
- **Owner:** VPN Layer (`dev.kartik.accessmanager.vpn.relay.jni`)
- **Lifecycle:** Created on VPN Start, Destroyed on VPN Stop.

---

## Native (C++) Interfaces

### `NetworkBackend`
- **Purpose:** Pure virtual interface representing the networking stack. Allows mocking out lwIP for testing.
- **Owner:** Native Layer (`app/src/main/cpp/backend/NetworkBackend.h`)

### `LwipBackend`
- **Purpose:** The concrete implementation of `NetworkBackend`. Wraps the lwIP stack in a thread-safe Actor model.
- **Owner:** Native Layer (`LwipBackend.cpp`)
- **Lifecycle:** Instantiated via `JNI_OnLoad` or `nativeInit`. Lives until `nativeDestroy`.
- **Callers:** JNI Bridge.

### `SessionManager`
- **Purpose:** Manages the POSIX socket lifecycle mapping TUN traffic to physical sockets.
- **Owner:** Native Layer (`SessionManager.cpp`)
- **Dependencies:** Standard POSIX socket API (`<sys/socket.h>`), JNI socket protector.

---

## JNI Methods (C++ -> Kotlin)

### `jniProtectSocket(int fd)`
- **Purpose:** Asks the Android OS to protect a file descriptor from VPN routing rules to prevent infinite loops.
- **Callers:** `SessionManager::getOrCreateSession`.

### `onDownlinkPacketReceived(byte[] data, int length)`
- **Purpose:** Delivers a fully formed IP packet synthesized by lwIP back to Kotlin to be written to the TUN interface.
- **Callers:** `LwipBackend::my_ip_output` -> `notify_downlink_packet`.
