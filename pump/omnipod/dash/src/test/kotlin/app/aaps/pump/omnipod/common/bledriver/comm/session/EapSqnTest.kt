package app.aaps.pump.omnipod.common.bledriver.comm.session

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests EapSqn — the EAP-AKA sequence number used to prevent replay
 * attacks during session establishment.
 */
class EapSqnTest {

    @Test
    @DisplayName("constructor from ByteArray requires exactly 6 bytes")
    fun constructorSize() {
        assertThrows<IllegalArgumentException> { EapSqn(ByteArray(4)) }
        assertThrows<IllegalArgumentException> { EapSqn(ByteArray(8)) }
        EapSqn(ByteArray(6)) // should not throw
    }

    @Test
    @DisplayName("constructor from Long creates 6-byte value")
    fun constructorFromLong() {
        val sqn = EapSqn(42L)
        assertThat(sqn.value).hasLength(6)
        assertThat(sqn.toLong()).isEqualTo(42L)
    }

    @Test
    @DisplayName("toLong roundtrips correctly")
    fun toLongRoundtrip() {
        for (v in listOf(0L, 1L, 255L, 65535L, 1000000L)) {
            assertThat(EapSqn(v).toLong()).isEqualTo(v)
        }
    }

    @Test
    @DisplayName("increment produces value + 1")
    fun increment() {
        val sqn = EapSqn(10L)
        val next = sqn.increment()
        assertThat(next.toLong()).isEqualTo(11L)
    }

    @Test
    @DisplayName("increment does not mutate original")
    fun incrementImmutable() {
        val sqn = EapSqn(5L)
        sqn.increment()
        assertThat(sqn.toLong()).isEqualTo(5L)
    }

    @Test
    @DisplayName("increment from zero produces 1")
    fun incrementFromZero() {
        val sqn = EapSqn(0L)
        assertThat(sqn.increment().toLong()).isEqualTo(1L)
    }

    @Test
    @DisplayName("toString contains numeric value")
    fun toStringTest() {
        val sqn = EapSqn(42L)
        assertThat(sqn.toString()).contains("42")
    }
}
