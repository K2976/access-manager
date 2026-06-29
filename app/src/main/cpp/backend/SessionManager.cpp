#include "SessionManager.h"
#include "common/Logger.h"
#include <vector>
#include <sys/socket.h>
#include <netinet/in.h>
#include <fcntl.h>
#include <unistd.h>
#include <cerrno>
#include <cassert>

extern bool jni_protect_socket(int fd);

namespace dev {
namespace kartik {
namespace accessmanager {
namespace backend {

SessionManager::SessionManager(LwipBackend* backend) : backend(backend) {}

SessionManager::~SessionManager() {
    closeAll();
}

Session* SessionManager::getOrCreateSession(const SessionKey& key, uint32_t original_dst_ip, uint16_t original_dst_port) {
    auto it = sessions.find(key);
    if (it != sessions.end()) {
        it->second->last_activity_ms = 0; // Reset timeout (will be updated by sys_now() later if needed)
        return it->second;
    }

    int domain = AF_INET;
    int type = (key.protocol == 6) ? SOCK_STREAM : SOCK_DGRAM;
    
    int fd = socket(domain, type, 0);
    if (fd < 0) {
        LOGE("[AM-S08] socket() FAILED errno=%d", errno);
        return nullptr;
    }
    assert(fd >= 0);
    LOGD("[AM-S08] socket() OK fd=%d proto=%d", fd, key.protocol);
    
    // Protect socket from VPN routing loop
    if (!jni_protect_socket(fd)) {
        LOGE("[AM-S08] protect() FAILED fd=%d", fd);
        ::close(fd);
        return nullptr;
    }
    LOGD("[AM-S08] protect() OK fd=%d", fd);
    
    // Set Non-blocking
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags < 0 || fcntl(fd, F_SETFL, flags | O_NONBLOCK) < 0) {
        LOGE("[AM-S08] fcntl() FAILED fd=%d errno=%d", fd, errno);
        ::close(fd);
        return nullptr;
    }
    
    // Connect to original destination
    struct sockaddr_in addr;
    addr.sin_family = AF_INET;
    addr.sin_port = original_dst_port; // Network byte order
    addr.sin_addr.s_addr = original_dst_ip; // Network byte order
    
    // Enable TCP Keepalive to detect dead connections across network transitions (e.g. Wi-Fi -> Mobile)
    if (key.protocol == 6) {
        int optval = 1;
        setsockopt(fd, SOL_SOCKET, SO_KEEPALIVE, &optval, sizeof(optval));
    }
    
    int res = ::connect(fd, (struct sockaddr*)&addr, sizeof(addr));
    if (res < 0 && errno != EINPROGRESS) {
        LOGE("[AM-S08] connect() FAILED fd=%d dst=%x:%d errno=%d", fd, original_dst_ip, ntohs(original_dst_port), errno);
        ::close(fd);
        return nullptr;
    }
    LOGD("[AM-S08] connect() fd=%d dst=%x:%d res=%d errno=%d", fd, original_dst_ip, ntohs(original_dst_port), res, errno);
    
    Session* session = new Session();
    session->key = key;
    session->original_dst_ip = original_dst_ip;
    session->original_dst_port = original_dst_port;
    session->fd = fd;
    session->state = SessionState::CONNECTING;
    session->pcb = nullptr;
    session->last_activity_ms = 0;
    session->tx_queue = nullptr;
    session->tx_offset = 0;
    session->manager = this;
    
    sessions[key] = session;
    fd_map[fd] = session;
    
    DestKey dkey{original_dst_ip, original_dst_port, key.protocol};
    dest_map[dkey] = session;
    
    LOGD("[AM-S08] Session CREATED fd=%d proto=%d", fd, key.protocol);
    return session;
}

Session* SessionManager::getSessionByDest(uint32_t original_dst_ip, uint16_t original_dst_port, uint8_t protocol) {
    DestKey dkey{original_dst_ip, original_dst_port, protocol};
    auto it = dest_map.find(dkey);
    return (it != dest_map.end()) ? it->second : nullptr;
}

Session* SessionManager::getSessionByKey(const SessionKey& key) {
    auto it = sessions.find(key);
    return (it != sessions.end()) ? it->second : nullptr;
}

Session* SessionManager::getSessionByFd(int fd) {
    auto it = fd_map.find(fd);
    return (it != fd_map.end()) ? it->second : nullptr;
}

void SessionManager::linkPcb(const SessionKey& key, tcp_pcb* pcb) {
    auto it = sessions.find(key);
    if (it != sessions.end()) {
        it->second->pcb = pcb;
        it->second->state = SessionState::CONNECTED;
    }
}

void SessionManager::closeSession(const SessionKey& key) {
    auto it = sessions.find(key);
    if (it != sessions.end()) {
        Session* s = it->second;
        assert(s != nullptr);
        assert(s->state != SessionState::CLOSED);
        s->state = SessionState::CLOSED;
        
        LOGD("Closing session FD %d", s->fd);
        if (s->fd >= 0) {
            ::close(s->fd);
        }
        
        if (s->pcb) {
            tcp_arg(s->pcb, nullptr);
            tcp_recv(s->pcb, nullptr);
            tcp_err(s->pcb, nullptr);
            
            err_t err = tcp_close(s->pcb);
            if (err != ERR_OK) {
                LOGE("tcp_close failed with %d, aborting pcb", err);
                tcp_abort(s->pcb);
            }
            s->pcb = nullptr;
        }
        
        if (s->tx_queue) {
            pbuf_free(s->tx_queue);
            s->tx_queue = nullptr;
        }
        
        fd_map.erase(s->fd);
        DestKey dkey{s->original_dst_ip, s->original_dst_port, s->key.protocol};
        dest_map.erase(dkey);
        
        delete s;
        sessions.erase(it);
    }
}

void SessionManager::cleanupStaleSessions(uint32_t current_time_ms) {
    constexpr uint32_t UDP_TIMEOUT_MS = 60000;
    
    std::vector<SessionKey> to_remove;
    for (const auto& pair : sessions) {
        if (pair.second->key.protocol == 17) {
            if (current_time_ms > pair.second->last_activity_ms + UDP_TIMEOUT_MS) {
                to_remove.push_back(pair.first);
            }
        }
    }
    
    for (const auto& k : to_remove) {
        closeSession(k);
    }
}

void SessionManager::closeAll() {
    while (!sessions.empty()) {
        closeSession(sessions.begin()->first);
    }
}

} // namespace backend
} // namespace accessmanager
} // namespace kartik
} // namespace dev
