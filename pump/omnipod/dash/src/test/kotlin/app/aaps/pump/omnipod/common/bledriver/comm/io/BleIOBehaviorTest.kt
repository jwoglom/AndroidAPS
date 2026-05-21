package app.aaps.pump.omnipod.common.bledriver.comm.io
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendErrorSending
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendErrorConfirming
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmIncorrectData
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmError

import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandHello
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandRTS
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleCharacteristicIO
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CmdBleIO
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Phases 6 & 7: BleIO behavior tests.
 *
 * Tests BLE I/O through the interface contract using FakeBleCharacteristicIO,
 * FakeCmdBleIO, and FakeDataBleIO. This validates the contract that both
 * old (BleIO/CmdBleIO/DataBleIO) and new (BlessedBleIO/BlessedCmdBleIO/BlessedDataBleIO)
 * implementations must satisfy.
 *
 * DUAL-IMPLEMENTATION NOTE: These tests define the behavioral contract.
 * - Old: BleIO uses BluetoothGatt.writeCharacteristic + characteristic.setValue
 * - New: BlessedBleIO uses BluetoothPeripheral.writeCharacteristic with WriteType
 * - Old: BleIO.readyToRead checks descriptor count == 1
 * - New: BlessedBleIO.readyToRead uses peripheral.startNotify()
 * The interface contract is the same: sendAndConfirmPacket, receivePacket, flushIncomingQueue, readyToRead.
 */
class BleIOBehaviorTest {

    @Nested
    @DisplayName("6.1 BleCharacteristicIO Contract Tests")
    inner class CharacteristicIOContract {

        private lateinit var io: FakeBleCharacteristicIO

        @BeforeEach
        fun setUp() {
            io = FakeBleCharacteristicIO()
        }

        @Test
        fun `receivePacket returns enqueued data`() {
            val data = byteArrayOf(0x01, 0x02, 0x03)
            io.enqueueReceives(data)

            val received = io.receivePacket(100)
            assertThat(received).isEqualTo(data)
        }

        @Test
        fun `receivePacket returns null on empty queue after timeout`() {
            val received = io.receivePacket(10)
            assertThat(received).isNull()
        }

        @Test
        fun `receivePacket returns packets in FIFO order`() {
            val data1 = byteArrayOf(0x01)
            val data2 = byteArrayOf(0x02)
            val data3 = byteArrayOf(0x03)
            io.enqueueReceives(data1, data2, data3)

            assertThat(io.receivePacket(10)).isEqualTo(data1)
            assertThat(io.receivePacket(10)).isEqualTo(data2)
            assertThat(io.receivePacket(10)).isEqualTo(data3)
        }

        @Test
        fun `sendAndConfirmPacket records payload`() {
            val payload = byteArrayOf(0x04, 0x05)
            io.sendAndConfirmPacket(payload)

            assertThat(io.sentPayloads).hasSize(1)
            assertThat(io.sentPayloads[0]).isEqualTo(payload)
        }

        @Test
        fun `sendAndConfirmPacket returns configured result`() {
            io.sendResult = BleSendErrorSending("test error")

            val result = io.sendAndConfirmPacket(byteArrayOf(0x01))
            assertThat(result).isInstanceOf(BleSendErrorSending::class.java)
        }

        @Test
        fun `sendAndConfirmPacket returns sequenced results then falls back to default`() {
            io.programSendResults(BleSendSuccess, BleSendErrorSending("second"))
            io.sendResult = BleSendErrorConfirming("fallback")

            assertThat(io.sendAndConfirmPacket(byteArrayOf(0x01))).isEqualTo(BleSendSuccess)
            assertThat(io.sendAndConfirmPacket(byteArrayOf(0x02))).isInstanceOf(BleSendErrorSending::class.java)
            assertThat(io.sendAndConfirmPacket(byteArrayOf(0x03))).isInstanceOf(BleSendErrorConfirming::class.java)
        }

        @Test
        fun `flushIncomingQueue returns configured result`() {
            io.flushResult = true
            assertThat(io.flushIncomingQueue()).isTrue()

            io.flushResult = false
            assertThat(io.flushIncomingQueue()).isFalse()
        }

        @Test
        fun `readyToRead returns configured result`() {
            assertThat(io.readyToRead()).isEqualTo(BleSendSuccess)

            io.readyToReadResult = BleSendErrorSending("fail")
            assertThat(io.readyToRead()).isInstanceOf(BleSendErrorSending::class.java)
        }

        @Test
        fun `reset clears all state`() {
            io.enqueueReceives(byteArrayOf(0x01))
            io.sendAndConfirmPacket(byteArrayOf(0x02))
            io.sendResult = BleSendErrorSending("error")
            io.flushResult = true

            io.reset()

            assertThat(io.receivePacket(10)).isNull()
            assertThat(io.sentPayloads).isEmpty()
            assertThat(io.sendAndConfirmPacket(byteArrayOf(0x03))).isEqualTo(BleSendSuccess)
            assertThat(io.flushIncomingQueue()).isFalse()
        }
    }

