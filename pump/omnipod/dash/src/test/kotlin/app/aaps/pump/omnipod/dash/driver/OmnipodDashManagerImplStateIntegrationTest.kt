package app.aaps.pump.omnipod.dash.driver

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.OmnipodDashBleManager
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionState
import app.aaps.pump.omnipod.common.bledriver.comm.session.NotConnected
import app.aaps.pump.omnipod.common.bledriver.event.PodEvent
import app.aaps.pump.omnipod.common.bledriver.pod.command.base.Command
import app.aaps.pump.omnipod.common.bledriver.pod.command.base.CommandType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BasalProgram
import app.aaps.pump.omnipod.common.bledriver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodStatus
import app.aaps.pump.omnipod.common.bledriver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.Response
import app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType
import app.aaps.pump.omnipod.common.bledriver.pod.response.SetUniqueIdResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.VersionResponse
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManagerImpl
import app.aaps.pump.omnipod.common.keys.DashStringNonPreferenceKey
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Observable
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

class OmnipodDashManagerImplStateIntegrationTest : TestBase() {

    @Mock lateinit var preferences: Preferences

    private lateinit var podStateManager: OmnipodDashPodStateManagerImpl
    private lateinit var bleManager: ScriptedDashBleManager
    private lateinit var manager: OmnipodDashManagerImpl

    @BeforeEach
    fun setUp() {
        whenever(preferences.getIfExists(DashStringNonPreferenceKey.PodState)).thenReturn(null)

        podStateManager = OmnipodDashPodStateManagerImpl(aapsLogger, rxBus, preferences)
        bleManager = ScriptedDashBleManager()
        manager = OmnipodDashManagerImpl(
            logger = aapsLogger,
            podStateManager = podStateManager,
            bleManager = bleManager,
            aapsSchedulers = aapsSchedulers
        )
    }

    @Test
    fun `getStatus updates pod state and message sequence from emitted events`() {
        podStateManager.uniqueId = TEST_UNIQUE_ID
        podStateManager.activationProgress = ActivationProgress.SET_UNIQUE_ID
        bleManager.enqueueConnect(connectEvents())
        bleManager.enqueueCommand { command ->
            Observable.just(
                PodEvent.CommandSent(command),
                PodEvent.ResponseReceived(command, runningStatusResponse())
            )
        }

        val observer = manager.getStatus(ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE).test()

        observer.assertComplete()
        observer.assertNoErrors()
        assertThat(bleManager.sentCommands.map { it.commandType }).containsExactly(CommandType.GET_STATUS)
        assertThat(bleManager.sentResponseTypes.single()).isEqualTo(DefaultStatusResponse::class)
        assertThat(podStateManager.messageSequenceNumber).isEqualTo(2.toShort())
        assertThat(podStateManager.podStatus).isEqualTo(PodStatus.RUNNING_ABOVE_MIN_VOLUME)
        assertThat(podStateManager.deliveryStatus).isEqualTo(DeliveryStatus.BASAL_ACTIVE)
        assertThat(podStateManager.lastStatusResponseReceived).isGreaterThan(0L)
        assertThat(podStateManager.activationTime).isNotNull()
    }

    @Test
    fun `activatePodPart1 updates activation progress and stores pairing version and prime state`() {
        bleManager.enqueuePair(
            Observable.just(
                PodEvent.Scanning,
                PodEvent.Pairing,
                PodEvent.Paired(Id.fromLong(TEST_UNIQUE_ID)),
                PodEvent.Connected
            )
        )
        bleManager.enqueueConnect(connectEvents())
        bleManager.enqueueConnect(connectEvents())
        bleManager.enqueueCommand { command ->
            Observable.just(
                PodEvent.CommandSent(command),
                PodEvent.ResponseReceived(command, versionResponse())
            )
        }
        bleManager.enqueueCommand { command ->
            Observable.just(
                PodEvent.CommandSent(command),
                PodEvent.ResponseReceived(command, setUniqueIdResponse(firstPrimePulses = 0, secondPrimePulses = 0))
            )
        }
        bleManager.enqueueCommand { command ->
            Observable.just(
                PodEvent.CommandSent(command),
                PodEvent.ResponseReceived(command, runningStatusResponse())
            )
        }
        bleManager.enqueueCommand { command ->
            Observable.just(
                PodEvent.CommandSent(command),
                PodEvent.ResponseReceived(command, runningStatusResponse())
            )
        }
        bleManager.enqueueCommand { command ->
            Observable.just(
                PodEvent.CommandSent(command),
                PodEvent.ResponseReceived(command, clutchDriveStatusResponse())
            )
        }

        val observer = manager.activatePodPart1(lowReservoirAlertTrigger = null).test()

        observer.awaitDone(2, TimeUnit.SECONDS)
        observer.assertComplete()
        observer.assertNoErrors()
        assertThat(bleManager.pairCalls).isEqualTo(1)
        assertThat(bleManager.connectCalls).isEqualTo(2)
        assertThat(bleManager.sentCommands.map { it.commandType }).containsExactly(
            CommandType.GET_VERSION,
            CommandType.SET_UNIQUE_ID,
            CommandType.PROGRAM_ALERTS,
            CommandType.PROGRAM_BOLUS,
            CommandType.GET_STATUS
        ).inOrder()
        assertThat(podStateManager.activationProgress).isEqualTo(ActivationProgress.PHASE_1_COMPLETED)
        assertThat(podStateManager.uniqueId).isEqualTo(TEST_UNIQUE_ID)
        assertThat(podStateManager.lotNumber).isEqualTo(135556289L)
        assertThat(podStateManager.podSequenceNumber).isEqualTo(611540L)
        assertThat(podStateManager.firstPrimeBolusVolume).isEqualTo(0.toShort())
        assertThat(podStateManager.podStatus).isEqualTo(PodStatus.CLUTCH_DRIVE_ENGAGED)
    }

