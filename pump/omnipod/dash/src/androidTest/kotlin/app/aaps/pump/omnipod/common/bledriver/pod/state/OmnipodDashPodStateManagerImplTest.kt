package app.aaps.pump.omnipod.common.bledriver.pod.state

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.pair.PairResult
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.bledriver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodStatus
import app.aaps.pump.omnipod.common.bledriver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.VersionResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.SetUniqueIdResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.AlarmStatusResponse
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Observable
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap

/**
 * Instrumented tests for OmnipodDashPodStateManagerImpl.
 *
 * Uses the Android test runner for real SystemClock, real Gson serialization,
 * and realistic lifecycle behavior. Dependencies (Preferences, RxBus) are
 * faked with simple in-memory implementations.
 */
@RunWith(AndroidJUnit4::class)
class OmnipodDashPodStateManagerImplTest {

    private lateinit var preferences: FakePreferences
    private lateinit var rxBus: FakeRxBus
    private lateinit var stateManager: OmnipodDashPodStateManagerImpl

    @Before
    fun setUp() {
        preferences = FakePreferences()
        rxBus = FakeRxBus()
        stateManager = OmnipodDashPodStateManagerImpl(AAPSLoggerTest(), rxBus, preferences)
    }

    private fun recreateStateManager(): OmnipodDashPodStateManagerImpl {
        return OmnipodDashPodStateManagerImpl(AAPSLoggerTest(), rxBus, preferences)
    }

    // --- Response test data ---

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    // region 1. Initial State

    @Test
    fun initialState_activationProgressIsNotStarted() {
        assertThat(stateManager.activationProgress).isEqualTo(ActivationProgress.NOT_STARTED)
    }

    @Test
    fun initialState_uniqueIdIsNull() {
        assertThat(stateManager.uniqueId).isNull()
    }

    @Test
    fun initialState_bluetoothAddressIsNull() {
        assertThat(stateManager.bluetoothAddress).isNull()
    }

    @Test
    fun initialState_ltkIsNull() {
        assertThat(stateManager.ltk).isNull()
    }

    @Test
    fun initialState_podStatusIsNull() {
        assertThat(stateManager.podStatus).isNull()
    }

    @Test
    fun initialState_isNotActivated() {
        assertThat(stateManager.isActivationCompleted).isFalse()
        assertThat(stateManager.isUniqueIdSet).isFalse()
        assertThat(stateManager.isPodRunning).isFalse()
    }

    @Test
    fun initialState_connectionCountersAreZero() {
        assertThat(stateManager.connectionAttempts).isEqualTo(0)
        assertThat(stateManager.successfulConnections).isEqualTo(0)
    }

    @Test
    fun initialState_messageSequenceNumberIsZero() {
        assertThat(stateManager.messageSequenceNumber).isEqualTo(0.toShort())
    }

    // endregion

    // region 2. State Persistence Roundtrip

    @Test
    fun persistence_activationProgressSurvivesReload() {
        stateManager.activationProgress = ActivationProgress.COMPLETED
        val reloaded = recreateStateManager()
        assertThat(reloaded.activationProgress).isEqualTo(ActivationProgress.COMPLETED)
    }

    @Test
    fun persistence_uniqueIdSurvivesReload() {
        stateManager.uniqueId = 12345L
        val reloaded = recreateStateManager()
        assertThat(reloaded.uniqueId).isEqualTo(12345L)
    }

    @Test
    fun persistence_bluetoothAddressSurvivesReload() {
        stateManager.bluetoothAddress = "AA:BB:CC:DD:EE:FF"
        val reloaded = recreateStateManager()
        assertThat(reloaded.bluetoothAddress).isEqualTo("AA:BB:CC:DD:EE:FF")
    }

    @Test
    fun persistence_ltkSurvivesReload() {
        stateManager.ltk = ByteArray(16) { it.toByte() }
        val reloaded = recreateStateManager()
        assertThat(reloaded.ltk).isEqualTo(ByteArray(16) { it.toByte() })
    }

