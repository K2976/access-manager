package dev.kartik.accessmanager.vpn.relay.jni

import android.util.Log
import dev.kartik.accessmanager.vpn.metrics.DevMetrics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of NativeBridge that defines the actual JNI method signatures.
 * 
 * It safely catches all LinkageErrors or RuntimeExceptions to guarantee the app
 * never crashes even if the native library is missing or fails to load.
 */
@Singleton
class JniNativeBridge @Inject constructor() : NativeBridge {

    private var isLoaded = false
    private var callbacks: NativeBridgeCallbacks? = null

    override fun loadLibrary(): Boolean {
        if (isLoaded) return true
        return try {
            System.loadLibrary("accessmanager-core") // Placeholder for future native lib
            isLoaded = true
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native library missing or failed to load: ${e.message}")
            DevMetrics.recordJniFailure()
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception loading native library: ${e.message}")
            DevMetrics.recordJniFailure()
            false
        }
    }

    override fun initialize(callbacks: NativeBridgeCallbacks): Boolean {
        if (!isLoaded) return false
        this.callbacks = callbacks
        return try {
            nativeInit()
            true
        } catch (e: Throwable) {
            handleNativeException("initialize", e)
            false
        }
    }

    override fun start(): Boolean {
        if (!isLoaded) return false
        return try {
            nativeStart()
            true
        } catch (e: Throwable) {
            handleNativeException("start", e)
            false
        }
    }

    override fun injectUplinkPacket(packetData: ByteArray, length: Int): Boolean {
        if (!isLoaded) return false
        return try {
            val start = System.nanoTime()
            nativeInjectUplink(packetData, length)
            DevMetrics.recordNativeCallbackLatency(System.nanoTime() - start)
            true
        } catch (e: Throwable) {
            handleNativeException("injectUplinkPacket", e)
            false
        }
    }

    override fun stop(): Boolean {
        if (!isLoaded) return false
        return try {
            nativeStop()
            true
        } catch (e: Throwable) {
            handleNativeException("stop", e)
            false
        }
    }

    override fun destroy() {
        if (!isLoaded) return
        try {
            nativeDestroy()
        } catch (e: Throwable) {
            handleNativeException("destroy", e)
        }
        callbacks = null
    }

    // --- JNI Callbacks from C++ ---

    /** Called from native C++ code to deliver a synthesized downlink packet. */
    @Suppress("unused")
    private fun jniOnDownlinkPacketReceived(packetData: ByteArray, length: Int) {
        callbacks?.onDownlinkPacketReceived(packetData, length)
    }

    /** Called from native C++ code to report errors. */
    @Suppress("unused")
    private fun jniOnNativeError(errorCode: Int, message: String) {
        DevMetrics.recordJniFailure()
        callbacks?.onNativeError(errorCode, message)
    }

    /** Called from native C++ code to report state changes. */
    @Suppress("unused")
    private fun jniOnNativeStateChanged(stateCode: Int) {
        callbacks?.onNativeStateChanged(stateCode)
    }

    private fun handleNativeException(method: String, e: Throwable) {
        Log.e(TAG, "Fatal JNI Exception in $method: ${e.message}", e)
        DevMetrics.recordJniFailure()
        callbacks?.onNativeError(-1, "Fatal JNI Exception in $method: ${e.message}")
    }

    // --- Native Method Scaffolding ---

    private external fun nativeInit()
    private external fun nativeStart()
    private external fun nativeInjectUplink(packetData: ByteArray, length: Int)
    private external fun nativeStop()
    private external fun nativeDestroy()

    companion object {
        private const val TAG = "JniNativeBridge"
    }
}
