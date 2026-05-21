package app.aaps.pump.omnipod.common.bledriver.comm.pair

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests PairResult — the output of LTK negotiation containing the
 * Long-Term Key and message sequence number.
 */
class PairResultTest {

    @Test
    @DisplayName("valid 16-byte LTK creates successfully")
    fun validLtk() {
        val result = PairResult(ByteArray(16), 1)
        assertThat(result.ltk).hasLength(16)
        assertThat(result.msgSeq).isEqualTo(1.toByte())
    }

    @Test
    @DisplayName("LTK shorter than 16 bytes throws IllegalArgumentException")
    fun shortLtk() {
        assertThrows<IllegalArgumentException> {
            PairResult(ByteArray(10), 1)
        }
    }

    @Test
    @DisplayName("LTK longer than 16 bytes throws IllegalArgumentException")
    fun longLtk() {
        assertThrows<IllegalArgumentException> {
            PairResult(ByteArray(20), 1)
        }
    }

    @Test
    @DisplayName("empty LTK throws IllegalArgumentException")
    fun emptyLtk() {
        assertThrows<IllegalArgumentException> {
            PairResult(ByteArray(0), 1)
        }
    }
}
