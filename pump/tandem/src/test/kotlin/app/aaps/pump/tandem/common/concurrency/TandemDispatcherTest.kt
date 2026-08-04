package app.aaps.pump.tandem.common.concurrency

import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.tandem.common.comm.ui.TandemUICommunication
import app.aaps.pump.tandem.common.driver.connector.TandemPumpConnectionManager
import app.aaps.shared.tests.AAPSLoggerTest
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * What [TandemDispatcher] owes AAPS's command queue.
 *
 * [PumpOpQueueTest] pins the *type* of a queue-manufactured failure; this pins what the plugin
 * hands back because of it. The two halves failing independently is how the original bug shipped:
 * the queue raised something reasonable and the dispatcher let it escape.
 *
 * The contract under test:
 *  - a mutating op ALWAYS returns a [PumpEnactResult] carrying a reason, for every failure the
 *    queue can manufacture, so `QueueWorker` never has to fall back to a bare "error"
 *  - a driver bug is NOT swallowed into a failure result — it surfaces as an exception, which
 *    `QueueWorker` already turns into a completed callback
 *  - nothing the dispatcher throws is ever a `CancellationException`, which `QueueWorker` would
 *    rethrow as worker cancellation and lose the command's callback
 */
class TandemDispatcherTest {

    private val logger = AAPSLoggerTest()

    private fun availability(initial: PumpAvailability = PumpAvailability.DeliveryEnabled) =
        PumpAvailabilityState(logger).also {
            when (initial) {
                PumpAvailability.DeliveryEnabled  -> it.markEnabled("test")
                PumpAvailability.DeliveryDisabled -> it.markDisabled("test")
                PumpAvailability.Unknown          -> it.markUnknown("test")
            }
        }

    private fun dispatcher(availability: PumpAvailabilityState = availability()): Pair<TandemDispatcher, PumpOpQueue> {
        val queue = PumpOpQueue(logger, availability, CommSuspendGate(logger), rateLimits = emptyMap())
        val rh = mock<ResourceHelper>()
        val connectionManager = mock<TandemPumpConnectionManager>()
        val uiCommunication = mock<TandemUICommunication>()
        return TandemDispatcher(queue, rh, logger, connectionManager, uiCommunication) to queue
    }

    @Test
    fun `a gated mutating op returns the failed result instead of throwing`() {
        val (sut, queue) = dispatcher(availability(PumpAvailability.DeliveryDisabled))
        val expected = mock<PumpEnactResult>()
        val bodyRuns = AtomicInteger(0)
        var seen: PumpOpFailure? = null

        val result = sut.submitMutating(
            name = "deliverBolus",
            failed = { f -> seen = f; expected }
        ) {
            bodyRuns.incrementAndGet()
            mock<PumpEnactResult>()
        }

        assertSame(expected, result)
        assertEquals(PumpOpFailure.Unavailable(PumpAvailability.DeliveryDisabled), seen)
        assertEquals(0, bodyRuns.get(), "a gated op must never reach the wire")
        queue.shutdown()
    }

    @Test
    fun `an overrunning mutating op returns the failed result carrying its deadline`() {
        val (sut, queue) = dispatcher()
        val expected = mock<PumpEnactResult>()
        var seen: PumpOpFailure? = null

        // This is the case from the bug report: before it, the timeout escaped as a
        // CancellationException, QueueWorker rethrew it, and setProfile hung for ten minutes.
        val result = sut.submitMutating(
            name = "setNewBasalProfile",
            maxDuration = 100.milliseconds,
            failed = { f -> seen = f; expected }
        ) {
            Thread.sleep(1_000)
            mock<PumpEnactResult>()
        }

        assertSame(expected, result)
        assertEquals(PumpOpFailure.TimedOut(100.milliseconds), seen)
        queue.shutdown()
    }

    @Test
    fun `a cancelled mutating op returns the failed result rather than escaping as cancellation`() {
        val (sut, queue) = dispatcher()
        val expected = mock<PumpEnactResult>()
        var seen: PumpOpFailure? = null

        val result = sut.submitMutating(
            name = "setTempBasalAbsolute",
            failed = { f -> seen = f; expected }
        ) {
            throw CancellationException("driver cancelled")
        }

        assertSame(expected, result)
        assertEquals(PumpOpFailure.Cancelled, seen)
        queue.shutdown()
    }

    @Test
    fun `a driver exception is not swallowed into a failed result`() {
        // Deliberate: a driver bug should surface as a driver bug. QueueWorker completes the
        // command's callback for ordinary exceptions, so nothing hangs - but silently reporting
        // "TBR could not be set" would hide a crash in the driver.
        val (sut, queue) = dispatcher()
        var failedCalls = 0

        val thrown = assertThrows(IllegalStateException::class.java) {
            sut.submitMutating<PumpEnactResult>(
                name = "cancelTempBasal",
                failed = { failedCalls++; mock() }
            ) {
                throw IllegalStateException("driver bug")
            }
        }

        assertEquals("driver bug", thrown.message)
        assertEquals(0, failedCalls)
        queue.shutdown()
    }

    @Test
    fun `submitDefault propagates a timeout as an ordinary exception, never as cancellation`() {
        // submitDefault has no PumpEnactResult to synthesise, so it propagates. What matters is
        // only that what it propagates is not a CancellationException.
        val (sut, queue) = dispatcher()

        val thrown = runCatching {
            sut.submitDefault(name = "getPumpStatus", maxDuration = 100.milliseconds) {
                Thread.sleep(1_000)
            }
        }.exceptionOrNull()

        assertTrue(thrown is PumpOpTimeoutException, "expected PumpOpTimeoutException, got $thrown")
        assertFalse(thrown is CancellationException, "QueueWorker would rethrow this and lose the command's callback")
        queue.shutdown()
    }

    @Test
    fun `submitting from inside an op body fails fast instead of deadlocking`() {
        // Both queue threads are rejected: the dispatch thread and the thread op bodies run on.
        // A re-entrant submit from either would wait for an op that cannot be dispatched until
        // the caller returns.
        val (sut, queue) = dispatcher()

        val thrown = assertThrows(IllegalStateException::class.java) {
            sut.submitDefault(name = "outer", maxDuration = 5.seconds) {
                sut.submitDefault(name = "inner") { }
            }
        }

        assertTrue(
            thrown.message?.contains(PumpOpQueue.THREAD_NAME) == true,
            "expected the deadlock guard to name the offending thread, got: ${thrown.message}"
        )
        queue.shutdown()
    }
}
