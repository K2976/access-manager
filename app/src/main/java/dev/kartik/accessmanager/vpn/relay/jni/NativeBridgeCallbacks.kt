package dev.kartik.accessmanager.vpn.relay.jni

/**
 * Defines the callback contract allowing the native C/C++ backend
 * to asynchronously communicate back to the Kotlin layer.
 * 
 * Implementations of this interface MUST be thread-safe, as callbacks
 * will originate from arbitrary native threads.
 */
interface NativeBridgeCallbacks {
    /**
     * Invoked when the native backend synthesizes a downlink packet
     * (e.g., traffic received from a remote internet socket wrapped in TCP/IP)
     * that needs to be written to the TUN interface.
     */
    fun onDownlinkPacketReceived(packetData: ByteArray, length: Int)

    /**
     * Invoked when a fatal or non-fatal error occurs in the native layer.
     */
    fun onNativeError(errorCode: Int, message: String)

    /**
     * Invoked when the native engine changes its internal lifecycle state.
     */
    fun onNativeStateChanged(stateCode: Int)
}
