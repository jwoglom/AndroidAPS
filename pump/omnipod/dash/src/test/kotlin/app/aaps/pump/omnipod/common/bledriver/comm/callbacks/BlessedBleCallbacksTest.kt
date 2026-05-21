package app.aaps.pump.omnipod.common.bledriver.comm.callbacks
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.WriteConfirmationSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.WriteConfirmationError
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.WriteConfirmation
import app.aaps.pump.omnipod.common.bledriver.comm.blessed.callbacks.BlessedBleCallbacks

import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CharacteristicType
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.io.IncomingPackets
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import com.welie.blessed.GattStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import com.welie.blessed.BluetoothPeripheral
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Phase 5: BlessedBleCallbacks unit tests.
 *
 * Tests GATT callback routing, write confirmation queue, and service discovery signaling.
 *
 * DUAL-IMPLEMENTATION NOTE: The old code used BleCommCallbacks (extending BluetoothGattCallback).
 * The new code uses BlessedBleCallbacks (extending BluetoothPeripheralCallback).
 * Key behavioral differences tested:
 * - Old: onCharacteristicChanged() for incoming data; New: onCharacteristicUpdate() with GattStatus
 * - Old: connection state change via onConnectionStateChange; New: via BluetoothCentralManagerCallback
 * - Old: flushConfirmationQueue before offer in onWrite; New: direct offer
 */
class BlessedBleCallbacksTest {

    private val logger = AAPSLoggerTest()
    private lateinit var incomingPackets: IncomingPackets
    private lateinit var callbacks: BlessedBleCallbacks
    private val peripheral: BluetoothPeripheral = mock()

    private fun createCharacteristic(uuid: String): BluetoothGattCharacteristic {
        val char = mock<BluetoothGattCharacteristic>()
        whenever(char.uuid).thenReturn(UUID.fromString(uuid))
        return char
    }

    private val cmdCharUuid = CharacteristicType.CMD.value
    private val dataCharUuid = CharacteristicType.DATA.value

    @BeforeEach
    fun setUp() {
        incomingPackets = IncomingPackets()
        callbacks = BlessedBleCallbacks(logger, incomingPackets)
    }

    @Nested
    @DisplayName("Service Discovery")
    inner class ServiceDiscovery {

        @Test
        fun `onServicesDiscovered signals latch`() {
            callbacks.onServicesDiscovered(peripheral)
            assertThat(callbacks.waitForServiceDiscovery(100)).isTrue()
        }

        @Test
        fun `waitForServiceDiscovery times out when not signaled`() {
            assertThat(callbacks.waitForServiceDiscovery(50)).isFalse()
        }

        @Test
        fun `resetConnection re-creates service discovery latch`() {
            callbacks.onServicesDiscovered(peripheral)
            assertThat(callbacks.waitForServiceDiscovery(50)).isTrue()

            callbacks.resetConnection()

            assertThat(callbacks.waitForServiceDiscovery(50)).isFalse()
        }
    }

    @Nested
    @DisplayName("Characteristic Update Routing")
    inner class CharacteristicUpdateRouting {

        @Test
        fun `onCharacteristicUpdate routes CMD packet to cmdQueue`() {
            val cmdChar = createCharacteristic(cmdCharUuid)
            val data = byteArrayOf(0x01, 0x02, 0x03)

            callbacks.onCharacteristicUpdate(peripheral, data, cmdChar, GattStatus.SUCCESS)

            assertThat(incomingPackets.cmdQueue.poll()).isEqualTo(data)
            assertThat(incomingPackets.dataQueue.poll()).isNull()
        }

        @Test
        fun `onCharacteristicUpdate routes DATA packet to dataQueue`() {
            val dataChar = createCharacteristic(dataCharUuid)
            val data = byteArrayOf(0x04, 0x05, 0x06)

            callbacks.onCharacteristicUpdate(peripheral, data, dataChar, GattStatus.SUCCESS)

            assertThat(incomingPackets.dataQueue.poll()).isEqualTo(data)
            assertThat(incomingPackets.cmdQueue.poll()).isNull()
        }

        @Test
        fun `onCharacteristicUpdate ignores unknown UUID without crash`() {
            val unknownChar = createCharacteristic("00001234-0000-1000-8000-00805f9b34fb")
            val data = byteArrayOf(0x01)

            callbacks.onCharacteristicUpdate(peripheral, data, unknownChar, GattStatus.SUCCESS)

            assertThat(incomingPackets.cmdQueue.poll()).isNull()
            assertThat(incomingPackets.dataQueue.poll()).isNull()
        }

        @Test
        fun `onCharacteristicUpdate ignores non-SUCCESS status`() {
            val cmdChar = createCharacteristic(cmdCharUuid)
            val data = byteArrayOf(0x01)

            callbacks.onCharacteristicUpdate(peripheral, data, cmdChar, GattStatus.INTERNAL_ERROR)

            assertThat(incomingPackets.cmdQueue.poll()).isNull()
        }

        @Test
        fun `multiple rapid updates all queued in order`() {
            val cmdChar = createCharacteristic(cmdCharUuid)
            val packets = (0..9).map { byteArrayOf(it.toByte()) }

            packets.forEach { callbacks.onCharacteristicUpdate(peripheral, it, cmdChar, GattStatus.SUCCESS) }

            for (i in 0..9) {
                val polled = incomingPackets.cmdQueue.poll()
                assertThat(polled).isNotNull()
                assertThat(polled!![0]).isEqualTo(i.toByte())
            }
        }
    }

