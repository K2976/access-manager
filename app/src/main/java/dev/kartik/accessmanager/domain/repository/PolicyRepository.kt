package dev.kartik.accessmanager.domain.repository

import dev.kartik.accessmanager.domain.model.AccessState
import kotlinx.coroutines.flow.Flow

/**
 * Repository for application access policies.
 *
 * Responsible only for storing and exposing policy state.
 * Does not know about installed apps, VPN, or persistence.
 */
interface PolicyRepository {
    /** Observe all policies as a map of packageName → AccessState. */
    val policies: Flow<Map<String, AccessState>>

    /** Set the access state for a specific package. */
    suspend fun setPolicy(packageName: String, state: AccessState)

    /** Toggle the access state for a specific package. Default is Allowed. */
    suspend fun togglePolicy(packageName: String)

    /** Get the current state for a specific package. Returns Allowed if no policy exists. */
    suspend fun getPolicy(packageName: String): AccessState

    /** Remove all policies. */
    suspend fun clearAll()
}
