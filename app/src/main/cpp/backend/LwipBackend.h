#pragma once

#include "NetworkBackend.h"
#include "arch/sys_arch.h"
#include "lwip/netif.h"
#include "SessionManager.h"
#include "RelayThread.h"
#include "lwip/udp.h"
#include <thread>

namespace dev {
namespace kartik {
namespace accessmanager {
namespace backend {

enum class BackendMessage {
    PACKET,
    STOP,
    POSIX_READY,
    POSIX_READY_OUT,
    POSIX_EOF
};

struct BackendEvent {
    BackendMessage type;
    uint8_t* data;
    size_t length;
    uint32_t enqueue_time_ms; // Added for latency tracking
    int fd; // POSIX file descriptor for POSIX_READY events
};

struct BackendMetrics {
    long packetsReceived = 0;
    long packetsRejected = 0;
    long pbufAllocations = 0;
    long pbufAllocationFailures = 0;
    long qDepth = 0;
    long totalMailboxLatencyNs = 0;
    long totalProcessingLatencyNs = 0;
    long latencyCount = 0;
};

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
    void workerThreadLoop();
    void notifyKotlinState(int stateCode);

    bool is_initialized;
    bool is_running;
    
    sys_mbox_t mbox;
    std::thread* worker_thread;
    
    struct netif my_netif; // Added for Sprint 17
    BackendMetrics metrics; // Added for Sprint 17
    
    void reportMetrics();
    
public:
    void flushTxQueue(Session* session);
    SessionManager session_manager;
    RelayThread relay_thread;
    struct udp_pcb* udp_pcb_listener;
};

} // namespace backend
} // namespace accessmanager
} // namespace kartik
} // namespace dev
