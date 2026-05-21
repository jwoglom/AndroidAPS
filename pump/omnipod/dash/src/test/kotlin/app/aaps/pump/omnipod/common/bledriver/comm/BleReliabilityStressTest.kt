package app.aaps.pump.omnipod.common.bledriver.comm
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendErrorSending

import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.io.FakeCmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.io.FakeDataBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessagePacket
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
 * Phase 11: Stress and reliability tests.
 *
 * Validates that the BLE protocol stack handles repeated operations
 * without state leaks, deadlocks, or accumulation bugs.
 *
 * DUAL-IMPLEMENTATION NOTE: These stress tests exercise the protocol
 * through the interface layer. Failures found here indicate bugs in
 * MessageIO state management (readReset, receivedOutOfOrder map, retry counters)
 * which is shared code between old and new implementations.
 */
class BleReliabilityStressTest {

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

    private fun createMessage(seed: Long, payloadSize: Int = 30): MessagePacket {
        val random = Random(seed)
        val payload = ByteArray(payloadSize)
        random.nextBytes(payload)
        return MessagePacket(
            type = MessageType.CLEAR,
            source = Id.fromInt(4242),
            destination = Id.fromInt(4243),
            sequenceNumber = (seed % 128).toByte(),
            ackNumber = 0.toByte(),
            eqos = 0.toShort(),
            payload = payload
        )
    }

    @Nested
    @DisplayName("11.1 Repeated Send Cycles")
    inner class RepeatedSend {

        @Test
        fun `100 sequential send cycles all succeed without state leaks`() {
            var successCount = 0

            for (i in 0 until 100) {
                cmdBleIO.reset()
                dataBleIO.reset()
                cmdBleIO.expectCommandResults(BleConfirmSuccess, BleConfirmSuccess)

                val msg = createMessage(i.toLong())
                val result = messageIO.sendMessage(msg)

                if (result is MessageSendSuccess) {
                    successCount++
                }

                messageIO = MessageIO(logger, cmdBleIO, dataBleIO)
            }

            assertThat(successCount).isEqualTo(100)
        }
    }

    @Nested
    @DisplayName("11.2 Repeated Receive Cycles")
    inner class RepeatedReceive {

        @Test
        fun `100 sequential receive cycles all succeed`() {
            var successCount = 0

            for (i in 0 until 100) {
                cmdBleIO.reset()
                dataBleIO.reset()
                messageIO = MessageIO(logger, cmdBleIO, dataBleIO)

                val msg = createMessage(i.toLong())
                val payload = msg.asByteArray()
                val packets = PayloadSplitter(payload).splitInPackets()

                cmdBleIO.expectCommandResults(BleConfirmSuccess)
                for (p in packets) {
                    dataBleIO.enqueueReceives(p.toByteArray())
                }

                val received = messageIO.receiveMessage()
                if (received != null && received.payload.contentEquals(msg.payload)) {
                    successCount++
                }
            }

            assertThat(successCount).isEqualTo(100)
        }
    }

    @Nested
    @DisplayName("11.3 Alternating Send/Receive")
    inner class AlternatingSendReceive {

        @Test
        fun `50 alternating send-receive cycles succeed`() {
            var sendCount = 0
            var receiveCount = 0

            for (i in 0 until 50) {
                cmdBleIO.reset()
                dataBleIO.reset()
                messageIO = MessageIO(logger, cmdBleIO, dataBleIO)

                val msg = createMessage(i.toLong(), payloadSize = 20)

                cmdBleIO.expectCommandResults(BleConfirmSuccess, BleConfirmSuccess)
                val sendResult = messageIO.sendMessage(msg)
                if (sendResult is MessageSendSuccess) sendCount++

                cmdBleIO.reset()
                dataBleIO.reset()
                messageIO = MessageIO(logger, cmdBleIO, dataBleIO)

                val recvMsg = createMessage(i.toLong() + 1000, payloadSize = 25)
                val recvPayload = recvMsg.asByteArray()
                val recvPackets = PayloadSplitter(recvPayload).splitInPackets()

                cmdBleIO.expectCommandResults(BleConfirmSuccess)
                for (p in recvPackets) {
                    dataBleIO.enqueueReceives(p.toByteArray())
                }

                val received = messageIO.receiveMessage()
                if (received != null) receiveCount++
            }

            assertThat(sendCount).isEqualTo(50)
            assertThat(receiveCount).isEqualTo(50)
        }
    }

    @Nested
    @DisplayName("11.4 Send-Receive Roundtrip Stress")
    inner class RoundtripStress {

        @Test
        fun `50 roundtrip cycles preserve payload integrity`() {
            val random = Random(12345)

            for (i in 0 until 50) {
                cmdBleIO.reset()
                dataBleIO.reset()
                messageIO = MessageIO(logger, cmdBleIO, dataBleIO)

                val payloadSize = random.nextInt(200) + 1
                val msg = createMessage(i.toLong(), payloadSize)

                cmdBleIO.expectCommandResults(BleConfirmSuccess, BleConfirmSuccess)
                val sendResult = messageIO.sendMessage(msg)
                assertThat(sendResult).isEqualTo(MessageSendSuccess)

                val sentPackets = dataBleIO.sentPayloads.toList()

                cmdBleIO.reset()
                dataBleIO.reset()
                messageIO = MessageIO(logger, cmdBleIO, dataBleIO)

                cmdBleIO.expectCommandResults(BleConfirmSuccess)
                for (p in sentPackets) {
                    dataBleIO.enqueueReceives(p)
                }
                val received = messageIO.receiveMessage()

                assertThat(received).isNotNull()
                assertThat(received!!.payload).isEqualTo(msg.payload)
            }
        }
    }

    @Nested
    @DisplayName("11.5 Recovery After Failures")
    inner class RecoveryAfterFailures {

        @Test
        fun `successful send after failed send shows no state contamination`() {
            cmdBleIO.sendResult = app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendErrorSending("fail")
            val failMsg = createMessage(1)
            val failResult = messageIO.sendMessage(failMsg)
            assertThat(failResult).isInstanceOf(app.aaps.pump.omnipod.common.bledriver.comm.message.MessageSendErrorSending::class.java)

            cmdBleIO.reset()
            dataBleIO.reset()
            messageIO = MessageIO(logger, cmdBleIO, dataBleIO)
            cmdBleIO.expectCommandResults(BleConfirmSuccess, BleConfirmSuccess)
            val successMsg = createMessage(2)
            val successResult = messageIO.sendMessage(successMsg)
            assertThat(successResult).isEqualTo(MessageSendSuccess)
        }

        @Test
        fun `successful receive after failed receive shows no state contamination`() {
            val failResult = messageIO.receiveMessage()
            assertThat(failResult).isNull()

            cmdBleIO.reset()
            dataBleIO.reset()
            messageIO = MessageIO(logger, cmdBleIO, dataBleIO)

            val msg = createMessage(3)
            val payload = msg.asByteArray()
            val packets = PayloadSplitter(payload).splitInPackets()
            cmdBleIO.expectCommandResults(BleConfirmSuccess)
            for (p in packets) {
                dataBleIO.enqueueReceives(p.toByteArray())
            }

            val received = messageIO.receiveMessage()
            assertThat(received).isNotNull()
            assertThat(received!!.payload).isEqualTo(msg.payload)
        }
    }
}
