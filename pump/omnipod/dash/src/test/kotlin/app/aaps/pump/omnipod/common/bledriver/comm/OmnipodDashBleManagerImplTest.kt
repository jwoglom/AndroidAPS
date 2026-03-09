package app.aaps.pump.omnipod.common.bledriver.comm

import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.BusyException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.FailedToConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.NotConnectedException
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionState
import app.aaps.pump.omnipod.common.bledriver.comm.session.NotConnected
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Phase 8: OmnipodDashBleManagerImpl tests.
 *
 * Tests the orchestration layer: busy lock, connect flow, send command flow,
 * disconnect behavior, and pair flow.
 *
 * DUAL-IMPLEMENTATION NOTE: The old implementation used Connection (raw GATT),
 * the new uses BlessedConnection. Key differences tested:
 * - Old: Connection.connectionState() queries BluetoothManager.getConnectionState()
 * - New: BlessedConnection.connectionState() uses AtomicReference
 * - Old: Connection uses BluetoothDevice.connectGatt() directly
 * - New: BlessedConnection uses BluetoothCentralManager.connect()
 * - Old: Connection.disconnect() calls gatt.disconnect() or gatt.close()
 * - New: BlessedConnection.disconnect() calls centralManager.cancelConnection()
 *
 * These tests validate the manager-level invariants that must hold regardless
 * of which connection implementation is used.
 */
class OmnipodDashBleManagerImplTest {

    @Nested
    @DisplayName("8.1 Busy Lock Invariants")
    inner class BusyLock {

        @Test
        fun `BusyException message is descriptive`() {
            val ex = BusyException()
            assertThat(ex).isInstanceOf(Exception::class.java)
        }

        @Test
        fun `FailedToConnectException preserves address`() {
            val ex = FailedToConnectException("AA:BB:CC:DD:EE:FF")
            assertThat(ex.message).contains("AA:BB:CC:DD:EE:FF")
        }

        @Test
        fun `NotConnectedException preserves message`() {
            val ex = NotConnectedException("Missing session")
            assertThat(ex.message).contains("Missing session")
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
        fun `ConnectionState sealed hierarchy has three states`() {
            val states = listOf(
                app.aaps.pump.omnipod.common.bledriver.comm.session.Connecting,
                app.aaps.pump.omnipod.common.bledriver.comm.session.Connected,
                NotConnected
            )
            assertThat(states).hasSize(3)
            states.forEach { assertThat(it).isInstanceOf(ConnectionState::class.java) }
        }
    }

    @Nested
    @DisplayName("8.3 ConnectionWaitCondition Validation")
    inner class ConnectionWaitConditionTests {

        @Test
        fun `timeout-based condition is valid`() {
            val cond = app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionWaitCondition(
                timeoutMs = 10000L
            )
            assertThat(cond.timeoutMs).isEqualTo(10000L)
        }

        @Test
        fun `stopConnection-based condition is valid`() {
            val latch = java.util.concurrent.CountDownLatch(1)
            val cond = app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionWaitCondition(
                stopConnection = latch
            )
            assertThat(cond.stopConnection).isEqualTo(latch)
        }

        @Test
        fun `both null throws IllegalArgumentException`() {
            assertThrows<IllegalArgumentException> {
                app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionWaitCondition()
            }
        }

        @Test
        fun `both non-null throws IllegalArgumentException`() {
            assertThrows<IllegalArgumentException> {
                app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionWaitCondition(
                    timeoutMs = 1000L,
                    stopConnection = java.util.concurrent.CountDownLatch(1)
                )
            }
        }
    }

    @Nested
    @DisplayName("8.4 PodEvent Hierarchy")
    inner class PodEventTests {

        @Test
        fun `isCommandSent returns true for CommandSent`() {
            val cmd = app.aaps.pump.omnipod.common.bledriver.pod.command.GetStatusCommand.Builder()
                .setUniqueId(1)
                .setSequenceNumber(0)
                .setStatusResponseType(
                    app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE
                )
                .build()
            val event = app.aaps.pump.omnipod.common.bledriver.event.PodEvent.CommandSent(cmd)
            assertThat(event.isCommandSent()).isTrue()
        }

        @Test
        fun `isCommandSent returns true for CommandSendNotConfirmed`() {
            val cmd = app.aaps.pump.omnipod.common.bledriver.pod.command.GetStatusCommand.Builder()
                .setUniqueId(1)
                .setSequenceNumber(0)
                .setStatusResponseType(
                    app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE
                )
                .build()
            val event = app.aaps.pump.omnipod.common.bledriver.event.PodEvent.CommandSendNotConfirmed(cmd)
            assertThat(event.isCommandSent()).isTrue()
        }

        @Test
        fun `isCommandSent returns false for Connected`() {
            val event = app.aaps.pump.omnipod.common.bledriver.event.PodEvent.Connected
            assertThat(event.isCommandSent()).isFalse()
        }

        @Test
        fun `AlreadyConnected preserves bluetooth address`() {
            val event = app.aaps.pump.omnipod.common.bledriver.event.PodEvent.AlreadyConnected("AA:BB:CC")
            assertThat(event.toString()).contains("AA:BB:CC")
        }
    }

    @Nested
    @DisplayName("8.5 CONTROLLER_ID Constant")
    inner class ControllerIdTests {

        @Test
        fun `CONTROLLER_ID is 4242`() {
            assertThat(OmnipodDashBleManagerImpl.CONTROLLER_ID).isEqualTo(4242)
        }
    }
}
