package app.aaps.pump.tandem.common.concurrency

import kotlin.time.Duration

/**
 * Why a mutating pump op failed *without the driver body ever producing a result*.
 *
 * Every case here is manufactured by [PumpOpQueue] before or around the op body, so the calling
 * plugin method has no [app.aaps.core.interfaces.pump.PumpEnactResult] of its own to return.
 * [TandemDispatcher.submitMutating] maps them onto the caller's `failed` lambda instead of
 * letting the exception escape into AAPS's command queue.
 *
 * Exhaustiveness is the point of the sealed hierarchy: a new case stops
 * `TandemMobiPumpPlugin.failureComment` compiling until it has been given a user-visible
 * explanation, so a queue-manufactured failure cannot quietly fall back to a bare "error" again.
 *
 * @see PumpUnavailableException
 * @see PumpOpTimeoutException
 * @see PumpOpFailedException
 */
internal sealed class PumpOpFailure {

    /** Delivery was gated by [PumpAvailability] — the op never reached the wire. */
    data class Unavailable(val availability: PumpAvailability) : PumpOpFailure()

    /**
     * The op overran its `maxDuration`. The caller is released at the deadline, but the body is
     * not interruptible, so the pump write may still land afterwards: the outcome is *unknown*,
     * not "nothing happened". See [PumpOpTimeoutException].
     */
    data class TimedOut(val maxDuration: Duration) : PumpOpFailure()

    /**
     * The op body raised a `CancellationException`, or the queue was shut down around it. As with
     * [TimedOut] the body is not interruptible, so the wire outcome is unknown.
     */
    data object Cancelled : PumpOpFailure()

    /**
     * Short diagnostic token identifying the driver state, for a log or a bug report. Deliberately
     * untranslated: it is not prose, and the surrounding sentence comes from a string resource.
     * [TimedOut] carries its own sentence and does not use this.
     */
    val diagnostic: String
        get() = when (this) {
            is Unavailable -> availability.name
            is TimedOut    -> "timeout $maxDuration"
            Cancelled      -> "cancelled"
        }
}
