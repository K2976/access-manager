package dev.kartik.accessmanager.data.repository

import dev.kartik.accessmanager.data.mapper.PolicyMapper
import dev.kartik.accessmanager.database.dao.PolicyDao
import dev.kartik.accessmanager.domain.model.AccessState
import dev.kartik.accessmanager.domain.repository.PolicyRepository
import dev.kartik.accessmanager.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Room-backed implementation of [PolicyRepository].
 *
 * Policies persist across process death and device reboot.
 * The DAO's Flow ensures reactive updates — any database write
 * automatically triggers a new emission to all observers.
 */
class PolicyRepositoryImpl @Inject constructor(
    private val policyDao: PolicyDao,
    @ApplicationScope private val externalScope: CoroutineScope,
) : PolicyRepository {

    // Eagerly cached in-memory state, synchronized with Room.
    private val cachedPolicies: StateFlow<Map<String, AccessState>> =
        policyDao.observeAll()
            .map { entities ->
                entities.associate { entity ->
                    entity.packageName to PolicyMapper.entityToAccessState(entity)
                }
            }
            .stateIn(
                scope = externalScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyMap()
            )

    override val policies: Flow<Map<String, AccessState>> = cachedPolicies

    override suspend fun setPolicy(packageName: String, state: AccessState) {
        policyDao.upsert(PolicyMapper.toEntity(packageName, state))
    }

    override suspend fun togglePolicy(packageName: String) {
        val current = getPolicy(packageName)
        setPolicy(packageName, current.toggle())
    }

    override suspend fun getPolicy(packageName: String): AccessState {
        val cache = cachedPolicies.value
        
        // The cache is the source of truth in memory.
        // If the key is present, it's a hit. If not, it's a "miss" in the sense 
        // that no explicit policy exists, but we return the default Allowed state
        // instantly without a DB lookup. Both cases are now O(1) in-memory operations.
        if (cache.containsKey(packageName)) {
            return cache[packageName]!!
        } else {
            return AccessState.Allowed
        }
    }

    override suspend fun clearAll() {
        policyDao.deleteAll()
    }
}
