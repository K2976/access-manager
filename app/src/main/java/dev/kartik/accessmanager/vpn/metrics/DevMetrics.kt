package dev.kartik.accessmanager.vpn.metrics

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight development metrics for cache performance tuning.
 *
 * All counters use AtomicInteger to remain thread-safe without explicit locks,
 * minimizing contention in the hot path.
 */
object DevMetrics {
    private val uidHits = AtomicInteger(0)
    private val uidMisses = AtomicInteger(0)
    
    private val policyHits = AtomicInteger(0)
    private val policyMisses = AtomicInteger(0)
    
    private val sessionsCreated = AtomicInteger(0)
    private val sessionsExpired = AtomicInteger(0)
    
    private val jniFailures = AtomicInteger(0)
    private val nativePacketsSent = AtomicInteger(0)
    private val nativePacketsReceived = AtomicInteger(0)
    
    fun recordUidCacheHit() { uidHits.incrementAndGet(); logMetricsIfNeeded() }
    fun recordUidCacheMiss() { uidMisses.incrementAndGet(); logMetricsIfNeeded() }
    
    fun recordPolicyCacheHit() { policyHits.incrementAndGet(); logMetricsIfNeeded() }
    fun recordPolicyCacheMiss() { policyMisses.incrementAndGet(); logMetricsIfNeeded() }
    
    fun recordSessionCreated() { sessionsCreated.incrementAndGet(); logMetricsIfNeeded() }
    fun recordSessionsExpired(count: Int) { sessionsExpired.addAndGet(count); logMetricsIfNeeded() }
    
    fun recordCleanupCycle(activeCount: Int) {
        Log.d("DevMetrics", "Cleanup Cycle Complete. Active sessions: $activeCount")
    }
    
    fun recordJniFailure() { jniFailures.incrementAndGet(); Log.e("DevMetrics", "JNI Failure recorded") }
    fun recordNativePacketSent() { nativePacketsSent.incrementAndGet() }
    fun recordNativePacketReceived() { nativePacketsReceived.incrementAndGet() }
    fun recordNativeStartupTime(nanos: Long) { Log.i("DevMetrics", "Native loaded in ${nanos / 1_000_000}ms") }
    fun recordNativeCallbackLatency(nanos: Long) { /* Highly frequent, better to aggregate in production */ }
    
    fun recordMalformedPacket() { Log.e("DevMetrics", "Malformed packet injected") }
    
    fun recordNativeMetrics(
        packetsReceived: Long,
        packetsRejected: Long,
        pbufAllocations: Long,
        pbufAllocationFailures: Long,
        actorQueueDepth: Long,
        avgMailboxLatencyNanos: Long,
        avgProcessingLatencyNanos: Long
    ) {
        Log.d("DevMetrics", "Native Stats - Rx: $packetsReceived, Rej: $packetsRejected, Q: $actorQueueDepth, " +
                "pbufs [allocs: $pbufAllocations, fails: $pbufAllocationFailures], " +
                "Latencies [mbox: ${avgMailboxLatencyNanos}ns, proc: ${avgProcessingLatencyNanos}ns]")
    }
    
    private fun logMetricsIfNeeded() {
        val total = uidHits.get() + uidMisses.get() + sessionsCreated.get()
        // Log every 1000 packets to avoid logcat spam
        if (total > 0 && total % 1000 == 0) {
            Log.d("DevMetrics", 
                "Cache Stats [1000 packets]:\n" +
                "UID:    ${uidHits.get()} hits, ${uidMisses.get()} misses\n" +
                "Policy: ${policyHits.get()} hits, ${policyMisses.get()} misses"
            )
        }
    }
}
