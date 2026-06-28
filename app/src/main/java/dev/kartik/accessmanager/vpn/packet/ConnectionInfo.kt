package dev.kartik.accessmanager.vpn.packet

import dev.kartik.accessmanager.vpn.connection.FlowKey
import java.net.InetAddress

/**
 * Immutable model representing a resolved network connection.
 *
 * Contains only connection metadata and ownership information.
 * Does not contain any policy or access state information.
 */
data class ConnectionInfo(
    val sourceAddress: InetAddress,
    val destinationAddress: InetAddress,
    val sourcePort: Int,
    val destinationPort: Int,
    val protocol: Int,
    val uid: Int? = null,
    val packageNames: List<String> = emptyList(),
) {
    fun toFlowKey(): FlowKey = FlowKey(
        sourceAddress = sourceAddress,
        destinationAddress = destinationAddress,
        sourcePort = sourcePort,
        destinationPort = destinationPort,
        protocol = protocol
    )
}
