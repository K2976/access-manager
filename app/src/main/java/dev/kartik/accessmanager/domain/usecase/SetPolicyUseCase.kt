package dev.kartik.accessmanager.domain.usecase

import dev.kartik.accessmanager.domain.model.AccessState
import dev.kartik.accessmanager.domain.repository.PolicyRepository
import javax.inject.Inject

/**
 * Sets a specific access state for an application.
 */
class SetPolicyUseCase @Inject constructor(
    private val policyRepository: PolicyRepository,
) {
    suspend operator fun invoke(packageName: String, state: AccessState) =
        policyRepository.setPolicy(packageName, state)
}
