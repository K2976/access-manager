#pragma once

#include <cstdint>
#include <cstddef>

namespace dev {
namespace kartik {
namespace accessmanager {
namespace backend {

/**
 * Interface representing a generic userspace network backend.
 * This completely insulates the JNI bridge from any lwIP-specific data structures
 * or compile-time dependencies.
 */
class NetworkBackend {
public:
    virtual ~NetworkBackend() = default;

    /**
     * Bootstraps the backend memory and internal states.
     * @return true on success.
     */
    virtual bool initialize() = 0;

    /**
     * Commands the backend to begin processing traffic and timers.
     */
    virtual bool start() = 0;

    /**
     * Injects an uplink packet from the Kotlin TUN interface into the backend.
     * Memory ownership of `data` remains with the caller. The backend MUST copy
     * the payload if it requires asynchronous processing.
     */
    virtual bool injectUplinkPacket(const uint8_t* data, size_t length) = 0;

    /**
     * Commands the backend to stop accepting packets and tear down connections.
     */
    virtual bool stop() = 0;

    /**
     * Frees all associated memory for the backend.
     */
    virtual void destroy() = 0;
};

} // namespace backend
} // namespace accessmanager
} // namespace kartik
} // namespace dev
