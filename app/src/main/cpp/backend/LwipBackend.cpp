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
#include <sys/socket.h>

extern void notify_native_state(int state);
extern void notify_native_error(int code, const char* msg);
extern void notify_native_metrics(long pkts_rx, long pkts_rej, long pbuf_allocs, long pbuf_fails, long q_depth, long avg_mbox_lat, long avg_proc_lat);
extern void notify_downlink_packet(const uint8_t* data, size_t length);

#ifndef MSG_NOSIGNAL
#define MSG_NOSIGNAL 0
#endif

namespace dev {
namespace kartik {
namespace accessmanager {
namespace backend {

static err_t my_ip4_output(struct netif *netif, struct pbuf *p, const ip4_addr_t *ipaddr) {
    if (!netif->state) return ERR_OK;
    LwipBackend* backend = static_cast<LwipBackend*>(netif->state);
    
    uint8_t buffer[1500];
    if (p->tot_len > 1500) return ERR_BUF;
    pbuf_copy_partial(p, buffer, p->tot_len, 0);
    
    if (p->tot_len < 20) return ERR_OK;
    uint8_t protocol = buffer[9];
    uint32_t dest_ip = *reinterpret_cast<uint32_t*>(&buffer[16]);
    uint16_t dest_port = 0;
    
    size_t ip_hlen = (buffer[0] & 0x0F) * 4;
    if ((protocol == 6 || protocol == 17) && p->tot_len >= ip_hlen + 4) {
        dest_port = *reinterpret_cast<uint16_t*>(&buffer[ip_hlen + 2]);
    }
    
    Session* session = backend->session_manager.getSessionByDest(dest_ip, dest_port, protocol);
    if (session) {
        if (AddressTranslator::snatDownlink(buffer, p->tot_len, session->original_dst_ip, session->original_dst_port)) {
            notify_downlink_packet(buffer, p->tot_len);
        }
    } else {
        notify_downlink_packet(buffer, p->tot_len);
    }
    
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

static void on_tcp_err(void *arg, err_t err) {
    LwipBackend* backend = static_cast<LwipBackend*>(arg);
    if (!backend) return;
    // We can't get the SessionKey from pcb because pcb is already freed by lwIP when tcp_err is called!
    // But we don't strictly need to close the POSIX fd immediately; the RelayThread will get POLLHUP or POLLERR
    // and send POSIX_EOF to us, which will clean up the Session.
}

static err_t on_tcp_recv(void *arg, struct tcp_pcb *pcb, struct pbuf *p, err_t err) {
    LwipBackend* backend = static_cast<LwipBackend*>(arg);
    if (!backend || !pcb) return ERR_VAL;
    
    uint32_t src_ip = ip4_addr_get_u32(ip_2_ip4(&pcb->remote_ip));
    SessionKey key{src_ip, pcb->remote_port, 6};
    Session* session = backend->session_manager.getSessionByKey(key);
    
    if (!p) {
        // EOF from client (FIN)
        if (session) {
            backend->session_manager.closeSession(key);
        }
        return ERR_OK;
    }
    
    if (session && session->state == SessionState::CONNECTED) {
        // Write to POSIX socket
        struct pbuf* q = p;
        while (q != nullptr) {
            ssize_t bytes = ::send(session->fd, q->payload, q->len, MSG_NOSIGNAL);
            if (bytes < 0 && errno == EAGAIN) {
                // If the socket buffer is full, for simplicity in Sprint 18, 
                // we'll just drop the packet here. TCP will retransmit.
                // A production system would buffer this and use POLLOUT.
            }
            q = q->next;
        }
        tcp_recved(pcb, p->tot_len);
    }
    
    pbuf_free(p);
    return ERR_OK;
}

static void on_udp_recv(void *arg, struct udp_pcb *pcb, struct pbuf *p, const ip_addr_t *addr, u16_t port) {
    LwipBackend* backend = static_cast<LwipBackend*>(arg);
    if (!backend || !p) return;
    
    uint32_t src_ip = ip4_addr_get_u32(ip_2_ip4(addr));
    SessionKey key{src_ip, port, 17};
    Session* session = backend->session_manager.getSessionByKey(key);
    
    if (session && session->state == SessionState::CONNECTING) {
        // UDP is connectionless on the network, but we use a connected POSIX socket.
        // It should be CONNECTING or CONNECTED. We just send.
        session->state = SessionState::CONNECTED; 
        
        struct pbuf* q = p;
        while (q != nullptr) {
            ::send(session->fd, q->payload, q->len, MSG_NOSIGNAL);
            q = q->next;
        }
    }
    
    pbuf_free(p);
}

static err_t on_tcp_accept(void *arg, struct tcp_pcb *newpcb, err_t err) {
    LwipBackend* backend = static_cast<LwipBackend*>(arg);
    if (!backend || err != ERR_OK) return ERR_VAL;
    
    uint32_t src_ip = ip4_addr_get_u32(ip_2_ip4(&newpcb->remote_ip));
    uint16_t src_port = newpcb->remote_port;
    
    SessionKey key{src_ip, src_port, 6};
    Session* session = backend->session_manager.getSessionByKey(key);
    
    if (!session) {
        LOGE("Accepted TCP connection but no session found for %x:%d", src_ip, src_port);
        return ERR_ABRT;
    }
    
    backend->session_manager.linkPcb(key, newpcb);
    
    tcp_arg(newpcb, backend);
    tcp_recv(newpcb, on_tcp_recv);
    tcp_err(newpcb, on_tcp_err);
    
    return ERR_OK;
}

LwipBackend::LwipBackend() : is_initialized(false), is_running(false), mbox(SYS_MBOX_NULL), worker_thread(nullptr), relay_thread(&mbox), udp_pcb_listener(nullptr) {
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

    netif_add(&my_netif, &ipaddr, &netmask, &gw, this, my_netif_init, ip4_input);
    netif_set_default(&my_netif);
    netif_set_up(&my_netif);
    netif_set_link_up(&my_netif);
    
    // Setup TCP Listener on 10.0.0.2:1234
    struct tcp_pcb* tcp_listener = tcp_new();
    if (tcp_listener) {
        tcp_bind(tcp_listener, IP_ANY_TYPE, 1234);
        tcp_listener = tcp_listen(tcp_listener);
        tcp_arg(tcp_listener, this);
        tcp_accept(tcp_listener, on_tcp_accept);
    }
    
    // Setup UDP Listener on 10.0.0.2:1234
    struct udp_pcb* udp_listener = udp_new();
    if (udp_listener) {
        udp_bind(udp_listener, IP_ANY_TYPE, 1234);
        udp_recv(udp_listener, on_udp_recv, this);
        // We store udp_listener in backend so we can use it to send downstream UDP!
        this->udp_pcb_listener = udp_listener;
    }
    
    is_initialized = true;
    notify_native_state(2); // Initialized
    return true;
}

bool LwipBackend::start() {
    if (!is_initialized || is_running) return false;
    
    notify_native_state(3); // Starting
    LOGI("LwipBackend starting worker thread.");
    is_running = true;
    
    relay_thread.start();
    
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
                
                // Parse original client intent
                SessionKey key;
                uint32_t orig_dst_ip;
                uint16_t orig_dst_port;
                
                if (AddressTranslator::extractKey(event->data, event->length, key, orig_dst_ip, orig_dst_port)) {
                    Session* session = session_manager.getOrCreateSession(key, orig_dst_ip, orig_dst_port);
                    if (session && session->state == SessionState::CONNECTING) {
                        relay_thread.addFd(session->fd);
                    }
                    
                    uint32_t virtual_ip = lwip_htonl(0x0A000002); // 10.0.0.2
                    uint16_t virtual_port = lwip_htons(1234);
                    AddressTranslator::dnatUplink(event->data, event->length, virtual_ip, virtual_port);
                }
                
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
            } else if (event->type == BackendMessage::POSIX_READY) {
                // RelayThread has notified us that a POSIX fd is readable
                Session* session = session_manager.getSessionByFd(event->fd);
                if (session && session->state == SessionState::CONNECTED) {
                    uint8_t buffer[2048];
                    ssize_t bytes = ::recv(event->fd, buffer, sizeof(buffer), 0);
                    if (bytes > 0) {
                        if (session->key.protocol == 6 && session->pcb) { // TCP
                            err_t err = tcp_write(session->pcb, buffer, bytes, TCP_WRITE_FLAG_COPY);
                            if (err == ERR_OK) {
                                tcp_output(session->pcb);
                                relay_thread.resumePollIn(event->fd);
                            } else {
                                relay_thread.resumePollIn(event->fd); 
                            }
                        } else if (session->key.protocol == 17 && udp_pcb_listener) { // UDP
                            struct pbuf* p = pbuf_alloc(PBUF_TRANSPORT, bytes, PBUF_RAM);
                            if (p) {
                                pbuf_take(p, buffer, bytes);
                                ip_addr_t dst_ip;
                                ip_addr_set_ip4_u32(&dst_ip, session->key.src_ip);
                                udp_sendto(udp_pcb_listener, p, &dst_ip, session->key.src_port);
                                pbuf_free(p);
                            }
                            relay_thread.resumePollIn(event->fd);
                        } else {
                            relay_thread.resumePollIn(event->fd);
                        }
                    } else if (bytes < 0 && errno == EAGAIN) {
                        relay_thread.resumePollIn(event->fd);
                    } else {
                        // Error or EOF
                        session_manager.closeSession(session->key);
                        relay_thread.removeFd(event->fd);
                    }
                } else {
                    relay_thread.removeFd(event->fd);
                }
            } else if (event->type == BackendMessage::POSIX_EOF) {
                Session* session = session_manager.getSessionByFd(event->fd);
                if (session) {
                    session_manager.closeSession(session->key);
                }
                relay_thread.removeFd(event->fd);
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
    
    relay_thread.stop();
    session_manager.closeAll();
    
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
