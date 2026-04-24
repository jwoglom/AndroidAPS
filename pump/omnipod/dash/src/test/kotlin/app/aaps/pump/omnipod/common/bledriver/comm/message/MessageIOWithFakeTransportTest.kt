package app.aaps.pump.omnipod.common.bledriver.comm.message

import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandCTS
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandRTS
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendErrorSending
import app.aaps.pump.omnipod.common.bledriver.comm.io.FakeCmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.io.FakeDataBleIO
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import com.google.crypto.tink.subtle.Hex
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * MessageIO tests using FakeCmdBleIO and FakeDataBleIO.
 * Validates RTS/CTS protocol and message send/receive without real BLE.
 */
class MessageIOWithFakeTransportTest {

    private val logger = AAPSLoggerTest()
    private lateinit var cmdBleIO: FakeCmdBleIO
    private lateinit var dataBleIO: FakeDataBleIO
    private lateinit var messageIO: MessageIO

    @BeforeEach
    fun setUp() {
        cmdBleIO = FakeCmdBleIO()
        dataBleIO = FakeDataBleIO()
        messageIO = MessageIO(logger, cmdBleIO, dataBleIO)
    }

    @Test
    fun sendMessage_singlePacket_emitsRtsCtsDataSuccess() {
        // Payload that fits in one packet (see PayloadSplitter.splitInOnePacket)
        val payload = Hex.decode("54,57,11,01,07,00,03,40,08,20,2e,a8,08,20,2e,a9".replace(",", ""))
        val msg = MessagePacket(
            type = MessageType.ENCRYPTED,
            source = Id.fromLong(136326824),
            destination = Id.fromLong(136326825),
            sequenceNumber = 7.toByte(),
            ackNumber = 0.toByte(),
            eqos = 1.toShort(),
            priority = false,
            lastMessage = false,
            gateway = false,
            sas = true,
            tfs = false,
            payload = payload
        )

        // Program fake: use expectCommandResults so peek returns null (no early Success)
        cmdBleIO.expectCommandResults(BleConfirmSuccess, BleConfirmSuccess)

        val result = messageIO.sendMessage(msg)

        assertThat(result).isEqualTo(MessageSendSuccess)
        assertThat(cmdBleIO.sentPayloads).contains(BleCommandRTS.data)
        assertThat(dataBleIO.sentPayloads).isNotEmpty()
    }

    @Test
    fun sendMessage_rtsSendFails_returnsError() {
        cmdBleIO.sendResult = BleSendErrorSending("write failed")

        val msg = MessagePacket(
            type = MessageType.ENCRYPTED,
            source = Id.fromLong(1),
            destination = Id.fromLong(2),
            sequenceNumber = 0.toByte(),
            ackNumber = 0.toByte(),
            eqos = 0.toShort(),
            priority = false,
            lastMessage = false,
            gateway = false,
            sas = false,
            tfs = false,
            payload = byteArrayOf(0x01, 0x02, 0x03)
        )

        val result = messageIO.sendMessage(msg)

        assertThat(result).isInstanceOf(MessageSendErrorSending::class.java)
    }
}
