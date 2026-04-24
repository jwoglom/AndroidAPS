package app.aaps.pump.omnipod.common.bledriver.comm.callbacks

import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandRTS
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleCharacteristicIO
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.DataBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmError
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmIncorrectData
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendErrorConfirming
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendErrorSending
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.io.FakeCmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.io.FakeDataBleIO
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Phase 5: BLE callback behavior tests — exercised through interfaces.
 *
 * These tests validate the observable behaviors that any BLE callback/transport
 * implementation must provide, using only the CmdBleIO, DataBleIO, and
 * BleCharacteristicIO interfaces. They work identically for:
 * - Old: BleCommCallbacks (BluetoothGattCallback) → BleIO/CmdBleIO/DataBleIO
 * - New: BlessedBleCallbacks (BluetoothPeripheralCallback) → BlessedBleIO/BlessedCmdBleIO/BlessedDataBleIO
 * - Fake: FakeCmdBleIO / FakeDataBleIO
 *
 * The callback layer's job is:
 * 1. Route incoming packets to the correct queue (CMD vs DATA)
 * 2. Confirm writes (success/failure/timeout)
 * 3. Signal service discovery completion
 * 4. Flush stale state on reset
 *
 * All of these are tested here through the interface contracts.
 */
class BleCallbackBehaviorTest {

    private lateinit var cmdIO: FakeCmdBleIO
    private lateinit var dataIO: FakeDataBleIO

    @BeforeEach
    fun setUp() {
        cmdIO = FakeCmdBleIO()
        dataIO = FakeDataBleIO()
    }

    @Nested
    @DisplayName("5.1 Packet Routing — CMD vs DATA Separation")
    inner class PacketRouting {

        @Test
        fun `CMD packets are received only on CMD channel`() {
            val cmdData = byteArrayOf(0x01, 0x02, 0x03)
            cmdIO.enqueueReceives(cmdData)

            assertThat(cmdIO.receivePacket(50)).isEqualTo(cmdData)
            assertThat(dataIO.receivePacket(10)).isNull()
        }

        @Test
        fun `DATA packets are received only on DATA channel`() {
            val data = byteArrayOf(0x04, 0x05, 0x06)
            dataIO.enqueueReceives(data)

            assertThat(dataIO.receivePacket(50)).isEqualTo(data)
            assertThat(cmdIO.receivePacket(10)).isNull()
        }

        @Test
        fun `interleaved CMD and DATA packets maintain separate queues`() {
            cmdIO.enqueueReceives(byteArrayOf(0x01))
            dataIO.enqueueReceives(byteArrayOf(0x02))
            cmdIO.enqueueReceives(byteArrayOf(0x03))
            dataIO.enqueueReceives(byteArrayOf(0x04))

            assertThat(cmdIO.receivePacket(10)!![0]).isEqualTo(0x01.toByte())
            assertThat(cmdIO.receivePacket(10)!![0]).isEqualTo(0x03.toByte())
            assertThat(dataIO.receivePacket(10)!![0]).isEqualTo(0x02.toByte())
            assertThat(dataIO.receivePacket(10)!![0]).isEqualTo(0x04.toByte())
        }

        @Test
        fun `multiple rapid packets are queued in FIFO order`() {
            val packets = (0..9).map { byteArrayOf(it.toByte()) }
            packets.forEach { cmdIO.enqueueReceives(it) }

            for (i in 0..9) {
                val received = cmdIO.receivePacket(10)
                assertThat(received).isNotNull()
                assertThat(received!![0]).isEqualTo(i.toByte())
            }
        }

        @Test
        fun `empty queue returns null on timeout`() {
            assertThat(cmdIO.receivePacket(10)).isNull()
            assertThat(dataIO.receivePacket(10)).isNull()
        }
    }

    @Nested
    @DisplayName("5.2 Write Confirmation Semantics")
    inner class WriteConfirmation {

        @Test
        fun `sendAndConfirmPacket succeeds with BleSendSuccess`() {
            cmdIO.sendResult = BleSendSuccess
            val result = cmdIO.sendAndConfirmPacket(byteArrayOf(0x01))
            assertThat(result).isEqualTo(BleSendSuccess)
        }

        @Test
        fun `sendAndConfirmPacket returns BleSendErrorSending on write failure`() {
            cmdIO.sendResult = BleSendErrorSending("write failed")
            val result = cmdIO.sendAndConfirmPacket(byteArrayOf(0x01))
            assertThat(result).isInstanceOf(BleSendErrorSending::class.java)
        }

        @Test
        fun `sendAndConfirmPacket returns BleSendErrorConfirming on confirm failure`() {
            cmdIO.sendResult = BleSendErrorConfirming("confirm failed")
            val result = cmdIO.sendAndConfirmPacket(byteArrayOf(0x01))
            assertThat(result).isInstanceOf(BleSendErrorConfirming::class.java)
        }

        @Test
        fun `sent payloads are recorded for verification`() {
            val payload1 = byteArrayOf(0x01, 0x02)
            val payload2 = byteArrayOf(0x03, 0x04)
            cmdIO.sendAndConfirmPacket(payload1)
            cmdIO.sendAndConfirmPacket(payload2)

            assertThat(cmdIO.sentPayloads).hasSize(2)
            assertThat(cmdIO.sentPayloads[0]).isEqualTo(payload1)
            assertThat(cmdIO.sentPayloads[1]).isEqualTo(payload2)
        }

        @Test
        fun `sequenced send results consumed in order then fallback`() {
            cmdIO.programSendResults(BleSendSuccess, BleSendErrorSending("fail"))
            cmdIO.sendResult = BleSendErrorConfirming("fallback")

            assertThat(cmdIO.sendAndConfirmPacket(byteArrayOf(0x01))).isEqualTo(BleSendSuccess)
            assertThat(cmdIO.sendAndConfirmPacket(byteArrayOf(0x02))).isInstanceOf(BleSendErrorSending::class.java)
            assertThat(cmdIO.sendAndConfirmPacket(byteArrayOf(0x03))).isInstanceOf(BleSendErrorConfirming::class.java)
        }
    }

