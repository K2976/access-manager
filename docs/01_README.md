# AccessManager

## Project Overview
AccessManager is a high-performance, per-application outbound firewall for Android devices. Unlike traditional firewalls that require root access (iptables) or simply block domains via DNS, AccessManager captures raw IP packets at the OS level using Android's `VpnService`, inspects the originating application (`UID`), evaluates user-defined block/allow rules, and selectively routes traffic to the internet using a lightweight, native C++ networking stack (lwIP).

## Purpose
The primary purpose of AccessManager is to provide users with granular, application-level control over their device's internet connectivity without requiring device rooting or compromising device security. 

## Features
- **Per-Application Firewalling**: Block or allow outbound internet access on a per-app basis.
- **Root-less Operation**: Utilizes Android's `VpnService` to capture packets natively.
- **High Performance**: Translates and routes packets completely natively in C++ (JNI) via a custom Actor-model relay engine to minimize latency and battery drain.
- **Transparent DNS**: Respects Android's native DNS routing, ensuring compatibility and avoiding unnecessary tunneling overhead.
- **Robust Connection Handling**: Dynamically manages TCP keepalives and UDP sweeps to cleanly survive Wi-Fi to Cellular network transitions.

## Screenshots
*(Placeholder for UI screenshots: Home Screen, App List, Active VPN Notification)*

## Architecture Overview
AccessManager is conceptually divided into two halves:
1. **The Kotlin Control Plane**: Handles Android lifecycle, UI, VpnService instantiation, UID packet resolution, and policy evaluation.
2. **The C++ Data Plane (Native Relay Engine)**: Intercepts raw IP packets, handles TCP/IP translation using the lwIP network stack, creates outbound POSIX sockets, and multiplexes I/O via `poll()`.

## Technology Stack
- **Android / Kotlin**: Core application, VpnService, Flow/Coroutines, Hilt (Dependency Injection), Jetpack Compose (UI).
- **C++17 (NDK)**: Native Bridge, Actor-based threading, raw memory management.
- **lwIP**: Lightweight IP stack (v2.1.3) used to terminate raw IP packets and translate them into BSD socket payloads.
- **CMake**: Build system for the native shared library (`libaccessmanager-core.so`).

## Why a VPN-based Firewall?
Android's security model strictly sandboxes applications. Without root access, it is impossible to modify iptables to block traffic. However, Android provides the `VpnService` API, which allows an application to register a virtual network interface (TUN). All device traffic is routed into this TUN interface. By reading from the TUN, AccessManager can act as a transparent proxy, making routing decisions purely in user-space.

## Why lwIP?
The traffic entering the TUN interface consists of raw IP packets (L3). To send this data to the internet, we must extract the payload (L4) and send it over standard POSIX sockets. Reassembling TCP segments, handling sequence numbers, sliding windows, and congestion control is notoriously difficult. `lwIP` is a battle-tested, embedded TCP/IP stack that perfectly handles this state machine, allowing us to feed it raw packets and receive reconstructed application data.

## High-Level Packet Flow
1. **App** sends a network request.
2. Android routes the packet into **AccessManagerVpnService** (TUN).
3. **PacketReader** reads the raw packet.
4. **ConnectionResolver** extracts the IPs, ports, and queries the Android OS for the originating `UID`.
5. **DecisionEngine** checks the `UID` against the database.
   - If blocked, the packet is silently dropped.
   - If allowed, it is pushed to the **Native Relay Engine** (via JNI).
6. **lwIP** receives the raw packet, manages the TCP handshake, and extracts the payload.
7. **SessionManager** creates a real POSIX socket and sends the payload to the actual destination.
8. Responses flow back through the POSIX socket -> lwIP -> JNI -> TUN -> Originating App.

## Build Requirements
- Android Studio Koala (or newer)
- Android SDK 34 (Minimum SDK 29)
- Android NDK (Version 25.1.8937393 or compatible)
- CMake (Version 3.22.1)
- Gradle 8+

## Installation & Running from Android Studio
1. Clone the repository.
2. Open the project in Android Studio.
3. Ensure NDK and CMake are installed via the SDK Manager (`Tools -> SDK Manager -> SDK Tools`).
4. Click `Sync Project with Gradle Files`.
5. Select a physical device or emulator running Android 10+ (API 29+).
6. Click `Run` (Shift + F10).

## Building APK
To build a release APK from the command line:
```bash
./gradlew assembleRelease
```
The resulting APK will be located at `app/build/outputs/apk/release/app-release.apk`.

## Current Limitations
- **IPv4 Only**: While Android handles IPv6 seamlessly by degrading it to IPv4 (because we don't declare an IPv6 route), true native IPv6 parsing inside the C++ relay is not implemented to prioritize architectural simplicity.
- **Bandwidth Limits**: The JNI boundary currently copies byte arrays on every downlink packet, which may limit absolute maximum theoretical throughput (e.g., >500Mbps) compared to kernel-space routing.

## Future Roadmap
- Implement Direct `ByteBuffer` memory mapping to achieve zero-copy JNI boundaries.
- Add advanced traffic analytics and historical charting.
- Expand rule policies (e.g., block on Mobile Data, allow on Wi-Fi).
