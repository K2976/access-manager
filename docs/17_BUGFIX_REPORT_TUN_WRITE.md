# Engineering Report: TUN Interface Downlink Bug Fix

## 1. Why the Bug Occurred
In the original implementation (Sprint 14), the native C++ relay engine had not yet been built. As a placeholder, the Kotlin VPN Service contained a stub observer for the `downlinkPackets` flow that simply logged the packet byte array size and discarded it.

When the native C++ engine was later completed, it successfully began receiving packets from the internet and pushing them into the `downlinkPackets` Kotlin flow. However, the placeholder stub in `AccessManagerVpnService` was never updated to actually write these packets back to the Android OS. 

As a result, originating applications on the device sent TCP SYN packets, the VPN forwarded them, the internet replied with SYN-ACKs, but those SYN-ACKs were silently dropped inside the VPN Service instead of being routed back to the applications. Because the applications never received the network responses, all their connections timed out, causing the entire device to lose internet access.

## 2. Why This Fixes It
The fix completes the final missing link in the packet lifecycle loop. By writing the `packet.data` byte array into a `FileOutputStream` attached to the VPN's `ParcelFileDescriptor`, the fully processed IP packets from `lwIP` are handed directly back to the Android OS network stack. The OS then delivers these packets to the originating applications (e.g., Chrome, WhatsApp), allowing the TCP handshakes to complete successfully and internet connectivity to resume.

## 3. Ownership of FileOutputStream
The `FileOutputStream` acts as the exclusive write-channel back to the Android OS. 
- It is created exactly once, immediately after the VPN interface is successfully established via `Builder().establish()`.
- It is held as a nullable class-level variable (`vpnOutputStream`) inside `AccessManagerVpnService`.
- Its lifecycle is perfectly synchronized with the VPN interface itself—it is not repeatedly instantiated or allocated per-packet.

## 4. Shutdown Ordering
During `stopVpn()`, resources must be cleaned up in a specific order to prevent blocking threads or leaking descriptors:
1. **Engines Stopped**: `relayEngine.stop()` and `connectionManager.stopCleanup()` are called first.
2. **Coroutines Cancelled**: `serviceScope?.cancel()` stops the flow collectors from attempting to process any new packets in flight.
3. **FileOutputStream Closed**: `vpnOutputStream?.close()` is invoked. This explicitly closes the underlying file descriptor.
4. **FileInputStream Unblocked**: Closing the file descriptor causes the blocking `FileInputStream.read()` loop inside `PacketReaderImpl` to throw an `IOException` (`EBADF`), which allows the background reading thread to exit cleanly.
5. **VPN Interface Closed**: Finally, `vpnInterface?.close()` is called for completeness.

## 5. Thread Safety
- The `FileOutputStream` is written to strictly from inside the `onEach` block of the `downlinkPackets` flow.
- Because this flow is collected on the `serviceScope` (which is bound to `Dispatchers.Main`), and because `tryEmit()` from the native layer serializes the packet delivery, there is no concurrent access to the `FileOutputStream` that could cause interleaved byte arrays.
- A try-catch block wraps the `write()` operation to gracefully handle the exact moment the stream is closed during shutdown, avoiding an unhandled `IOException` crash.

## 6. Expected Packet Flow After the Fix
With this fix, the packet pipeline now forms a complete, closed loop:

1. Application generates traffic -> Android OS -> `TUN Interface`
2. `PacketReaderImpl` reads raw bytes (`FileInputStream`) -> Emits `RawPacket`
3. `ConnectionResolver` parses metadata and resolves the owning `UID`
4. `DecisionEngine` evaluates the application's policy (`Allow`/`Block`)
5. If Allowed -> `NativeRelayEngine.enqueueUplink()`
6. JNI Bridge -> C++ `Actor Thread Mailbox`
7. C++ `lwIP` parses IP/TCP and terminates the connection locally
8. C++ `SessionManager` protects a POSIX Socket and calls `connect()`
9. C++ `RelayThread` forwards the application payload via `send()`
10. Remote Internet Server replies
11. C++ `RelayThread` reads the payload via `recv()`
12. C++ `lwIP` wraps the payload in an IP packet
13. JNI callback `notify_downlink_packet()` pushes the packet to Kotlin
14. `AccessManagerVpnService` receives it via `downlinkPackets.onEach`
15. `AccessManagerVpnService` writes bytes to `FileOutputStream`
16. Android OS delivers the packet to the originating Application.

**Result:** Allowed applications have full internet connectivity, blocked applications have none. The firewall is strictly enforced.
