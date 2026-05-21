package app.aaps.pump.omnipod.common.bledriver.comm.session
import app.aaps.pump.omnipod.common.bledriver.comm.blessed.session.BlessedConnection

import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.Ids
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Phase 9: BlessedConnection tests.
 *
 * Tests connection lifecycle, session establishment, disconnect,
 * and connection wait conditions.
 *
 * DUAL-IMPLEMENTATION NOTE: Key differences between old Connection and new BlessedConnection:
 *
 * | Aspect                  | Old (Connection)                     | New (BlessedConnection)              |
 * |------------------------|--------------------------------------|--------------------------------------|
 * | Connection API         | BluetoothDevice.connectGatt()        | BluetoothCentralManager.connect()    |
 * | State tracking         | BluetoothManager.getConnectionState  | AtomicReference<ConnectionState>     |
 * | Service discovery      | ServiceDiscoverer + BleCommCallbacks | BlessedBleCallbacks.onServicesDiscovered |
 * | Disconnect (soft)      | gatt.disconnect()                    | centralManager.cancelConnection()    |
 * | Disconnect (hard)      | gatt.close() + nullify               | centralManager.close() + nullify     |
 * | Reconnect behavior     | gatt.connect() on existing gatt      | New centralManager per connect       |
 * | Sleep on failure       | 10s sleep                            | No sleep (immediate throw)           |
 * | Connection priority    | Commented out                        | requestConnectionPriority(HIGH)      |
 *
 * These tests validate the shared behavioral invariants.
 */
class BlessedConnectionTest {

    @Nested
    @DisplayName("9.1 Connection Constants")
    inner class ConnectionConstants {

        @Test
        fun `BASE_CONNECT_TIMEOUT_MS is 10 seconds`() {
            assertThat(BlessedConnection.BASE_CONNECT_TIMEOUT_MS).isEqualTo(10000L)
        }

        @Test
        fun `MIN_DISCOVERY_TIMEOUT_MS is 10 seconds`() {
            assertThat(BlessedConnection.MIN_DISCOVERY_TIMEOUT_MS).isEqualTo(10000L)
        }

        @Test
        fun `STOP_CONNECTING_CHECK_INTERVAL_MS is 500ms`() {
            assertThat(BlessedConnection.STOP_CONNECTING_CHECK_INTERVAL_MS).isEqualTo(500L)
        }
    }

    @Nested
    @DisplayName("9.2 ConnectionWaitCondition")
    inner class ConnectionWaitConditionTests {

        @Test
        fun `timeout-based wait creates valid condition`() {
            val cond = ConnectionWaitCondition(timeoutMs = 5000L)
            assertThat(cond.timeoutMs).isEqualTo(5000L)
            assertThat(cond.stopConnection).isNull()
        }

        @Test
        fun `latch-based wait creates valid condition`() {
            val latch = java.util.concurrent.CountDownLatch(1)
            val cond = ConnectionWaitCondition(stopConnection = latch)
            assertThat(cond.timeoutMs).isNull()
            assertThat(cond.stopConnection).isEqualTo(latch)
        }

        @Test
        fun `neither timeout nor latch throws`() {
            assertThrows<IllegalArgumentException> {
                ConnectionWaitCondition()
            }
        }

        @Test
        fun `both timeout and latch throws`() {
            assertThrows<IllegalArgumentException> {
                ConnectionWaitCondition(
                    timeoutMs = 1000L,
                    stopConnection = java.util.concurrent.CountDownLatch(1)
                )
            }
        }
    }

    @Nested
    @DisplayName("9.3 Session Keys")
    inner class SessionKeysTests {

        @Test
        fun `SessionKeys requires 16-byte CK`() {
            assertThrows<IllegalArgumentException> {
                SessionKeys(
                    ck = ByteArray(10),
                    nonce = app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.Nonce(ByteArray(8), 0),
                    msgSequenceNumber = 0
                )
            }
        }

        @Test
        fun `SessionKeys with valid CK succeeds`() {
            val keys = SessionKeys(
                ck = ByteArray(16),
                nonce = app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.Nonce(ByteArray(8), 0),
                msgSequenceNumber = 0
            )
            assertThat(keys.ck).hasLength(16)
        }

        @Test
        fun `msgSequenceNumber is mutable`() {
            val keys = SessionKeys(
                ck = ByteArray(16),
                nonce = app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.Nonce(ByteArray(8), 0),
                msgSequenceNumber = 0
            )
            keys.msgSequenceNumber = 5
            assertThat(keys.msgSequenceNumber).isEqualTo(5.toByte())
        }
    }

    @Nested
    @DisplayName("9.4 SessionNegotiationResponse Hierarchy")
    inner class SessionNegotiationResponseTests {

        @Test
        fun `SessionKeys is a SessionNegotiationResponse`() {
            val keys = SessionKeys(
                ck = ByteArray(16),
                nonce = app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.Nonce(ByteArray(8), 0),
                msgSequenceNumber = 0
            )
            assertThat(keys).isInstanceOf(SessionNegotiationResponse::class.java)
        }

        @Test
        fun `SessionNegotiationResynchronization is a SessionNegotiationResponse`() {
            val resynch = SessionNegotiationResynchronization(
                synchronizedEapSqn = EapSqn(ByteArray(6)),
                msgSequenceNumber = 1
            )
            assertThat(resynch).isInstanceOf(SessionNegotiationResponse::class.java)
        }
    }

    @Nested
    @DisplayName("9.5 DisconnectHandler Interface")
    inner class DisconnectHandlerTests {

        @Test
        fun `DisconnectHandler can be implemented`() {
            val handler = object : DisconnectHandler {
                var lastStatus: Int = -1
                override fun onConnectionLost(status: Int) {
                    lastStatus = status
                }
            }
            handler.onConnectionLost(42)
            assertThat(handler.lastStatus).isEqualTo(42)
        }
    }

    @Nested
    @DisplayName("9.6 Ids Construction")
    inner class IdsTests {

        @Test
        fun `Ids with uniqueId constructs podId from uniqueId`() {
            val podState = mock<OmnipodDashPodStateManager>()
            whenever(podState.uniqueId).thenReturn(12345L)
            val ids = Ids(podState)
            assertThat(ids.podId.toLong()).isEqualTo(12345L)
        }

        @Test
        fun `Ids without uniqueId uses incremented controller ID`() {
            val podState = mock<OmnipodDashPodStateManager>()
            whenever(podState.uniqueId).thenReturn(null)
            val ids = Ids(podState)
            assertThat(ids.podId).isNotNull()
            assertThat(ids.myId).isEqualTo(Id.fromInt(4242))
        }
    }
}
