# Security Architecture

## Why VpnService?
Android strictly enforces sandboxing; one application cannot view or modify the network traffic of another application. The only API provided by the Android OS to intercept device-wide traffic without rooting the device is `VpnService`. By registering as a VPN, the OS kernel dynamically updates iptables to route all outgoing IP packets into a virtual TUN interface, which AccessManager reads from.

## Socket Protection & Routing Loops
A massive vulnerability in custom VPN implementations is the **Routing Loop**. 
1. AccessManager reads a packet from the TUN.
2. It opens a POSIX socket to send the payload to the internet.
3. Because the POSIX socket belongs to the Android device, the OS routes the POSIX socket's traffic back into the TUN interface!
4. AccessManager reads its own packet, sends it again, creating an infinite loop that crashes the device in milliseconds.

To prevent this, AccessManager uses the `VpnService.protect(fd)` API. 
When the C++ `SessionManager` creates a new POSIX socket, it instantly triggers a JNI callback (`jniProtectSocket`) passing the raw file descriptor. Kotlin invokes `vpnService.protect(fd)`. The Linux kernel binds this specific socket directly to the physical underlying network interface (Wi-Fi/Cellular), bypassing the TUN routing rules entirely.

## Memory Safety
C++ is inherently memory-unsafe. AccessManager mitigates leaks and segfaults via strict ownership:
- **No shared pointers:** The architecture does not rely on complex `std::shared_ptr` graphs. 
- **Actor Isolation:** `lwIP` is notoriously difficult to use safely in multi-threaded environments. We eliminate this entirely by isolating lwIP inside a single Actor thread. Memory is allocated and freed on that single thread.
- **Resource Reaping:** `SessionManager` guarantees that if a session dies, its `fd` is closed exactly once, and its lwIP `pcb` is explicitly aborted.

## JNI Safety
JNI transitions can easily crash the JVM if abused.
- `NativeBridge` catches all `LinkageError` and `RuntimeException` exceptions. If the `.so` file is corrupted or incompatible, the UI degrades gracefully rather than force-closing.
- Byte arrays passed from Kotlin to C++ are immediately copied and released (`ReleaseByteArrayElements(..., JNI_ABORT)`). We never hold dangling pointers to Java heap memory.

## Thread Safety
- Mutexes are completely avoided in the hot path. 
- `RelayThread` and `ActorThread` communicate entirely via `sys_mbox_t` (message queues) and a `pipe()` (control pipe).
- This lock-free message passing prevents deadlocks and starvation.

## Security Assumptions
1. **OS Integrity:** We assume the Android OS correctly isolates memory between UIDs and that `getConnectionOwnerUid` cannot be spoofed by a malicious local app.
2. **DNS Leakage:** We explicitly allow Android to route DNS outside the TUN. We assume the system DNS resolver is secure.

## Current Limitations
- **No TLS Interception:** AccessManager operates at Layer 3/4. It observes IPs and Ports, but it does NOT decrypt HTTPS traffic (Layer 7). It cannot inspect URLs or payloads.
- **IPv6 Bypassing:** AccessManager relies on Android's OS-level IPv6 translation/blocking. It does not actively parse IPv6 headers in C++.
