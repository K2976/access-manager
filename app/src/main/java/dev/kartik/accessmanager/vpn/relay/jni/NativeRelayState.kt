package dev.kartik.accessmanager.vpn.relay.jni

/**
 * Represents the lifecycle state of the native networking backend.
 * This state is strongly typed and safely insulates the Kotlin layer
 * from raw C++ enum values.
 */
sealed interface NativeRelayState {
    /** The native library has not been loaded into memory yet. */
    data object Unloaded : NativeRelayState
    
    /** The native library is currently being loaded and initialized via JNI. */
    data object Loading : NativeRelayState

    /** The native library is loaded and initialized, but not processing traffic. */
    data object Initialized : NativeRelayState

    /** The native backend is preparing to start its worker thread. */
    data object Starting : NativeRelayState
    
    /** The native library is actively processing traffic. */
    data object Running : NativeRelayState

    /** The native backend has received a stop command and is tearing down. */
    data object Stopping : NativeRelayState
    
    /** The native library has been cleanly shut down. */
    data object Stopped : NativeRelayState
    
    /** The native library failed to load, initialize, or encountered a fatal runtime error. */
    data class Failed(val reason: String, val cause: Throwable? = null) : NativeRelayState
}
