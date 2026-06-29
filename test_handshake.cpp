#include <iostream>
#include <cstdint>
#include <iomanip>
#include <cstring>
#include <arpa/inet.h>

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

void updateChecksum16_buggy(uint16_t& checksum, uint16_t old_val, uint16_t new_val) {
    uint32_t sum = (~checksum & 0xFFFF);
    sum += (~old_val & 0xFFFF);
    sum += new_val;
    sum = (sum & 0xFFFF) + (sum >> 16);
    checksum = static_cast<uint16_t>(~sum);
}

void updateChecksum16_fixed(uint16_t& checksum, uint16_t old_val, uint16_t new_val) {
    uint32_t sum = (~checksum & 0xFFFF);
    sum += (~old_val & 0xFFFF);
    sum += new_val;
    sum = (sum & 0xFFFF) + (sum >> 16);
    sum += (sum >> 16);
    checksum = static_cast<uint16_t>(~sum);
}

bool use_fixed = false;

void updateChecksum32(uint16_t& checksum, uint32_t old_val, uint32_t new_val) {
    if (use_fixed) {
        updateChecksum16_fixed(checksum, static_cast<uint16_t>(old_val >> 16), static_cast<uint16_t>(new_val >> 16));
        updateChecksum16_fixed(checksum, static_cast<uint16_t>(old_val & 0xFFFF), static_cast<uint16_t>(new_val & 0xFFFF));
    } else {
        updateChecksum16_buggy(checksum, static_cast<uint16_t>(old_val >> 16), static_cast<uint16_t>(new_val >> 16));
        updateChecksum16_buggy(checksum, static_cast<uint16_t>(old_val & 0xFFFF), static_cast<uint16_t>(new_val & 0xFFFF));
    }
}

void updateChecksum16(uint16_t& checksum, uint16_t old_val, uint16_t new_val) {
    if (use_fixed) {
        updateChecksum16_fixed(checksum, old_val, new_val);
    } else {
        updateChecksum16_buggy(checksum, old_val, new_val);
    }
}

uint16_t computeIpChecksum(const uint8_t* data, size_t length) {
    uint32_t sum = 0;
    size_t i = 0;
    while (i < (length & ~1)) {
        sum += (data[i] << 8) | data[i+1];
        i += 2;
    }
    if (i < length) sum += (data[i] << 8);
    while (sum >> 16) sum = (sum & 0xFFFF) + (sum >> 16);
    return ~sum & 0xFFFF;
}

uint16_t computeTcpChecksum(const uint8_t* packet, size_t length) {
    const ip4_hdr* ip = reinterpret_cast<const ip4_hdr*>(packet);
    size_t ip_hlen = (ip->ihl_version & 0x0F) * 4;
    
    uint32_t sum = 0;
    uint8_t pseudo[12];
    memcpy(pseudo, &ip->saddr, 4);
    memcpy(pseudo+4, &ip->daddr, 4);
    pseudo[8] = 0;
    pseudo[9] = ip->protocol;
    uint16_t tcp_len = htons(length - ip_hlen);
    memcpy(pseudo+10, &tcp_len, 2);
    
    for (int i = 0; i < 12; i+=2) {
        sum += (pseudo[i] << 8) | pseudo[i+1];
    }
    
    const uint8_t* tcp = packet + ip_hlen;
    size_t tcp_length = length - ip_hlen;
    for (size_t i = 0; i < (tcp_length & ~1); i+=2) {
        if (i == 16) continue;
        sum += (tcp[i] << 8) | tcp[i+1];
    }
    if (tcp_length % 2 != 0) {
        sum += (tcp[tcp_length-1] << 8);
    }
    
    while (sum >> 16) sum = (sum & 0xFFFF) + (sum >> 16);
    return ~sum & 0xFFFF;
}

void snatDownlink(uint8_t* packet, size_t length, uint32_t original_dst_ip, uint16_t original_dst_port) {
    ip4_hdr* ip = reinterpret_cast<ip4_hdr*>(packet);
    size_t ip_hlen = (ip->ihl_version & 0x0F) * 4;
    
    uint32_t old_saddr = ip->saddr;
    uint32_t new_saddr = original_dst_ip;
    
    tcp_hdr* tcp = reinterpret_cast<tcp_hdr*>(packet + ip_hlen);
    uint16_t old_sport = tcp->source;
    uint16_t new_sport = original_dst_port;
    
    updateChecksum32(tcp->check, old_saddr, new_saddr);
    updateChecksum16(tcp->check, old_sport, new_sport);
    tcp->source = new_sport;
    
    updateChecksum32(ip->check, old_saddr, new_saddr);
    ip->saddr = new_saddr;
}

void dnatUplink(uint8_t* packet, size_t length, uint32_t virtual_ip, uint16_t virtual_port) {
    ip4_hdr* ip = reinterpret_cast<ip4_hdr*>(packet);
    size_t ip_hlen = (ip->ihl_version & 0x0F) * 4;
    
    uint32_t old_daddr = ip->daddr;
    uint32_t new_daddr = virtual_ip;
    
    tcp_hdr* tcp = reinterpret_cast<tcp_hdr*>(packet + ip_hlen);
    uint16_t old_dport = tcp->dest;
    uint16_t new_dport = virtual_port;
    
    updateChecksum32(tcp->check, old_daddr, new_daddr);
    updateChecksum16(tcp->check, old_dport, new_dport);
    tcp->dest = new_dport;
    
    updateChecksum32(ip->check, old_daddr, new_daddr);
    ip->daddr = new_daddr;
}

