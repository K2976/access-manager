package dev.kartik.accessmanager.vpn.connection

import dev.kartik.accessmanager.vpn.metrics.DevMetrics
import dev.kartik.accessmanager.vpn.packet.ConnectionInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the lifecycle of logical network flows.
 *
 * It is responsible only for state tracking, session creation, and timeout cleanup.
 * It strictly performs NO networking, socket creation, or traffic relay.
 */
@Singleton
class ConnectionManager @Inject constructor() {

    private val sessions = ConcurrentHashMap<FlowKey, ConnectionSession>()
    private var cleanupJob: Job? = null

    /**
     * Finds an existing session for the given connection info, or creates a new one.
     * Updates the activity timestamp and packet count atomically.
     */
    fun processPacket(info: ConnectionInfo): ConnectionSession {
        val key = info.toFlowKey()
        val now = System.currentTimeMillis()

        return sessions.compute(key) { _, current ->
            if (current == null) {
                DevMetrics.recordSessionCreated()
                ConnectionSession(
                    flowKey = key,
                    connectionInfo = info,
                    createdAt = now,
                    lastActivityAt = now,
                    state = ConnectionState.New,
                    packetCount = 1
                )
            } else {
                current.copy(
                    lastActivityAt = now,
                    state = ConnectionState.Active,
                    packetCount = current.packetCount + 1
                )
            }
        }!!
    }

    /**
     * Starts the periodic cleanup routine for expired sessions.
     * Ties the lifecycle of the cleanup directly to the provided scope
     * (which should be the VPN service scope).
     */
    fun startCleanup(scope: CoroutineScope) {
        if (cleanupJob?.isActive == true) return

        cleanupJob = scope.launch {
            while (isActive) {
                delay(CLEANUP_INTERVAL_MS)
                cleanupExpiredSessions()
            }
        }
    }

    /**
     * Stops the cleanup routine when the VPN is torn down.
     */
    fun stopCleanup() {
        cleanupJob?.cancel()
        cleanupJob = null
        sessions.clear() // Clear memory on shutdown
    }

    private fun cleanupExpiredSessions() {
        val now = System.currentTimeMillis()
        var expiredCount = 0
        
        val iterator = sessions.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val session = entry.value
            
            val timeout = if (session.flowKey.protocol == PROTOCOL_TCP) {
                TCP_TIMEOUT_MS
            } else {
                UDP_TIMEOUT_MS
            }

            if (now - session.lastActivityAt > timeout) {
                iterator.remove()
                expiredCount++
            }
        }
        
        if (expiredCount > 0) {
            DevMetrics.recordSessionsExpired(expiredCount)
        }
        DevMetrics.recordCleanupCycle(sessions.size)
    }

    companion object {
        private const val PROTOCOL_TCP = 6
        
        // Configurable timeouts based on typical network stack behaviors.
        // TCP typically requires a longer idle timeout (e.g., 15 minutes) for long-lived keepalives.
        const val TCP_TIMEOUT_MS = 15L * 60L * 1000L // 15 minutes
        
        // UDP is connectionless; most NATs drop UDP mappings after 2-3 minutes of inactivity.
        const val UDP_TIMEOUT_MS = 3L * 60L * 1000L // 3 minutes
        
        // Run the cleanup sweep every 60 seconds. Frequent enough to reclaim memory quickly,
        // sparse enough to consume negligible CPU overhead.
        const val CLEANUP_INTERVAL_MS = 60_000L
    }
}
