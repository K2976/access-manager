package dev.kartik.accessmanager.data.repository

import dev.kartik.accessmanager.data.datasource.PackageManagerDataSource
import dev.kartik.accessmanager.domain.model.InstalledApp
import dev.kartik.accessmanager.domain.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AppRepositoryImpl @Inject constructor(
    private val packageManagerDataSource: PackageManagerDataSource,
) : AppRepository {

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    override val installedApps: Flow<List<InstalledApp>> = _installedApps.asStateFlow()

    override suspend fun refresh() {
        val apps = withContext(Dispatchers.IO) {
            packageManagerDataSource.getInstalledApps()
        }
        _installedApps.value = apps
    }
}
