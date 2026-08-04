package app.aaps.implementation.queue

import android.content.Context
import android.os.PowerManager
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.implementation.profile.ProfileSwitchSilentGate
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import javax.inject.Provider
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class QueueWorkerTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var powerManager: PowerManager
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var localAlertUtils: LocalAlertUtils
    private val localAlertUtilsProvider: Provider<LocalAlertUtils> by lazy { Provider { localAlertUtils } }
    @Mock lateinit var smsCommunicator: SmsCommunicator
    private val smsCommunicatorProvider: Provider<SmsCommunicator> by lazy { Provider { smsCommunicator } }
    @Mock lateinit var jobName: CommandQueueName
    @Mock lateinit var workManager: WorkManager

    private val testScope = CoroutineScope(Dispatchers.Unconfined)
    private val bolusProgressData by lazy { BolusProgressData(ch, rh, testScope) }
    private val profileSwitchSilentGate = ProfileSwitchSilentGate()

    private lateinit var commandQueue: CommandQueueImplementation
    private lateinit var sut: QueueWorker

    @BeforeEach
    fun prepare() {
        whenever(persistenceLayer.observeChanges(anyOrNull<Class<*>>())).thenReturn(emptyFlow())
        commandQueue = CommandQueueImplementation(
            aapsLogger, rxBus, rh, constraintChecker,
            profileFunction, activePlugin, config, dateUtil, fabricPrivacy,
            notificationManager, persistenceLayer, decimalFormatter, pumpEnactResultProvider, pumpSync, preferences, profileSwitchSilentGate, localAlertUtilsProvider, smsCommunicatorProvider, jobName, workManager, testScope, bolusProgressData
        )

        val pumpDescription = PumpDescription()
        pumpDescription.basalMinimumRate = 0.1

        whenever(context.getSystemService(Context.POWER_SERVICE)).thenReturn(powerManager)
        runBlocking { whenever(profileFunction.getProfile()).thenReturn(effectiveProfile) }

        val bolusConstraint = ConstraintObject(0.0, aapsLogger)
        whenever(constraintChecker.applyBolusConstraints(anyOrNull())).thenReturn(bolusConstraint)
        whenever(constraintChecker.applyExtendedBolusConstraints(anyOrNull())).thenReturn(bolusConstraint)
        val carbsConstraint = ConstraintObject(0, aapsLogger)
        whenever(constraintChecker.applyCarbsConstraints(anyOrNull())).thenReturn(carbsConstraint)
        val rateConstraint = ConstraintObject(0.0, aapsLogger)
        whenever(constraintChecker.applyBasalConstraints(anyOrNull(), anyOrNull())).thenReturn(rateConstraint)
        val percentageConstraint = ConstraintObject(0, aapsLogger)
        whenever(constraintChecker.applyBasalPercentConstraints(anyOrNull(), anyOrNull()))
            .thenReturn(percentageConstraint)
        whenever(rh.gs(ArgumentMatchers.eq(app.aaps.core.ui.R.string.temp_basal_absolute), anyOrNull(), anyOrNull())).thenReturn("TEMP BASAL %1\$.2f U/h %2\$d min")
        // Command.status() feeds EventPumpStatusChanged, whose action parameter is non-null: an
        // unstubbed rh.gs() here NPEs the worker one line before executeWithCallback() and every
        // assertion below it becomes unreachable.
        whenever(rh.gs(app.aaps.core.ui.R.string.set_profile)).thenReturn("SET PROFILE")
        whenever(rh.gs(ArgumentMatchers.eq(app.aaps.core.ui.R.string.read_status), anyOrNull())).thenReturn("READSTATUS %1\$s")
        whenever(rh.gs(app.aaps.core.ui.R.string.command_interrupted_outcome_unknown)).thenReturn("Interrupted")
        whenever(rh.gs(app.aaps.core.ui.R.string.command_timeout_outcome_unknown)).thenReturn("Timed out")

        // QueueWorker now uses constructor injection (@HiltWorker). Supply a WorkerFactory that
        // builds it with the test mocks instead of relying on field injection.
        sut = TestListenableWorkerBuilder<QueueWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    QueueWorker(
                        appContext, workerParameters, aapsLogger, fabricPrivacy, commandQueue,
                        rxBus, activePlugin, rh, preferences, config, bolusProgressData
                    )
            })
            .build()
    }

    @Test
    fun commandIsPickedUp() = runTest(timeout = 30.seconds) {
        val tbrJob = launch { commandQueue.tempBasalAbsolute(2.0, 60, true, validProfile, PumpSync.TemporaryBasalType.NORMAL) }
        yield()
        val result = sut.doWorkAndLog()
        tbrJob.join()
        assertIs<ListenableWorker.Result.Success>(result)
        assertThat(commandQueue.size()).isEqualTo(0)
    }

    // region lost-callback regressions
    //
    // Every suspend method on CommandQueue awaits a Deferred that only its command's callback
    // completes, so any worker exit that abandons a performing command without running that
    // callback leaves the caller awaiting forever. setProfile is bounded by a 10-minute guard and
    // surfaced the original incident as a delayed, reasonless "Failed to update basal profile";
    // bolus() has no timeout at all, so there the wait is unbounded.

    /** Enqueues a profile set whose result is captured, without blocking the test coroutine. */
    private fun TestScope.launchSetProfile(): () -> PumpEnactResult? {
        var result: PumpEnactResult? = null
        backgroundScope.launch { result = commandQueue.setProfile(effectiveProfile, false) }
        return { result }
    }

    @Test
    fun `a driver timeout during execute fails the command instead of killing the worker`() = runTest(timeout = 30.seconds) {
        // TimeoutCancellationException is a CancellationException, so it used to hit QueueWorker's
        // "honour worker cancellation" rethrow: the callback never ran and the caller hung. This also
        // pins the catch ordering - Kotlin does not reject the subclass catch sitting above its
        // superclass, so a swap would silently make that branch unreachable.
        testPumpPlugin.setNewBasalProfileTimesOut = true
        val result = launchSetProfile()
        yield()
        assertThat(commandQueue.size()).isEqualTo(1)

        val workerResult = sut.doWorkAndLog()
        yield()

        // The worker survived the driver's timeout and shut down cleanly.
        assertIs<ListenableWorker.Result.Success>(workerResult)
        assertThat(result()).isNotNull()
        assertThat(result()!!.success).isFalse()
        assertThat(result()!!.comment).isEqualTo("Timed out")
        assertThat(commandQueue.size()).isEqualTo(0)
        assertThat(commandQueue.performing).isNull()
    }

    @Test
    fun `a command queued behind one that times out still runs`() = runTest(timeout = 30.seconds) {
        // The point of failing the command rather than letting the exception unwind: the worker keeps
        // draining. Without it the second command sat in the queue until another worker started.
        testPumpPlugin.setNewBasalProfileTimesOut = true
        val profileResult = launchSetProfile()
        yield()
        var statusResult: PumpEnactResult? = null
        backgroundScope.launch { statusResult = commandQueue.readStatus("behind a timeout") }
        yield()
        assertThat(commandQueue.size()).isEqualTo(2)

        assertIs<ListenableWorker.Result.Success>(sut.doWorkAndLog())
        yield()

        assertThat(profileResult()).isNotNull()
        assertThat(profileResult()!!.success).isFalse()
        assertThat(statusResult).isNotNull()
        assertThat(commandQueue.size()).isEqualTo(0)
    }

    @Test
    fun `a worker starting over a command left performing resumes its caller`() = runTest(timeout = 30.seconds) {
        // A previous worker died without running its finally (process kill), leaving `performing`
        // set. The new worker must resume that caller, not just clear the slot - clearing it is what
        // resetPerforming() used to do here, and the caller then waited out its own timeout.
        val result = launchSetProfile()
        yield()
        commandQueue.pickup()
        assertThat(commandQueue.performing).isNotNull()
        assertThat(result()).isNull()

        assertIs<ListenableWorker.Result.Success>(sut.doWorkAndLog())
        yield()

        assertThat(commandQueue.performing).isNull()
        assertThat(result()).isNotNull()
        assertThat(result()!!.success).isFalse()
        assertThat(result()!!.comment).isEqualTo("Interrupted")
    }

    @Test
    fun `a bare CancellationException from the driver propagates but still resumes the caller`() = runTest(timeout = 30.seconds) {
        // Real coroutine cancellation must keep unwinding - the queue must not swallow it the way it
        // now handles a driver timeout. The finally block is what stops that rethrow from costing the
        // caller its callback.
        testPumpPlugin.setNewBasalProfileThrowsCancellation = true
        val result = launchSetProfile()
        yield()

        assertFailsWith<CancellationException> { sut.doWorkAndLog() }
        yield()

        assertThat(commandQueue.performing).isNull()
        assertThat(result()).isNotNull()
        assertThat(result()!!.success).isFalse()
        assertThat(result()!!.comment).isEqualTo("Interrupted")
    }

    @Test
    fun `a command completing normally still resolves its caller through the worker`() = runTest(timeout = 30.seconds) {
        // Control for the tests above: same path, no timeout and no cancellation.
        val result = launchSetProfile()
        yield()

        assertIs<ListenableWorker.Result.Success>(sut.doWorkAndLog())
        yield()

        assertThat(result()).isNotNull()
        assertThat(commandQueue.performing).isNull()
        assertThat(commandQueue.size()).isEqualTo(0)
    }
    // endregion
}
