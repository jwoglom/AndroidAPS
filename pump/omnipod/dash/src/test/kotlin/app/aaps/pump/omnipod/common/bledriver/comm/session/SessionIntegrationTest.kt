package app.aaps.pump.omnipod.common.bledriver.comm.session
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendErrorSending

import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.Ids
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.EnDecrypt
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.Nonce
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.io.FakeCmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.io.FakeDataBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessagePacket
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageType
import app.aaps.pump.omnipod.common.bledriver.comm.message.StringLengthPrefixEncoding
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
 * Phase 2: Session layer integration tests.
 *
 * Tests command send/receive/ACK through the full MessageIO protocol stack
 * with fake BLE transport. Validates retry logic, error propagation, and
 * sequence number management.
 *
 * DUAL-IMPLEMENTATION NOTE: Session is implementation-agnostic — it operates
 * on MessageIO which uses the CmdBleIO/DataBleIO interfaces. These tests
 * validate Session behavior identically for both old and new BLE backends.
 */
class SessionIntegrationTest {

    private val logger = AAPSLoggerTest()
    private lateinit var cmdBleIO: FakeCmdBleIO
    private lateinit var dataBleIO: FakeDataBleIO
    private lateinit var msgIO: MessageIO
    private lateinit var session: Session
    private lateinit var enDecrypt: EnDecrypt
    private lateinit var podEnDecrypt: EnDecrypt

    private val testCk = ByteArray(16) { (it + 1).toByte() }
    private val myId = Id.fromInt(4242)
    private val podId = Id.fromInt(4243)

    @BeforeEach
    fun setUp() {
        cmdBleIO = FakeCmdBleIO()
        dataBleIO = FakeDataBleIO()
        msgIO = MessageIO(logger, cmdBleIO, dataBleIO)

        val nonce = Nonce(prefix = ByteArray(8) { 0 }, sqn = 0)
        enDecrypt = EnDecrypt(logger, nonce, testCk)

        val podNonce = Nonce(prefix = ByteArray(8) { 0 }, sqn = 0)
        podEnDecrypt = EnDecrypt(logger, podNonce, testCk)

        val podState = mock<OmnipodDashPodStateManager>()
        whenever(podState.uniqueId).thenReturn(4243L)
        val ids = Ids(podState)

        val sessionKeys = SessionKeys(
            ck = testCk,
            nonce = Nonce(prefix = ByteArray(8) { 0 }, sqn = 0),
            msgSequenceNumber = 0
        )
        session = Session(logger, msgIO, ids, sessionKeys = sessionKeys, enDecrypt = enDecrypt)
    }

    private fun scriptSendSuccess() {
        cmdBleIO.expectCommandResults(BleConfirmSuccess, BleConfirmSuccess)
    }

    private fun createEncryptedResponse(seqNum: Byte): ByteArray {
        val responsePayload = byteArrayOf(
            0x00, 0x00, 0x10, 0x92.toByte(),
            0x00, 0x04,
            0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        val wrappedPayload = StringLengthPrefixEncoding.formatKeys(
            arrayOf("0.0="),
            arrayOf(responsePayload)
        )
        val responseMsg = MessagePacket(
            type = MessageType.ENCRYPTED,
            sequenceNumber = seqNum,
            source = podId,
            destination = myId,
            payload = wrappedPayload,
            eqos = 1
        )
        return podEnDecrypt.encrypt(responseMsg).asByteArray()
    }

    private fun scriptReceiveResponse(seqNum: Byte) {
        val encryptedBytes = createEncryptedResponse(seqNum)
        val packets = PayloadSplitter(encryptedBytes).splitInPackets()
        cmdBleIO.expectCommandResults(BleConfirmSuccess)
        for (packet in packets) {
            dataBleIO.enqueueReceives(packet.toByteArray())
        }
    }

    @Nested
    @DisplayName("2.1 sendCommand")
    inner class SendCommand {

        @Test
        fun `command sent successfully on first try`() {
            scriptSendSuccess()

            val result = session.sendCommand(
                app.aaps.pump.omnipod.common.bledriver.pod.command.GetStatusCommand.Builder()
                    .setUniqueId(4243)
                    .setSequenceNumber(0)
                    .setStatusResponseType(
                        app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE
                    )
                    .build()
            )

            assertThat(result).isInstanceOf(CommandSendSuccess::class.java)
        }

        @Test
        fun `send fails with sending error on all retries`() {
            cmdBleIO.sendResult = app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendErrorSending("fail")

            val result = session.sendCommand(
                app.aaps.pump.omnipod.common.bledriver.pod.command.GetStatusCommand.Builder()
                    .setUniqueId(4243)
                    .setSequenceNumber(0)
                    .setStatusResponseType(
                        app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE
                    )
                    .build()
            )

            assertThat(result).isInstanceOf(CommandSendErrorSending::class.java)
        }

        @Test
        fun `sequence number incremented after sendCommand call`() {
            val initialSeq = session.sessionKeys.msgSequenceNumber
            scriptSendSuccess()

            session.sendCommand(
                app.aaps.pump.omnipod.common.bledriver.pod.command.GetStatusCommand.Builder()
                    .setUniqueId(4243)
                    .setSequenceNumber(0)
                    .setStatusResponseType(
                        app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE
                    )
                    .build()
            )

            assertThat(session.sessionKeys.msgSequenceNumber).isEqualTo((initialSeq + 1).toByte())
        }
    }

    @Nested
    @DisplayName("2.2 readAndAckResponse")
    inner class ReadAndAckResponse {

        @Test
        fun `response receive fails after all retries returns CommandReceiveError`() {
            val result = session.readAndAckResponse()
            assertThat(result).isInstanceOf(CommandReceiveError::class.java)
        }
    }
}
