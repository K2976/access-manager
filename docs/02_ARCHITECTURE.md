# AccessManager Architecture

## Overview
AccessManager is fundamentally divided into two deeply isolated planes:
1. **The Kotlin Control Plane (Android/Java)**: Manages Android lifecycle, database storage, UI, policy evaluations, and the raw TUN interface.
2. **The C++ Data Plane (Native NDK)**: A highly optimized, actor-based networking engine that terminates IP packets, multiplexes I/O, and interacts with the OS via POSIX sockets.

These two planes communicate exclusively over a strict JNI bridge boundary, passing byte arrays and lifecycle events without sharing object state.

```mermaid
graph TD
    subgraph Android OS
        App[Originating App] -->|IP Traffic| VpnService[TUN Interface]
        VpnService -->|Raw Read| KotlinReader[PacketReader]
    end
    
    subgraph Kotlin Control Plane
        KotlinReader --> Resolver[ConnectionResolver]
        Resolver --> Decision[DecisionEngine]
        Decision -->|Allow| NativeRelayEngine[JNI Bridge]
        Decision -->|Block| Drop[Silently Drop]
    end
    
    subgraph C++ Data Plane
        NativeRelayEngine -->|mbox_post| ActorThread[LwipBackend Actor]
        ActorThread <-->|L3 packets| lwIP[lwIP Stack]
        lwIP <-->|L4 payloads| SessionManager[SessionManager]
        SessionManager <-->|poll()| RelayThread[Relay Thread multiplexer]
    end
    
    subgraph Internet
        RelayThread <-->|POSIX Sockets| Remote[Remote Servers]
    end
```

## Layered Architecture & Package Structure

### 1. Kotlin Layer (`dev.kartik.accessmanager`)
- `vpn/service/`: Contains `AccessManagerVpnService`, responsible for configuring the Android `VpnService.Builder` and creating the TUN interface.
- `vpn/packet/`: Contains `PacketReader` (reads TUN bytes) and `ConnectionResolver` (extracts L3/L4 headers and calls `ConnectivityManager.getConnectionOwnerUid()` to identify the app).
- `vpn/decision/`: Contains `DecisionEngine`, which looks up policies to decide `Allow` or `Block`.
- `vpn/relay/`: Contains the `RelayEngine` abstraction and `JniNativeBridge`, which acts as the boundary into C++.
- `data/`, `domain/`, `presentation/`: Standard Clean Architecture layers managing the Room database and Jetpack Compose UI.

### 2. JNI Layer (`bridge/`)
The JNI layer (`bridge.cpp`) maps Java method signatures to C++ function calls. It abstracts away `JNIEnv` pointer management, thread attachment, and byte array extractions. It guarantees that memory allocated in C++ stays in C++, and memory allocated in Kotlin stays in Kotlin.

### 3. Native Layer (`backend/`)
- `LwipBackend`: The orchestrator of the native plane. It runs an isolated **Worker Thread (Actor)** that exclusively owns the lwIP runtime.
- `RelayThread`: An isolated thread running a POSIX `poll()` loop to monitor all active outgoing sockets for readable/writable events.
- `SessionManager`: Maps unique packet signatures (IP:Port + Protocol) to active POSIX file descriptors.
- `AddressTranslator`: Performs checksum-safe NAT (Network Address Translation).

### 4. lwIP Integration (`third_party/lwip/`)
Instead of using lwIP's thread-safe socket API (which introduces locking overhead), AccessManager uses lwIP in **RAW Mode**. `LwipBackend` acts as a mock network interface (`netif`). Packets from the TUN are injected directly into lwIP's core via `my_netif_input`. When lwIP completes TCP handshakes and extracts payloads, it calls callbacks (`on_tcp_recv`), which we immediately push to `SessionManager` to send to the real internet.

## Dependency Direction
`UI -> Domain -> Data` (Standard Clean Architecture)
`VpnService -> Packet Pipeline -> NativeRelayEngine -> JNI -> C++ Backend`
The C++ backend knows absolutely nothing about Android, UIDs, or policies. It is a pure, dumb, fast packet-forwarding engine. The Kotlin layer handles all the intelligence.

## Separation of Concerns
- **Kotlin** handles *Policy* (Who is this? Are they allowed?).
- **C++** handles *Mechanism* (How do I convert this raw packet into a socket stream?).
This strict separation guarantees that the complex, memory-unsafe networking code never accidentally interferes with Android's garbage-collected, stateful UI environment.
