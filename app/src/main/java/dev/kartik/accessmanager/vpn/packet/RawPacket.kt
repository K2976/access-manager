package dev.kartik.accessmanager.vpn.packet

/**
 * An immutable representation of a raw packet captured from the VPN tunnel.
 *
 * This layer treats the packet as opaque binary data. It does not parse IP headers,
 * TCP/UDP structures, or payloads.
 *
 * The [data] array must be a copy, not a reference to a reused buffer,
 * ensuring downstream consumers can safely process it asynchronously.
 */
class RawPacket(val data: ByteArray) {
    val size: Int get() = data.size
}
