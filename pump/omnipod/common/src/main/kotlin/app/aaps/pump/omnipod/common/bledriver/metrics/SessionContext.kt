package app.aaps.pump.omnipod.common.bledriver.metrics

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class SessionContext(
    val sessionId: String = UUID.randomUUID().toString(),
    val tStartEpochMs: Long = System.currentTimeMillis(),
    val tStartMonoNs: Long = System.nanoTime()
) {

    @Volatile var podHash: String? = null
    @Volatile var macHash: String? = null

    @Volatile var commandInFlight: String? = null
    @Volatile var tCmdStartMonoNs: Long? = null
    @Volatile var tSendDoneMonoNs: Long? = null
    @Volatile var lastSendRetries: Int = 0
    @Volatile var lifecycle: String = "starting"

    val cmdSent = java.util.concurrent.atomic.AtomicInteger(0)
    val cmdFailed = java.util.concurrent.atomic.AtomicInteger(0)
    val endEmitted = AtomicBoolean(false)

    private val phaseStarts = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun beginPhase(name: String) { phaseStarts[name] = System.nanoTime(); lifecycle = name }
    fun endPhase(name: String): Long {
        val start = phaseStarts.remove(name) ?: return 0L
        return (System.nanoTime() - start) / 1_000_000L
    }

    fun fillPodHashIfMissing(uniqueId: Long?) {
        if (podHash == null) podHash = PodIdHasher.hashPodId(uniqueId)
    }

    fun fillMacHashIfMissing(address: String?) {
        if (macHash == null) macHash = PodIdHasher.hashMac(address)
    }
}
