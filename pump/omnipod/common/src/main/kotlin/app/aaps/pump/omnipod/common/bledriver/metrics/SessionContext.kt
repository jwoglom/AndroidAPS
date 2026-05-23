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
    // Last sequence number we sent in commandAttempt. The pod echoes back its
    // own sequenceNumberOfLastProgrammingCommand on every status response; we
    // compare against this to spot desyncs (recorded on pod_status_snapshot).
    @Volatile var lastSentSeq: Int? = null

    val cmdSent = java.util.concurrent.atomic.AtomicInteger(0)
    val cmdFailed = java.util.concurrent.atomic.AtomicInteger(0)
    val busyRejectedCount = java.util.concurrent.atomic.AtomicInteger(0)
    val endEmitted = AtomicBoolean(false)

    // Set to true when DashMetrics.eapAkaPhase() fires. Gates emission of
    // eap_aka_sequence_number in session_end so first sessions that never reach
    // EAP-AKA don't report a misleading default of 1.
    @Volatile var eapAkaPhaseOccurred: Boolean = false

    // Updated whenever a command completes with outcome == "ok"; lets sessionEnd
    // report the idle gap (ms since last successful command) before disconnect.
    @Volatile var lastCommandOkMonoNs: Long? = null

    // Link-state samples populated from GATT callbacks; surfaced as rollups on
    // session_end so an analyst can ask "was this session weak?" without folding
    // the rssi_sample stream.
    @Volatile var lastRssiDbm: Int? = null
    @Volatile var minRssiDbm: Int? = null
    @Volatile var maxRssiDbm: Int? = null
    val rssiSamplesCount = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile var lastMtuBytes: Int? = null
    @Volatile var lastPhyTx: String? = null
    @Volatile var lastPhyRx: String? = null

    // Last sampled environment state. Used by env_sample change detection so
    // the idle poll only emits when something has actually changed since the
    // previous sample (or since session_start, which seeds these).
    @Volatile var lastEnvBatteryBucket: Int? = null
    @Volatile var lastEnvAppState: String? = null
    @Volatile var lastEnvPowerSave: Boolean? = null
    @Volatile var lastEnvDeviceIdle: Boolean? = null
    @Volatile var lastEnvLocationOn: Boolean? = null
    @Volatile var lastEnvBtAdapterState: String? = null
    @Volatile var lastEnvIsCharging: Boolean? = null

    @Synchronized
    fun recordRssiSample(rssi: Int) {
        lastRssiDbm = rssi
        val cur = minRssiDbm
        if (cur == null || rssi < cur) minRssiDbm = rssi
        val curMax = maxRssiDbm
        if (curMax == null || rssi > curMax) maxRssiDbm = rssi
        rssiSamplesCount.incrementAndGet()
    }

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
