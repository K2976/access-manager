package dev.kartik.accessmanager.vpn.packet

import android.content.pm.PackageManager
import android.util.LruCache
import dev.kartik.accessmanager.vpn.metrics.DevMetrics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves an Android UID to its associated package name(s) with an LRU cache.
 *
 * This completely isolates PackageManager IPC from the hot packet path.
 */
@Singleton
class UidPackageResolver @Inject constructor(
    private val packageManager: PackageManager,
) {
    // 512 is ample for most Android devices which typically have < 300 apps.
    // The cache is inherently thread-safe in LruCache (it synchronizes on itself).
    private val cache = LruCache<Int, List<String>>(512)

    /**
     * Returns a list of package names associated with the given [uid].
     */
    fun resolvePackages(uid: Int): List<String> {
        val cached = cache.get(uid)
        if (cached != null) {
            DevMetrics.recordUidCacheHit()
            return cached
        }

        DevMetrics.recordUidCacheMiss()
        
        val resolved = packageManager.getPackagesForUid(uid)?.toList() ?: emptyList()
        cache.put(uid, resolved)
        
        return resolved
    }
}