    @Test
    fun `activatePodPart2 completes activation and updates timezone metadata`() {
        preparePhase1CompletedPod()
        bleManager.enqueueConnect(connectEvents())
        bleManager.enqueueConnect(connectEvents())
        bleManager.enqueueCommand { command ->
            Observable.just(
                PodEvent.CommandSent(command),
                PodEvent.ResponseReceived(command, runningStatusResponse())
            )
        }
        bleManager.enqueueCommand { command ->
            Observable.just(
                PodEvent.CommandSent(command),
                PodEvent.ResponseReceived(command, runningStatusResponse())
            )
        }
        bleManager.enqueueCommand { command ->
            Observable.just(
                PodEvent.CommandSent(command),
                PodEvent.ResponseReceived(command, runningStatusResponse())
            )
        }
        bleManager.enqueueCommand { command ->
            Observable.just(
                PodEvent.CommandSent(command),
                PodEvent.ResponseReceived(command, runningStatusResponse())
            )
        }

        val basalProgram = BasalProgram(
            listOf(
                BasalProgram.Segment(
                    startSlotIndex = 0,
                    endSlotIndex = 48,
                    basalRateInHundredthUnitsPerHour = 100
                )
            )
        )

        val observer = manager.activatePodPart2(
            basalProgram = basalProgram,
            userConfiguredExpirationReminderHours = 2L,
            userConfiguredExpirationAlarmHours = 1L
        ).test()

        observer.awaitDone(2, TimeUnit.SECONDS)
        observer.assertComplete()
        observer.assertNoErrors()
        assertThat(bleManager.connectCalls).isEqualTo(2)
        assertThat(bleManager.sentCommands.map { it.commandType }).containsExactly(
            CommandType.PROGRAM_BASAL,
            CommandType.PROGRAM_ALERTS,
            CommandType.PROGRAM_BOLUS,
            CommandType.GET_STATUS
        ).inOrder()
        assertThat(podStateManager.activationProgress).isEqualTo(ActivationProgress.COMPLETED)
        assertThat(podStateManager.timeZoneUpdated).isNotNull()
        assertThat(podStateManager.podStatus).isEqualTo(PodStatus.RUNNING_ABOVE_MIN_VOLUME)
    }

    @Test
    fun `deactivatePod removes the bond after a successful response`() {
        prepareActivePod()
        bleManager.enqueueConnect(connectEvents())
        bleManager.enqueueCommand { command ->
            Observable.just(
                PodEvent.CommandSent(command),
                PodEvent.ResponseReceived(command, runningStatusResponse())
            )
        }

        val observer = manager.deactivatePod().test()

        observer.assertComplete()
        observer.assertNoErrors()
        assertThat(bleManager.sentCommands.map { it.commandType }).containsExactly(CommandType.DEACTIVATE)
        assertThat(bleManager.removeBondCalls).isEqualTo(1)
    }

    @Test
    fun `suspendDelivery marks suspend alerts enabled after success`() {
        prepareActivePod()
        podStateManager.activationProgress = ActivationProgress.COMPLETED
        bleManager.enqueueConnect(connectEvents())
        bleManager.enqueueCommand { command ->
            Observable.just(
                PodEvent.CommandSent(command),
                PodEvent.ResponseReceived(command, runningStatusResponse())
            )
        }

        val observer = manager.suspendDelivery(hasBasalBeepEnabled = true).test()

        observer.assertComplete()
        observer.assertNoErrors()
        assertThat(bleManager.sentCommands.map { it.commandType }).containsExactly(CommandType.STOP_DELIVERY)
        assertThat(podStateManager.suspendAlertsEnabled).isTrue()
    }

