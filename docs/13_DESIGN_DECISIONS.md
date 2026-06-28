# Design Decisions

This document captures the rationale behind major architectural choices in AccessManager.

## Why lwIP?
**Context:** We receive raw L3 packets from the Android TUN interface, but we need to send L4 payloads over standard POSIX internet sockets.
**Alternative 1 (Manual TCP):** Attempting to manually parse TCP headers, handle sequence numbers, window scaling, retransmissions, and out-of-order packets is immensely complex and prone to security vulnerabilities.
**Alternative 2 (tun2socks):** A popular tool for proxying TUN to SOCKS. However, it forces all traffic through a SOCKS5 proxy protocol, which we do not need (we want direct connections).
**Alternative 3 (gVisor/netstack):** Google's Go-based netstack is excellent but introduces the heavy overhead of the Go runtime (Garbage Collection) inside a battery-constrained Android background service.
**Decision:** `lwIP` (Lightweight IP) is written in pure C, highly battle-tested in embedded systems, and incredibly memory efficient. It perfectly fits our requirement for a fast, memory-safe, headless TCP state machine.

## Why the Actor Model?
**Context:** lwIP is designed for embedded environments. Its core is generally NOT thread-safe unless used with extreme caution via its specialized APIs, which introduce heavy locking overhead.
**Decision:** We run lwIP in "raw mode" entirely inside a single isolated C++ thread (`LwipBackend`). All interactions with lwIP from JNI or the `RelayThread` are passed via lock-free Mailboxes (`sys_mbox_post`). This eliminates mutex contention entirely on the hot packet path.

## Why C++ via JNI?
**Context:** Kotlin/Java has `SocketChannel` and NIO capabilities.
**Decision:** Processing thousands of packets per second in a JVM background service generates immense Garbage Collection (GC) pressure. GC pauses cause network latency spikes (jitter) and drain battery. By dropping down to C++, we manually manage memory pools (`pbuf`), eliminating GC overhead completely for the actual packet routing.

## Why Kotlin Flow?
**Context:** Passing asynchronous network events to the UI.
**Decision:** `StateFlow` and `SharedFlow` inherently handle Android lifecycle complexities (e.g., stopping collection when the app is in the background) much better than RxJava or raw Callbacks.

## Why Room & Hilt & Jetpack Compose?
**Decision:** Standard modern Android development practices. Room ensures compile-time SQL safety. Hilt makes injecting the complex `VpnService` dependencies trivial. Compose allows declarative UI generation for the massive list of installed applications without RecyclerView boilerplate.

## Why IPv4-First (Bypassing IPv6)?
**Context:** Supporting IPv6 requires deep structural changes to handle 128-bit addresses in hash maps, dual-stack `ip_hdr` parsing, and complex NAT66 routing.
**Decision:** Android's `VpnService` behaves deterministically based on provided routes. By explicitly *not* providing an IPv6 address to the `VpnBuilder`, Android safely translates IPv6 endpoints to IPv4 or blocks IPv6 entirely, routing everything via IPv4 through our TUN interface. The firewall achieves 100% effectiveness natively without requiring the architectural weight of a manual IPv6 stack.

## Why not transparent HTTPS Inspection (DPI)?
**Context:** Some firewalls attempt to inspect URLs to block specific websites.
**Decision:** This requires generating root Certificates, installing them in the Android system trust store (which requires user intervention or rooting), and performing computationally heavy Man-in-the-Middle (MITM) decryption on all traffic. AccessManager is strictly a Layer 3/4 firewall (IPs and Ports) mapped to Android UIDs. It respects user privacy by never looking at the L7 payload.
