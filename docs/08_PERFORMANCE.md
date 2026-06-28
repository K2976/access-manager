# Performance

AccessManager is designed to minimize battery drain and maximize throughput. However, bridging the JVM and native C++ environments introduces distinct performance characteristics.

## Current Bottlenecks

### JNI Byte Copies
When a packet arrives from the TUN interface, it exists as a `ByteArray` in the JVM heap.
To process it in C++:
1. `GetByteArrayElements` extracts it (potentially copying it, depending on the JVM).
2. The C++ JNI bridge performs a `memcpy` to allocate it on the C++ heap via `new uint8_t[]` to safely pass it to the Actor thread.
3. The Actor thread copies it *again* into an lwIP `pbuf` via `pbuf_take()`.

On the downlink (internet to device), the reverse happens:
1. `RelayThread` reads the socket into a C++ buffer.
2. The JNI callback allocates a new `jbyteArray` (`env->NewByteArray`).
3. The data is copied into the Java array (`env->SetByteArrayRegion`).

### Packet Allocations
For every single packet traversing the firewall, a `BackendEvent` object is allocated on the C++ heap. At high speeds (e.g., 50 Mbps = ~4000 packets per second), this results in 4000 `new`/`delete` operations per second.

### Relay Throughput
Despite the allocations and copies, modern ARM processors handle memory operations extremely quickly. The current implementation comfortably saturates standard Wi-Fi connections (upwards of 150+ Mbps) without noticeably impacting UI frame rates or system responsiveness. The POSIX `poll()` loop scales excellently up to hundreds of concurrent TCP connections.

## Future Optimization Opportunities

*(Note: These are documented for architectural awareness. They are intentionally not implemented to preserve the simplicity and stability of the current v1.0 architecture).*

1. **Direct ByteBuffers (Zero-Copy JNI):**
   Instead of `jbyteArray`, the system could allocate a large `java.nio.ByteBuffer.allocateDirect()`. This allocates memory directly in the native C++ heap. Both Kotlin and C++ can read/write to this exact memory address simultaneously. This completely eliminates the `memcpy` and `NewByteArray` JNI overhead.
2. **Event Object Pools (Ring Buffers):**
   Instead of `new BackendEvent` per packet, a pre-allocated array of 10,000 `BackendEvent` structs could be used as a circular Ring Buffer. The JNI thread claims an index, writes the data, and passes the index to the Actor thread, dropping heap fragmentation to absolute zero.
3. **Batching:**
   Currently, the C++ engine fires one JNI callback per downlink packet. Batching 10-20 packets together before firing the JNI callback would drastically reduce JVM context-switching overhead.
