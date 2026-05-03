package app.aaps.pump.tandem.common.concurrency

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Tri-state availability of the pump for delivery-affecting operations.
 *
 *  - [DeliveryEnabled] — ready for mutating ops
 *  - [DeliveryDisabled] — explicitly suspended (cartridge change, fill, etc.)
 *  - [Unknown] — initial / post-disconnect; treated conservatively the same as [DeliveryDisabled]
 *    until first successful status read promotes it.
 */
enum class PumpAvailability {
    DeliveryEnabled,
    DeliveryDisabled,
    Unknown;

    /** True when mutating ops are allowed. Both [DeliveryDisabled] and [Unknown] block. */
    val allowsDelivery: Boolean get() = this == DeliveryEnabled
}

/**
 * Owns the current [PumpAvailability] value with an expiry watchdog.
 *
 * Non-[PumpAvailability.DeliveryEnabled] states must be entered via [withAvailability], which
 * pairs them with a maxDuration. A periodic watchdog tick force-clears (back to [PumpAvailability.Unknown])
 * any state past its expiry, preventing a crashed workflow from permanently locking the pump.
 *
 * Thread-safe: state mutations go through `synchronized(lock)`. [observe] exposes a StateFlow.
 */
class PumpAvailabilityState(
    private val logger: AAPSLogger,
    private val watchdogTick: Duration = 5.seconds,
    private val timeSource: () -> Long = System::currentTimeMillis
) {

    private data class Entry(val state: PumpAvailability, val expiresAt: Long, val reason: String)

    private val lock = Any()
    private var entry: Entry = Entry(PumpAvailability.Unknown, Long.MAX_VALUE, "init")

    private val _flow = MutableStateFlow(PumpAvailability.Unknown)
    val state: StateFlow<PumpAvailability> = _flow.asStateFlow()

    val current: PumpAvailability get() = synchronized(lock) { entry.state }

    /** Promote from [PumpAvailability.Unknown] to [PumpAvailability.DeliveryEnabled]. Called after first successful status read. */
    fun markDeliveryEnabled(reason: String = "status read") {
        setUnchecked(PumpAvailability.DeliveryEnabled, Long.MAX_VALUE, reason)
    }

    /** Drop back to [PumpAvailability.Unknown], e.g. on disconnect. */
    fun markUnknown(reason: String) {
        setUnchecked(PumpAvailability.Unknown, Long.MAX_VALUE, reason)
    }

    /**
     * Pin availability to [state] for the duration of [block]. Restores prior state in finally.
     *
     * If [state] is non-[PumpAvailability.DeliveryEnabled], an expiry is registered at
     * `now + maxDuration`; if the watchdog fires before [block] returns, availability is forcibly
     * reset to [PumpAvailability.Unknown] and the still-running block is on its own (the dispatcher's
     * per-op timeout is the second line of defense).
     */
    suspend fun <R> withAvailability(
        state: PumpAvailability,
        maxDuration: Duration,
        reason: String,
        block: suspend () -> R
    ): R {
        val prior = synchronized(lock) { entry }
        val expiresAt = if (state == PumpAvailability.DeliveryEnabled) Long.MAX_VALUE
        else timeSource() + maxDuration.inWholeMilliseconds
        setUnchecked(state, expiresAt, reason)
        try {
            return block()
        } finally {
            // Only restore if our entry is still the active one. If the watchdog already cleared
            // us (or another withAvailability nested-replaced us), don't clobber the newer state.
            synchronized(lock) {
                if (entry.state == state && entry.reason == reason && entry.expiresAt == expiresAt) {
                    entry = prior
                    _flow.value = prior.state
                    logger.debug(LTag.PUMP, "PumpAvailability: restored to ${prior.state} after '$reason'")
                }
            }
        }
    }

    private fun setUnchecked(state: PumpAvailability, expiresAt: Long, reason: String) {
        synchronized(lock) {
            entry = Entry(state, expiresAt, reason)
            _flow.value = state
        }
        logger.debug(LTag.PUMP, "PumpAvailability: $state (reason='$reason', expiresAt=$expiresAt)")
    }

    /** Background watchdog scope. Caller owns lifecycle; cancel via [stopWatchdog]. */
    private var watchdogJob: Job? = null
    private val watchdogScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun startWatchdog() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = watchdogScope.launch {
            while (true) {
                delay(watchdogTick)
                tickOnce()
            }
        }
    }

    fun stopWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    /** Visible for test. */
    internal fun tickOnce() {
        val now = timeSource()
        synchronized(lock) {
            if (entry.state != PumpAvailability.DeliveryEnabled && now >= entry.expiresAt) {
                logger.warn(
                    LTag.PUMP,
                    "PumpAvailability watchdog: clearing stale ${entry.state} (reason='${entry.reason}', expired ${now - entry.expiresAt}ms ago)"
                )
                entry = Entry(PumpAvailability.Unknown, Long.MAX_VALUE, "watchdog-cleared:${entry.reason}")
                _flow.value = PumpAvailability.Unknown
            }
        }
    }

    fun shutdown() {
        stopWatchdog()
        watchdogScope.cancel()
    }
}
