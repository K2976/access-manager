package dev.kartik.accessmanager.vpn.packet

import android.util.Log

object PacketVerifier {

    fun verify(packet: ByteArray) {
        if (packet.size < 20) {
            Log.e("PacketVerifier", "Malformed packet: length < 20")
            return
        }

        val version = (packet[0].toInt() and 0xF0) ushr 4
        if (version != 4) {
            Log.e("PacketVerifier", "Non-IPv4 packet: version = $version")
            return
        }

        val ihl = packet[0].toInt() and 0x0F
        val headerLength = ihl * 4
        if (packet.size < headerLength) {
            Log.e("PacketVerifier", "Malformed packet: size < headerLength")
            return
        }

        val totalLength = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        if (packet.size < totalLength) {
            Log.e("PacketVerifier", "Malformed packet: size < totalLength ($totalLength)")
            return
        }

        val computedIpChecksum = computeChecksum(packet, 0, headerLength)
        if (computedIpChecksum != 0) {
            Log.e("PacketVerifier", "IPv4 Checksum invalid: computed sum = $computedIpChecksum")
        }

        val protocol = packet[9].toInt() and 0xFF
        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)

        if (protocol == 6) { // TCP
            val tcpHeaderOffset = headerLength
            if (packet.size < tcpHeaderOffset + 20) {
                Log.e("PacketVerifier", "Malformed TCP packet: truncated header")
                return
            }

            val tcpLength = totalLength - headerLength
            val computedTcpChecksum = computeTcpUdpChecksum(packet, srcIp, dstIp, protocol, tcpHeaderOffset, tcpLength)
            if (computedTcpChecksum != 0) {
                Log.e("PacketVerifier", "TCP Checksum invalid: computed sum = $computedTcpChecksum")
            }
            
            val seq = ((packet[tcpHeaderOffset + 4].toLong() and 0xFF) shl 24) or
                      ((packet[tcpHeaderOffset + 5].toLong() and 0xFF) shl 16) or
                      ((packet[tcpHeaderOffset + 6].toLong() and 0xFF) shl 8) or
                      (packet[tcpHeaderOffset + 7].toLong() and 0xFF)
            
            val ack = ((packet[tcpHeaderOffset + 8].toLong() and 0xFF) shl 24) or
                      ((packet[tcpHeaderOffset + 9].toLong() and 0xFF) shl 16) or
                      ((packet[tcpHeaderOffset + 10].toLong() and 0xFF) shl 8) or
                      (packet[tcpHeaderOffset + 11].toLong() and 0xFF)
                      
            Log.i("PacketVerifier", "TCP OK - Seq: $seq, Ack: $ack, Len: $totalLength")

        } else if (protocol == 17) { // UDP
            val udpHeaderOffset = headerLength
            if (packet.size < udpHeaderOffset + 8) return
            
            val udpLength = totalLength - headerLength
            val computedUdpChecksum = computeTcpUdpChecksum(packet, srcIp, dstIp, protocol, udpHeaderOffset, udpLength)
            if (computedUdpChecksum != 0 && computedUdpChecksum != 0xFFFF) {
                Log.e("PacketVerifier", "UDP Checksum invalid: computed sum = $computedUdpChecksum")
            } else {
                Log.i("PacketVerifier", "UDP OK - Len: $totalLength")
            }
        }
    }

    private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = 0
        while (i < length - 1) {
            val word = ((data[offset + i].toInt() and 0xFF) shl 8) or (data[offset + i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < length) {
            val word = (data[offset + i].toInt() and 0xFF) shl 8
            sum += word
        }
        
        while ((sum ushr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return (sum.inv()) and 0xFFFF
    }

    private fun computeTcpUdpChecksum(packet: ByteArray, srcIp: ByteArray, dstIp: ByteArray, protocol: Int, offset: Int, length: Int): Int {
        var sum = 0
        
        // Pseudo header
        for (i in 0 until 4 step 2) {
            sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
            sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
        }
        sum += protocol
        sum += length
        
        // Payload
        var i = 0
        while (i < length - 1) {
            val word = ((packet[offset + i].toInt() and 0xFF) shl 8) or (packet[offset + i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < length) {
            val word = (packet[offset + i].toInt() and 0xFF) shl 8
            sum += word
        }
        
        while ((sum ushr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return (sum.inv()) and 0xFFFF
    }
}
