#include "LwipBackend.h"
#include "lwip/init.h"
#include "lwip/tcpip.h"
#include "lwip/netif.h"
#include "lwip/ip4.h"
#include "lwip/ip6.h"
#include "lwip/timeouts.h"
#include "arch/sys_arch.h"
#include "common/Logger.h"
#include <chrono>

extern void notify_native_state(int state);
extern void notify_native_error(int code, const char* msg);
extern void notify_native_metrics(long pkts_rx, long pkts_rej, long pbuf_allocs, long pbuf_fails, long q_depth, long avg_mbox_lat, long avg_proc_lat);

namespace dev {
namespace kartik {
namespace accessmanager {
namespace backend {

// Dummy output functions for Sprint 17 (no actual forwarding)
static err_t my_ip4_output(struct netif *netif, struct pbuf *p, const ip4_addr_t *ipaddr) {
    return ERR_OK;
}

static err_t my_ip6_output(struct netif *netif, struct pbuf *p, const ip6_addr_t *ipaddr) {
    return ERR_OK;
}

static err_t my_netif_init(struct netif *netif) {
    netif->name[0] = 't';
    netif->name[1] = 'n';
    netif->output = my_ip4_output;
    netif->output_ip6 = my_ip6_output;
    netif->mtu = 1500;
    netif->flags = NETIF_FLAG_LINK_UP | NETIF_FLAG_UP;
    return ERR_OK;
}

LwipBackend::LwipBackend() : is_initialized(false), is_running(false), mbox(SYS_MBOX_NULL), worker_thread(nullptr) {
    LOGD("LwipBackend instantiated.");
    notify_native_state(1); // Loading
}

LwipBackend::~LwipBackend() {
    destroy();
    LOGD("LwipBackend destroyed.");
}

bool LwipBackend::initialize() {
    if (is_initialized) return true;
    
    LOGI("LwipBackend initializing lwIP runtime.");
    
    // Create the actor mailbox
    if (sys_mbox_new(&mbox, 1024) != ERR_OK) {
        LOGE("Failed to create mailbox.");
        notify_native_error(1, "Mailbox creation failed");
        return false;
    }
    
    lwip_init();
    
    // Initialize TUN netif
    ip4_addr_t ipaddr, netmask, gw;
    IP4_ADDR(&gw, 10, 0, 0, 1);
    IP4_ADDR(&ipaddr, 10, 0, 0, 2);
    IP4_ADDR(&netmask, 255, 255, 255, 0);

    netif_add(&my_netif, &ipaddr, &netmask, &gw, nullptr, my_netif_init, ip4_input);
    netif_set_default(&my_netif);
    netif_set_up(&my_netif);
    netif_set_link_up(&my_netif);
    
    is_initialized = true;
    notify_native_state(2); // Initialized
    return true;
}

bool LwipBackend::start() {
    if (!is_initialized || is_running) return false;
    
    notify_native_state(3); // Starting
    LOGI("LwipBackend starting worker thread.");
    is_running = true;
    
    worker_thread = new std::thread(&LwipBackend::workerThreadLoop, this);
    
    return true;
}

void LwipBackend::reportMetrics() {
    if (metrics.packetsReceived % 100 == 0) {
        long avg_mbox = metrics.latencyCount > 0 ? metrics.totalMailboxLatencyNs / metrics.latencyCount : 0;
        long avg_proc = metrics.latencyCount > 0 ? metrics.totalProcessingLatencyNs / metrics.latencyCount : 0;
        notify_native_metrics(
            metrics.packetsReceived, metrics.packetsRejected,
            metrics.pbufAllocations, metrics.pbufAllocationFailures,
            metrics.qDepth, avg_mbox, avg_proc
        );
        metrics.totalMailboxLatencyNs = 0;
        metrics.totalProcessingLatencyNs = 0;
        metrics.latencyCount = 0;
    }
}

void LwipBackend::workerThreadLoop() {
    LOGI("LwipBackend worker thread entered.");
    notify_native_state(4); // Running
    
    while (is_running) {
        void* msg_ptr = nullptr;
        // Fetch blocks until a message arrives or a timeout (100ms for timers)
        u32_t time = sys_arch_mbox_fetch(&mbox, &msg_ptr, 100); 
        
        sys_check_timeouts(); // Run lwIP internal timers
        
        if (msg_ptr != nullptr) {
            auto pop_time = std::chrono::steady_clock::now();
            BackendEvent* event = static_cast<BackendEvent*>(msg_ptr);
            
            if (event->type == BackendMessage::STOP) {
                LOGI("Worker thread received STOP message.");
                delete event;
                break;
            } else if (event->type == BackendMessage::PACKET) {
                metrics.packetsReceived++;
                
                // Track latency
                uint32_t now_ms = sys_now();
                long mbox_latency_ns = (now_ms >= event->enqueue_time_ms) ? (now_ms - event->enqueue_time_ms) * 1000000 : 0;
                
                // Process packet
                struct pbuf* p = pbuf_alloc(PBUF_RAW, event->length, PBUF_POOL);
                if (p != nullptr) {
                    metrics.pbufAllocations++;
                    if (pbuf_take(p, event->data, event->length) == ERR_OK) {
                        uint8_t ip_version = (event->data[0] >> 4);
                        if (ip_version == 4) {
                            my_netif.input(p, &my_netif); // Calls ip4_input
                        } else if (ip_version == 6) {
                            // IPv6 (Requires setting netif->input to ip6_input, 
                            // but Sprint 17 says feed it, we can just call ip6_input)
                            ip6_input(p, &my_netif);
                        } else {
                            metrics.packetsRejected++;
                            pbuf_free(p);
                        }
                    } else {
                        metrics.packetsRejected++;
                        pbuf_free(p);
                    }
                } else {
                    metrics.pbufAllocationFailures++;
                }
                
                delete[] event->data;
                
                auto end_time = std::chrono::steady_clock::now();
                long proc_latency_ns = std::chrono::duration_cast<std::chrono::nanoseconds>(end_time - pop_time).count();
                
                metrics.totalMailboxLatencyNs += mbox_latency_ns;
                metrics.totalProcessingLatencyNs += proc_latency_ns;
                metrics.latencyCount++;
                
                reportMetrics();
            }
            delete event;
        }
    }
    
    LOGI("LwipBackend worker thread exiting.");
}

bool LwipBackend::injectUplinkPacket(const uint8_t* data, size_t length) {
    if (!is_running || !mbox) return false;
    
    uint8_t* copied_data = new uint8_t[length];
    memcpy(copied_data, data, length);
    
    BackendEvent* event = new BackendEvent{BackendMessage::PACKET, copied_data, length, sys_now()};
    if (sys_mbox_trypost(&mbox, event) != ERR_OK) {
        delete[] copied_data;
        delete event;
        return false;
    }
    return true;
}

bool LwipBackend::stop() {
    if (!is_running) return false;
    
    LOGI("LwipBackend stopping.");
    notify_native_state(5); // Stopping
    is_running = false;
    
    if (mbox) {
        BackendEvent* event = new BackendEvent{BackendMessage::STOP, nullptr, 0, sys_now()};
        sys_mbox_post(&mbox, event);
    }
    
    if (worker_thread && worker_thread->joinable()) {
        worker_thread->join();
        delete worker_thread;
        worker_thread = nullptr;
    }
    
    notify_native_state(6); // Stopped
    return true;
}

void LwipBackend::destroy() {
    if (is_initialized) {
        LOGI("LwipBackend tearing down structures.");
        if (mbox) {
            sys_mbox_free(&mbox);
            mbox = SYS_MBOX_NULL;
        }
        is_initialized = false;
    }
}

} // namespace backend
} // namespace accessmanager
} // namespace kartik
} // namespace dev