    @Test
    fun persistence_messageSequenceNumberSurvivesReload() {
        stateManager.increaseMessageSequenceNumber()
        stateManager.increaseMessageSequenceNumber()
        stateManager.increaseMessageSequenceNumber()
        val reloaded = recreateStateManager()
        assertThat(reloaded.messageSequenceNumber).isEqualTo(3.toShort())
    }

    // endregion

    // region 3. uniqueId / bluetoothAddress Guards

    @Test
    fun uniqueId_setOnceSucceeds() {
        stateManager.uniqueId = 100L
        assertThat(stateManager.uniqueId).isEqualTo(100L)
    }

    @Test
    fun uniqueId_setSameValueTwiceSucceeds() {
        stateManager.uniqueId = 100L
        stateManager.uniqueId = 100L
        assertThat(stateManager.uniqueId).isEqualTo(100L)
    }

    @Test(expected = IllegalStateException::class)
    fun uniqueId_setDifferentValueThrows() {
        stateManager.uniqueId = 100L
        stateManager.uniqueId = 200L
    }

    @Test
    fun bluetoothAddress_setOnceSucceeds() {
        stateManager.bluetoothAddress = "AA:BB:CC:DD:EE:FF"
        assertThat(stateManager.bluetoothAddress).isEqualTo("AA:BB:CC:DD:EE:FF")
    }

    @Test(expected = IllegalStateException::class)
    fun bluetoothAddress_setDifferentValueThrows() {
        stateManager.bluetoothAddress = "AA:BB:CC:DD:EE:FF"
        stateManager.bluetoothAddress = "11:22:33:44:55:66"
    }

    // endregion

    // region 4. Message Sequence Number

    @Test
    fun messageSequenceNumber_incrementsBy1() {
        assertThat(stateManager.messageSequenceNumber).isEqualTo(0.toShort())
        stateManager.increaseMessageSequenceNumber()
        assertThat(stateManager.messageSequenceNumber).isEqualTo(1.toShort())
    }

    @Test
    fun messageSequenceNumber_wrapsAt16() {
        for (i in 0 until 16) {
            stateManager.increaseMessageSequenceNumber()
        }
        assertThat(stateManager.messageSequenceNumber).isEqualTo(0.toShort())
    }

    @Test
    fun messageSequenceNumber_wrapsCorrectlyAfter15() {
        for (i in 0 until 15) {
            stateManager.increaseMessageSequenceNumber()
        }
        assertThat(stateManager.messageSequenceNumber).isEqualTo(15.toShort())
        stateManager.increaseMessageSequenceNumber()
        assertThat(stateManager.messageSequenceNumber).isEqualTo(0.toShort())
    }

    // endregion

    // region 5. EAP-AKA Sequence Number

    @Test
    fun eapAkaSequenceNumber_initialValueIs1() {
        assertThat(stateManager.eapAkaSequenceNumber).isEqualTo(1L)
    }

    @Test
    fun eapAkaSequenceNumber_increaseReturns6ByteArray() {
        val result = stateManager.increaseEapAkaSequenceNumber()
        assertThat(result).hasLength(6)
    }

    @Test
    fun eapAkaSequenceNumber_increaseAdvancesValue() {
        val before = stateManager.eapAkaSequenceNumber
        stateManager.increaseEapAkaSequenceNumber()
        assertThat(stateManager.eapAkaSequenceNumber).isEqualTo(before + 1)
    }

    @Test
    fun eapAkaSequenceNumber_commitPersists() {
        stateManager.increaseEapAkaSequenceNumber()
        stateManager.commitEapAkaSequenceNumber()
        val reloaded = recreateStateManager()
        assertThat(reloaded.eapAkaSequenceNumber).isEqualTo(stateManager.eapAkaSequenceNumber)
    }

    // endregion

    // region 6. updateFromDefaultStatusResponse

