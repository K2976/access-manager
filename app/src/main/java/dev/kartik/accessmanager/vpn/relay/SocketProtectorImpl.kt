package dev.kartik.accessmanager.vpn.relay

import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocketProtectorImpl @Inject constructor() : SocketProtector {
    private var protectorRef: WeakReference<SocketProtector>? = null
    
    fun register(protector: SocketProtector) {
        protectorRef = WeakReference(protector)
    }
    
    fun unregister() {
        protectorRef = null
    }

    override fun protect(socket: Int): Boolean {
        val p = protectorRef?.get()
        return p?.protect(socket) ?: false
    }
}