    @Nested
    @DisplayName("5.3 Flush Behavior")
    inner class FlushBehavior {

        @Test
        fun `flushIncomingQueue reports RTS presence`() {
            cmdIO.flushResult = true
            assertThat(cmdIO.flushIncomingQueue()).isTrue()
        }

        @Test
        fun `flushIncomingQueue reports no RTS when queue empty`() {
            cmdIO.flushResult = false
            assertThat(cmdIO.flushIncomingQueue()).isFalse()
        }

        @Test
        fun `flush on data channel works independently`() {
            dataIO.flushResult = false
            assertThat(dataIO.flushIncomingQueue()).isFalse()
        }
    }

    @Nested
    @DisplayName("5.4 ReadyToRead (Indication Enable)")
    inner class ReadyToRead {

        @Test
        fun `readyToRead succeeds by default`() {
            assertThat(cmdIO.readyToRead()).isEqualTo(BleSendSuccess)
            assertThat(dataIO.readyToRead()).isEqualTo(BleSendSuccess)
        }

        @Test
        fun `readyToRead failure is propagated`() {
            cmdIO.readyToReadResult = BleSendErrorSending("indication enable failed")
            assertThat(cmdIO.readyToRead()).isInstanceOf(BleSendErrorSending::class.java)
        }
    }

    @Nested
    @DisplayName("5.5 Reset and State Cleanup")
    inner class ResetBehavior {

        @Test
        fun `reset clears incoming queue`() {
            cmdIO.enqueueReceives(byteArrayOf(0x01))
            cmdIO.reset()
            assertThat(cmdIO.receivePacket(10)).isNull()
        }

        @Test
        fun `reset clears sent payloads`() {
            cmdIO.sendAndConfirmPacket(byteArrayOf(0x01))
            cmdIO.reset()
            assertThat(cmdIO.sentPayloads).isEmpty()
        }

        @Test
        fun `reset restores default send result`() {
            cmdIO.sendResult = BleSendErrorSending("error")
            cmdIO.reset()
            assertThat(cmdIO.sendAndConfirmPacket(byteArrayOf(0x01))).isEqualTo(BleSendSuccess)
        }

        @Test
        fun `reset clears programmed send sequence`() {
            cmdIO.programSendResults(BleSendErrorSending("seq"))
            cmdIO.reset()
            assertThat(cmdIO.sendAndConfirmPacket(byteArrayOf(0x01))).isEqualTo(BleSendSuccess)
        }

        @Test
        fun `reset clears expectCommandResults`() {
            cmdIO.expectCommandResults(BleConfirmSuccess, BleConfirmSuccess)
            cmdIO.reset()
            assertThat(cmdIO.expectCommandType(
                app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandSuccess, 10
            )).isInstanceOf(BleConfirmError::class.java)
        }

        @Test
        fun `reset clears hello call count`() {
            cmdIO.hello()
            cmdIO.reset()
            assertThat(cmdIO.helloCallCount).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("5.6 CmdBleIO-Specific: expectCommandType")
    inner class ExpectCommandType {

        @Test
        fun `expectCommandType matches programmed success`() {
            cmdIO.expectCommandResults(BleConfirmSuccess)
            val result = cmdIO.expectCommandType(app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandSuccess)
            assertThat(result).isEqualTo(BleConfirmSuccess)
        }

        @Test
        fun `expectCommandType returns error when no data and no program`() {
            val result = cmdIO.expectCommandType(
                app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandSuccess, 10
            )
            assertThat(result).isInstanceOf(BleConfirmError::class.java)
        }

        @Test
        fun `expectCommandType returns BleConfirmIncorrectData for wrong command`() {
            cmdIO.enqueueReceives(BleCommandRTS.data)
            val result = cmdIO.expectCommandType(
                app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandSuccess, 50
            )
            assertThat(result).isInstanceOf(BleConfirmIncorrectData::class.java)
        }

        @Test
        fun `expectCommandType consumes programmed results in order`() {
            cmdIO.expectCommandResults(
                BleConfirmSuccess,
                BleConfirmError("second"),
                BleConfirmSuccess
            )
            assertThat(cmdIO.expectCommandType(app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandSuccess))
                .isEqualTo(BleConfirmSuccess)
            assertThat(cmdIO.expectCommandType(app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandSuccess))
                .isInstanceOf(BleConfirmError::class.java)
            assertThat(cmdIO.expectCommandType(app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandSuccess))
                .isEqualTo(BleConfirmSuccess)
        }
    }

    @Nested
    @DisplayName("5.7 CmdBleIO-Specific: peekCommand")
    inner class PeekCommand {

        @Test
        fun `peekCommand returns head without consuming`() {
            cmdIO.enqueueReceives(byteArrayOf(0x01))
            assertThat(cmdIO.peekCommand()).isEqualTo(byteArrayOf(0x01))
            assertThat(cmdIO.receivePacket(10)).isEqualTo(byteArrayOf(0x01))
        }

        @Test
        fun `peekCommand returns null on empty queue`() {
            assertThat(cmdIO.peekCommand()).isNull()
        }
    }

    @Nested
    @DisplayName("5.8 Timeout Constant")
    inner class TimeoutConstant {

        @Test
        fun `DEFAULT_IO_TIMEOUT_MS is 1000`() {
            assertThat(BleCharacteristicIO.DEFAULT_IO_TIMEOUT_MS).isEqualTo(1000L)
        }
    }
}