    @Test
    fun updateFromDefaultStatusResponse_setsAllFields() {
        val encoded = hexToBytes("1D1800A02800000463FF0000")
        val response = DefaultStatusResponse(encoded)
        stateManager.updateFromDefaultStatusResponse(response)

        assertThat(stateManager.podStatus).isEqualTo(PodStatus.RUNNING_ABOVE_MIN_VOLUME)
        assertThat(stateManager.deliveryStatus).isEqualTo(DeliveryStatus.BASAL_ACTIVE)
        assertThat(stateManager.pulsesDelivered).isEqualTo(320.toShort())
        assertThat(stateManager.minutesSinceActivation).isEqualTo(280.toShort())
    }

    @Test
    fun updateFromDefaultStatusResponse_reservoirBelow1023IsStored() {
        val encoded = hexToBytes("1D1905281000004387D3039A")
        val response = DefaultStatusResponse(encoded)
        stateManager.updateFromDefaultStatusResponse(response)

        assertThat(stateManager.pulsesRemaining).isEqualTo(979.toShort())
    }

    @Test
    fun updateFromDefaultStatusResponse_reservoirAt1023IsNotStored() {
        val encoded = hexToBytes("1D1800A02800000463FF0000")
        val response = DefaultStatusResponse(encoded)
        assertThat(response.reservoirPulsesRemaining).isEqualTo(1023.toShort())

        stateManager.updateFromDefaultStatusResponse(response)
        assertThat(stateManager.pulsesRemaining).isNull()
    }

    @Test
    fun updateFromDefaultStatusResponse_setsActivationTimeOnFirstCall() {
        val encoded = hexToBytes("1D1800A02800000463FF0000")
        val response = DefaultStatusResponse(encoded)
        assertThat(stateManager.activationTime).isNull()

        stateManager.updateFromDefaultStatusResponse(response)
        assertThat(stateManager.activationTime).isNotNull()
    }

    @Test
    fun updateFromDefaultStatusResponse_persistsThroughReload() {
        val encoded = hexToBytes("1D1800A02800000463FF0000")
        stateManager.updateFromDefaultStatusResponse(DefaultStatusResponse(encoded))
        val reloaded = recreateStateManager()

        assertThat(reloaded.podStatus).isEqualTo(PodStatus.RUNNING_ABOVE_MIN_VOLUME)
        assertThat(reloaded.pulsesDelivered).isEqualTo(320.toShort())
    }

    @Test
    fun updateFromDefaultStatusResponse_firesRxBusEvent() {
        val encoded = hexToBytes("1D1800A02800000463FF0000")
        stateManager.updateFromDefaultStatusResponse(DefaultStatusResponse(encoded))
        assertThat(rxBus.sentEvents).isNotEmpty()
    }

    // endregion

    // region 7. updateFromVersionResponse

    @Test
    fun updateFromVersionResponse_setsFirmwareAndBleVersion() {
        val encoded = hexToBytes("0115040A00020701040A00020701044C0000000000000000000000000000000000000000000000")
        val response = VersionResponse(encoded)
        stateManager.updateFromVersionResponse(response)

        assertThat(stateManager.firmwareVersion).isNotNull()
        assertThat(stateManager.bluetoothVersion).isNotNull()
        assertThat(stateManager.lotNumber).isNotNull()
        assertThat(stateManager.podSequenceNumber).isNotNull()
    }

    // endregion

    // region 8. updateFromSetUniqueIdResponse

    @Test
    fun updateFromSetUniqueIdResponse_setsPumpParameters() {
        val encoded = hexToBytes("011b13881e0f0002003c040A00020701040A0002070104001392000012AE00001092")
        val response = SetUniqueIdResponse(encoded)
        stateManager.updateFromSetUniqueIdResponse(response)

        assertThat(stateManager.pulseRate).isNotNull()
        assertThat(stateManager.primePulseRate).isNotNull()
        assertThat(stateManager.podLifeInHours).isNotNull()
        assertThat(stateManager.firstPrimeBolusVolume).isNotNull()
        assertThat(stateManager.secondPrimeBolusVolume).isNotNull()
    }

