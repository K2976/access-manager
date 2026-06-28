package dev.kartik.accessmanager.vpn.relay

/**
 * Abstracts the Android VpnService.protect() capability so the Relay Engine
 * can protect native sockets without taking a hard dependency on the VpnService lifecycle.
 */
interface SocketProtector {
    /**
     * Protects a native file descriptor from being routed back through the VPN tunnel.
     * @return true if the socket was successfully protected, false otherwise.
     */
    fun protect(fd: Int): Boolean
}