    @Nested
    @DisplayName("Write Confirmation")
    inner class WriteConfirmation {

        @Test
        fun `onCharacteristicWrite SUCCESS produces WriteConfirmationSuccess`() {
            val cmdChar = createCharacteristic(cmdCharUuid)
            val payload = byteArrayOf(0x01, 0x02)

            callbacks.onCharacteristicWrite(peripheral, payload, cmdChar, GattStatus.SUCCESS)

            val confirmation = callbacks.confirmWrite(payload, cmdCharUuid, 100)
            assertThat(confirmation).isInstanceOf(WriteConfirmationSuccess::class.java)
        }

        @Test
        fun `onCharacteristicWrite FAILURE produces WriteConfirmationError`() {
            val cmdChar = createCharacteristic(cmdCharUuid)
            val payload = byteArrayOf(0x01, 0x02)

            callbacks.onCharacteristicWrite(peripheral, payload, cmdChar, GattStatus.WRITE_NOT_PERMITTED)

            val confirmation = callbacks.confirmWrite(payload, cmdCharUuid, 100)
            assertThat(confirmation).isInstanceOf(WriteConfirmationError::class.java)
        }

        @Test
        fun `confirmWrite times out when no callback arrives`() {
            val confirmation = callbacks.confirmWrite(byteArrayOf(0x01), cmdCharUuid, 50)
            assertThat(confirmation).isInstanceOf(WriteConfirmationError::class.java)
            assertThat((confirmation as WriteConfirmationError).msg).contains("Timeout")
        }

        @Test
        fun `confirmWrite rejects mismatched payload`() {
            val cmdChar = createCharacteristic(cmdCharUuid)
            val sentPayload = byteArrayOf(0x01, 0x02)
            val wrongPayload = byteArrayOf(0x03, 0x04)

            callbacks.onCharacteristicWrite(peripheral, wrongPayload, cmdChar, GattStatus.SUCCESS)

            val confirmation = callbacks.confirmWrite(sentPayload, cmdCharUuid, 100)
            assertThat(confirmation).isInstanceOf(WriteConfirmationError::class.java)
            assertThat((confirmation as WriteConfirmationError).msg).contains("incorrect")
        }

        @Test
        fun `confirmWrite rejects mismatched UUID`() {
            val cmdChar = createCharacteristic(cmdCharUuid)
            val payload = byteArrayOf(0x01)

            callbacks.onCharacteristicWrite(peripheral, payload, cmdChar, GattStatus.SUCCESS)

            val confirmation = callbacks.confirmWrite(payload, dataCharUuid, 100)
            assertThat(confirmation).isInstanceOf(WriteConfirmationError::class.java)
        }

        @Test
        fun `onDescriptorWrite SUCCESS produces WriteConfirmationSuccess`() {
            val descriptor = mock<BluetoothGattDescriptor>()
            whenever(descriptor.uuid).thenReturn(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            val payload = byteArrayOf(0x02, 0x00)

            callbacks.onDescriptorWrite(peripheral, payload, descriptor, GattStatus.SUCCESS)

            val confirmation = callbacks.confirmWrite(
                payload,
                "00002902-0000-1000-8000-00805f9b34fb",
                100
            )
            assertThat(confirmation).isInstanceOf(WriteConfirmationSuccess::class.java)
        }

        @Test
        fun `flushConfirmationQueue clears pending writes`() {
            val cmdChar = createCharacteristic(cmdCharUuid)
            callbacks.onCharacteristicWrite(peripheral, byteArrayOf(0x01), cmdChar, GattStatus.SUCCESS)

            callbacks.flushConfirmationQueue()

            val confirmation = callbacks.confirmWrite(byteArrayOf(0x01), cmdCharUuid, 50)
            assertThat(confirmation).isInstanceOf(WriteConfirmationError::class.java)
        }

        @Test
        fun `resetConnection flushes write queue`() {
            val cmdChar = createCharacteristic(cmdCharUuid)
            callbacks.onCharacteristicWrite(peripheral, byteArrayOf(0x01), cmdChar, GattStatus.SUCCESS)

            callbacks.resetConnection()

            val confirmation = callbacks.confirmWrite(byteArrayOf(0x01), cmdCharUuid, 50)
            assertThat(confirmation).isInstanceOf(WriteConfirmationError::class.java)
        }
    }
}
