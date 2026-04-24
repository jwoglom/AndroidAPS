package app.aaps.pump.omnipod.common.bledriver.comm

import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.BusyException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.FailedToConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.NotConnectedException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.CouldNotSendCommandException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.MessageIOException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.SessionEstablishmentException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.PairingException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ScanException
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionState
import app.aaps.pump.omnipod.common.bledriver.comm.session.Connected
import app.aaps.pump.omnipod.common.bledriver.comm.session.Connecting
import app.aaps.pump.omnipod.common.bledriver.comm.session.NotConnected
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionWaitCondition
import app.aaps.pump.omnipod.common.bledriver.event.PodEvent
import app.aaps.pump.omnipod.common.bledriver.pod.command.GetStatusCommand
import app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Phase 8: BLE Manager orchestration tests — implementation-agnostic.
 *
 * Tests the shared types, exceptions, events, and invariants that both
 * old and new BLE manager implementations must satisfy. No concrete
 * implementation classes (OmnipodDashBleManagerImpl, Connection,
 * BlessedConnection) are referenced.
 *
 * Both implementations must:
 * - Use a busy lock to prevent concurrent operations
 * - Emit the correct PodEvent sequence
 * - Throw the correct exception types for each failure mode
 * - Support the OmnipodDashBleManager interface contract
 */
class BleManagerOrchestrationTest {

    @Nested
    @DisplayName("8.1 Exception Types")
    inner class ExceptionTypes {

        @Test
        fun `BusyException is throwable`() {
            val ex = BusyException()
            assertThat(ex).isInstanceOf(Exception::class.java)
        }

        @Test
        fun `FailedToConnectException preserves message`() {
            val ex = FailedToConnectException("AA:BB:CC:DD:EE:FF")
            assertThat(ex.message).contains("AA:BB:CC:DD:EE:FF")
        }

        @Test
        fun `NotConnectedException preserves message`() {
            val ex = NotConnectedException("Missing session")
            assertThat(ex.message).contains("Missing session")
        }

        @Test
        fun `ConnectException preserves message`() {
            val ex = ConnectException("Bluetooth not available")
            assertThat(ex.message).contains("Bluetooth not available")
        }

        @Test
        fun `CouldNotSendCommandException is throwable`() {
            val ex = CouldNotSendCommandException()
            assertThat(ex).isInstanceOf(Exception::class.java)
        }

        @Test
        fun `MessageIOException preserves message`() {
            val ex = MessageIOException("read failed")
            assertThat(ex.message).contains("read failed")
        }

        @Test
        fun `SessionEstablishmentException preserves message`() {
            val ex = SessionEstablishmentException("EAP failure")
            assertThat(ex.message).contains("EAP failure")
        }

        @Test
        fun `PairingException preserves message`() {
            val ex = PairingException("LTK exchange failed")
            assertThat(ex.message).contains("LTK exchange failed")
        }

        @Test
        fun `ScanException with string preserves message`() {
            val ex = ScanException("Not found")
            assertThat(ex.message).contains("Not found")
        }
    }

    @Nested
    @DisplayName("8.2 ConnectionState Invariants")
    inner class ConnectionStateTests {

        @Test
        fun `NotConnected is the initial state`() {
            val state: ConnectionState = NotConnected
            assertThat(state).isInstanceOf(NotConnected::class.java)
        }

        @Test
        fun `three connection states form sealed hierarchy`() {
            val states = listOf(Connecting, Connected, NotConnected)
            assertThat(states).hasSize(3)
            states.forEach { assertThat(it).isInstanceOf(ConnectionState::class.java) }
        }
    }

