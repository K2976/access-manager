package dev.kartik.accessmanager.data.repository

import dev.kartik.accessmanager.data.mapper.PolicyMapper
import dev.kartik.accessmanager.database.dao.PolicyDao
import dev.kartik.accessmanager.domain.model.AccessState
import dev.kartik.accessmanager.domain.repository.PolicyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
) : PolicyRepository {

    override val policies: Flow<Map<String, AccessState>> =
        policyDao.observeAll().map { entities ->
            entities.associate { entity ->
                entity.packageName to PolicyMapper.entityToAccessState(entity)
            }
        }

    override suspend fun setPolicy(packageName: String, state: AccessState) {
        policyDao.upsert(PolicyMapper.toEntity(packageName, state))
    }

    override suspend fun togglePolicy(packageName: String) {
        val current = getPolicy(packageName)
        setPolicy(packageName, current.toggle())
    }

    override suspend fun getPolicy(packageName: String): AccessState {
        val entity = policyDao.getByPackageName(packageName)
        return entity?.let { PolicyMapper.entityToAccessState(it) } ?: AccessState.Allowed
    }

    override suspend fun clearAll() {
        policyDao.deleteAll()
    }
}
