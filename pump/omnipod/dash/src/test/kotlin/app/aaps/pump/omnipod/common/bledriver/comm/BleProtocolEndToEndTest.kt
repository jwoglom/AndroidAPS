package app.aaps.pump.omnipod.common.bledriver.comm

import app.aaps.pump.omnipod.common.bledriver.comm.io.BleConfirmSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.io.BleSendErrorSending
import app.aaps.pump.omnipod.common.bledriver.comm.io.BleSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.io.FakeCmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.io.FakeDataBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessagePacket
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageSendErrorSending
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageType
import app.aaps.pump.omnipod.common.bledriver.comm.packet.PayloadSplitter
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * Phase 10: End-to-End protocol tests.
 *
 * Wires the full protocol stack (MessageIO → BLE I/O) with fake transport
 * to validate complete message exchange scenarios.
 *
 * DUAL-IMPLEMENTATION NOTE: These tests exercise the entire protocol stack
 * through the interface layer. The RTS/CTS/DATA/SUCCESS protocol is identical
 * between old and new implementations. These tests prove that the protocol
 * state machine works correctly regardless of the underlying BLE transport.
 */
class BleProtocolEndToEndTest {

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

    private fun createMessage(payloadSize: Int, seed: Long = 42): MessagePacket {
        val random = Random(seed)
        val payload = ByteArray(payloadSize)
        random.nextBytes(payload)
        return MessagePacket(
            type = MessageType.CLEAR,
            source = Id.fromInt(4242),
            destination = Id.fromInt(4243),
            sequenceNumber = 1.toByte(),
            ackNumber = 0.toByte(),
            eqos = 0.toShort(),
            payload = payload
        )
    }

    @Nested
    @DisplayName("10.1 Full Send-Receive Cycle")
    inner class FullCycle {

        @Test
        fun `send then receive same message preserves content`() {
            val msg = createMessage(50)

            cmdBleIO.expectCommandResults(BleConfirmSuccess, BleConfirmSuccess)
            val sendResult = messageIO.sendMessage(msg)
            assertThat(sendResult).isEqualTo(MessageSendSuccess)

            val sentDataPackets = dataBleIO.sentPayloads.toList()

            cmdBleIO.reset()
            dataBleIO.reset()
            messageIO = MessageIO(logger, cmdBleIO, dataBleIO)

            cmdBleIO.expectCommandResults(BleConfirmSuccess)
            for (packet in sentDataPackets) {
                dataBleIO.enqueueReceives(packet)
            }

            val received = messageIO.receiveMessage()

            assertThat(received).isNotNull()
            assertThat(received!!.payload).isEqualTo(msg.payload)
            assertThat(received.source).isEqualTo(msg.source)
            assertThat(received.destination).isEqualTo(msg.destination)
        }

        @Test
        fun `bidirectional message exchange`() {
            val msg1 = createMessage(30, seed = 1)
            val msg2 = createMessage(60, seed = 2)

            cmdBleIO.expectCommandResults(BleConfirmSuccess, BleConfirmSuccess)
            assertThat(messageIO.sendMessage(msg1)).isEqualTo(MessageSendSuccess)

            cmdBleIO.reset()
            dataBleIO.reset()
            messageIO = MessageIO(logger, cmdBleIO, dataBleIO)

            val msg2Bytes = msg2.asByteArray()
            val packets = PayloadSplitter(msg2Bytes).splitInPackets()
            cmdBleIO.expectCommandResults(BleConfirmSuccess)
            for (p in packets) {
                dataBleIO.enqueueReceives(p.toByteArray())
            }
            val received = messageIO.receiveMessage()

            assertThat(received).isNotNull()
            assertThat(received!!.payload).isEqualTo(msg2.payload)
        }
    }

    @Nested
    @DisplayName("10.2 Disconnect During Operation")
    inner class DisconnectDuring {

        @Test
        fun `disconnect during command send returns error without stuck state`() {
            cmdBleIO.sendResult = BleSendErrorSending("disconnected")

            val msg = createMessage(20)
            val result = messageIO.sendMessage(msg)

            assertThat(result).isInstanceOf(MessageSendErrorSending::class.java)

            cmdBleIO.reset()
            dataBleIO.reset()
            cmdBleIO.expectCommandResults(BleConfirmSuccess, BleConfirmSuccess)
            val msg2 = createMessage(10, seed = 2)
            val result2 = messageIO.sendMessage(msg2)
            assertThat(result2).isEqualTo(MessageSendSuccess)
        }
    }

    @Nested
    @DisplayName("10.3 Various Payload Sizes")
    inner class PayloadSizes {

        @Test
        fun `messages of various sizes all round-trip correctly`() {
            val sizes = listOf(0, 1, 5, 18, 19, 20, 50, 100, 150, 200, 250)
            val random = Random(999)

            for (size in sizes) {
                cmdBleIO.reset()
                dataBleIO.reset()
                messageIO = MessageIO(logger, cmdBleIO, dataBleIO)

                val payload = ByteArray(size)
                if (size > 0) random.nextBytes(payload)
                val msg = MessagePacket(
                    type = MessageType.CLEAR,
                    source = Id.fromInt(1),
                    destination = Id.fromInt(2),
                    sequenceNumber = 0.toByte(),
                    payload = payload
                )

                cmdBleIO.expectCommandResults(BleConfirmSuccess, BleConfirmSuccess)
                val sendResult = messageIO.sendMessage(msg)
                assertThat(sendResult).isEqualTo(MessageSendSuccess)

                val sentData = dataBleIO.sentPayloads.toList()
                cmdBleIO.reset()
                dataBleIO.reset()
                messageIO = MessageIO(logger, cmdBleIO, dataBleIO)

                cmdBleIO.expectCommandResults(BleConfirmSuccess)
                for (p in sentData) {
                    dataBleIO.enqueueReceives(p)
                }
                val received = messageIO.receiveMessage()

                assertThat(received).isNotNull()
                assertThat(received!!.payload).isEqualTo(payload)
            }
        }
    }
}
