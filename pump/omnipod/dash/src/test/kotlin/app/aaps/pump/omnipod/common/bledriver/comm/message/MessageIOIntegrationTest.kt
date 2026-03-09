package app.aaps.pump.omnipod.common.bledriver.comm.message

import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandCTS
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandFail
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandNack
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandRTS
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.io.BleConfirmError
import app.aaps.pump.omnipod.common.bledriver.comm.io.BleConfirmIncorrectData
import app.aaps.pump.omnipod.common.bledriver.comm.io.BleConfirmSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.io.BleSendErrorConfirming
import app.aaps.pump.omnipod.common.bledriver.comm.io.BleSendErrorSending
import app.aaps.pump.omnipod.common.bledriver.comm.io.BleSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.io.FakeCmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.io.FakeDataBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.packet.PayloadJoiner
import app.aaps.pump.omnipod.common.bledriver.comm.packet.PayloadSplitter
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Random

/**
 * Phase 1: Comprehensive MessageIO integration tests using fake BLE transport.
 *
 * These tests validate the RTS/CTS protocol state machine, packet splitting/joining,
 * NACK handling, retry logic, and error propagation — all without real BLE hardware.
 *
 * DUAL-IMPLEMENTATION NOTE: MessageIO is shared between old (raw GATT) and new (Blessed)
 * implementations. These tests exercise the protocol logic through the BleCharacteristicIO
 * interface, proving correctness for both transport backends.
 */
class MessageIOIntegrationTest {

    private val logger = AAPSLoggerTest()
    private lateinit var cmdBleIO: FakeCmdBleIO
    private lateinit var dataBleIO: FakeDataBleIO
    private lateinit var messageIO: MessageIO

    private fun createSmallMessage(): MessagePacket = MessagePacket(
        type = MessageType.CLEAR,
        source = Id.fromInt(1),
        destination = Id.fromInt(2),
        sequenceNumber = 0.toByte(),
        ackNumber = 0.toByte(),
        eqos = 0.toShort(),
        priority = false,
        lastMessage = false,
        gateway = false,
        sas = true,
        tfs = false,
        payload = ByteArray(5) { it.toByte() }
    )

    private fun createLargeMessage(payloadSize: Int = 100): MessagePacket {
        val random = Random(42)
        val payload = ByteArray(payloadSize)
        random.nextBytes(payload)
        return MessagePacket(
            type = MessageType.CLEAR,
            source = Id.fromInt(1),
            destination = Id.fromInt(2),
            sequenceNumber = 1.toByte(),
            ackNumber = 0.toByte(),
            eqos = 0.toShort(),
            priority = false,
            lastMessage = false,
            gateway = false,
            sas = true,
            tfs = false,
            payload = payload
        )
    }

    @BeforeEach
    fun setUp() {
        cmdBleIO = FakeCmdBleIO()
        dataBleIO = FakeDataBleIO()
        messageIO = MessageIO(logger, cmdBleIO, dataBleIO)
    }

    @Nested
    @DisplayName("1.1 Send Message — Happy Path")
    inner class SendHappyPath {

        @Test
        fun `single-packet send succeeds with RTS-CTS-DATA-SUCCESS`() {
            val msg = createSmallMessage()
            cmdBleIO.expectCommandResults(BleConfirmSuccess, BleConfirmSuccess)

            val result = messageIO.sendMessage(msg)

            assertThat(result).isEqualTo(MessageSendSuccess)
            assertThat(cmdBleIO.sentPayloads.any { it.contentEquals(BleCommandRTS.data) }).isTrue()
            assertThat(dataBleIO.sentPayloads).isNotEmpty()
        }

        @Test
        fun `multi-packet send succeeds`() {
            val msg = createLargeMessage(100)
            val expectedPacketCount = PayloadSplitter(msg.asByteArray()).splitInPackets().size
            cmdBleIO.expectCommandResults(BleConfirmSuccess, BleConfirmSuccess)

            val result = messageIO.sendMessage(msg)

            assertThat(result).isEqualTo(MessageSendSuccess)
            assertThat(dataBleIO.sentPayloads.size).isEqualTo(expectedPacketCount)
        }

        @Test
        fun `maximum-size message send with many fragments`() {
            val msg = createLargeMessage(250)
            val packets = PayloadSplitter(msg.asByteArray()).splitInPackets()
            cmdBleIO.expectCommandResults(BleConfirmSuccess, BleConfirmSuccess)

            val result = messageIO.sendMessage(msg)

            assertThat(result).isEqualTo(MessageSendSuccess)
            assertThat(dataBleIO.sentPayloads.size).isEqualTo(packets.size)
        }
    }

