package app.aaps.pump.omnipod.common.bledriver.comm.pair

import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.Ids
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.PodScanner
import app.aaps.pump.omnipod.common.bledriver.comm.io.BleConfirmSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.io.FakeCmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.io.FakeDataBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessagePacket
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageType
import app.aaps.pump.omnipod.common.bledriver.comm.packet.PayloadSplitter
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Phase 4: LTK Exchanger / Pairing integration tests.
 *
 * Since LTKExchanger is `internal` to the common module, these tests validate
 * the pairing protocol's supporting types and the MessageIO-level behavior
 * that the pairing flow depends on.
 *
 * DUAL-IMPLEMENTATION NOTE: The pairing protocol uses MessageIO, which is
 * transport-agnostic via CmdBleIO/DataBleIO interfaces. The pairing sequence
 * (SP1→SP2→SPS1→SPS2→SP0GP0→P0) uses the same RTS/CTS/DATA protocol.
 * Key differences between old and new:
 * - Old: pairing discovery via raw BluetoothLeScanner
 * - New: pairing discovery via BlessedPodScanner (BluetoothCentralManager)
 * The message-level exchange is identical.
 */
class LTKExchangerIntegrationTest {

    private val logger = AAPSLoggerTest()
    private lateinit var cmdBleIO: FakeCmdBleIO
    private lateinit var dataBleIO: FakeDataBleIO
    private lateinit var msgIO: MessageIO

    @BeforeEach
    fun setUp() {
        cmdBleIO = FakeCmdBleIO()
        dataBleIO = FakeDataBleIO()
        msgIO = MessageIO(logger, cmdBleIO, dataBleIO)
    }

    @Nested
    @DisplayName("4.1 Ids — Pod Identity Management")
    inner class IdsTests {

        @Test
        fun `Ids with null uniqueId uses incremented controller ID`() {
            val podState = mock<OmnipodDashPodStateManager>()
            whenever(podState.uniqueId).thenReturn(null)
            val ids = Ids(podState)
            assertThat(ids.podId).isNotNull()
            assertThat(ids.myId).isEqualTo(Id.fromInt(4242))
        }

        @Test
        fun `Ids with uniqueId uses that ID for podId`() {
            val podState = mock<OmnipodDashPodStateManager>()
            whenever(podState.uniqueId).thenReturn(12345L)
            val ids = Ids(podState)
            assertThat(ids.podId.toLong()).isEqualTo(12345L)
        }

        @Test
        fun `notActivated returns POD_ID_NOT_ACTIVATED`() {
            val notActivated = Ids.notActivated()
            assertThat(notActivated.toLong()).isEqualTo(PodScanner.POD_ID_NOT_ACTIVATED)
        }

        @Test
        fun `controller ID is always 4242`() {
            val podState = mock<OmnipodDashPodStateManager>()
            whenever(podState.uniqueId).thenReturn(99L)
            val ids = Ids(podState)
            assertThat(ids.myId).isEqualTo(Id.fromInt(4242))
        }
    }

    @Nested
    @DisplayName("4.2 PodScanner Constants")
    inner class PodScannerConstants {

        @Test
        fun `SCAN_FOR_SERVICE_UUID is correct`() {
            assertThat(PodScanner.SCAN_FOR_SERVICE_UUID)
                .isEqualTo("00004024-0000-1000-8000-00805F9B34FB")
        }

        @Test
        fun `POD_ID_NOT_ACTIVATED is 0xFFFFFFFE`() {
            assertThat(PodScanner.POD_ID_NOT_ACTIVATED).isEqualTo(0xFFFFFFFEL)
        }
    }

