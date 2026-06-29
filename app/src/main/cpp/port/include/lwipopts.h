#ifndef ACCESSMANAGER_LWIPOPTS_H
#define ACCESSMANAGER_LWIPOPTS_H

#include <stdint.h>

// Operating System support (Requires sys_arch.c to be implemented)
#define NO_SYS 0

// We are bridging TUN, no hardware checksums usually needed, 
// but AccessManager doesn't do packet forwarding yet, so this is minimal.
#define LWIP_IPV4 1
#define LWIP_IPV6 0
#define LWIP_TCP 1
#define LWIP_UDP 1

// Socket API is needed to act as the client/server locally
// Disabled as we use raw API.
// #define LWIP_SOCKET 1

// Disable stats for lower memory
#define LWIP_STATS 0

// Debugging (Disable in production)
#define LWIP_DEBUG 0

// Memory configuration
#define MEM_SIZE (64 * 1024)
#define MEMP_NUM_PBUF 64
#define MEMP_NUM_TCP_PCB 32
#define MEMP_NUM_TCP_SEG 256
#define PBUF_POOL_SIZE 128
#define PBUF_POOL_BUFSIZE 1700 // Standard MTU + headers

// TCP Options
#define TCP_MSS 1460
#define TCP_WND (16 * TCP_MSS)
#define TCP_SND_BUF (16 * TCP_MSS)
#define TCP_SND_QUEUELEN (4 * TCP_SND_BUF / TCP_MSS)

// Thread configuration
#define TCPIP_THREAD_NAME "lwip_tcpip"
#define TCPIP_THREAD_STACKSIZE 4096
#define TCPIP_THREAD_PRIO 1

#define TCPIP_MBOX_SIZE 1024
#define DEFAULT_TCP_RECVMBOX_SIZE 1024
#define DEFAULT_UDP_RECVMBOX_SIZE 1024
#define DEFAULT_RAW_RECVMBOX_SIZE 1024
#define DEFAULT_ACCEPTMBOX_SIZE 1024

// System configuration
#define SYS_LIGHTWEIGHT_PROT 1
#define LWIP_TIMEVAL_PRIVATE 0

// Disable sockets and netconn APIs (we use raw API)
#define LWIP_SOCKET 0
#define LWIP_NETCONN 0

#endif // ACCESSMANAGER_LWIPOPTS_H
