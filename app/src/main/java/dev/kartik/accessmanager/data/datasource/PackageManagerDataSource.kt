package dev.kartik.accessmanager.data.datasource

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import dev.kartik.accessmanager.domain.model.InstalledApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Data source that reads installed applications from Android PackageManager.
 *
 * Uses a MAIN/LAUNCHER intent query to discover launchable applications,
 * which is the most reliable approach across Android 11+ package visibility
 * restrictions.
 *
 * All PackageManager access is isolated here.
 * No UI logic. No caching. No state.
 */
class PackageManagerDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun getInstalledApps(includeSystemApps: Boolean = false): List<InstalledApp> {
        val packageManager = context.packageManager

        // Query for all apps that have a launcher activity.
        // This is the canonical way to discover user-visible apps and is
        // resilient to Android 11+ package visibility filtering.
        val launchIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos: List<ResolveInfo> = packageManager.queryIntentActivities(launchIntent, 0)

        Log.d("PackageManagerDS", "queryIntentActivities returned ${resolveInfos.size} results")

        val apps = resolveInfos
            .asSequence()
            .mapNotNull { resolveInfo ->
                try {
                    val appInfo = packageManager.getApplicationInfo(
                        resolveInfo.activityInfo.packageName, 0
                    )
                    appInfo
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
            }
            .filter { includeSystemApps || !it.isSystemApp() }
            .distinctBy { it.packageName }
            .map { it.toDomainModel(packageManager) }
            .sortedBy { it.appName.lowercase() }
            .toList()

        Log.d("PackageManagerDS", "Final app list size: ${apps.size} (includeSystemApps=$includeSystemApps)")
        return apps
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
