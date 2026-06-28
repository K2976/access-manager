package dev.kartik.accessmanager.vpn.connection

import dev.kartik.accessmanager.vpn.packet.ConnectionInfo

/**
 * A lightweight, immutable representation of an active logical network session.
 *
 * This intentionally excludes any references to physical sockets, IO streams,
 * or relay logic to maintain a strict separation of concerns.
 */
data class ConnectionSession(
    val flowKey: FlowKey,
    val connectionInfo: ConnectionInfo,
    val createdAt: Long,
    val lastActivityAt: Long,
    val state: ConnectionState,
    val packetCount: Long,
)
