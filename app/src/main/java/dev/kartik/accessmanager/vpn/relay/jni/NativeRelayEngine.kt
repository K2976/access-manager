package dev.kartik.accessmanager.vpn.relay.jni

import android.util.Log
import dev.kartik.accessmanager.vpn.metrics.DevMetrics
import dev.kartik.accessmanager.vpn.packet.RawPacket
import dev.kartik.accessmanager.vpn.relay.RelayEngine
import dev.kartik.accessmanager.vpn.relay.SocketProtector
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An implementation of RelayEngine that delegates all work to the JNI NativeBridge.
 * 
 * It manages the native lifecycle safely, exposing strongly-typed states and flows
 * back to the AccessManagerVpnService.
 */
@Singleton
class NativeRelayEngine @Inject constructor(
    private val bridge: NativeBridge,
    private val socketProtector: SocketProtector
) : RelayEngine, NativeBridgeCallbacks {

    private val _relayState = MutableStateFlow<NativeRelayState>(NativeRelayState.Unloaded)
    val relayState: StateFlow<NativeRelayState> = _relayState.asStateFlow()

    // Downlink flow with a generous buffer to prevent dropping incoming internet traffic
    // if the VPN writer is temporarily suspended.
    private val _downlinkPackets = MutableSharedFlow<RawPacket>(
        replay = 0,
        extraBufferCapacity = 1024,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    
    override val downlinkPackets: Flow<RawPacket> = _downlinkPackets.asSharedFlow()

    override fun start() {
        if (_relayState.value is NativeRelayState.Running) return
        
        _relayState.value = NativeRelayState.Loading
        
        val startNanos = System.nanoTime()
        val loaded = bridge.loadLibrary()
        
        if (!loaded) {
            _relayState.value = NativeRelayState.Failed("Failed to load native library (missing or unsupported architecture).")
            return
        }

        val initialized = bridge.initialize(this)
        if (!initialized) {
            _relayState.value = NativeRelayState.Failed("Failed to initialize native bridge.")
            return
        }

        _relayState.value = NativeRelayState.Initialized

        _relayState.value = NativeRelayState.Starting
        val started = bridge.start()
        if (!started) {
            _relayState.value = NativeRelayState.Failed("Failed to start native packet processor.")
            return
        }

        DevMetrics.recordNativeStartupTime(System.nanoTime() - startNanos)
        _relayState.value = NativeRelayState.Running
        Log.i(TAG, "NativeRelayEngine started successfully.")
    }

    override suspend fun enqueueUplink(packet: RawPacket) {
        if (_relayState.value !is NativeRelayState.Running) return

        // In a zero-copy future, we might pass packet.data direct buffer here.
        val success = bridge.injectUplinkPacket(packet.data, packet.size)
        if (success) {
            DevMetrics.recordNativePacketSent()
        }
    }

    override fun stop() {
        if (_relayState.value is NativeRelayState.Stopped || _relayState.value is NativeRelayState.Unloaded) return
        
        _relayState.value = NativeRelayState.Stopping
        bridge.stop()
        bridge.destroy()
        
        _relayState.value = NativeRelayState.Stopped
        Log.i(TAG, "NativeRelayEngine stopped successfully.")
    }

    // --- NativeBridgeCallbacks implementation ---

    override fun onDownlinkPacketReceived(packetData: ByteArray, length: Int) {
        // Copy the data out of the native array before passing it into Kotlin Flow
        // because the native stack likely reuses the same buffer on subsequent JNI calls.
        val dataCopy = packetData.copyOfRange(0, length)
        val packet = RawPacket(dataCopy)
        
        val emitted = _downlinkPackets.tryEmit(packet)
        if (emitted) {
            DevMetrics.recordNativePacketReceived()
        } else {
            Log.w(TAG, "Downlink packet buffer overflow. Packet dropped.")
        }
    }

    override fun onNativeError(errorCode: Int, message: String) {
        Log.e(TAG, "Native Error [$errorCode]: $message")
        _relayState.value = NativeRelayState.Failed("Native Error [$errorCode]: $message")
    }

    override fun onNativeStateChanged(stateCode: Int) {
        // Here we map raw int codes from C++ to Kotlin state objects, e.g.:
        // 0 = Ready, 1 = Running, 2 = Stopped
        Log.d(TAG, "Native state changed to $stateCode")
    }
    
    override fun protectSocket(fd: Int): Boolean {
        return socketProtector.protect(fd)
    }

    companion object {
        private const val TAG = "NativeRelayEngine"
    }
}
