package app.aaps.pump.tandem.common.concurrency

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import com.jwoglom.pumpx2.pump.messages.Message
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.ArrayDeque
import java.util.EnumMap
import java.util.concurrent.Executors
import kotlin.time.Duration

/** Thrown into a fast-failing op's Deferred when delivery is gated by [PumpAvailability]. */
class PumpUnavailableException(val availability: PumpAvailability, val opName: String) :
    RuntimeException("Pump op '$opName' rejected: availability=$availability")

/**
 * Thrown into an op's Deferred when it overran its [PumpOp.maxDuration].
 *
 * Deliberately a plain [RuntimeException] and **not** kotlinx's `TimeoutCancellationException`,
 * which is a `CancellationException`. AAPS's `QueueWorker` rethrows `CancellationException` to
 * honour worker cancellation, which bypasses the handler that completes a failed command's
 * callback — the caller's `Deferred` then never completes and `CommandQueue.setProfile` hangs
 * until its own 10-minute timeout, surfacing as a bare "Failed to update basal profile" with no
 * reason. Handing back a normal exception keeps the command-failed path intact.
 *
 * Note the op body is not interruptible (see [BlockingPumpOp]). The deadline bounds how long the
 * *caller* waits, not the wire: the body keeps running on its own thread and the underlying pump
 * write may still land after this is raised. Callers that mutate pump state must treat a timeout
 * as "outcome unknown", not as "nothing happened" — [PumpOpFailure.TimedOut] carries that to the
 * user.
 */
class PumpOpTimeoutException(val opName: String, val maxDuration: Duration) :
    RuntimeException("Pump op '$opName' timed out after $maxDuration")

/**
 * Wraps a [CancellationException] raised by an op body (or by [PumpOpQueue.shutdown] cancelling
 * the dispatcher scope) so it reaches the caller as an ordinary failure. Same rationale as
 * [PumpOpTimeoutException]: cancellation must not propagate out of the queue and be mistaken for
 * AAPS worker cancellation.
 */
class PumpOpFailedException(val opName: String, cause: Throwable) :
    RuntimeException("Pump op '$opName' was cancelled: ${cause.message}", cause)