void printTcpDetails(const char* name, const uint8_t* packet) {
    const ip4_hdr* ip = reinterpret_cast<const ip4_hdr*>(packet);
    const tcp_hdr* tcp = reinterpret_cast<const tcp_hdr*>(packet + 20);
    uint16_t flags = ntohs(tcp->res1_doff_flags) & 0x01FF;
    std::cout << name << ": SEQ=" << ntohl(tcp->seq) 
              << " ACK=" << ntohl(tcp->ack_seq) 
              << " FLAGS=0x" << std::hex << flags << std::dec
              << " WIN=" << ntohs(tcp->window) 
              << " PAYLOAD=" << ntohs(ip->tot_len) - 40 << "\n";
}

void verifyPacket(const char* name, uint8_t* packet, size_t length) {
    ip4_hdr* ip = reinterpret_cast<ip4_hdr*>(packet);
    tcp_hdr* tcp = reinterpret_cast<tcp_hdr*>(packet + 20);
    
    uint16_t stored_ip = ntohs(ip->check);
    uint16_t stored_tcp = ntohs(tcp->check);
    
    ip->check = 0;
    uint16_t calc_ip = computeIpChecksum(packet, 20);
    ip->check = htons(stored_ip);
    
    uint16_t calc_tcp = computeTcpChecksum(packet, length);
    
    std::cout << "--- " << name << " ---\n";
    printTcpDetails(name, packet);
    std::cout << "IPv4 Stored: 0x" << std::hex << std::setfill('0') << std::setw(4) << stored_ip 
              << " Calc: 0x" << std::setw(4) << calc_ip << "\n";
    std::cout << "TCP  Stored: 0x" << std::setw(4) << stored_tcp 
              << " Calc: 0x" << std::setw(4) << calc_tcp << "\n";
    if (stored_ip == calc_ip && stored_tcp == calc_tcp) {
        std::cout << "Status: CHECKSUMS MATCH\n\n";
    } else {
        std::cout << "Status: MISMATCH!\n\n";
    }
}

void buildPacket(uint8_t* packet, uint32_t saddr, uint32_t daddr, uint16_t sport, uint16_t dport, uint32_t seq, uint32_t ack, uint16_t flags) {
    ip4_hdr* ip = reinterpret_cast<ip4_hdr*>(packet);
    ip->ihl_version = 0x45;
    ip->tot_len = htons(40);
    ip->ttl = 64;
    ip->protocol = 6;
    ip->saddr = htonl(saddr);
    ip->daddr = htonl(daddr);
    
    tcp_hdr* tcp = reinterpret_cast<tcp_hdr*>(packet + 20);
    tcp->source = htons(sport);
    tcp->dest = htons(dport);
    tcp->seq = htonl(seq);
    tcp->ack_seq = htonl(ack);
    tcp->res1_doff_flags = htons((5 << 12) | flags);
    tcp->window = htons(65535);
    
    ip->check = 0;
    tcp->check = 0;
    ip->check = htons(computeIpChecksum(packet, 20));
    tcp->check = htons(computeTcpChecksum(packet, 40));
}

void runHandshakeSimulation() {
    uint8_t syn_packet[40] = {0};
    uint8_t synack_packet[40] = {0};
    uint8_t ack_packet[40] = {0};
    
    uint32_t client_ip = 0x0A000001; // 10.0.0.1
    uint32_t google_ip = 0x0808b023; // 8.8.176.35
    uint32_t virt_ip = 0x0A000002;   // 10.0.0.2
    
    // 1. Client sends SYN to Google
    buildPacket(syn_packet, client_ip, google_ip, 10000, 443, 100, 0, 0x02); // SYN
    std::cout << "[Client -> TUN]\n";
    verifyPacket("Client SYN", syn_packet, 40);
    
    // 2. DNAT translates to lwIP
    dnatUplink(syn_packet, 40, htonl(virt_ip), htons(1234));
    std::cout << "[TUN -> lwIP]\n";
    verifyPacket("DNAT SYN", syn_packet, 40);
    
    // 3. lwIP generates SYN-ACK to client
    buildPacket(synack_packet, virt_ip, client_ip, 1234, 10000, 500, 101, 0x12); // SYN-ACK
    std::cout << "[lwIP -> TUN]\n";
    verifyPacket("lwIP SYN-ACK", synack_packet, 40);
    
    // 4. SNAT translates back to Google
    snatDownlink(synack_packet, 40, htonl(google_ip), htons(443));
    std::cout << "[TUN -> Client]\n";
    verifyPacket("SNAT SYN-ACK", synack_packet, 40);
    
    // 5. Client sends ACK
    buildPacket(ack_packet, client_ip, google_ip, 10000, 443, 101, 501, 0x10); // ACK
    std::cout << "[Client -> TUN]\n";
    verifyPacket("Client ACK", ack_packet, 40);
}

int main() {
    std::cout << "=== RUNNING WITH BUGGY CHECKSUM ===\n";
    use_fixed = false;
    runHandshakeSimulation();
    
    std::cout << "=== RUNNING WITH FIXED CHECKSUM ===\n";
    use_fixed = true;
    runHandshakeSimulation();
    
    return 0;
}
