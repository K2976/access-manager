#ifndef ACCESSMANAGER_SESSIONMANAGER_H
#define ACCESSMANAGER_SESSIONMANAGER_H

#include "AddressTranslator.h"
#include <unordered_map>
#include <mutex>
#include "lwip/tcp.h"

namespace dev {
namespace kartik {
namespace accessmanager {
namespace backend {

enum class SessionState {
    CONNECTING,
    CONNECTED,
    CLOSING,
    CLOSED
};

class SessionManager;

struct Session {
    SessionKey key;
    uint32_t original_dst_ip;
    uint16_t original_dst_port;
    
    int fd;
    SessionState state;
    
    tcp_pcb* pcb; // Null for UDP
    
    uint32_t last_activity_ms;
    
    struct pbuf* tx_queue; // Backpressure queue
    size_t tx_offset; // Offset into the head of tx_queue
    SessionManager* manager; // Back pointer
};

class LwipBackend;

class SessionManager {
public:
    SessionManager(LwipBackend* backend);
    ~SessionManager();
    
    LwipBackend* backend;

    // Retrieves an existing session or creates a new one.
    // Creates a POSIX socket, protects it via JNI, and initiates connect().
    // Returns nullptr if creation/protection fails.
    Session* getOrCreateSession(const SessionKey& key, uint32_t original_dst_ip, uint16_t original_dst_port);

    // Retrieves a session by its original destination (for Downlink lookup).
    Session* getSessionByDest(uint32_t original_dst_ip, uint16_t original_dst_port, uint8_t protocol);
    
    // Retrieves a session by its SessionKey
    Session* getSessionByKey(const SessionKey& key);
    
    // Retrieves a session by its POSIX file descriptor (for RelayThread).
    Session* getSessionByFd(int fd);

    // Binds an lwIP TCP PCB to an existing session.
    void linkPcb(const SessionKey& key, tcp_pcb* pcb);
    
    // Closes the POSIX socket and removes the session.
    void closeSession(const SessionKey& key);
    
    // Periodically removes stale UDP sessions and zombies.
    void cleanupStaleSessions(uint32_t current_time_ms);

    // Closes all sessions during shutdown.
    void closeAll();

private:
    std::unordered_map<SessionKey, Session*, SessionKeyHash> sessions;
    std::unordered_map<int, Session*> fd_map;
    
    // Fast lookup for downlink NAT
    struct DestKey {
        uint32_t ip;
        uint16_t port;
        uint8_t protocol;
        bool operator==(const DestKey& o) const { return ip == o.ip && port == o.port && protocol == o.protocol; }
    };
    
    struct DestKeyHash {
        size_t operator()(const DestKey& k) const {
            return (static_cast<size_t>(k.ip) << 24) ^ (static_cast<size_t>(k.port) << 8) ^ k.protocol;
        }
    };
    
    std::unordered_map<DestKey, Session*, DestKeyHash> dest_map;
};

} // namespace backend
} // namespace accessmanager
} // namespace kartik
} // namespace dev

#endif // ACCESSMANAGER_SESSIONMANAGER_H
