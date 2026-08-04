package app.aaps.pump.tandem.common.concurrency

import kotlin.time.Duration

/**
 * Wraps an existing blocking call site as a [PumpOp]. Used by the Phase A migration to route
 * legacy synchronous methods (e.g. `pumpConnectionManager.deliverBolus(...)`) through the queue
 * without rewriting their bodies. Ops are serialised by the queue, so blocking here is safe
 * relative to other queued ops.
 *
 * Note: [block] is plain Java/Kotlin code and never suspends, so it cannot be preempted by
 * `withTimeout` at all — run on the dispatch thread it made [maxDuration] inert. [PumpOpQueue]
 * therefore runs it on a thread of its own, where the deadline really does bound how long the
 * *caller* waits. It still cannot abort an in-progress BLE round-trip: the queue holds the next
 * op back until the body finishes, and the COMMAND_TIMEOUT inside TandemCommunicationManager
 * remains the bound on the wire.
 */
class BlockingPumpOp<T>(
    override val name: String,
    override val maxDuration: Duration,
    override val requiresDeliveryEnabled: Boolean,
    override val coalesceKey: String? = null,
    private val block: () -> T
) : PumpOp<T>() {
    override suspend fun run(ctx: PumpOpContext): T = block()
}
