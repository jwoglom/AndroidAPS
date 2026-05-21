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
import app.aaps.pump.omnipod.common.bledriver.pod.command.GetStatusCommand
import app.aaps.pump.omnipod.common.bledriver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.spongycastle.crypto.engines.AESEngine
import org.spongycastle.crypto.modes.CCMBlockCipher
import org.spongycastle.crypto.params.AEADParameters
import org.spongycastle.crypto.params.KeyParameter

/**
 * Phase 2: Session layer integration tests.
 *
 * Tests command send/receive/ACK through the full MessageIO protocol stack
 * with fake BLE transport. Validates retry logic, error propagation,
 * sequence number management, and the full encrypted response flow.
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
    private lateinit var sessionNonce: Nonce

    private val testCk = ByteArray(16) { (it + 1).toByte() }
    private val myId = Id.fromInt(4242)
    private val podId = Id.fromInt(4243)

    @BeforeEach
    fun setUp() {
        cmdBleIO = FakeCmdBleIO()
        dataBleIO = FakeDataBleIO()
        msgIO = MessageIO(logger, cmdBleIO, dataBleIO)

        sessionNonce = Nonce(prefix = ByteArray(8) { 0 }, sqn = 0)
        enDecrypt = EnDecrypt(logger, sessionNonce, testCk)

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

    /**
     * Encrypt a response message as the pod would, using CCM with the matching
     * nonce direction (podReceiving=false → bit 7 set → matches controller decrypt).
     */
    private fun podSideEncrypt(msg: MessagePacket, sqnValue: Long): MessagePacket {
        val payload = msg.payload
        val header = msg.asByteArray(true).copyOfRange(0, 16)
        val podNonce = Nonce(prefix = ByteArray(8) { 0 }, sqn = sqnValue - 1)
        val n = podNonce.increment(false)
        val cipher = CCMBlockCipher(AESEngine())
        cipher.init(true, AEADParameters(KeyParameter(testCk), 64, n, header))
        val encryptedPayload = ByteArray(payload.size + 8)
        cipher.processPacket(payload, 0, payload.size, encryptedPayload, 0)
        return msg.copy(payload = encryptedPayload)
    }

    private fun createDefaultStatusPayload(): ByteArray {
        val statusBytes = hexToBytes("1D1800A02800000463FF0000")
        val uniqueIdBytes = byteArrayOf(0x00, 0x00, 0x10, 0x93.toByte())
        val seqAndLen = byteArrayOf(0x00, 0x0C)
        val crc = byteArrayOf(0x00, 0x00)
        return uniqueIdBytes + seqAndLen + statusBytes + crc
    }

    private fun scriptReceiveEncryptedResponse() {
        val responsePlain = createDefaultStatusPayload()
        val wrappedPayload = StringLengthPrefixEncoding.formatKeys(
            arrayOf("0.0="),
            arrayOf(responsePlain)
        )
        val responseMsg = MessagePacket(
            type = MessageType.ENCRYPTED,
            sequenceNumber = 0.toByte(),
            source = podId,
            destination = myId,
            payload = wrappedPayload,
            eqos = 1
        )
        val currentSqn = sessionNonce.sqn
        val encryptedMsg = podSideEncrypt(responseMsg, currentSqn + 1)
        val encryptedBytes = encryptedMsg.asByteArray()
        val packets = PayloadSplitter(encryptedBytes).splitInPackets()

        cmdBleIO.expectCommandResults(
            BleConfirmSuccess, // receiveMessage: expect RTS
            BleConfirmSuccess, // sendMessage (ACK): expect CTS
            BleConfirmSuccess  // sendMessage (ACK): expect SUCCESS
        )
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
            val result = session.sendCommand(createGetStatusCommand())
            assertThat(result).isInstanceOf(CommandSendSuccess::class.java)
        }

        @Test
        fun `send fails with sending error on all retries`() {
            cmdBleIO.sendResult = BleSendErrorSending("fail")
            val result = session.sendCommand(createGetStatusCommand())
            assertThat(result).isInstanceOf(CommandSendErrorSending::class.java)
        }

        @Test
        fun `sequence number incremented after sendCommand call`() {
            val initialSeq = session.sessionKeys.msgSequenceNumber
            scriptSendSuccess()
            session.sendCommand(createGetStatusCommand())
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

        @Test
        fun `response received and ACKed successfully returns CommandReceiveSuccess`() {
            scriptReceiveEncryptedResponse()

            val result = session.readAndAckResponse()

            assertThat(result).isInstanceOf(CommandReceiveSuccess::class.java)
            val success = result as CommandReceiveSuccess
            assertThat(success.result).isInstanceOf(DefaultStatusResponse::class.java)
        }

        @Test
        fun `sequence number incremented after successful response`() {
            val initialSeq = session.sessionKeys.msgSequenceNumber
            scriptReceiveEncryptedResponse()

            session.readAndAckResponse()

            assertThat(session.sessionKeys.msgSequenceNumber).isEqualTo((initialSeq + 1).toByte())
        }

        @Test
        fun `ACK failure returns CommandAckError with parsed response`() {
            val responsePlain = createDefaultStatusPayload()
            val wrappedPayload = StringLengthPrefixEncoding.formatKeys(
                arrayOf("0.0="),
                arrayOf(responsePlain)
            )
            val responseMsg = MessagePacket(
                type = MessageType.ENCRYPTED,
                sequenceNumber = 0.toByte(),
                source = podId,
                destination = myId,
                payload = wrappedPayload,
                eqos = 1
            )
            val currentSqn = sessionNonce.sqn
            val encryptedMsg = podSideEncrypt(responseMsg, currentSqn + 1)
            val encryptedBytes = encryptedMsg.asByteArray()
            val packets = PayloadSplitter(encryptedBytes).splitInPackets()

            cmdBleIO.expectCommandResults(BleConfirmSuccess)
            for (packet in packets) {
                dataBleIO.enqueueReceives(packet.toByteArray())
            }
            // receiveMessage needs CTS + SUCCESS sends to succeed,
            // then sendMessage (ACK) needs RTS send to fail
            cmdBleIO.programSendResults(
                app.aaps.pump.omnipod.common.bledriver.comm.io.BleSendSuccess,   // CTS in receiveMessage
                app.aaps.pump.omnipod.common.bledriver.comm.io.BleSendSuccess,   // SUCCESS in receiveMessage
                app.aaps.pump.omnipod.common.bledriver.comm.io.BleSendErrorSending("ACK fail") // RTS in sendMessage
            )

            val result = session.readAndAckResponse()

            assertThat(result).isInstanceOf(CommandAckError::class.java)
            val ackError = result as CommandAckError
            assertThat(ackError.result).isInstanceOf(DefaultStatusResponse::class.java)
        }
    }

    @Nested
    @DisplayName("2.3 Full Command Cycle")
    inner class FullCycle {

        @Test
        fun `send command then receive response — full encrypted cycle`() {
            scriptSendSuccess()
            val sendResult = session.sendCommand(createGetStatusCommand())
            assertThat(sendResult).isInstanceOf(CommandSendSuccess::class.java)

            cmdBleIO.reset()
            dataBleIO.reset()

            scriptReceiveEncryptedResponse()
            val receiveResult = session.readAndAckResponse()
            assertThat(receiveResult).isInstanceOf(CommandReceiveSuccess::class.java)
        }
    }

    private fun createGetStatusCommand() = GetStatusCommand.Builder()
        .setUniqueId(4243)
        .setSequenceNumber(0)
        .setStatusResponseType(ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE)
        .build()

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
}
