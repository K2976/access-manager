#pragma once

#include "NetworkBackend.h"

namespace dev {
namespace kartik {
namespace accessmanager {
namespace backend {

/**
 * The concrete implementation of NetworkBackend that encapsulates lwIP.
 * Currently serves as a structural scaffold for Sprint 15.
 */
class LwipBackend : public NetworkBackend {
public:
    LwipBackend();
    ~LwipBackend() override;

    bool initialize() override;
    bool start() override;
    bool injectUplinkPacket(const uint8_t* data, size_t length) override;
    bool stop() override;
    void destroy() override;

private:
    bool is_initialized;
    bool is_running;
};

} // namespace backend
} // namespace accessmanager
} // namespace kartik
} // namespace dev
