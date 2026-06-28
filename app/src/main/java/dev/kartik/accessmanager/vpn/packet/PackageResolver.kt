package dev.kartik.accessmanager.vpn.packet

import android.content.pm.PackageManager
import javax.inject.Inject

/**
 * Resolves an Android UID to its associated package name(s).
 */
class PackageResolver @Inject constructor(
    private val packageManager: PackageManager,
) {

    /**
     * Returns a list of package names associated with the given [uid].
     * Can return multiple packages if they share the same UID.
     * Returns an empty list if the UID cannot be resolved.
     */
    fun resolvePackages(uid: Int): List<String> {
        return packageManager.getPackagesForUid(uid)?.toList() ?: emptyList()
    }
}