    // endregion

    // region 9. updateFromAlarmStatusResponse

    @Test
    fun updateFromAlarmStatusResponse_setsAlarmFields() {
        val encoded = hexToBytes("021302020800000000000000140000000000000000000000")
        val response = AlarmStatusResponse(encoded)
        stateManager.updateFromAlarmStatusResponse(response)

        assertThat(stateManager.podStatus).isNotNull()
        assertThat(stateManager.alarmType).isNotNull()
    }

    // endregion

    // region 10. Active Command Lifecycle

    @Test
    fun createActiveCommand_succeedsWhenNoActiveCommand() {
        val result = stateManager.createActiveCommand(historyId = 1L).blockingGet()
        assertThat(result).isNotNull()
        assertThat(stateManager.activeCommand).isNotNull()
    }

    @Test
    fun createActiveCommand_failsWhenActiveCommandExists() {
        stateManager.createActiveCommand(historyId = 1L).blockingGet()
        stateManager.createActiveCommand(historyId = 2L).test().apply {
            assertError(IllegalStateException::class.java)
        }
    }

    @Test
    fun getCommandConfirmationFromState_noActiveCommandReturnsNoActiveCommand() {
        assertThat(stateManager.getCommandConfirmationFromState())
            .isEqualTo(NoActiveCommand)
    }

    @Test
    fun observeNoActiveCommand_completesWhenNoCommand() {
        stateManager.observeNoActiveCommand().test().apply {
            assertComplete()
        }
    }

    @Test
    fun observeNoActiveCommand_errorsWhenCommandExists() {
        stateManager.createActiveCommand(historyId = 1L).blockingGet()
        stateManager.observeNoActiveCommand().test().apply {
            assertError(IllegalStateException::class.java)
        }
    }

    // endregion

    // region 11. recoverActivationFromPodStatus

    @Test
    fun recoverActivation_filledToNotStarted() {
        stateManager.updatePodStatusForRecoveryTest(PodStatus.FILLED)
        stateManager.recoverActivationFromPodStatus()
        assertThat(stateManager.activationProgress).isEqualTo(ActivationProgress.NOT_STARTED)
    }

    @Test
    fun recoverActivation_uidSetToSetUniqueId() {
        stateManager.updatePodStatusForRecoveryTest(PodStatus.UID_SET)
        stateManager.recoverActivationFromPodStatus()
        assertThat(stateManager.activationProgress).isEqualTo(ActivationProgress.SET_UNIQUE_ID)
    }

    @Test
    fun recoverActivation_clutchDriveEngagedToPrimeCompleted() {
        stateManager.updatePodStatusForRecoveryTest(PodStatus.CLUTCH_DRIVE_ENGAGED)
        stateManager.recoverActivationFromPodStatus()
        assertThat(stateManager.activationProgress).isEqualTo(ActivationProgress.PRIME_COMPLETED)
    }

    @Test
    fun recoverActivation_runningAboveMinToCannulaInserted() {
        stateManager.updatePodStatusForRecoveryTest(PodStatus.RUNNING_ABOVE_MIN_VOLUME)
        stateManager.recoverActivationFromPodStatus()
        assertThat(stateManager.activationProgress).isEqualTo(ActivationProgress.CANNULA_INSERTED)
    }

    @Test
    fun recoverActivation_runningBelowMinToCannulaInserted() {
        stateManager.updatePodStatusForRecoveryTest(PodStatus.RUNNING_BELOW_MIN_VOLUME)
        stateManager.recoverActivationFromPodStatus()
        assertThat(stateManager.activationProgress).isEqualTo(ActivationProgress.CANNULA_INSERTED)
    }

    @Test
    fun recoverActivation_busyStatusReturnsBusy() {
        stateManager.updatePodStatusForRecoveryTest(PodStatus.ENGAGING_CLUTCH_DRIVE)
        val result = stateManager.recoverActivationFromPodStatus()
        assertThat(result).isEqualTo("Busy")
    }

