cmake_minimum_required(VERSION 3.22.1)

project("accessmanager-core")

# Add root directory so #include "common/Logger.h" works correctly
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Add lwIP and port headers
include_directories(
    third_party/lwip/src/include
    port/include
)

# Gather source files
set(BRIDGE_SRC bridge/bridge.cpp)
set(BACKEND_SRC 
    backend/LwipBackend.cpp
    backend/AddressTranslator.cpp
    backend/SessionManager.cpp
    backend/RelayThread.cpp
)
set(PORT_SRC port/sys_arch.cpp)

# lwIP core files
set(LWIP_SRC
    third_party/lwip/src/core/init.c
    third_party/lwip/src/core/def.c
    third_party/lwip/src/core/dns.c
    third_party/lwip/src/core/inet_chksum.c
    third_party/lwip/src/core/ip.c
    third_party/lwip/src/core/mem.c
    third_party/lwip/src/core/memp.c
    third_party/lwip/src/core/netif.c
    third_party/lwip/src/netif/ethernet.c
    third_party/lwip/src/core/pbuf.c
    third_party/lwip/src/core/raw.c
    third_party/lwip/src/core/stats.c
    third_party/lwip/src/core/sys.c
    third_party/lwip/src/core/tcp.c
    third_party/lwip/src/core/tcp_in.c
    third_party/lwip/src/core/tcp_out.c
    third_party/lwip/src/core/timeouts.c
    third_party/lwip/src/core/udp.c
    
    third_party/lwip/src/core/ipv4/autoip.c
    third_party/lwip/src/core/ipv4/dhcp.c
    third_party/lwip/src/core/ipv4/etharp.c
    third_party/lwip/src/core/ipv4/icmp.c
    third_party/lwip/src/core/ipv4/igmp.c
    third_party/lwip/src/core/ipv4/ip4_frag.c
    third_party/lwip/src/core/ipv4/ip4.c
    third_party/lwip/src/core/ipv4/ip4_addr.c
    
    third_party/lwip/src/core/ipv6/dhcp6.c
    third_party/lwip/src/core/ipv6/ethip6.c
    third_party/lwip/src/core/ipv6/icmp6.c
    third_party/lwip/src/core/ipv6/inet6.c
    third_party/lwip/src/core/ipv6/ip6.c
    third_party/lwip/src/core/ipv6/ip6_addr.c
    third_party/lwip/src/core/ipv6/ip6_frag.c
    third_party/lwip/src/core/ipv6/mld6.c
    third_party/lwip/src/core/ipv6/nd6.c
    
    third_party/lwip/src/api/tcpip.c
    third_party/lwip/src/api/err.c
)

# Create the shared library
add_library(${CMAKE_PROJECT_NAME} SHARED
    ${BRIDGE_SRC}
    ${BACKEND_SRC}
    ${PORT_SRC}
    ${LWIP_SRC}
)

# Link against Android standard libraries (log for __android_log_print)
find_library(log-lib log)
target_link_libraries(${CMAKE_PROJECT_NAME} ${log-lib})
