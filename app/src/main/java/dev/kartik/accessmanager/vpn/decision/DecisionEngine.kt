package dev.kartik.accessmanager.vpn.decision

import dev.kartik.accessmanager.domain.model.AccessState
import dev.kartik.accessmanager.domain.repository.PolicyRepository
import dev.kartik.accessmanager.vpn.packet.ConnectionInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The core rules engine that determines whether a connection should be allowed or blocked.
 *
 * This engine only produces a [Decision]. It does not modify packets, forward traffic,
 * or manage sockets. It is completely independent of the networking layer.
 */
@Singleton
class DecisionEngine @Inject constructor(
    private val policyRepository: PolicyRepository,
) {

    /**
     * Evaluates a connection against the user's access policies.
     *
     * @return The [Decision] that should be applied to this connection.
     */
    suspend fun evaluate(info: ConnectionInfo): Decision {
        if (info.uid == null || info.packageNames.isEmpty()) {
            // If we cannot identify the application that originated the packet,
            // we return Unknown. The default security posture for this application
            // will treat Unknown as Allow to prevent bricking core OS networking,
            // but the engine itself just reports Unknown.
            android.util.Log.d("AM-S03", "decision=Unknown uid=${info.uid} pkg=${info.packageNames} reason=unresolved")
            return Decision.Unknown("Unresolved UID or package")
        }

        var decision: Decision = Decision.Allow

        // Multiple packages can share the same UID in Android.
        // We evaluate policies for all packages associated with this connection.
        // If ANY package sharing this UID is blocked, the connection is blocked.
        for (packageName in info.packageNames) {
            val state = policyRepository.getPolicy(packageName)
            if (state == AccessState.Blocked) {
                decision = Decision.Block
                break // No need to check other packages if one is already blocked
            }
        }

        android.util.Log.d("AM-S03", "decision=$decision uid=${info.uid} pkg=${info.packageNames.firstOrNull()}")
        return decision
    }
}
