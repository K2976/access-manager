package dev.kartik.accessmanager.domain.usecase

import dev.kartik.accessmanager.domain.model.AccessState
import dev.kartik.accessmanager.domain.repository.PolicyRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Observes access policies for all applications.
 */
class GetPoliciesUseCase @Inject constructor(
    private val policyRepository: PolicyRepository,
) {
    operator fun invoke(): Flow<Map<String, AccessState>> = policyRepository.policies
}
