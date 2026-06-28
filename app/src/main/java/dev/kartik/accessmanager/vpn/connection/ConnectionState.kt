package dev.kartik.accessmanager.vpn.connection

/**
 * Strongly typed state representing the lifecycle of a ConnectionSession.
 *
 * Designed to easily support future protocol-specific states (e.g., TCP SYN_SENT)
 * without requiring an architectural redesign.
 */
sealed interface ConnectionState {
    /** The flow has just been observed for the first time. */
    data object New : ConnectionState
    
    /** The flow is actively transmitting data. */
    data object Active : ConnectionState
    
    /** The flow is established but has had no recent activity. */
    data object Idle : ConnectionState
    
    /** The flow is in the process of terminating. */
    data object Closing : ConnectionState
    
    /** The flow is terminated or expired. */
    data object Closed : ConnectionState
}
