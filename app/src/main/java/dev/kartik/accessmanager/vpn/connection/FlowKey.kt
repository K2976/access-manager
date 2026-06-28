package dev.kartik.accessmanager.vpn.connection

import java.net.InetAddress

/**
 * An immutable key representing a unique network flow (5-tuple).
 *
 * This key is used for O(1) lookups in the ConnectionManager.
 * It is intrinsically thread-safe and has a stable hashCode/equals.
 */
data class FlowKey(
    val sourceAddress: InetAddress,
    val destinationAddress: InetAddress,
    val sourcePort: Int,
    val destinationPort: Int,
    val protocol: Int,
)
