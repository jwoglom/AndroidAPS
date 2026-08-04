package app.aaps.pump.tandem.common.concurrency

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.pump.tandem.common.comm.ui.TandemUICommunication
import app.aaps.pump.tandem.common.driver.connector.TandemPumpConnectionManager
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The single chokepoint for pump-talking work. All callers go through one of the four `submit*`
 * helpers; the lambda runs with [PumpDispatcherScope] as receiver, which is the only way to
 * reach the underlying [TandemPumpConnectionManager] / [TandemUICommunication]. Outside the
 * lambda the symbols are unresolvable.
 *
 * Wraps [PumpOpQueue] so the priority / availability / rate-limit / comm-suspend semantics
 * established in Phases A–B are preserved.
 */
@Singleton
class TandemDispatcher @Inject constructor(
    private val pumpOps: PumpOpQueue,
    private val rh: ResourceHelper,
    private val logger: AAPSLogger,
    pumpConnectionManager: TandemPumpConnectionManager,
    tandemUICommunication: TandemUICommunication
) {

    private val scope: PumpDispatcherScope = object : PumpDispatcherScope {
        override val pumpConnectionManager = pumpConnectionManager
        override val tandemUICommunication = tandemUICommunication
    }

    /**
     * The blocking submit* helpers must never be called from one of the queue's own threads:
     * runBlocking would wait for an op that cannot be dispatched until the caller returns →
     * deadlock. Matches on prefix so both the dispatch thread and the op-body thread
     * ([PumpOpQueue.BODY_THREAD_NAME]) are rejected. Fail fast instead.
     */
    private fun assertNotOnQueueThread(name: String) {
        check(!Thread.currentThread().name.startsWith(PumpOpQueue.THREAD_NAME)) {
            "$name called on the ${Thread.currentThread().name} thread; runBlocking would deadlock the queue. " +
                "Do not call submit* from inside a pump op block."
        }
    }

    /**
     * AAPS-side mutating ops (bolus, TBR, profile). Routes via [Priority.DEFAULT] with
     * `requiresDeliveryEnabled = true`.
     *
     * No queue-manufactured failure is allowed to escape into AAPS's command queue; each is mapped
     * onto [failed] so the caller always returns a [PumpEnactResult] carrying a reason:
     *  - [PumpUnavailableException] → [PumpOpFailure.Unavailable] — delivery gated by the pump
     *    being suspended / in cartridge change / not yet observed. AAPS re-requests next cycle.
     *  - [PumpOpTimeoutException] → [PumpOpFailure.TimedOut] — the op overran [maxDuration].
     *  - [PumpOpFailedException] → [PumpOpFailure.Cancelled] — the body raised a
     *    `CancellationException`, or the queue shut down around it.
     *
     * The timeout mapping is load-bearing, and worth stating precisely because the first attempt
     * at this fix described the failure wrongly. `maxDuration` used to be *inert* for the ops this
     * driver actually submits: their bodies never suspend, so while they ran on the queue's
     * dispatch thread `withTimeout` could not deliver its cancellation and no timeout was ever
     * raised. `CommandQueue.setProfile` simply waited on its `Deferred` for the full 10-minute
     * guard and then posted a reasonless "Failed to update basal profile" — while the pump had in
     * fact already been written. [PumpOpQueue] now runs bodies off the dispatch thread, so the
     * deadline genuinely fires; that in turn makes the *type* it fires as matter, because a
     * `CancellationException` would be rethrown by `QueueWorker` as worker cancellation and the
     * command's callback would be lost instead of completed.
     *
     * Exceptions thrown by [block] itself are deliberately *not* caught: a driver bug should
     * surface as a driver bug, and `QueueWorker` already completes the command's callback for
     * ordinary exceptions.
     */
    internal fun <T : PumpEnactResult> submitMutating(
        name: String,
        maxDuration: Duration = 2.minutes,
        failed: (PumpOpFailure) -> T,
        block: PumpDispatcherScope.() -> T
    ): T = runBlocking {
        assertNotOnQueueThread(name)
        try {
            pumpOps.submit(
                BlockingPumpOp(name, maxDuration, requiresDeliveryEnabled = true) { scope.block() },
                Priority.DEFAULT
            ).await()
        } catch (e: PumpUnavailableException) {
            logger.warn(LTag.PUMP, "$name: ${e.message}")
            failed(PumpOpFailure.Unavailable(e.availability))
        } catch (e: PumpOpTimeoutException) {
            // The op body is not interruptible, so the wire work may still be running — or may
            // have succeeded. Log loudly: this is the case where AAPS and the pump can disagree.
            logger.error(LTag.PUMP, "$name: ${e.message}; pump state may have changed regardless")
            failed(PumpOpFailure.TimedOut(e.maxDuration))
        } catch (e: PumpOpFailedException) {
            logger.error(LTag.PUMP, "$name: ${e.message}; pump state may have changed regardless", e)
            failed(PumpOpFailure.Cancelled)
        }
    }

    /**
     * Reads / config writes that don't require delivery to be enabled. Routes via
     * [Priority.DEFAULT].
     *
     * [T] is unconstrained, so there is no failure value to synthesise — a timeout propagates as
     * [PumpOpTimeoutException]. That is an ordinary exception rather than a `CancellationException`
     * (see [PumpOpTimeoutException]), so `QueueWorker` completes the command's callback and the
     * awaiting caller is resolved instead of hanging.
     */
    internal fun <T> submitDefault(
        name: String,
        maxDuration: Duration = 30.seconds,
        block: PumpDispatcherScope.() -> T
    ): T = runBlocking {
        assertNotOnQueueThread(name)
        pumpOps.submit(
            BlockingPumpOp(name, maxDuration, requiresDeliveryEnabled = false) { scope.block() },
            Priority.DEFAULT
        ).await()
    }

    /**
     * UI-driven sends — taps, refresh, settings change. Fire-and-forget at
     * [Priority.USER_INITIATED]; jumps ahead of background AAPS work so the user's tap doesn't
     * wait. Response (if any) arrives via the existing listener path.
     */
    internal fun submitUser(
        name: String,
        maxDuration: Duration = 10.seconds,
        block: PumpDispatcherScope.() -> Unit
    ) {
        pumpOps.submit(
            BlockingPumpOp(name, maxDuration, requiresDeliveryEnabled = false) { scope.block() },
            Priority.USER_INITIATED
        )
    }

    /**
     * Emergency overrides — bolus cancel, alarm acknowledgement. Front of the queue and bypass
     * the availability gate (typically running *during* the workflow that disabled delivery).
     */
    internal fun submitCritical(
        name: String,
        maxDuration: Duration = 30.seconds,
        block: PumpDispatcherScope.() -> Unit
    ) {
        runBlocking {
            assertNotOnQueueThread(name)
            try {
                pumpOps.submit(
                    BlockingPumpOp(name, maxDuration, requiresDeliveryEnabled = false) { scope.block() },
                    Priority.CRITICAL
                ).await()
            } catch (t: Throwable) {
                logger.error(LTag.PUMP, "$name failed: ${t.message}", t)
            }
        }
    }

    /**
     * Lowest-priority maintenance work (history log fetches). Fire-and-forget at
     * [Priority.BACKGROUND] — subject to the queue's token-bucket rate limit so a long sync
     * cannot saturate the wire.
     */
    internal fun submitBackground(
        name: String,
        maxDuration: Duration = 10.seconds,
        block: PumpDispatcherScope.() -> Unit
    ) {
        pumpOps.submit(
            BlockingPumpOp(name, maxDuration, requiresDeliveryEnabled = false) { scope.block() },
            Priority.BACKGROUND
        )
    }

    /** Surface for callers (e.g. plugin's isBusy()) to query queue activity. */
    fun isBusy(): Boolean = pumpOps.isBusy()

    @Suppress("unused") private val unused = rh // reserved for future PumpEnactResult message wiring
}