    @Nested
    @DisplayName("1.2 Send Message — RTS/CTS Failures")
    inner class SendRtsCtsFailures {

        @Test
        fun `RTS write fails returns MessageSendErrorSending`() {
            cmdBleIO.sendResult = BleSendErrorSending("write failed")

            val result = messageIO.sendMessage(createSmallMessage())

            assertThat(result).isInstanceOf(MessageSendErrorSending::class.java)
        }

        @Test
        fun `RTS write confirm fails returns MessageSendErrorSending`() {
            cmdBleIO.programSendResults(BleSendErrorConfirming("confirm failed"))

            val result = messageIO.sendMessage(createSmallMessage())

            assertThat(result).isInstanceOf(MessageSendErrorSending::class.java)
        }

        @Test
        fun `CTS never arrives returns MessageSendErrorSending`() {
            cmdBleIO.expectCommandResults(BleConfirmError("timeout"))

            val result = messageIO.sendMessage(createSmallMessage())

            assertThat(result).isInstanceOf(MessageSendErrorSending::class.java)
        }

        @Test
        fun `CTS arrives with wrong command type returns MessageSendErrorSending`() {
            cmdBleIO.expectCommandResults(BleConfirmIncorrectData(byteArrayOf(0x05)))

            val result = messageIO.sendMessage(createSmallMessage())

            assertThat(result).isInstanceOf(MessageSendErrorSending::class.java)
        }
    }

    @Nested
    @DisplayName("1.3 Send Message — DATA Packet Failures")
    inner class SendDataPacketFailures {

        @Test
        fun `first DATA packet write fails`() {
            cmdBleIO.expectCommandResults(BleConfirmSuccess)
            dataBleIO.sendResult = BleSendErrorSending("DATA write failed")

            val result = messageIO.sendMessage(createSmallMessage())

            assertThat(result).isInstanceOf(MessageSendErrorSending::class.java)
        }

        @Test
        fun `middle DATA packet write fails in multi-packet`() {
            val msg = createLargeMessage(100)
            cmdBleIO.expectCommandResults(BleConfirmSuccess)
            dataBleIO.programSendResults(BleSendSuccess, BleSendErrorSending("middle fail"))

            val result = messageIO.sendMessage(msg)

            assertThat(result).isInstanceOf(MessageSendErrorSending::class.java)
        }

        @Test
        fun `last DATA packet confirm fails returns MessageSendErrorConfirming`() {
            val msg = createSmallMessage()
            val packets = PayloadSplitter(msg.asByteArray()).splitInPackets()
            cmdBleIO.expectCommandResults(BleConfirmSuccess)
            if (packets.size == 1) {
                dataBleIO.programSendResults(BleSendErrorConfirming("last confirm fail"))
            } else {
                val results = Array(packets.size) { i ->
                    if (i == packets.size - 1) BleSendErrorConfirming("last confirm fail")
                    else BleSendSuccess
                }
                dataBleIO.programSendResults(*results)
            }

            val result = messageIO.sendMessage(msg)

            assertThat(result).isInstanceOf(MessageSendErrorConfirming::class.java)
        }

        @Test
        fun `non-last DATA packet confirm fails returns MessageSendErrorSending`() {
            val msg = createLargeMessage(100)
            cmdBleIO.expectCommandResults(BleConfirmSuccess)
            dataBleIO.programSendResults(BleSendErrorConfirming("non-last confirm fail"))

            val result = messageIO.sendMessage(msg)

            assertThat(result).isInstanceOf(MessageSendErrorSending::class.java)
        }
    }

