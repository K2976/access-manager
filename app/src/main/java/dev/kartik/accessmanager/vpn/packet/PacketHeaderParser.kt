package dev.kartik.accessmanager.vpn.packet

import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Performs minimal parsing of raw packets to extract the connection 5-tuple
 * (source IP, destination IP, source port, destination port, protocol).
 *
 * This parser intentionally ignores payloads and complex IP options.
 * Packets that are not IPv4/IPv6 TCP/UDP are ignored.
 */
object PacketHeaderParser {

    private const val IPV4 = 4
    private const val IPV6 = 6
    private const val PROTOCOL_TCP = 6
    private const val PROTOCOL_UDP = 17

    /**
     * Parses a RawPacket and returns a ConnectionInfo populated with network
     * and transport layer metadata. Returns null if the packet is malformed
     * or uses an unsupported protocol.
     */
    fun parse(packet: RawPacket): ConnectionInfo? {
        val buffer = packet.data
        if (buffer.isEmpty()) return null

        // High nibble of first byte is the IP version
        val version = (buffer[0].toInt() and 0xF0) ushr 4

        return when (version) {
            IPV4 -> parseIpv4(buffer)
            IPV6 -> parseIpv6(buffer)
            else -> null
        }
    }

    private fun parseIpv4(buffer: ByteArray): ConnectionInfo? {
        if (buffer.size < 20) return null // Minimum IPv4 header length

        // Low nibble of first byte is the Internet Header Length (IHL) in 32-bit words
        val ihl = buffer[0].toInt() and 0x0F
        val headerLength = ihl * 4

        if (buffer.size < headerLength) return null

        val protocol = buffer[9].toInt() and 0xFF
        if (protocol != PROTOCOL_TCP && protocol != PROTOCOL_UDP) return null

        val srcIp = buffer.copyOfRange(12, 16)
        val destIp = buffer.copyOfRange(16, 20)

        return extractTransportHeader(buffer, headerLength, protocol, srcIp, destIp)
    }

    private fun parseIpv6(buffer: ByteArray): ConnectionInfo? {
        if (buffer.size < 40) return null // Fixed IPv6 header length

        val protocol = buffer[6].toInt() and 0xFF
        // Note: For simplicity in Sprint 08, we assume the next header is TCP/UDP directly.
        // We are ignoring packets with IPv6 extension headers here.
        if (protocol != PROTOCOL_TCP && protocol != PROTOCOL_UDP) return null

        val srcIp = buffer.copyOfRange(8, 24)
        val destIp = buffer.copyOfRange(24, 40)

        return extractTransportHeader(buffer, 40, protocol, srcIp, destIp)
    }

    private fun extractTransportHeader(
        buffer: ByteArray,
        ipHeaderLength: Int,
        protocol: Int,
        srcIpBytes: ByteArray,
        destIpBytes: ByteArray,
    ): ConnectionInfo? {
        if (buffer.size < ipHeaderLength + 4) return null // Need at least 4 bytes for src/dest ports

        val srcPort = ((buffer[ipHeaderLength].toInt() and 0xFF) shl 8) or
            (buffer[ipHeaderLength + 1].toInt() and 0xFF)
        val destPort = ((buffer[ipHeaderLength + 2].toInt() and 0xFF) shl 8) or
            (buffer[ipHeaderLength + 3].toInt() and 0xFF)

        return try {
            ConnectionInfo(
                sourceAddress = InetAddress.getByAddress(srcIpBytes),
                destinationAddress = InetAddress.getByAddress(destIpBytes),
                sourcePort = srcPort,
                destinationPort = destPort,
                protocol = protocol,
            )
        } catch (_: UnknownHostException) {
            null
        }
    }
}
