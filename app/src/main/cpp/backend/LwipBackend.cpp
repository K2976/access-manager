#include "LwipBackend.h"
#include "common/Logger.h"

namespace dev {
namespace kartik {
namespace accessmanager {
namespace backend {

LwipBackend::LwipBackend() : is_initialized(false), is_running(false) {
    LOGD("LwipBackend instantiated.");
}

LwipBackend::~LwipBackend() {
    destroy();
    LOGD("LwipBackend destroyed.");
}

bool LwipBackend::initialize() {
    if (is_initialized) return true;
    
    // Future Sprint 16: lwip_init()
    LOGI("LwipBackend initializing (scaffold).");
    is_initialized = true;
    return true;
}

bool LwipBackend::start() {
    if (!is_initialized || is_running) return false;
    
    // Future Sprint 16: start sys_arch threads
    LOGI("LwipBackend starting.");
    is_running = true;
    return true;
}

bool LwipBackend::injectUplinkPacket(const uint8_t* data, size_t length) {
    if (!is_running) return false;
    
    // Future Sprint 16: copy packet to pbuf and send to netif
    // LOGV("LwipBackend received %zu bytes of uplink traffic.", length);
    return true;
}

bool LwipBackend::stop() {
    if (!is_running) return false;
    
    LOGI("LwipBackend stopping.");
    is_running = false;
    return true;
}

void LwipBackend::destroy() {
    if (is_initialized) {
        LOGI("LwipBackend tearing down structures.");
        is_initialized = false;
    }
}

} // namespace backend
} // namespace accessmanager
} // namespace kartik
} // namespace dev