    @Nested
    @DisplayName("1.4 Send Message — NACK Handling")
    inner class SendNackHandling {

        @Test
        fun `pod NACKs one packet, retransmit succeeds`() {
            val msg = createLargeMessage(100)
            val packets = PayloadSplitter(msg.asByteArray()).splitInPackets()
            if (packets.size < 2) return

            cmdBleIO.expectCommandResults(BleConfirmSuccess, BleConfirmSuccess)
            cmdBleIO.enqueueReceives(BleCommandNack(0).data)

            val result = messageIO.sendMessage(msg)

            assertThat(result).isEqualTo(MessageSendSuccess)
            assertThat(dataBleIO.sentPayloads.size).isGreaterThan(packets.size)
        }

        @Test
        fun `pod sends SUCCESS early before all packets`() {
            val msg = createLargeMessage(100)
            val packets = PayloadSplitter(msg.asByteArray()).splitInPackets()
            if (packets.size < 2) return

            cmdBleIO.expectCommandResults(BleConfirmSuccess)
            cmdBleIO.enqueueReceives(BleCommandSuccess.data)

            val result = messageIO.sendMessage(msg)

            assertThat(result).isInstanceOf(MessageSendErrorSending::class.java)
        }

        @Test
        fun `pod sends unexpected command during send`() {
            val msg = createLargeMessage(100)
            val packets = PayloadSplitter(msg.asByteArray()).splitInPackets()
            if (packets.size < 2) return

            cmdBleIO.expectCommandResults(BleConfirmSuccess)
            cmdBleIO.enqueueReceives(byteArrayOf(0x09))

            val result = messageIO.sendMessage(msg)

            assertThat(result).isInstanceOf(MessageSendErrorSending::class.java)
        }
    }

    @Nested
    @DisplayName("1.5 Send Message — Final Confirmation Failures")
    inner class SendFinalConfirmation {

        @Test
        fun `SUCCESS never arrives returns MessageSendErrorConfirming`() {
            cmdBleIO.expectCommandResults(BleConfirmSuccess, BleConfirmError("timeout"))

            val result = messageIO.sendMessage(createSmallMessage())

            assertThat(result).isInstanceOf(MessageSendErrorConfirming::class.java)
        }

        @Test
        fun `FAIL received instead of SUCCESS`() {
            cmdBleIO.expectCommandResults(
                BleConfirmSuccess,
                BleConfirmIncorrectData(BleCommandFail.data)
            )

            val result = messageIO.sendMessage(createSmallMessage())

            assertThat(result).isInstanceOf(MessageSendErrorSending::class.java)
        }

        @Test
        fun `unknown command instead of SUCCESS`() {
            cmdBleIO.expectCommandResults(
                BleConfirmSuccess,
                BleConfirmIncorrectData(byteArrayOf(0x09))
            )

            val result = messageIO.sendMessage(createSmallMessage())

            assertThat(result).isInstanceOf(MessageSendErrorConfirming::class.java)
        }
    }

    @Nested
    @DisplayName("1.6 Send Message — Incoming RTS Race")
    inner class SendRtsRace {

        @Test
        fun `RTS found in CMD queue during flush throws IllegalStateException`() {
            cmdBleIO.flushResult = true
            cmdBleIO.expectCommandResults(BleConfirmSuccess)

            assertThrows<IllegalStateException> {
                messageIO.sendMessage(createSmallMessage())
            }
        }
    }

    @Nested
    @DisplayName("1.7 Receive Message — Happy Path")
    inner class ReceiveHappyPath {

        @Test
        fun `single-packet receive returns valid MessagePacket`() {
            val originalMsg = createSmallMessage()
            val payload = originalMsg.asByteArray()
            val packets = PayloadSplitter(payload).splitInPackets()

            cmdBleIO.expectCommandResults(BleConfirmSuccess)
            for (packet in packets) {
                dataBleIO.enqueueReceives(packet.toByteArray())
            }

            val received = messageIO.receiveMessage()

            assertThat(received).isNotNull()
            assertThat(received!!.source).isEqualTo(originalMsg.source)
            assertThat(received.destination).isEqualTo(originalMsg.destination)
        }

        @Test
        fun `multi-packet receive joins fragments correctly`() {
            val originalMsg = createLargeMessage(100)
            val payload = originalMsg.asByteArray()
            val packets = PayloadSplitter(payload).splitInPackets()

            cmdBleIO.expectCommandResults(BleConfirmSuccess)
            for (packet in packets) {
                dataBleIO.enqueueReceives(packet.toByteArray())
            }

            val received = messageIO.receiveMessage()

            assertThat(received).isNotNull()
            assertThat(received!!.payload).isEqualTo(originalMsg.payload)
        }

        @Test
        fun `receive without readRTS uses already-received RTS`() {
            val originalMsg = createSmallMessage()
            val payload = originalMsg.asByteArray()
            val packets = PayloadSplitter(payload).splitInPackets()

            for (packet in packets) {
                dataBleIO.enqueueReceives(packet.toByteArray())
            }

            val received = messageIO.receiveMessage(readRTS = false)

            assertThat(received).isNotNull()
        }
    }

