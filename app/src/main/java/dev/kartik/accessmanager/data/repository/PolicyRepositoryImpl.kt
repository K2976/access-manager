package dev.kartik.accessmanager.data.repository

import dev.kartik.accessmanager.domain.model.AccessState
import dev.kartik.accessmanager.domain.repository.PolicyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * In-memory implementation of [PolicyRepository].
 *
 * All state is lost when the process dies. This is intentional for Sprint 03.
 * Persistence will be added in Sprint 04.
 */
class PolicyRepositoryImpl @Inject constructor() : PolicyRepository {

    private val store = ConcurrentHashMap<String, AccessState>()
    private val _policies = MutableStateFlow<Map<String, AccessState>>(emptyMap())

    override val policies: Flow<Map<String, AccessState>> = _policies.asStateFlow()

    override suspend fun setPolicy(packageName: String, state: AccessState) {
        store[packageName] = state
        emitCurrentState()
    }

    override suspend fun togglePolicy(packageName: String) {
        val current = store.getOrDefault(packageName, AccessState.Allowed)
        store[packageName] = current.toggle()
        emitCurrentState()
    }

    override suspend fun getPolicy(packageName: String): AccessState =
        store.getOrDefault(packageName, AccessState.Allowed)

    override suspend fun clearAll() {
        store.clear()
        emitCurrentState()
    }

    private fun emitCurrentState() {
        _policies.update { store.toMap() }
    }
}
