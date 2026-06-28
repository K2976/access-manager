#include "AddressTranslator.h"
#include <arpa/inet.h>
#include <cstring>
#include "common/Logger.h"

namespace dev {
namespace kartik {
namespace accessmanager {
namespace backend {

// Standard IPv4 Header
struct ip4_hdr {
    uint8_t  ihl_version;
    uint8_t  tos;
    uint16_t tot_len;
    uint16_t id;
    uint16_t frag_off;
    uint8_t  ttl;
    uint8_t  protocol;
    uint16_t check;
    uint32_t saddr;
    uint32_t daddr;
} __attribute__((packed));

// Standard TCP Header
struct tcp_hdr {
    uint16_t source;
    uint16_t dest;
    uint32_t seq;
    uint32_t ack_seq;
    uint16_t res1_doff_flags;
    uint16_t window;
    uint16_t check;
    uint16_t urg_ptr;
} __attribute__((packed));

// Standard UDP Header
struct udp_hdr {
    uint16_t source;
    uint16_t dest;
    uint16_t len;
    uint16_t check;
} __attribute__((packed));

// RFC 1624 Differential Checksum Update
// HC' = ~(~HC + ~m + m')
void AddressTranslator::updateChecksum16(uint16_t& checksum, uint16_t old_val, uint16_t new_val) {
    uint32_t sum = (~checksum & 0xFFFF);
    sum += (~old_val & 0xFFFF);
    sum += new_val;
    sum = (sum & 0xFFFF) + (sum >> 16);
    checksum = static_cast<uint16_t>(~sum);
}

void AddressTranslator::updateChecksum32(uint16_t& checksum, uint32_t old_val, uint32_t new_val) {
    updateChecksum16(checksum, static_cast<uint16_t>(old_val >> 16), static_cast<uint16_t>(new_val >> 16));
    updateChecksum16(checksum, static_cast<uint16_t>(old_val & 0xFFFF), static_cast<uint16_t>(new_val & 0xFFFF));
}

bool AddressTranslator::extractKey(const uint8_t* packet, size_t length, SessionKey& out_key, uint32_t& out_original_dst_ip, uint16_t& out_original_dst_port) {
    if (length < sizeof(ip4_hdr)) return false;
    
    const ip4_hdr* ip = reinterpret_cast<const ip4_hdr*>(packet);
    if ((ip->ihl_version >> 4) != 4) return false;
    
    size_t ip_hlen = (ip->ihl_version & 0x0F) * 4;
    if (length < ip_hlen) return false;
    
    out_key.src_ip = ip->saddr;
    out_key.protocol = ip->protocol;
    out_original_dst_ip = ip->daddr;
    
    if (ip->protocol == 6) {
        if (length < ip_hlen + sizeof(tcp_hdr)) return false;
        const tcp_hdr* tcp = reinterpret_cast<const tcp_hdr*>(packet + ip_hlen);
        out_key.src_port = tcp->source;
        out_original_dst_port = tcp->dest;
        return true;
    } else if (ip->protocol == 17) {
        if (length < ip_hlen + sizeof(udp_hdr)) return false;
        const udp_hdr* udp = reinterpret_cast<const udp_hdr*>(packet + ip_hlen);
        out_key.src_port = udp->source;
        out_original_dst_port = udp->dest;
        return true;
    }
    return false;
}

bool AddressTranslator::dnatUplink(uint8_t* packet, size_t length, uint32_t virtual_ip, uint16_t virtual_port) {
    if (length < sizeof(ip4_hdr)) return false;
    
    ip4_hdr* ip = reinterpret_cast<ip4_hdr*>(packet);
    size_t ip_hlen = (ip->ihl_version & 0x0F) * 4;
    
    uint32_t old_daddr = ip->daddr;
    uint32_t new_daddr = virtual_ip;
    
    if (ip->protocol == 6) {
        tcp_hdr* tcp = reinterpret_cast<tcp_hdr*>(packet + ip_hlen);
        uint16_t old_dport = tcp->dest;
        uint16_t new_dport = virtual_port;
        
        if (tcp->check != 0) {
            updateChecksum32(tcp->check, old_daddr, new_daddr);
            updateChecksum16(tcp->check, old_dport, new_dport);
        }
        tcp->dest = new_dport;
        
    } else if (ip->protocol == 17) {
        udp_hdr* udp = reinterpret_cast<udp_hdr*>(packet + ip_hlen);
        uint16_t old_dport = udp->dest;
        uint16_t new_dport = virtual_port;
        
        if (udp->check != 0) {
            updateChecksum32(udp->check, old_daddr, new_daddr);
            updateChecksum16(udp->check, old_dport, new_dport);
        }
        udp->dest = new_dport;
    }
    
    updateChecksum32(ip->check, old_daddr, new_daddr);
    ip->daddr = new_daddr;
    
    return true;
}

bool AddressTranslator::snatDownlink(uint8_t* packet, size_t length, uint32_t original_dst_ip, uint16_t original_dst_port) {
    if (length < sizeof(ip4_hdr)) return false;
    
    ip4_hdr* ip = reinterpret_cast<ip4_hdr*>(packet);
    if ((ip->ihl_version >> 4) != 4) return false;
    
    size_t ip_hlen = (ip->ihl_version & 0x0F) * 4;
    if (length < ip_hlen) return false;
    
    uint32_t old_saddr = ip->saddr;
    uint32_t new_saddr = original_dst_ip;
    
    if (ip->protocol == 6) { // TCP
        if (length < ip_hlen + sizeof(tcp_hdr)) return false;
        tcp_hdr* tcp = reinterpret_cast<tcp_hdr*>(packet + ip_hlen);
        
        uint16_t old_sport = tcp->source;
        uint16_t new_sport = original_dst_port;
        
        if (tcp->check != 0) {
            updateChecksum32(tcp->check, old_saddr, new_saddr);
            updateChecksum16(tcp->check, old_sport, new_sport);
        }
        
        tcp->source = new_sport;
        
    } else if (ip->protocol == 17) { // UDP
        if (length < ip_hlen + sizeof(udp_hdr)) return false;
        udp_hdr* udp = reinterpret_cast<udp_hdr*>(packet + ip_hlen);
        
        uint16_t old_sport = udp->source;
        uint16_t new_sport = original_dst_port;
        
        if (udp->check != 0) {
            updateChecksum32(udp->check, old_saddr, new_saddr);
            updateChecksum16(udp->check, old_sport, new_sport);
        }
        
        udp->source = new_sport;
    } else {
        return false;
    }
    
    // Update IPv4 Header Checksum
    updateChecksum32(ip->check, old_saddr, new_saddr);
    ip->saddr = new_saddr;
    
    return true;
}

} // namespace backend
} // namespace accessmanager
} // namespace kartik
} // namespace dev
