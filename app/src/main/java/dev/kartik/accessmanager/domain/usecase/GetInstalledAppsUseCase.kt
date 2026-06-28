package dev.kartik.accessmanager.domain.usecase

import dev.kartik.accessmanager.domain.model.InstalledApp
import dev.kartik.accessmanager.domain.repository.AppRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetInstalledAppsUseCase @Inject constructor(
    private val appRepository: AppRepository,
) {
    operator fun invoke(): Flow<List<InstalledApp>> = appRepository.installedApps

    suspend fun refresh() = appRepository.refresh()
}