/**
 * Single-dispatcher pump op queue with four-tier priority and per-tier rate limiting.
 *
 * Architecture: one dedicated single-thread executor owns BLE I/O. Submission [Priority]
 * determines insertion point — CRITICAL > USER_INITIATED > DEFAULT > BACKGROUND,
 * FIFO within each tier. Status reads with a [PumpOp.coalesceKey] are deduped at submit time.
 *
 * Each tier may have an optional [RateLimit]; by default only [Priority.BACKGROUND] is rate
 * limited (see [defaultRateLimits]) so history-log-style maintenance work cannot saturate the
 * wire. When the head-of-queue tier is rate-gated the dispatcher sleeps until either a token
 * becomes available or a higher-priority op is submitted (which preempts via [wakeup]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PumpOpQueue(
    private val logger: AAPSLogger,
    private val availability: PumpAvailabilityState,
    private val commSuspend: CommSuspendGate,
    private val sender: Sender? = null,
    rateLimits: Map<Priority, RateLimit> = defaultRateLimits()
) {

    companion object {
        /** Name of the single thread the dispatch loop runs on. Callers must never block on it (deadlock). */
        const val THREAD_NAME = "TandemPumpOpQueue"

        /**
         * Name of the single thread op bodies run on. Shares [THREAD_NAME]'s prefix so
         * `TandemDispatcher.assertNotOnQueueThread` rejects a re-entrant submit from either
         * thread — both would deadlock.
         */
        const val BODY_THREAD_NAME = "$THREAD_NAME-body"

        /** Default: BACKGROUND throttled at 1 msg/s, burst 2. Other tiers unrate-limited. */
        fun defaultRateLimits(): Map<Priority, RateLimit> = mapOf(
            Priority.BACKGROUND to RateLimit(rps = 1.0, burst = 2)
        )
    }

    /**
     * Wire sender abstraction. In production this delegates to TandemCommunicationManager.
     * Kept narrow so the queue itself has no BLE knowledge.
     */
    fun interface Sender {
        fun sendOnWire(request: Message, forceSend: Boolean): Message?
    }

    private class Entry<T>(
        val op: PumpOp<T>,
        val priority: Priority,
        val deferred: CompletableDeferred<T>
    )

    private val lock = Any()
    private val queues: EnumMap<Priority, ArrayDeque<Entry<*>>> =
        EnumMap<Priority, ArrayDeque<Entry<*>>>(Priority::class.java).also { map ->
            Priority.values().forEach { map[it] = ArrayDeque() }
        }
    private val limiters: Map<Priority, TokenBucket> = rateLimits.mapValues { TokenBucket(it.value) }
    /** coalesceKey -> Deferred of the pending op, so duplicate submits can share its result. */
    private val pendingByKey = HashMap<String, CompletableDeferred<*>>()
    private var inFlight: Entry<*>? = null

    private val wakeup = Channel<Unit>(capacity = Channel.CONFLATED)

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, THREAD_NAME).apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /**
     * Op bodies run here, never on the dispatch thread.
     *
     * [BlockingPumpOp] bodies never suspend, so a body sharing the dispatch thread made
     * [PumpOp.maxDuration] **inert**, not merely late: `withTimeout` delivers its cancellation by
     * dispatching onto the coroutine's own dispatcher, and that dispatch queues behind the body
     * already blocking the single thread. The block then ran to completion and `withTimeout`
     * returned its value, so no timeout was ever raised however long the op took — the caller
     * simply waited, which is the hang this queue kept producing. Off the dispatch thread the
     * deadline is real; see [runEntry].
     *
     * Still a single thread: the comm layer matches responses against shared in-flight lists and
     * is not re-entrant, so bodies must not overlap even when one has outlived its deadline.
     */
    private val bodyExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, BODY_THREAD_NAME).apply { isDaemon = true }
    }
    private val bodyScope = CoroutineScope(SupervisorJob() + bodyExecutor.asCoroutineDispatcher())

    /** Single mutex around the wire so awaitable comm-suspend gating composes naturally. */
    private val wireMutex = Mutex()

    init {
        scope.launch { dispatchLoop() }
    }

    fun isBusy(): Boolean = synchronized(lock) {
        inFlight != null || queues.values.any { it.isNotEmpty() }
    }

    fun <T> submit(op: PumpOp<T>, priority: Priority): Deferred<T> {
        val key = op.coalesceKey
        synchronized(lock) {
            if (key != null) {
                @Suppress("UNCHECKED_CAST")
                val existing = pendingByKey[key] as CompletableDeferred<T>?
                if (existing != null) {
                    logger.debug(LTag.PUMP, "PumpOpQueue: coalescing '${op.name}' onto existing key=$key")
                    return existing
                }
            }
            val deferred = CompletableDeferred<T>()
            val entry = Entry(op, priority, deferred)
            queues[priority]!!.addLast(entry)
            if (key != null) pendingByKey[key] = deferred
            logger.debug(LTag.PUMP, "PumpOpQueue: submitted '${op.name}' priority=$priority ${snapshotSizes()}")
            wakeup.trySend(Unit)
            return deferred
        }
    }

    private fun snapshotSizes(): String =
        queues.entries.joinToString(", ", "(", ")") { "${it.key}=${it.value.size}" }

    private sealed class Pick {
        data class Ready(val entry: Entry<*>) : Pick()
        /** Head-of-queue tier is rate-limited; wait this many ms (or shorter, on wakeup). */
        data class WaitMs(val ms: Long) : Pick()
        /** All queues empty. */
        data object Idle : Pick()
    }

    private suspend fun dispatchLoop() {
        while (true) {
            try {
                when (val pick = pickNext()) {
                    is Pick.Ready  -> runEntry(pick.entry)
                    is Pick.WaitMs -> withTimeoutOrNull(pick.ms) { wakeup.receive() }
                    is Pick.Idle   -> wakeup.receive()
                }
            } catch (t: CancellationException) {
                throw t // shutdown() cancelled the scope — stop dispatching.
            } catch (t: Throwable) {
                // This is the only dispatcher. Letting a throw escape kills it silently, and
                // every queued and future op's Deferred then stays uncompleted with its caller
                // blocked forever — the same class of hang PumpOpTimeoutException exists to
                // prevent. Log it and keep going.
                logger.error(LTag.PUMP, "PumpOpQueue: dispatch loop error, continuing: ${t.message}", t)
                synchronized(lock) { inFlight = null }
            }
        }
    }

    private fun pickNext(): Pick = synchronized(lock) {
        // Whole-queue pause when the pump signals comm-suspended. Applies regardless of priority
        // — even CRITICAL ops can't usefully send while the pump's BT buffer is full.
        val pauseRemaining = commSuspend.remainingPauseMs()
        if (pauseRemaining > 0 && queues.values.any { it.isNotEmpty() }) {
            return Pick.WaitMs(pauseRemaining)
        }
        for (p in Priority.values()) {
            val q = queues[p]!!
            if (q.isEmpty()) continue
            val limiter = limiters[p]
            if (limiter == null) {
                val e = q.pollFirst()
                inFlight = e
                return Pick.Ready(e)
            }
            val waitMs = limiter.tryAcquire()
            if (waitMs == 0L) {
                val e = q.pollFirst()
                inFlight = e
                return Pick.Ready(e)
            }
            // Head priority is rate-gated; do NOT fall through to lower priorities (would invert
            // priority ordering). Wait for either a token or a higher-priority submission.
            return Pick.WaitMs(waitMs)
        }
        return Pick.Idle
    }

    private suspend fun <T> runEntry(entry: Entry<T>) {
        val op = entry.op
        val deferred = entry.deferred
        var body: Deferred<T>? = null
        try {
            if (op.requiresDeliveryEnabled && !availability.current.allowsDelivery) {
                logger.warn(
                    LTag.PUMP,
                    "PumpOpQueue: fast-failing '${op.name}' — availability=${availability.current}"
                )
                deferred.completeExceptionally(PumpUnavailableException(availability.current, op.name))
                return
            }
            val ctx = Ctx(op)
            // Run the body on bodyScope, not here: only then can withTimeout actually release the
            // caller at maxDuration rather than after the (uninterruptible) body has finished.
            val running = bodyScope.async { op.run(ctx) }
            body = running
            deferred.complete(withTimeout(op.maxDuration) { running.await() })
        } catch (t: TimeoutCancellationException) {
            // The body may have finished in the same instant the deadline fired. Prefer its result
            // over a manufactured failure — reporting "failed" for an op that succeeded is exactly
            // how AAPS and the pump ended up disagreeing.
            val landed = body?.takeIf { it.isCompleted }?.let { runCatching { it.getCompleted() } }
            if (landed != null && landed.isSuccess) {
                logger.warn(LTag.PUMP, "PumpOpQueue: '${op.name}' completed as its ${op.maxDuration} deadline fired; keeping the result")
                deferred.complete(landed.getOrThrow())
            } else {
                logger.error(LTag.PUMP, "PumpOpQueue: '${op.name}' timed out after ${op.maxDuration}; releasing caller, body may still be running")
                deferred.completeExceptionally(PumpOpTimeoutException(op.name, op.maxDuration))
            }
        } catch (t: Throwable) {
            logger.error(LTag.PUMP, "PumpOpQueue: '${op.name}' threw: ${t.message}", t)
            // Same reasoning as the timeout branch: a CancellationException from the op body (or
            // from shutdown() cancelling the scope) must not reach the caller as cancellation, or
            // its callback is lost and the awaiting command hangs.
            deferred.completeExceptionally(
                if (t is CancellationException) PumpOpFailedException(op.name, t) else t
            )
        } finally {
            body?.let { drainOverrunningBody(op, it) }
            synchronized(lock) {
                inFlight = null
                op.coalesceKey?.let { pendingByKey.remove(it) }
            }
            // Backstop for the whole class of bug this queue keeps hitting: a caller blocked on a
            // Deferred that is never completed waits forever. Every exit from runEntry must leave
            // the Deferred completed, however it got here.
            if (!deferred.isCompleted) {
                logger.error(LTag.PUMP, "PumpOpQueue: '${op.name}' left its result uncompleted")
                deferred.completeExceptionally(
                    PumpOpFailedException(op.name, IllegalStateException("op produced no result"))
                )
            }
        }
    }

    /**
     * Hold the next op back until a body that outlived its deadline really finishes.
     *
     * The body is not interruptible and the comm layer is not re-entrant, so overlapping it with
     * the next op would interleave traffic on the wire. The caller has already been released —
     * that is the point: [PumpOp.maxDuration] bounds the caller, this bounds the wire. Returns
     * immediately in the normal case, where the body is already done.
     */
    private suspend fun drainOverrunningBody(op: PumpOp<*>, body: Deferred<*>) {
        if (body.isCompleted) return
        logger.warn(LTag.PUMP, "PumpOpQueue: holding the queue until '${op.name}' body finishes")
        // NonCancellable so the wire is still handed over cleanly during shutdown().
        withContext(NonCancellable) { body.join() }
        logger.warn(LTag.PUMP, "PumpOpQueue: '${op.name}' body finished after its deadline; wire free")
    }

    /** Per-op context. Created fresh per op so the [PumpOp.name] is captured for logs. */
    private inner class Ctx(@Suppress("unused") private val op: PumpOp<*>) : PumpOpContext {
        override suspend fun send(request: Message, forceSend: Boolean): Message? {
            val s = sender ?: error("PumpOpQueue.Sender not configured; ctx.send() unavailable")
            return wireMutex.withLock {
                commSuspend.await()
                s.sendOnWire(request, forceSend)
            }
        }
    }

    fun shutdown() {
        scope.cancel()
        bodyScope.cancel()
        executor.shutdown()
        bodyExecutor.shutdown()
    }
}
