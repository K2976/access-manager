package dev.kartik.accessmanager.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.kartik.accessmanager.MainActivity
import dev.kartik.accessmanager.vpn.connection.ConnectionManager
import dev.kartik.accessmanager.vpn.decision.DecisionEngine
import dev.kartik.accessmanager.vpn.model.VpnState
import dev.kartik.accessmanager.vpn.packet.ConnectionResolver
import dev.kartik.accessmanager.vpn.packet.PacketReader
import dev.kartik.accessmanager.vpn.relay.RelayEngine
import dev.kartik.accessmanager.vpn.relay.SocketProtector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * VPN service that manages the VPN tunnel lifecycle and initiates packet capture.
 *
 * This sprint establishes the lifecycle and raw packet capture — no packet parsing,
 * no socket creation, no relay, no filtering.
 */
@AndroidEntryPoint
class AccessManagerVpnService : VpnService(), SocketProtector {

    @Inject
    lateinit var vpnManager: VpnManager
    
    @Inject
    lateinit var packetReader: PacketReader
    
    @Inject
    lateinit var connectionResolver: ConnectionResolver
    
    @Inject
    lateinit var connectionManager: ConnectionManager

    @Inject
    lateinit var decisionEngine: DecisionEngine

    @Inject
    lateinit var relayEngine: RelayEngine

    @Inject
    lateinit var socketProtector: dev.kartik.accessmanager.vpn.relay.SocketProtectorImpl

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnOutputStream: java.io.FileOutputStream? = null
    private var serviceScope: CoroutineScope? = null
    
    private val lifecycleMutex = Mutex()
    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var downlinkJob: Job? = null
    private var uplinkJob: Job? = null
    
