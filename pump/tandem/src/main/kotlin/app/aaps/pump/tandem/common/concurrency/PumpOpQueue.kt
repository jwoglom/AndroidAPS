package app.aaps.pump.tandem.common.concurrency

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import com.jwoglom.pumpx2.pump.messages.Message
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.ArrayDeque
import java.util.concurrent.Executors
import kotlin.time.Duration

/** Thrown into a fast-failing op's Deferred when delivery is gated by [PumpAvailability]. */
class PumpUnavailableException(val availability: PumpAvailability, val opName: String) :
    RuntimeException("Pump op '$opName' rejected: availability=$availability")

/**
 * Single-dispatcher pump op queue with three-tier priority.
 *
 * Architecture: one dedicated single-thread executor owns BLE I/O. Submission [Priority]
 * determines insertion point — [Priority.CRITICAL] ahead of [Priority.USER_INITIATED] ahead of
 * [Priority.SYSTEM_INITIATED], FIFO within each tier. Status reads with a [PumpOp.coalesceKey]
 * are deduped at submit time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PumpOpQueue(
    private val logger: AAPSLogger,
    private val availability: PumpAvailabilityState,
    private val commSuspend: CommSuspendGate,
    private val sender: Sender? = null
) {

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
    private val criticalQueue = ArrayDeque<Entry<*>>()
    private val userQueue = ArrayDeque<Entry<*>>()
    private val systemQueue = ArrayDeque<Entry<*>>()
    /** coalesceKey -> Deferred of the pending op, so duplicate submits can share its result. */
    private val pendingByKey = HashMap<String, CompletableDeferred<*>>()
    private var inFlight: Entry<*>? = null

    private val wakeup = Channel<Unit>(capacity = Channel.CONFLATED)

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "TandemPumpOpQueue").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** Single mutex around the wire so awaitable comm-suspend gating composes naturally. */
    private val wireMutex = Mutex()

    init {
        scope.launch { dispatchLoop() }
    }

    fun isBusy(): Boolean = synchronized(lock) {
        inFlight != null || criticalQueue.isNotEmpty() || userQueue.isNotEmpty() || systemQueue.isNotEmpty()
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
            when (priority) {
                Priority.CRITICAL         -> criticalQueue.addLast(entry)
                Priority.USER_INITIATED   -> userQueue.addLast(entry)
                Priority.SYSTEM_INITIATED -> systemQueue.addLast(entry)
            }
            if (key != null) pendingByKey[key] = deferred
            logger.debug(
                LTag.PUMP,
                "PumpOpQueue: submitted '${op.name}' priority=$priority " +
                    "(critical=${criticalQueue.size}, user=${userQueue.size}, system=${systemQueue.size})"
            )
            wakeup.trySend(Unit)
            return deferred
        }
    }

    private suspend fun dispatchLoop() {
        while (true) {
            val entry = nextEntry() ?: run {
                wakeup.receive()
                continue
            }
            runEntry(entry)
        }
    }

    private fun nextEntry(): Entry<*>? = synchronized(lock) {
        val next = criticalQueue.pollFirst() ?: userQueue.pollFirst() ?: systemQueue.pollFirst()
        if (next != null) inFlight = next
        next
    }

    private suspend fun <T> runEntry(entry: Entry<T>) {
        val op = entry.op
        val deferred = entry.deferred
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
            val result = withTimeout(op.maxDuration) { op.run(ctx) }
            deferred.complete(result)
        } catch (t: TimeoutCancellationException) {
            logger.error(LTag.PUMP, "PumpOpQueue: '${op.name}' timed out after ${op.maxDuration}")
            deferred.completeExceptionally(t)
        } catch (t: Throwable) {
            logger.error(LTag.PUMP, "PumpOpQueue: '${op.name}' threw: ${t.message}", t)
            deferred.completeExceptionally(t)
        } finally {
            synchronized(lock) {
                inFlight = null
                op.coalesceKey?.let { pendingByKey.remove(it) }
            }
        }
    }

    /** Per-op context. Created fresh per op so the [PumpOp.name] is captured for logs. */
    private inner class Ctx(private val op: PumpOp<*>) : PumpOpContext {
        override suspend fun send(request: Message, forceSend: Boolean): Message? {
            val s = sender ?: error("PumpOpQueue.Sender not configured; ctx.send() unavailable")
            return wireMutex.withLock {
                commSuspend.await()
                s.sendOnWire(request, forceSend)
            }
        }

        override suspend fun <R> withAvailability(
            state: PumpAvailability,
            maxDuration: Duration,
            reason: String,
            block: suspend () -> R
        ): R = availability.withAvailability(state, maxDuration, "${op.name}:$reason", block)
    }

    fun shutdown() {
        scope.cancel()
        executor.shutdown()
    }
}