    @Test
    fun recoverActivation_basalProgramSetToProgrammedBasal() {
        stateManager.updatePodStatusForRecoveryTest(PodStatus.BASAL_PROGRAM_SET)
        stateManager.recoverActivationFromPodStatus()
        assertThat(stateManager.activationProgress).isEqualTo(ActivationProgress.PROGRAMMED_BASAL)
    }

    // endregion

    // region 12. connectionSuccessRatio

    @Test
    fun connectionSuccessRatio_zeroWhenNoAttempts() {
        assertThat(stateManager.connectionSuccessRatio()).isEqualTo(0.0f)
    }

    @Test
    fun connectionSuccessRatio_oneWhenAllSuccessful() {
        stateManager.incrementSuccessfulConnectionAttemptsAfterRetries()
        stateManager.incrementSuccessfulConnectionAttemptsAfterRetries()
        assertThat(stateManager.connectionSuccessRatio()).isEqualTo(1.0f)
    }

    @Test
    fun connectionSuccessRatio_halfWhenEvenSplit() {
        stateManager.incrementSuccessfulConnectionAttemptsAfterRetries()
        stateManager.incrementFailedConnectionsAfterRetries()
        assertThat(stateManager.connectionSuccessRatio()).isEqualTo(0.5f)
    }

    // endregion

    // region 13. Reset

    @Test
    fun reset_clearsAllState() {
        stateManager.uniqueId = 12345L
        stateManager.bluetoothAddress = "AA:BB:CC"
        stateManager.ltk = ByteArray(16)
        stateManager.activationProgress = ActivationProgress.COMPLETED
        stateManager.increaseMessageSequenceNumber()

        stateManager.reset()

        assertThat(stateManager.uniqueId).isNull()
        assertThat(stateManager.bluetoothAddress).isNull()
        assertThat(stateManager.ltk).isNull()
        assertThat(stateManager.activationProgress).isEqualTo(ActivationProgress.NOT_STARTED)
        assertThat(stateManager.messageSequenceNumber).isEqualTo(0.toShort())
    }

    @Test
    fun reset_persistsClearedState() {
        stateManager.uniqueId = 12345L
        stateManager.reset()
        val reloaded = recreateStateManager()
        assertThat(reloaded.uniqueId).isNull()
    }

    // endregion

    // region 14. Last Bolus Lifecycle

    @Test
    fun lastBolus_createSetsFields() {
        stateManager.createLastBolus(5.0, 1L, BS.Type.NORMAL)
        val bolus = stateManager.lastBolus
        assertThat(bolus).isNotNull()
        assertThat(bolus!!.requestedUnits).isEqualTo(5.0)
        assertThat(bolus.deliveryComplete).isFalse()
    }

    @Test
    fun lastBolus_markCompleteWorks() {
        stateManager.createLastBolus(5.0, 1L, BS.Type.NORMAL)
        val completed = stateManager.markLastBolusComplete()
        assertThat(completed).isNotNull()
        assertThat(completed!!.deliveryComplete).isTrue()
    }

    @Test
    fun lastBolus_markCompleteWithNoBolusReturnsNull() {
        val result = stateManager.markLastBolusComplete()
        assertThat(result).isNull()
    }

    // endregion

    // region 15. updateFromPairing

    @Test
    fun updateFromPairing_setsLtkAndUniqueId() {
        val ltk = ByteArray(16) { (it + 0x10).toByte() }
        val pairResult = PairResult(ltk, 4.toByte())
        val uniqueId = Id.fromInt(54321)

        stateManager.updateFromPairing(uniqueId, pairResult)

        assertThat(stateManager.ltk).isEqualTo(ltk)
        assertThat(stateManager.uniqueId).isEqualTo(54321L)
        assertThat(stateManager.eapAkaSequenceNumber).isEqualTo(1L)
    }

    // endregion

    // region 16. Alert Settings

