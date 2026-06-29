#include <iostream>
#include <cstdint>
#include <iomanip>

void updateChecksum16_v1(uint16_t& checksum, uint16_t old_val, uint16_t new_val) {
    uint32_t sum = (~checksum & 0xFFFF);
    sum += (~old_val & 0xFFFF);
    sum += new_val;
    sum = (sum & 0xFFFF) + (sum >> 16);
    checksum = static_cast<uint16_t>(~sum);
}

void updateChecksum16_v2(uint16_t& checksum, uint16_t old_val, uint16_t new_val) {
    uint32_t sum = (~checksum & 0xFFFF);
    sum += (~old_val & 0xFFFF);
    sum += new_val;
    sum = (sum & 0xFFFF) + (sum >> 16);
    sum += (sum >> 16); // Second fold
    checksum = static_cast<uint16_t>(~sum);
}

int main() {
    uint16_t checksum1 = 0x0000;
    uint16_t checksum2 = 0x0000;
    updateChecksum16_v1(checksum1, 0x0000, 0x0001); // ~0x0000 = 0xFFFF, ~0x0000 = 0xFFFF, 0x0001 => 0x1FFFF
    updateChecksum16_v2(checksum2, 0x0000, 0x0001);
    
    std::cout << std::hex << "V1: " << checksum1 << " V2: " << checksum2 << std::endl;
    return 0;
}
