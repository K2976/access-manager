package dev.kartik.accessmanager.vpn.packet

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.IOException
import javax.inject.Inject

/**
 * Implementation of [PacketReader] that performs blocking I/O on [Dispatchers.IO].
 */
class PacketReaderImpl @Inject constructor() : PacketReader {

    override fun read(vpnInterface: ParcelFileDescriptor): Flow<RawPacket> = channelFlow {
        withContext(Dispatchers.IO) {
            val inputStream = FileInputStream(vpnInterface.fileDescriptor)
            
            // 32767 is a safe upper bound for typical VPN MTUs (which are usually ~1500).
            // We allocate this buffer ONCE per VPN session and reuse it for every packet.
            val buffer = ByteArray(32767)
            var packetId = 0L

            try {
                while (isActive) {
                    val length = inputStream.read(buffer)
                    if (length > 0) {
                        packetId++
                        // We must copy the data out of the reusable buffer so that
                        // subsequent reads do not overwrite the packet being processed.
                        val packetData = buffer.copyOfRange(0, length)
                        val packet = RawPacket(packetData)
                        Log.d("AM-S01", "PKT=$packetId len=$length")
                        
                        // Send blocks if the channel buffer is full, providing backpressure.
                        send(packet)
                    } else if (length == -1) {
                        // EOF reached, meaning the tunnel was closed.
                        break
                    }
                }
            } catch (e: IOException) {
                // An IOException (specifically EBADF) is expected when the VPN stops
                // and the ParcelFileDescriptor is closed from another thread.
                Log.d("PacketReader", "Read loop terminated: ${e.message}")
            }
        }
    }
}