    @Test
    fun sameAlertSettings_returnsTrueWhenMatching() {
        stateManager.updateExpirationAlertSettings(true, 8, true, 72).blockingAwait()
        stateManager.updateLowReservoirAlertSettings(true, 10).blockingAwait()

        assertThat(stateManager.sameAlertSettings(true, 8, true, 72, true, 10)).isTrue()
    }

    @Test
    fun sameAlertSettings_returnsFalseWhenDifferent() {
        stateManager.updateExpirationAlertSettings(true, 8, true, 72).blockingAwait()
        stateManager.updateLowReservoirAlertSettings(true, 10).blockingAwait()

        assertThat(stateManager.sameAlertSettings(false, 8, true, 72, true, 10)).isFalse()
    }

    // endregion

    /**
     * Helper to set pod status directly for recovery testing.
     * Uses updateFromDefaultStatusResponse with a crafted payload.
     */
    private fun OmnipodDashPodStateManagerImpl.updatePodStatusForRecoveryTest(status: PodStatus) {
        val statusByte = status.value.toInt() or (DeliveryStatus.BASAL_ACTIVE.value.toInt() shl 4)
        val encoded = ByteArray(12)
        encoded[0] = 0x1D
        encoded[1] = statusByte.toByte()
        updateFromDefaultStatusResponse(DefaultStatusResponse(encoded))
    }

    // --- Fake Implementations ---

    class FakePreferences : Preferences {
        private val store = ConcurrentHashMap<String, Any>()

        override val simpleMode: Boolean = false
        override val apsMode: Boolean = true
        override val nsclientMode: Boolean = false
        override val pumpControlMode: Boolean = false

        override fun get(key: StringNonPreferenceKey): String = store[key.key] as? String ?: key.defaultValue
        override fun getIfExists(key: StringNonPreferenceKey): String? = store[key.key] as? String
        override fun put(key: StringNonPreferenceKey, value: String) { store[key.key] = value }

