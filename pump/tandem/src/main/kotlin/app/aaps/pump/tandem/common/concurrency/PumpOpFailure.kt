package app.aaps.pump.tandem.common.concurrency

import kotlin.time.Duration

/**
 * Why a mutating pump op failed *without the driver body ever producing a result*.
 *
 * Both cases are manufactured by [PumpOpQueue] before or around the op body, so the calling
 * plugin method has no [app.aaps.core.interfaces.pump.PumpEnactResult] of its own to return.
 * [TandemDispatcher.submitMutating] maps them onto the caller's `failed` lambda instead of
 * letting the exception escape into AAPS's command queue.
 *
 * @see PumpUnavailableException
 * @see PumpOpTimeoutException
 */
internal sealed class PumpOpFailure {

    /** Delivery was gated by [PumpAvailability] — the op never reached the wire. */
    data class Unavailable(val availability: PumpAvailability) : PumpOpFailure()

    /** The op overran its `maxDuration`. The wire work may still have completed — see [TandemDispatcher]. */
    data class TimedOut(val maxDuration: Duration) : PumpOpFailure()

    /**
     * Short diagnostic token appended to the user-visible comment, e.g. `Unknown` or
     * `timeout 2m`. Deliberately untranslated: it identifies a driver state for a log or a bug
     * report, not prose. The surrounding sentence comes from a string resource.
     */
    val diagnostic: String
        get() = when (this) {
            is Unavailable -> availability.name
            is TimedOut    -> "timeout $maxDuration"
        }
}
