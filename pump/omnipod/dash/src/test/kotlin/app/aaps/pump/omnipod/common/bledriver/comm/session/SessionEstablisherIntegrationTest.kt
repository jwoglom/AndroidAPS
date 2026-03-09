package app.aaps.pump.omnipod.common.bledriver.comm.session

import app.aaps.core.interfaces.configuration.Config
import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.Ids
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.SessionEstablishmentException
import app.aaps.pump.omnipod.common.bledriver.comm.io.BleConfirmError
import app.aaps.pump.omnipod.common.bledriver.comm.io.BleConfirmSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.io.BleSendErrorSending
import app.aaps.pump.omnipod.common.bledriver.comm.io.FakeCmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.io.FakeDataBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessagePacket
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageType
import app.aaps.pump.omnipod.common.bledriver.comm.packet.PayloadSplitter
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Phase 3: SessionEstablisher integration tests.
 *
 * Tests EAP-AKA session negotiation including challenge/response,
 * resynchronization, and various error conditions.
 *
 * DUAL-IMPLEMENTATION NOTE: SessionEstablisher is transport-agnostic,
 * operating through MessageIO. These tests validate the EAP-AKA protocol
 * logic identically for both old and new BLE backends.
 */
class SessionEstablisherIntegrationTest {

    private val logger = AAPSLoggerTest()
    private val config = mock<Config>()
    private lateinit var cmdBleIO: FakeCmdBleIO
    private lateinit var dataBleIO: FakeDataBleIO
    private lateinit var msgIO: MessageIO

    private val testLtk = ByteArray(16) { (it + 0x10).toByte() }
    private val testEapSqn = ByteArray(6) { 0x00 }
    private val myId = Id.fromInt(4242)
    private val podId = Id.fromInt(4243)

    @BeforeEach
    fun setUp() {
        cmdBleIO = FakeCmdBleIO()
        dataBleIO = FakeDataBleIO()
        msgIO = MessageIO(logger, cmdBleIO, dataBleIO)
        whenever(config.DEBUG).thenReturn(false)
    }

    private fun createIds(): Ids {
        val podState = mock<OmnipodDashPodStateManager>()
        whenever(podState.uniqueId).thenReturn(4243L)
        return Ids(podState)
    }

    @Nested
    @DisplayName("3.1 EAP-AKA Key Exchange Failures")
    inner class EapAkaFailures {

        @Test
        fun `challenge send fails throws SessionEstablishmentException`() {
            cmdBleIO.sendResult = BleSendErrorSending("write failed")

            val ids = createIds()
            val establisher = SessionEstablisher(logger, config, msgIO, testLtk, testEapSqn, ids, 0)

            assertThrows<SessionEstablishmentException> {
                establisher.negotiateSessionKeys()
            }
        }

        @Test
        fun `challenge response never arrives throws SessionEstablishmentException`() {
            cmdBleIO.expectCommandResults(BleConfirmSuccess, BleConfirmSuccess)

            val ids = createIds()
            val establisher = SessionEstablisher(logger, config, msgIO, testLtk, testEapSqn, ids, 0)

            assertThrows<SessionEstablishmentException> {
                establisher.negotiateSessionKeys()
            }
        }
    }

    @Nested
    @DisplayName("3.2 Input Validation")
    inner class InputValidation {

        @Test
        fun `LTK with wrong length throws IllegalArgumentException`() {
            val wrongLtk = ByteArray(10)
            val ids = createIds()

            assertThrows<IllegalArgumentException> {
                SessionEstablisher(logger, config, msgIO, wrongLtk, testEapSqn, ids, 0)
            }
        }

        @Test
        fun `EAP SQN with wrong length throws IllegalArgumentException`() {
            val wrongSqn = ByteArray(4)
            val ids = createIds()

            assertThrows<IllegalArgumentException> {
                SessionEstablisher(logger, config, msgIO, testLtk, wrongSqn, ids, 0)
            }
        }
    }
}