        // Stubs for unused methods
        override fun get(key: app.aaps.core.keys.interfaces.BooleanNonPreferenceKey) = key.defaultValue
        override fun getIfExists(key: app.aaps.core.keys.interfaces.BooleanNonPreferenceKey): Boolean? = null
        override fun put(key: app.aaps.core.keys.interfaces.BooleanNonPreferenceKey, value: Boolean) {}
        override fun get(key: app.aaps.core.keys.interfaces.BooleanPreferenceKey) = key.defaultValue
        override fun get(key: app.aaps.core.keys.interfaces.BooleanComposedNonPreferenceKey, vararg arguments: Any) = false
        override fun get(key: app.aaps.core.keys.interfaces.BooleanComposedNonPreferenceKey, vararg arguments: Any, defaultValue: Boolean) = defaultValue
        override fun getIfExists(key: app.aaps.core.keys.interfaces.BooleanComposedNonPreferenceKey, vararg arguments: Any): Boolean? = null
        override fun put(key: app.aaps.core.keys.interfaces.BooleanComposedNonPreferenceKey, vararg arguments: Any, value: Boolean) {}
        override fun remove(key: app.aaps.core.keys.interfaces.ComposedKey, vararg arguments: Any) {}
        override fun get(key: app.aaps.core.keys.interfaces.StringPreferenceKey) = key.defaultValue
        override fun get(key: app.aaps.core.keys.interfaces.StringComposedNonPreferenceKey, vararg arguments: Any) = ""
        override fun getIfExists(key: app.aaps.core.keys.interfaces.StringComposedNonPreferenceKey, vararg arguments: Any): String? = null
        override fun put(key: app.aaps.core.keys.interfaces.StringComposedNonPreferenceKey, vararg arguments: Any, value: String) {}
        override fun get(key: app.aaps.core.keys.interfaces.DoubleNonPreferenceKey) = key.defaultValue
        override fun get(key: app.aaps.core.keys.interfaces.DoublePreferenceKey) = key.defaultValue
        override fun getIfExists(key: app.aaps.core.keys.interfaces.DoublePreferenceKey): Double? = null
        override fun put(key: app.aaps.core.keys.interfaces.DoubleNonPreferenceKey, value: Double) {}
        override fun get(key: app.aaps.core.keys.interfaces.DoubleComposedNonPreferenceKey, vararg arguments: Any) = 0.0
        override fun getIfExists(key: app.aaps.core.keys.interfaces.DoubleComposedNonPreferenceKey, vararg arguments: Any): Double? = null
        override fun put(key: app.aaps.core.keys.interfaces.DoubleComposedNonPreferenceKey, vararg arguments: Any, value: Double) {}
        override fun get(key: app.aaps.core.keys.interfaces.UnitDoublePreferenceKey) = key.defaultValue
        override fun getIfExists(key: app.aaps.core.keys.interfaces.UnitDoublePreferenceKey): Double? = null
        override fun put(key: app.aaps.core.keys.interfaces.UnitDoublePreferenceKey, value: Double) {}
        override fun get(key: app.aaps.core.keys.interfaces.IntNonPreferenceKey) = key.defaultValue
        override fun getIfExists(key: app.aaps.core.keys.interfaces.IntNonPreferenceKey): Int? = null
        override fun put(key: app.aaps.core.keys.interfaces.IntComposedNonPreferenceKey, vararg arguments: Any, value: Int) {}
        override fun put(key: app.aaps.core.keys.interfaces.IntNonPreferenceKey, value: Int) {}
        override fun inc(key: app.aaps.core.keys.interfaces.IntNonPreferenceKey) {}
        override fun get(key: app.aaps.core.keys.interfaces.IntComposedNonPreferenceKey, vararg arguments: Any) = 0
        override fun get(key: app.aaps.core.keys.interfaces.IntPreferenceKey) = key.defaultValue
        override fun get(key: app.aaps.core.keys.interfaces.LongNonPreferenceKey) = key.defaultValue
        override fun getIfExists(key: app.aaps.core.keys.interfaces.LongNonPreferenceKey): Long? = null
        override fun put(key: app.aaps.core.keys.interfaces.LongNonPreferenceKey, value: Long) {}
        override fun get(key: app.aaps.core.keys.interfaces.LongPreferenceKey) = key.defaultValue
        override fun inc(key: app.aaps.core.keys.interfaces.LongNonPreferenceKey) {}
        override fun get(key: app.aaps.core.keys.interfaces.LongComposedNonPreferenceKey, vararg arguments: Any) = 0L
        override fun getIfExists(key: app.aaps.core.keys.interfaces.LongComposedNonPreferenceKey, vararg arguments: Any): Long? = null
        override fun put(key: app.aaps.core.keys.interfaces.LongComposedNonPreferenceKey, vararg arguments: Any, value: Long) {}
        override fun remove(key: app.aaps.core.keys.interfaces.NonPreferenceKey) {}
        override fun isUnitDependent(key: String) = false
        override fun get(key: String): app.aaps.core.keys.interfaces.NonPreferenceKey? = null
        override fun getIfExists(key: String): app.aaps.core.keys.interfaces.NonPreferenceKey? = null
        override fun getDependingOn(key: String): List<app.aaps.core.keys.interfaces.PreferenceKey> = emptyList()
        override fun registerPreferences(clazz: Class<out app.aaps.core.keys.interfaces.NonPreferenceKey>) {}
        override fun allMatchingStrings(key: app.aaps.core.keys.interfaces.ComposedKey): List<String> = emptyList()
        override fun allMatchingInts(key: app.aaps.core.keys.interfaces.ComposedKey): List<Int> = emptyList()
        override fun isExportableKey(key: String) = false
    }

    class FakeRxBus : RxBus {
        val sentEvents = mutableListOf<Event>()
        override fun send(event: Event) { sentEvents.add(event) }
        override fun <T : Any> toObservable(eventType: Class<T>): Observable<T> = Observable.empty()
    }
}
