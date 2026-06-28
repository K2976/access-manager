package dev.kartik.accessmanager.vpn.packet

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.flow.Flow

/**
 * Responsible solely for reading raw packets from the VPN interface.
 */
interface PacketReader {
    /**
     * Begins reading packets from the given [vpnInterface].
     *
     * @return A stream of captured [RawPacket]s. The stream terminates
     * when the coroutine scope is cancelled or the file descriptor is closed.
     */
    fun read(vpnInterface: ParcelFileDescriptor): Flow<RawPacket>
}