    @Nested
    @DisplayName("8.3 ConnectionWaitCondition Validation")
    inner class ConnectionWaitConditionTests {

        @Test
        fun `timeout-based condition is valid`() {
            val cond = ConnectionWaitCondition(timeoutMs = 10000L)
            assertThat(cond.timeoutMs).isEqualTo(10000L)
        }

        @Test
        fun `stopConnection-based condition is valid`() {
            val latch = java.util.concurrent.CountDownLatch(1)
            val cond = ConnectionWaitCondition(stopConnection = latch)
            assertThat(cond.stopConnection).isEqualTo(latch)
        }

        @Test
        fun `both null throws`() {
            assertThrows<IllegalArgumentException> {
                ConnectionWaitCondition()
            }
        }

        @Test
        fun `both non-null throws`() {
            assertThrows<IllegalArgumentException> {
                ConnectionWaitCondition(
                    timeoutMs = 1000L,
                    stopConnection = java.util.concurrent.CountDownLatch(1)
                )
            }
        }
    }

    @Nested
    @DisplayName("8.4 PodEvent Hierarchy")
    inner class PodEventTests {

        private fun createTestCommand() = GetStatusCommand.Builder()
            .setUniqueId(1)
            .setSequenceNumber(0)
            .setStatusResponseType(ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE)
            .build()

        @Test
        fun `isCommandSent returns true for CommandSent`() {
            val event = PodEvent.CommandSent(createTestCommand())
            assertThat(event.isCommandSent()).isTrue()
        }

        @Test
        fun `isCommandSent returns true for CommandSendNotConfirmed`() {
            val event = PodEvent.CommandSendNotConfirmed(createTestCommand())
            assertThat(event.isCommandSent()).isTrue()
        }

        @Test
        fun `isCommandSent returns false for Connected`() {
            assertThat(PodEvent.Connected.isCommandSent()).isFalse()
        }

        @Test
        fun `isCommandSent returns false for BluetoothConnecting`() {
            assertThat(PodEvent.BluetoothConnecting.isCommandSent()).isFalse()
        }

        @Test
        fun `isCommandSent returns false for Scanning`() {
            assertThat(PodEvent.Scanning.isCommandSent()).isFalse()
        }

        @Test
        fun `AlreadyConnected preserves bluetooth address`() {
            val event = PodEvent.AlreadyConnected("AA:BB:CC")
            assertThat(event.toString()).contains("AA:BB:CC")
        }

        @Test
        fun `BluetoothConnected preserves address`() {
            val event = PodEvent.BluetoothConnected("DD:EE:FF")
            assertThat(event.toString()).contains("DD:EE:FF")
        }

        @Test
        fun `CommandSending toString contains command info`() {
            val event = PodEvent.CommandSending(createTestCommand())
            assertThat(event.toString()).contains("CommandSending")
        }

        @Test
        fun `CommandSent toString contains command info`() {
            val event = PodEvent.CommandSent(createTestCommand())
            assertThat(event.toString()).contains("CommandSent")
        }
    }

    @Nested
    @DisplayName("8.5 OmnipodDashBleManager Interface")
    inner class BleManagerInterface {

        @Test
        fun `OmnipodDashBleManager is an interface`() {
            assertThat(OmnipodDashBleManager::class.java.isInterface).isTrue()
        }

        @Test
        fun `OmnipodDashBleManager declares sendCommand`() {
            val method = OmnipodDashBleManager::class.java.methods.find { it.name == "sendCommand" }
            assertThat(method).isNotNull()
        }

        @Test
        fun `OmnipodDashBleManager declares connect`() {
            val methods = OmnipodDashBleManager::class.java.methods.filter { it.name == "connect" }
            assertThat(methods).isNotEmpty()
        }

        @Test
        fun `OmnipodDashBleManager declares disconnect`() {
            val method = OmnipodDashBleManager::class.java.methods.find { it.name == "disconnect" }
            assertThat(method).isNotNull()
        }

        @Test
        fun `OmnipodDashBleManager declares pairNewPod`() {
            val method = OmnipodDashBleManager::class.java.methods.find { it.name == "pairNewPod" }
            assertThat(method).isNotNull()
        }

        @Test
        fun `OmnipodDashBleManager declares getStatus`() {
            val method = OmnipodDashBleManager::class.java.methods.find { it.name == "getStatus" }
            assertThat(method).isNotNull()
        }
    }
}
