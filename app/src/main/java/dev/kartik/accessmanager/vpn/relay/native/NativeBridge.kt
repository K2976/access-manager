package dev.kartik.accessmanager.vpn.relay.native

/**
 * The strict contractual boundary between Kotlin and the JNI networking backend.
 * 
 * This interface contains absolutely no networking logic, Android UI concepts,
 * or business rules. It exists solely to manage the JNI lifecycle and pass byte arrays.
 */
interface NativeBridge {
    /**
     * Attempts to load the native shared library (e.g., .so file).
     * @return true if successful, false otherwise.
     */
    fun loadLibrary(): Boolean

    /**
     * Initializes the native runtime and registers Kotlin callbacks.
     * @return true if initialization succeeded.
     */
    fun initialize(callbacks: NativeBridgeCallbacks): Boolean

    /**
     * Commands the native layer to begin processing traffic.
     */
    fun start(): Boolean

    /**
     * Injects an uplink packet (received from the TUN) into the native networking stack.
     */
    fun injectUplinkPacket(packetData: ByteArray, length: Int): Boolean

    /**
     * Commands the native layer to cleanly tear down sockets and stop processing.
     */
    fun stop(): Boolean

    /**
     * Destroys the native runtime context and frees all associated C++ memory.
     */
    fun destroy()
}
