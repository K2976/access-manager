package dev.kartik.accessmanager.vpn.model

/**
 * Strongly typed representation of VPN lifecycle state.
 *
 * Intentionally NOT a Boolean or enum. A sealed interface allows
 * [Error] to carry a reason string while keeping other states lightweight.
 */
sealed interface VpnState {
    data object Stopped : VpnState
    data object Starting : VpnState
    data object Running : VpnState
    data object Stopping : VpnState
    data class Error(val reason: String) : VpnState
}
