# Firewall Engine

The Firewall Engine resides entirely within the Kotlin Control Plane. It ensures that blocked applications never reach the Native Relay Engine.

## Core Components

### PolicyRepository
The Single Source of Truth for firewall rules. Powered by a Room SQLite database, it stores which applications (identified by package name) are allowed or blocked.

### DecisionEngine
A stateless evaluator that takes `ConnectionInfo` (UID, Package Name, Protocol) and queries the `PolicyRepository` to return a definitive `AccessState` (`Allow` or `Block`).

### UID Resolution
When a packet is read from the TUN interface, it only contains IP addresses and ports.
1. `ConnectionResolver` parses the packet to find the Source IP/Port and Destination IP/Port.
2. It calls Android's `ConnectivityManager.getConnectionOwnerUid()`.
3. The OS kernel looks up its internal socket tables and returns the UID (User ID) of the application that created that socket.

### Package Resolution
Android UIDs can map to multiple package names (if apps share a UID). `UidPackageResolver` uses the Android `PackageManager` to resolve the UID back to the human-readable package name (e.g., `com.android.chrome`).

## Rule Evaluation
When the `DecisionEngine` evaluates a packet:
1. If the specific package has a `BLOCKED` policy -> Return `Block`.
2. If the package has an `ALLOWED` policy -> Return `Allow`.
3. If no policy exists (e.g., a newly installed app) -> Default to `Allow` (or Block, depending on user settings).

## The Security Guarantee
**Why blocked packets never reach sockets:**
The `DecisionEngine` evaluation happens *before* the packet is handed to the `NativeRelayEngine`. If the decision is `Block`, the packet byte array is simply dropped on the floor in Kotlin.
Because the packet never enters JNI, lwIP never sees a SYN packet, the `SessionManager` never creates a POSIX socket, and the OS never establishes an outbound connection. The originating application's socket will simply timeout waiting for a SYN-ACK, effectively severing it from the internet.

**Why allowed packets are relayed:**
Allowed packets cross the JNI bridge. Because the VPN Service captures ALL device traffic, the only way for the traffic to escape the device is through the `SessionManager`'s sockets, which are explicitly `protect()`ed using the `VpnService` API to bypass the TUN routing rules and route directly over the physical network interface.
