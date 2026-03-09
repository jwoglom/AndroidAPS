package app.aaps.pump.omnipod.common.bledriver.comm.session

import app.aaps.pump.omnipod.common.bledriver.comm.Id
import app.aaps.pump.omnipod.common.bledriver.comm.Ids
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.Nonce
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Phase 9: Connection layer tests — shared types only.
 *
 * Tests the types and contracts that both old (Connection) and new
 * (BlessedConnection) implementations share:
 * - ConnectionState sealed hierarchy
 * - ConnectionWaitCondition validation
 * - SessionKeys construction and validation
 * - SessionNegotiationResponse hierarchy
 * - DisconnectHandler interface
 * - Ids construction
 *
 * No implementation-specific classes (BlessedConnection, Connection,
 * BluetoothCentralManager, BluetoothGatt) are referenced.
 */
class ConnectionLayerTest {

    @Nested
    @DisplayName("9.1 ConnectionState Sealed Hierarchy")
    inner class ConnectionStateTests {

        @Test
        fun `three connection states exist`() {
            val states = listOf(Connecting, Connected, NotConnected)
            assertThat(states).hasSize(3)
            states.forEach { assertThat(it).isInstanceOf(ConnectionState::class.java) }
        }

        @Test
        fun `NotConnected is distinct from Connected`() {
            assertThat(NotConnected).isNotEqualTo(Connected)
        }

        @Test
        fun `Connecting is distinct from Connected`() {
            assertThat(Connecting).isNotEqualTo(Connected)
        }
    }

    @Nested
    @DisplayName("9.2 ConnectionWaitCondition Validation")
    inner class ConnectionWaitConditionTests {

        @Test
        fun `timeout-based condition is valid`() {
            val cond = ConnectionWaitCondition(timeoutMs = 5000L)
            assertThat(cond.timeoutMs).isEqualTo(5000L)
            assertThat(cond.stopConnection).isNull()
        }

        @Test
        fun `latch-based condition is valid`() {
            val latch = java.util.concurrent.CountDownLatch(1)
            val cond = ConnectionWaitCondition(stopConnection = latch)
            assertThat(cond.timeoutMs).isNull()
            assertThat(cond.stopConnection).isEqualTo(latch)
        }

        @Test
        fun `both null throws IllegalArgumentException`() {
            assertThrows<IllegalArgumentException> {
                ConnectionWaitCondition()
            }
        }

        @Test
        fun `both non-null throws IllegalArgumentException`() {
            assertThrows<IllegalArgumentException> {
                ConnectionWaitCondition(
                    timeoutMs = 1000L,
                    stopConnection = java.util.concurrent.CountDownLatch(1)
                )
            }
        }

        @Test
        fun `timeoutMs is mutable`() {
            val cond = ConnectionWaitCondition(timeoutMs = 5000L)
            cond.timeoutMs = 3000L
            assertThat(cond.timeoutMs).isEqualTo(3000L)
        }
    }

    @Nested
    @DisplayName("9.3 SessionKeys")
    inner class SessionKeysTests {

        @Test
        fun `SessionKeys requires 16-byte CK`() {
            assertThrows<IllegalArgumentException> {
                SessionKeys(
                    ck = ByteArray(10),
                    nonce = Nonce(ByteArray(8), 0),
                    msgSequenceNumber = 0
                )
            }
        }

        @Test
        fun `SessionKeys with valid 16-byte CK succeeds`() {
            val keys = SessionKeys(
                ck = ByteArray(16),
                nonce = Nonce(ByteArray(8), 0),
                msgSequenceNumber = 0
            )
            assertThat(keys.ck).hasLength(16)
        }

        @Test
        fun `msgSequenceNumber is mutable`() {
            val keys = SessionKeys(
                ck = ByteArray(16),
                nonce = Nonce(ByteArray(8), 0),
                msgSequenceNumber = 0
            )
            keys.msgSequenceNumber = 5
            assertThat(keys.msgSequenceNumber).isEqualTo(5.toByte())
        }

        @Test
        fun `Nonce requires 8-byte prefix`() {
            assertThrows<IllegalArgumentException> {
                Nonce(ByteArray(4), 0)
            }
        }

        @Test
        fun `Nonce increment produces 13-byte result`() {
            val nonce = Nonce(ByteArray(8), 0)
            val result = nonce.increment(podReceiving = true)
            assertThat(result).hasLength(13)
        }
    }

    @Nested
    @DisplayName("9.4 SessionNegotiationResponse Hierarchy")
    inner class SessionNegotiationResponseTests {

        @Test
        fun `SessionKeys is a SessionNegotiationResponse`() {
            val keys = SessionKeys(
                ck = ByteArray(16),
                nonce = Nonce(ByteArray(8), 0),
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
        fun `DisconnectHandler can be implemented and invoked`() {
            var receivedStatus = -1
            val handler = object : DisconnectHandler {
                override fun onConnectionLost(status: Int) {
                    receivedStatus = status
                }
            }
            handler.onConnectionLost(42)
            assertThat(receivedStatus).isEqualTo(42)
        }

        @Test
        fun `DisconnectHandler handles zero status`() {
            var receivedStatus = -1
            val handler = object : DisconnectHandler {
                override fun onConnectionLost(status: Int) {
                    receivedStatus = status
                }
            }
            handler.onConnectionLost(0)
            assertThat(receivedStatus).isEqualTo(0)
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
