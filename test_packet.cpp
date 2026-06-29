#include <iostream>
#include <cstdint>
#include <iomanip>
#include <cstring>
#include <vector>
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

void updateChecksum16(uint16_t& checksum, uint16_t old_val, uint16_t new_val) {
    uint32_t sum = (~checksum & 0xFFFF);
    sum += (~old_val & 0xFFFF);
    sum += new_val;
    sum = (sum & 0xFFFF) + (sum >> 16);
    checksum = static_cast<uint16_t>(~sum);
}

void updateChecksum32(uint16_t& checksum, uint32_t old_val, uint32_t new_val) {
    updateChecksum16(checksum, static_cast<uint16_t>(old_val >> 16), static_cast<uint16_t>(new_val >> 16));
    updateChecksum16(checksum, static_cast<uint16_t>(old_val & 0xFFFF), static_cast<uint16_t>(new_val & 0xFFFF));
}

uint16_t computeChecksum(const uint8_t* data, size_t length) {
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

int main() {
    uint8_t packet[40] = {0};
    ip4_hdr* ip = reinterpret_cast<ip4_hdr*>(packet);
    ip->ihl_version = 0x45;
    ip->tot_len = htons(40);
    ip->ttl = 64;
    ip->protocol = 6;
    ip->saddr = htonl(0x0A000002);
    ip->daddr = htonl(0xC0A80105);
    ip->check = 0;
    ip->check = htons(computeChecksum(packet, 20));
    
    int fail_count = 0;
    for (uint32_t target = 0x08080808; target < 0x0808FFFF; target++) {
        uint8_t temp[40];
        memcpy(temp, packet, 40);
        ip4_hdr* tip = reinterpret_cast<ip4_hdr*>(temp);
        
        uint32_t old_saddr = tip->saddr;
        uint32_t new_saddr = htonl(target);
        
        updateChecksum32(tip->check, old_saddr, new_saddr);
        tip->saddr = new_saddr;
        
        uint16_t stored = ntohs(tip->check);
        tip->check = 0;
        uint16_t true_check = computeChecksum(temp, 20);
        
        if (stored != true_check) {
            fail_count++;
            if (fail_count == 1) {
                std::cout << "First Failure for IP: " << std::hex << target << "\n";
                std::cout << "Stored: " << stored << " True: " << true_check << "\n";
            }
        }
    }
    std::cout << "Total failures: " << fail_count << "\n";
    return 0;
}
