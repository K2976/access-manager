package dev.kartik.accessmanager.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.kartik.accessmanager.MainActivity
import dev.kartik.accessmanager.vpn.model.VpnState
import javax.inject.Inject

/**
 * VPN service that manages the VPN tunnel lifecycle.
 *
 * This sprint establishes only the lifecycle — no packet processing,
 * no socket creation, no relay, no filtering.
 */
@AndroidEntryPoint
class AccessManagerVpnService : VpnService() {

    @Inject
    lateinit var vpnManager: VpnManager

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVpn()
            else -> startVpn()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    private fun startVpn() {
        try {
            startForeground(NOTIFICATION_ID, createNotification())

            vpnInterface = Builder()
                .setSession(SESSION_NAME)
                .addAddress(VPN_ADDRESS, VPN_PREFIX_LENGTH)
                .addRoute(VPN_ROUTE, VPN_ROUTE_PREFIX_LENGTH)
                .setBlocking(false)
                .establish()

            if (vpnInterface == null) {
                vpnManager.updateState(VpnState.Error("Failed to establish VPN interface"))
                stopSelf()
                return
            }

            vpnManager.updateState(VpnState.Running)
        } catch (e: Exception) {
            vpnManager.updateState(VpnState.Error(e.message ?: "Failed to start VPN"))
            stopSelf()
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (_: Exception) {
            // Best-effort cleanup
        }
        vpnManager.updateState(VpnState.Stopped)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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

        // Minimal VPN configuration — lifecycle only, no packet processing
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_PREFIX_LENGTH = 32
        private const val VPN_ROUTE = "0.0.0.0"
        private const val VPN_ROUTE_PREFIX_LENGTH = 0
    }
}