    private fun prepareActivePod() {
        podStateManager.updateFromSetUniqueIdResponse(setUniqueIdResponse())
        podStateManager.updateFromDefaultStatusResponse(runningStatusResponse())
        podStateManager.activationProgress = ActivationProgress.COMPLETED
    }

    private fun preparePhase1CompletedPod() {
        podStateManager.updateFromSetUniqueIdResponse(setUniqueIdResponse(firstPrimePulses = 0, secondPrimePulses = 0))
        podStateManager.updateFromDefaultStatusResponse(runningStatusResponse())
        podStateManager.activationProgress = ActivationProgress.PHASE_1_COMPLETED
    }

    private fun connectEvents(): Observable<PodEvent> =
        Observable.just(
            PodEvent.BluetoothConnecting,
            PodEvent.BluetoothConnected(TEST_ADDRESS),
            PodEvent.EstablishingSession,
            PodEvent.Connected
        )

    private fun versionResponse(): VersionResponse =
        VersionResponse(Hex.decodeHex("0115040A00010300040208146CC1000954D400FFFFFFFF"))

    private fun setUniqueIdResponse(
        firstPrimePulses: Int = 52,
        secondPrimePulses: Int = 10
    ): SetUniqueIdResponse {
        val encoded = Hex.decodeHex("011B13881008340A50040A00010300040308146CC1000954D402420001")
        encoded[6] = firstPrimePulses.toByte()
        encoded[7] = secondPrimePulses.toByte()
        return SetUniqueIdResponse(encoded)
    }

    private fun runningStatusResponse(): DefaultStatusResponse =
        DefaultStatusResponse(Hex.decodeHex("1D1800A02800000463FF"))

    private fun clutchDriveStatusResponse(): DefaultStatusResponse {
        val encoded = Hex.decodeHex("1D1800A02800000463FF")
        encoded[1] = 0x15.toByte()
        return DefaultStatusResponse(encoded)
    }

    private class ScriptedDashBleManager : OmnipodDashBleManager {
        private val connectPlans = ArrayDeque<Observable<PodEvent>>()
        private val pairPlans = ArrayDeque<Observable<PodEvent>>()
        private val commandPlans = ArrayDeque<(Command) -> Observable<PodEvent>>()

        val sentCommands = mutableListOf<Command>()
        val sentResponseTypes = mutableListOf<KClass<out Response>>()
        var connectCalls = 0
        var pairCalls = 0
        var removeBondCalls = 0
        val disconnectCalls = mutableListOf<Boolean>()

        fun enqueueConnect(observable: Observable<PodEvent>) {
            connectPlans.addLast(observable)
        }

        fun enqueuePair(observable: Observable<PodEvent>) {
            pairPlans.addLast(observable)
        }

        fun enqueueCommand(plan: (Command) -> Observable<PodEvent>) {
            commandPlans.addLast(plan)
        }

        override fun sendCommand(cmd: Command, responseType: KClass<out Response>): Observable<PodEvent> {
            sentCommands += cmd
            sentResponseTypes += responseType
            val plan = if (commandPlans.isEmpty()) null else commandPlans.removeFirst()
            return plan?.invoke(cmd) ?: error("No scripted sendCommand response left for ${cmd.commandType}")
        }

        override fun getStatus(): ConnectionState = NotConnected

        override fun connect(timeoutMs: Long): Observable<PodEvent> {
            connectCalls++
            return if (connectPlans.isEmpty()) error("No scripted connect response left") else connectPlans.removeFirst()
        }

        override fun connect(stopConnectionLatch: CountDownLatch): Observable<PodEvent> {
            connectCalls++
            return if (connectPlans.isEmpty()) error("No scripted connect response left") else connectPlans.removeFirst()
        }

        override fun pairNewPod(): Observable<PodEvent> {
            pairCalls++
            return if (pairPlans.isEmpty()) error("No scripted pair response left") else pairPlans.removeFirst()
        }

        override fun disconnect(closeGatt: Boolean) {
            disconnectCalls += closeGatt
        }

        override fun removeBond() {
            removeBondCalls++
        }
    }

    private companion object {
        const val TEST_UNIQUE_ID = 37879809L
        const val TEST_ADDRESS = "AA:BB:CC:DD:EE:FF"
    }
}