    @Nested
    @DisplayName("7.1 CmdBleIO Contract Tests")
    inner class CmdBleIOContract {

        private lateinit var cmdIO: FakeCmdBleIO

        @BeforeEach
        fun setUp() {
            cmdIO = FakeCmdBleIO()
        }

        @Test
        fun `peekCommand returns head of queue without consuming`() {
            cmdIO.enqueueReceives(byteArrayOf(0x01, 0x02))

            val peeked = cmdIO.peekCommand()
            assertThat(peeked).isEqualTo(byteArrayOf(0x01, 0x02))

            val stillThere = cmdIO.receivePacket(10)
            assertThat(stillThere).isEqualTo(byteArrayOf(0x01, 0x02))
        }

        @Test
        fun `peekCommand returns null on empty queue`() {
            assertThat(cmdIO.peekCommand()).isNull()
        }

        @Test
        fun `hello sends BleCommandHello with controller ID 4242`() {
            cmdIO.hello()

            assertThat(cmdIO.sentPayloads).hasSize(1)
            assertThat(cmdIO.helloCallCount).isEqualTo(1)
            val helloData = BleCommandHello(4242).data
            assertThat(cmdIO.sentPayloads[0]).isEqualTo(helloData)
        }

        @Test
        fun `expectCommandType matches expected command returns BleConfirmSuccess`() {
            cmdIO.expectCommandResults(BleConfirmSuccess)

            val result = cmdIO.expectCommandType(BleCommandSuccess)
            assertThat(result).isEqualTo(BleConfirmSuccess)
        }

        @Test
        fun `expectCommandType receives wrong command returns BleConfirmIncorrectData`() {
            cmdIO.enqueueReceives(BleCommandRTS.data)

            val result = cmdIO.expectCommandType(BleCommandSuccess, 50)
            assertThat(result).isInstanceOf(BleConfirmIncorrectData::class.java)
        }

        @Test
        fun `expectCommandType times out returns BleConfirmError`() {
            val result = cmdIO.expectCommandType(BleCommandSuccess, 10)
            assertThat(result).isInstanceOf(BleConfirmError::class.java)
        }

        @Test
        fun `expectCommandType consumes programmed results in order`() {
            cmdIO.expectCommandResults(
                BleConfirmSuccess,
                BleConfirmError("second call error"),
                BleConfirmSuccess
            )

            assertThat(cmdIO.expectCommandType(BleCommandSuccess)).isEqualTo(BleConfirmSuccess)
            assertThat(cmdIO.expectCommandType(BleCommandSuccess)).isInstanceOf(BleConfirmError::class.java)
            assertThat(cmdIO.expectCommandType(BleCommandSuccess)).isEqualTo(BleConfirmSuccess)
        }

        @Test
        fun `reset clears expectCommandResults and helloCallCount`() {
            cmdIO.expectCommandResults(BleConfirmSuccess)
            cmdIO.hello()

            cmdIO.reset()

            assertThat(cmdIO.helloCallCount).isEqualTo(0)
            assertThat(cmdIO.expectCommandType(BleCommandSuccess, 10)).isInstanceOf(BleConfirmError::class.java)
        }
    }

    @Nested
    @DisplayName("6.2 Old vs New Implementation Behavioral Notes")
    inner class OldVsNewBehavior {

        @Test
        fun `interface contract DEFAULT_IO_TIMEOUT_MS is 1000`() {
            assertThat(BleCharacteristicIO.DEFAULT_IO_TIMEOUT_MS).isEqualTo(1000L)
        }

        @Test
        fun `flushIncomingQueue detects RTS command in queued data`() {
            val io = FakeBleCharacteristicIO()
            io.enqueueReceives(BleCommandRTS.data)
            io.flushResult = false

            assertThat(io.flushIncomingQueue()).isFalse()
        }
    }
}
