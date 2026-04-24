package app.aaps.pump.omnipod.common.bledriver.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests Flag utility — bit manipulation used by MessagePacket header
 * serialization. The bit ordering (MSB-first: idx 0 = bit 7) is critical
 * for correct message encoding/decoding.
 */
class FlagTest {

    @Test
    @DisplayName("default value is 0")
    fun defaultValue() {
        assertThat(Flag().value).isEqualTo(0)
    }

    @Test
    @DisplayName("set bit 0 sets MSB (bit 7)")
    fun setBit0() {
        val f = Flag()
        f.set(0, true)
        assertThat(f.value).isEqualTo(0x80) // 1000_0000
    }

    @Test
    @DisplayName("set bit 7 sets LSB (bit 0)")
    fun setBit7() {
        val f = Flag()
        f.set(7, true)
        assertThat(f.value).isEqualTo(0x01) // 0000_0001
    }

    @Test
    @DisplayName("set bit 3 sets bit 4 in value")
    fun setBit3() {
        val f = Flag()
        f.set(3, true)
        assertThat(f.value).isEqualTo(0x10) // 0001_0000
    }

    @Test
    @DisplayName("set false does not change value")
    fun setFalse() {
        val f = Flag()
        f.set(0, false)
        assertThat(f.value).isEqualTo(0)
    }

    @Test
    @DisplayName("get returns 1 for set bit")
    fun getSetBit() {
        val f = Flag(0x80) // bit 7 set
        assertThat(f.get(0)).isEqualTo(1)
    }

    @Test
    @DisplayName("get returns 0 for unset bit")
    fun getUnsetBit() {
        val f = Flag(0x80) // only bit 7 set
        assertThat(f.get(7)).isEqualTo(0)
    }

    @Test
    @DisplayName("multiple bits can be set independently")
    fun multipleBits() {
        val f = Flag()
        f.set(0, true) // bit 7
        f.set(7, true) // bit 0
        assertThat(f.value).isEqualTo(0x81) // 1000_0001
    }

    @Test
    @DisplayName("constructor with value preserves all bits")
    fun constructorWithValue() {
        val f = Flag(0xFF)
        for (i in 0..7) {
            assertThat(f.get(i.toByte())).isEqualTo(1)
        }
    }

    @Test
    @DisplayName("all bits zero produces 0")
    fun allZero() {
        val f = Flag(0x00)
        for (i in 0..7) {
            assertThat(f.get(i.toByte())).isEqualTo(0)
        }
    }

    @Test
    @DisplayName("roundtrip: set then get for each bit position")
    fun roundtrip() {
        for (bit in 0..7) {
            val f = Flag()
            f.set(bit.toByte(), true)
            assertThat(f.get(bit.toByte())).isEqualTo(1)
            for (other in 0..7) {
                if (other != bit) {
                    assertThat(f.get(other.toByte())).isEqualTo(0)
                }
            }
        }
    }
}
