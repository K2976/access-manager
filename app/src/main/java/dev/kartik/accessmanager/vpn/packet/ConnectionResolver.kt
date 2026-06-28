package dev.kartik.accessmanager.vpn.packet

import android.net.ConnectivityManager
import android.os.Process
import java.net.InetSocketAddress
import javax.inject.Inject

/**
 * Central component responsible for extracting connection metadata
 * and resolving the owning application UID.
 *
 * This component intentionally separates parsing from policy decisions.
 */
class ConnectionResolver @Inject constructor(
    private val connectivityManager: ConnectivityManager,
    private val packageResolver: PackageResolver,
) {

    /**
     * Accepts a [RawPacket], parses its headers, resolves its owner UID,
     * and maps it to installed package names.
     *
     * @return A fully resolved [ConnectionInfo], or null if the packet
     *         could not be parsed or is unsupported.
     */
    fun resolve(packet: RawPacket): ConnectionInfo? {
        val baseInfo = PacketHeaderParser.parse(packet) ?: return null

        val localAddress = InetSocketAddress(baseInfo.sourceAddress, baseInfo.sourcePort)
        val remoteAddress = InetSocketAddress(baseInfo.destinationAddress, baseInfo.destinationPort)

        val uid = resolveUid(baseInfo.protocol, localAddress, remoteAddress)
        
        val packages = if (uid != null) {
            packageResolver.resolvePackages(uid)
        } else {
            emptyList()
        }

        return baseInfo.copy(
            uid = uid,
            packageNames = packages,
        )
    }

    private fun resolveUid(
        protocol: Int,
        local: InetSocketAddress,
        remote: InetSocketAddress,
    ): Int? {
        return try {
            val uid = connectivityManager.getConnectionOwnerUid(protocol, local, remote)
            if (uid == Process.INVALID_UID) null else uid
        } catch (_: SecurityException) {
            // Can happen if the app lacks permissions, though VPN apps typically have it implicitly.
            null
        } catch (_: IllegalArgumentException) {
            // Invalid arguments or unsupported protocol/address combination.
            null
        } catch (_: Exception) {
            // Failsafe catch to prevent crashes during packet resolution.
            null
        }
    }
}