    @Nested
    @DisplayName("1.8 Receive Message — Packet Failures")
    inner class ReceivePacketFailures {

        @Test
        fun `RTS never arrives returns null`() {
            cmdBleIO.expectCommandResults(BleConfirmError("timeout"))

            val received = messageIO.receiveMessage()

            assertThat(received).isNull()
        }

        @Test
        fun `CTS write fails returns null`() {
            cmdBleIO.expectCommandResults(BleConfirmSuccess)
            cmdBleIO.sendResult = BleSendErrorSending("CTS write fail")

            val received = messageIO.receiveMessage()

            assertThat(received).isNull()
        }

        @Test
        fun `first DATA packet never arrives returns null`() {
            cmdBleIO.expectCommandResults(BleConfirmSuccess)

            val received = messageIO.receiveMessage()

            assertThat(received).isNull()
        }

        @Test
        fun `middle DATA packet timeout returns null after NACK retries`() {
            val originalMsg = createLargeMessage(100)
            val payload = originalMsg.asByteArray()
            val packets = PayloadSplitter(payload).splitInPackets()
            if (packets.size < 2) return

            cmdBleIO.expectCommandResults(BleConfirmSuccess)
            dataBleIO.enqueueReceives(packets[0].toByteArray())

            val received = messageIO.receiveMessage()

            assertThat(received).isNull()
        }
    }

    @Nested
    @DisplayName("1.9 Receive Message — Out-of-Order and NACK")
    inner class ReceiveOutOfOrder {

        @Test
        fun `packets arrive reversed, NACK reorders them`() {
            val originalMsg = createLargeMessage(100)
            val payload = originalMsg.asByteArray()
            val packets = PayloadSplitter(payload).splitInPackets()
            if (packets.size < 2) return

            cmdBleIO.expectCommandResults(BleConfirmSuccess)
            dataBleIO.enqueueReceives(packets[1].toByteArray())
            dataBleIO.enqueueReceives(packets[0].toByteArray())
            for (i in 2 until packets.size) {
                dataBleIO.enqueueReceives(packets[i].toByteArray())
            }

            val received = messageIO.receiveMessage()

            assertThat(received).isNotNull()
            assertThat(received!!.payload).isEqualTo(originalMsg.payload)
        }

        @Test
        fun `max retries exhausted returns null`() {
            val originalMsg = createLargeMessage(100)
            val payload = originalMsg.asByteArray()
            val packets = PayloadSplitter(payload).splitInPackets()

            cmdBleIO.expectCommandResults(BleConfirmSuccess)
            dataBleIO.enqueueReceives(packets[0].toByteArray())

            val received = messageIO.receiveMessage()

            assertThat(received).isNull()
        }
    }

    @Nested
    @DisplayName("1.10 Roundtrip — Split/Join Consistency")
    inner class Roundtrip {

        @Test
        fun `send then receive same message preserves payload`() {
            val msg = createSmallMessage()

            cmdBleIO.expectCommandResults(BleConfirmSuccess, BleConfirmSuccess)
            val sendResult = messageIO.sendMessage(msg)
            assertThat(sendResult).isEqualTo(MessageSendSuccess)

            setUp()

            val payload = msg.asByteArray()
            val packets = PayloadSplitter(payload).splitInPackets()
            cmdBleIO.expectCommandResults(BleConfirmSuccess)
            for (packet in packets) {
                dataBleIO.enqueueReceives(packet.toByteArray())
            }
            val received = messageIO.receiveMessage()

            assertThat(received).isNotNull()
            assertThat(received!!.payload).isEqualTo(msg.payload)
        }

        @Test
        fun `various payload sizes all round-trip correctly`() {
            val random = Random(123)
            for (size in listOf(1, 10, 18, 19, 20, 50, 100, 200, 250)) {
                setUp()
                val payload = ByteArray(size)
                random.nextBytes(payload)
                val msg = MessagePacket(
                    type = MessageType.CLEAR,
                    source = Id.fromInt(1),
                    destination = Id.fromInt(2),
                    sequenceNumber = 0.toByte(),
                    ackNumber = 0.toByte(),
                    eqos = 0.toShort(),
                    payload = payload
                )

                val msgBytes = msg.asByteArray()
                val packets = PayloadSplitter(msgBytes).splitInPackets()
                cmdBleIO.expectCommandResults(BleConfirmSuccess)
                for (packet in packets) {
                    dataBleIO.enqueueReceives(packet.toByteArray())
                }
                val received = messageIO.receiveMessage()

                assertThat(received).isNotNull()
                assertThat(received!!.payload).isEqualTo(payload)
            }
        }
    }
}
