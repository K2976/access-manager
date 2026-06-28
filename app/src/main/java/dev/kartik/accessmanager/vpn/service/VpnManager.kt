package dev.kartik.accessmanager.vpn.service

import android.content.Context
import android.content.Intent
import dev.kartik.accessmanager.vpn.model.VpnState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central manager for VPN lifecycle operations.
 *
 * The rest of the application should communicate with the VPN exclusively
 * through this manager. The UI must never interact with [AccessManagerVpnService] directly.
 */
@Singleton
class VpnManager @Inject constructor() {

    private val _state = MutableStateFlow<VpnState>(VpnState.Stopped)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    /**
     * Start the VPN service.
     *
     * Caller is responsible for ensuring VPN permission has already been granted
     * (via VpnService.prepare()) before calling this method.
     */
    fun start(context: Context) {
        if (_state.value is VpnState.Running || _state.value is VpnState.Starting) return
        _state.value = VpnState.Starting
        val intent = Intent(context, AccessManagerVpnService::class.java).apply {
            action = AccessManagerVpnService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    fun stop(context: Context) {
        if (_state.value is VpnState.Stopped || _state.value is VpnState.Stopping) return
        _state.value = VpnState.Stopping
        val intent = Intent(context, AccessManagerVpnService::class.java).apply {
            action = AccessManagerVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    /** Called by the VPN service to report its actual state. */
    internal fun updateState(state: VpnState) {
        _state.value = state
    }
}
