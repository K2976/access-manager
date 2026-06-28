package dev.kartik.accessmanager.domain.usecase

import dev.kartik.accessmanager.domain.repository.PolicyRepository
import javax.inject.Inject

/**
 * Toggles an application's access state between Allowed and Blocked.
 */
class TogglePolicyUseCase @Inject constructor(
    private val policyRepository: PolicyRepository,
) {
    suspend operator fun invoke(packageName: String) =
        policyRepository.togglePolicy(packageName)
}
