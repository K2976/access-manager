package dev.kartik.accessmanager.domain.repository

import dev.kartik.accessmanager.domain.model.InstalledApp
import kotlinx.coroutines.flow.Flow

/**
 * Repository for installed application discovery.
 *
 * Responsible only for fetching the list of installed apps.
 * Does not handle rules, persistence, or internet state.
 */
interface AppRepository {
    val installedApps: Flow<List<InstalledApp>>
    suspend fun refresh()
}
