package app.aaps.pump.omnipod.common.bledriver.comm.io

import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CharacteristicType
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.io.IncomingPackets
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests IncomingPackets — the CMD/DATA queue router used by both old
 * (BleCommCallbacks) and new (BlessedBleCallbacks) implementations.
 */
class IncomingPacketsTest {

    private lateinit var packets: IncomingPackets

    @BeforeEach
    fun setUp() {
        packets = IncomingPackets()
    }

    @Test
    @DisplayName("CMD type routes to cmdQueue")
    fun cmdRouting() {
        val queue = packets.byCharacteristicType(CharacteristicType.CMD)
        assertThat(queue).isSameInstanceAs(packets.cmdQueue)
    }

    @Test
    @DisplayName("DATA type routes to dataQueue")
    fun dataRouting() {
        val queue = packets.byCharacteristicType(CharacteristicType.DATA)
        assertThat(queue).isSameInstanceAs(packets.dataQueue)
    }

    @Test
    @DisplayName("CMD and DATA queues are independent")
    fun queuesIndependent() {
        packets.cmdQueue.add(byteArrayOf(0x01))
        packets.dataQueue.add(byteArrayOf(0x02))

        assertThat(packets.cmdQueue.poll()).isEqualTo(byteArrayOf(0x01))
        assertThat(packets.dataQueue.poll()).isEqualTo(byteArrayOf(0x02))
    }

    @Test
    @DisplayName("queues start empty")
    fun queuesStartEmpty() {
        assertThat(packets.cmdQueue.poll()).isNull()
        assertThat(packets.dataQueue.poll()).isNull()
    }

    @Test
    @DisplayName("multiple items maintain FIFO order")
    fun fifoOrder() {
        packets.byCharacteristicType(CharacteristicType.CMD).add(byteArrayOf(0x01))
        packets.byCharacteristicType(CharacteristicType.CMD).add(byteArrayOf(0x02))
        packets.byCharacteristicType(CharacteristicType.CMD).add(byteArrayOf(0x03))

        assertThat(packets.cmdQueue.poll()!![0]).isEqualTo(0x01.toByte())
        assertThat(packets.cmdQueue.poll()!![0]).isEqualTo(0x02.toByte())
        assertThat(packets.cmdQueue.poll()!![0]).isEqualTo(0x03.toByte())
    }
}