    @Nested
    @DisplayName("4.3 Pairing Message Protocol via MessageIO")
    inner class PairingProtocol {

        @Test
        fun `pairing message can be sent via MessageIO RTS-CTS protocol`() {
            val pairingMsg = MessagePacket(
                type = MessageType.PAIRING,
                source = Id.fromInt(4242),
                destination = Ids.notActivated(),
                sequenceNumber = 1.toByte(),
                payload = byteArrayOf(0x01, 0x02, 0x03, 0x04)
            )

            cmdBleIO.expectCommandResults(BleConfirmSuccess, BleConfirmSuccess)
            val result = msgIO.sendMessage(pairingMsg)

            assertThat(result).isEqualTo(MessageSendSuccess)
        }

        @Test
        fun `pairing response can be received via MessageIO`() {
            // NOTE: MessagePacket.parse derives type from eqos/tfs bits in f1,
            // so eqos must match type value for correct roundtrip.
            // In production, the receiver never checks the type field — only the payload.
            val pairingResponse = MessagePacket(
                type = MessageType.PAIRING,
                source = Ids.notActivated(),
                destination = Id.fromInt(4242),
                sequenceNumber = 1.toByte(),
                eqos = MessageType.PAIRING.value.toShort(),
                payload = byteArrayOf(0x05, 0x06, 0x07, 0x08)
            )
            val responseBytes = pairingResponse.asByteArray()
            val packets = PayloadSplitter(responseBytes).splitInPackets()

            cmdBleIO.expectCommandResults(BleConfirmSuccess)
            for (p in packets) {
                dataBleIO.enqueueReceives(p.toByteArray())
            }

            val received = msgIO.receiveMessage()

            assertThat(received).isNotNull()
            assertThat(received!!.payload).isEqualTo(pairingResponse.payload)
        }

        @Test
        fun `pairing send-receive roundtrip preserves payload`() {
            val msg = MessagePacket(
                type = MessageType.PAIRING,
                source = Id.fromInt(4242),
                destination = Ids.notActivated(),
                sequenceNumber = 2.toByte(),
                eqos = MessageType.PAIRING.value.toShort(),
                payload = ByteArray(50) { (it * 3).toByte() }
            )

            cmdBleIO.expectCommandResults(BleConfirmSuccess, BleConfirmSuccess)
            val sendResult = msgIO.sendMessage(msg)
            assertThat(sendResult).isEqualTo(MessageSendSuccess)

            val sentData = dataBleIO.sentPayloads.toList()

            cmdBleIO.reset()
            dataBleIO.reset()
            msgIO = MessageIO(logger, cmdBleIO, dataBleIO)

            cmdBleIO.expectCommandResults(BleConfirmSuccess)
            for (p in sentData) {
                dataBleIO.enqueueReceives(p)
            }
            val received = msgIO.receiveMessage()

            assertThat(received).isNotNull()
            assertThat(received!!.payload).isEqualTo(msg.payload)
        }

        @Test
        fun `session establishment message can be sent and received`() {
            val msg = MessagePacket(
                type = MessageType.SESSION_ESTABLISHMENT,
                source = Id.fromInt(4242),
                destination = Id.fromInt(4243),
                sequenceNumber = 3.toByte(),
                eqos = MessageType.SESSION_ESTABLISHMENT.value.toShort(),
                payload = ByteArray(40) { it.toByte() }
            )

            cmdBleIO.expectCommandResults(BleConfirmSuccess, BleConfirmSuccess)
            val sendResult = msgIO.sendMessage(msg)
            assertThat(sendResult).isEqualTo(MessageSendSuccess)

            val sentData = dataBleIO.sentPayloads.toList()
            cmdBleIO.reset()
            dataBleIO.reset()
            msgIO = MessageIO(logger, cmdBleIO, dataBleIO)

            cmdBleIO.expectCommandResults(BleConfirmSuccess)
            for (p in sentData) {
                dataBleIO.enqueueReceives(p)
            }
            val received = msgIO.receiveMessage()

            assertThat(received).isNotNull()
            assertThat(received!!.payload).isEqualTo(msg.payload)
        }
    }

    @Nested
    @DisplayName("4.4 Id Operations")
    inner class IdOperations {

        @Test
        fun `Id fromInt and fromLong produce same result for small values`() {
            val fromInt = Id.fromInt(4242)
            val fromLong = Id.fromLong(4242L)
            assertThat(fromInt).isEqualTo(fromLong)
        }

        @Test
        fun `Id increment changes last byte`() {
            val id = Id.fromInt(4242)
            val incremented = id.increment()
            assertThat(incremented).isNotEqualTo(id)
        }

        @Test
        fun `Id toLong roundtrips correctly`() {
            val original = 12345L
            val id = Id.fromLong(original)
            assertThat(id.toLong()).isEqualTo(original)
        }

        @Test
        fun `Id address is 4 bytes`() {
            val id = Id.fromInt(4242)
            assertThat(id.address).hasLength(4)
        }
    }
}
