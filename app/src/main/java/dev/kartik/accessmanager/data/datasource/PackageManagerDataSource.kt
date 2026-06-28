package dev.kartik.accessmanager.data.datasource

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dev.kartik.accessmanager.domain.model.InstalledApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Data source that reads installed applications from Android PackageManager.
 *
 * All PackageManager access is isolated here.
 * No UI logic. No caching. No state.
 */
class PackageManagerDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun getInstalledApps(includeSystemApps: Boolean = false): List<InstalledApp> {
        val packageManager = context.packageManager
        val applications = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        return applications
            .asSequence()
            .filter { includeSystemApps || !it.isSystemApp() }
            .distinctBy { it.packageName }
            .map { it.toDomainModel(packageManager) }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    private fun ApplicationInfo.isSystemApp(): Boolean =
        flags and ApplicationInfo.FLAG_SYSTEM != 0

    private fun ApplicationInfo.toDomainModel(packageManager: PackageManager): InstalledApp =
        InstalledApp(
            packageName = packageName,
            appName = packageManager.getApplicationLabel(this).toString(),
            icon = packageManager.getApplicationIcon(this),
            uid = uid,
            isSystemApp = isSystemApp(),
        )
}