    private val isStopping = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        socketProtector.register(this)
        lifecycleScope.launch {
            lifecycleMutex.withLock {
                when (intent?.action) {
                    ACTION_STOP -> stopVpn()
                    else -> startVpn()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        socketProtector.unregister()
        lifecycleScope.launch {
            lifecycleMutex.withLock { stopVpn() }
            lifecycleScope.cancel()
        }
        super.onDestroy()
    }

    override fun onRevoke() {
        socketProtector.unregister()
        lifecycleScope.launch {
            lifecycleMutex.withLock { stopVpn() }
        }
        super.onRevoke()
    }

    private suspend fun startVpn() {
        if (isStopping.get()) return
        if (vpnManager.state.value is VpnState.Running) return
        
        try {
            startForeground(NOTIFICATION_ID, createNotification())

            // Initialize a new CoroutineScope for this VPN session
            serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            vpnInterface = Builder()
                .setSession(SESSION_NAME)
                .addAddress(VPN_ADDRESS, VPN_PREFIX_LENGTH)
                .addRoute(VPN_ROUTE, VPN_ROUTE_PREFIX_LENGTH)
                .setMtu(VPN_MTU) // 1500 to match typical network conditions and our buffer size
                .setBlocking(true) // Blocking is fine; our read loop runs on Dispatchers.IO
                .establish()

            val pfd = vpnInterface
            if (pfd == null) {
                vpnManager.updateState(VpnState.Error("Failed to establish VPN interface"))
                stopSelf()
                return
            }

            // Create the single FileOutputStream bound to the VPN interface lifecycle
            val outputStream = java.io.FileOutputStream(pfd.fileDescriptor)
            vpnOutputStream = outputStream
            
            // Start periodic cleanup for the connection manager tied to this VPN session
            connectionManager.startCleanup(serviceScope!!)
            
            // Start the relay engine backend
            relayEngine.start()
            
            var packetsReceived = 0L
            var packetsWritten = 0L
            var bytesWritten = 0L
            var writeExceptions = 0L

            // 1. Downlink Pipeline: Remote Internet -> RelayEngine -> TUN Interface
            downlinkJob = relayEngine.downlinkPackets
                .onEach { packet ->
                    packetsReceived++
                    
                    // Stage 16: Detect TCP SYN-ACK to track handshake completion
                    val data = packet.data
                    if (data.size >= 34) {
                        val ipVersion = (data[0].toInt() and 0xF0) ushr 4
                        if (ipVersion == 4) {
                            val protocol = data[9].toInt() and 0xFF
                            val ihl = (data[0].toInt() and 0x0F) * 4
                            if (protocol == 6 && data.size >= ihl + 14) {
                                val flags = data[ihl + 13].toInt() and 0xFF
                                val isSyn = (flags and 0x02) != 0
                                val isAck = (flags and 0x10) != 0
                                if (isSyn && isAck) {
                                    Log.i("AM-S16", "SYN-ACK detected! len=${data.size} -> writing to TUN")
                                }
                            }
                        }
                    }
                    
                    try {
                        outputStream.write(packet.data)
                        outputStream.flush()
                        packetsWritten++
                        bytesWritten += packet.data.size
                        Log.d("AM-S15", "TUN write OK len=${packet.data.size} total_rx=$packetsReceived total_wx=$packetsWritten bytes=$bytesWritten")
                    } catch (e: Exception) {
                        writeExceptions++
                        Log.e("AM-S15", "TUN write FAILED: ${e.message} err_count=$writeExceptions")
                    }
                }
                .launchIn(serviceScope!!)

            // 2. Uplink Pipeline: TUN Interface -> VPN Service -> RelayEngine -> Remote Internet
            uplinkJob = packetReader.read(pfd)
                .onEach { packet ->
                    // Stage 16: Detect TCP SYN (outgoing connection attempt) and ACK (handshake completion)
                    val data = packet.data
                    if (data.size >= 34) {
                        val ipVersion = (data[0].toInt() and 0xF0) ushr 4
                        if (ipVersion == 4) {
                            val protocol = data[9].toInt() and 0xFF
                            val ihl = (data[0].toInt() and 0x0F) * 4
                            if (protocol == 6 && data.size >= ihl + 14) {
                                val flags = data[ihl + 13].toInt() and 0xFF
                                val isSyn = (flags and 0x02) != 0
                                val isAck = (flags and 0x10) != 0
                                val isFin = (flags and 0x01) != 0
                                if (isSyn && !isAck) {
                                    Log.i("AM-S16", "CLIENT SYN detected (new connection)")
                                } else if (isAck && !isSyn && !isFin) {
                                    Log.i("AM-S16", "CLIENT ACK detected (handshake may be complete)")
                                }
                            }
                        }
                    }
                    
                    val info = connectionResolver.resolve(packet) ?: return@onEach
                    val session = connectionManager.processPacket(info)
                    val decision = decisionEngine.evaluate(session.connectionInfo)
                    
                    if (decision is dev.kartik.accessmanager.vpn.decision.Decision.Allow) {
                        relayEngine.enqueueUplink(packet)
                    } else if (decision is dev.kartik.accessmanager.vpn.decision.Decision.Unknown) {
                        // Minimal Fix: Only allow Unknown traffic if it is DNS (UDP port 53).
                        // This prevents QUIC (UDP 443) from blocked apps bypassing the firewall 
                        // and exhausting native POSIX file descriptors.
                        val isDns = session.connectionInfo.protocol == 17 && session.connectionInfo.destinationPort == 53
                        if (isDns) {
                            relayEngine.enqueueUplink(packet)
                        } else {
                            android.util.Log.w("AM-FIREWALL", "Dropped Unknown non-DNS packet to port ${session.connectionInfo.destinationPort}")
                        }
                    }
                }
                .launchIn(serviceScope!!)

            vpnManager.updateState(VpnState.Running)
        } catch (e: Exception) {
            vpnManager.updateState(VpnState.Error(e.message ?: "Failed to start VPN"))
            stopSelf()
        }
    }

    private suspend fun stopVpn() {
        if (!isStopping.compareAndSet(false, true)) return
        
        try {
            // 1. Close FileDescriptors FIRST to unblock packetReader IO
            vpnOutputStream?.close()
            vpnOutputStream = null
            vpnInterface?.close()
            vpnInterface = null
            
            // 2. Cancel the coroutine scope which interrupts the flow collectors
            serviceScope?.cancel()
            
            // 3. Wait for downlinks and uplinks to completely terminate
            downlinkJob?.join()
            uplinkJob?.join()
            downlinkJob = null
            uplinkJob = null
            serviceScope = null

            // 4. Stop RelayEngine safely now that no coroutines are running
            relayEngine.stop()
            
            // 5. Stop ConnectionManager cleanup
            connectionManager.stopCleanup()
        } catch (_: Exception) {
            // Best-effort cleanup
        } finally {
            isStopping.set(false)
            vpnManager.updateState(VpnState.Stopped)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotification(): Notification {
        ensureNotificationChannel()

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = Intent(this, AccessManagerVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AccessManager Active")
            .setContentText("Managing application Internet access")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent,
            )
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Notification shown while AccessManager VPN is active"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "dev.kartik.accessmanager.vpn.START"
        const val ACTION_STOP = "dev.kartik.accessmanager.vpn.STOP"

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "vpn_service"
        private const val CHANNEL_NAME = "VPN Service"
        private const val SESSION_NAME = "AccessManager"

        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_PREFIX_LENGTH = 32
        private const val VPN_ROUTE = "0.0.0.0"
        private const val VPN_ROUTE_PREFIX_LENGTH = 0
        private const val VPN_MTU = 1500
    }
}
