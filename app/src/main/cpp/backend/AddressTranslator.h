#ifndef ACCESSMANAGER_ADDRESSTRANSLATOR_H
#define ACCESSMANAGER_ADDRESSTRANSLATOR_H

#include <stdint.h>
#include <stddef.h>

namespace dev {
namespace kartik {
namespace accessmanager {
namespace backend {

// SessionKey uniquely identifies a connection from the TUN interface perspective.
struct SessionKey {
    uint32_t src_ip;
    uint16_t src_port;
    uint8_t protocol; // 6 for TCP, 17 for UDP

    bool operator==(const SessionKey& o) const {
        return src_ip == o.src_ip && src_port == o.src_port && protocol == o.protocol;
    }
};

struct SessionKeyHash {
    size_t operator()(const SessionKey& k) const {
        return (static_cast<size_t>(k.src_ip) << 24) ^ 
               (static_cast<size_t>(k.src_port) << 8) ^ 
               static_cast<size_t>(k.protocol);
    }
};

class AddressTranslator {
public:
    // Extracts the SessionKey and original destination from the packet without modifying it.
    static bool extractKey(const uint8_t* packet, size_t length, SessionKey& out_key, uint32_t& out_original_dst_ip, uint16_t& out_original_dst_port);

    // Destination NAT (Uplink: TUN -> lwIP)
    // Rewrites the destination IP and Port to the provided virtual_ip and virtual_port.
    static bool dnatUplink(uint8_t* packet, size_t length, uint32_t virtual_ip, uint16_t virtual_port);

    // Source NAT (Downlink: lwIP -> TUN)
    // Restores the original destination IP and Port (which becomes the Source IP/Port of the reply packet).
    static bool snatDownlink(uint8_t* packet, size_t length, uint32_t original_dst_ip, uint16_t original_dst_port);

private:
    static void updateChecksum16(uint16_t& checksum, uint16_t old_val, uint16_t new_val);
    static void updateChecksum32(uint16_t& checksum, uint32_t old_val, uint32_t new_val);
};

} // namespace backend
} // namespace accessmanager
} // namespace kartik
} // namespace dev

#endif // ACCESSMANAGER_ADDRESSTRANSLATOR_H
