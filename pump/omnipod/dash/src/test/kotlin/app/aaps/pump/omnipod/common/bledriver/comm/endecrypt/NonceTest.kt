package app.aaps.pump.omnipod.common.bledriver.comm.endecrypt

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests Nonce — the AES-CCM nonce used for message encryption/decryption.
 * The podReceiving flag controls bit 7 of the counter byte, which is
 * critical for distinguishing message direction.
 */
class NonceTest {

    @Test
    @DisplayName("prefix must be exactly 8 bytes")
    fun prefixLength() {
        assertThrows<IllegalArgumentException> { Nonce(ByteArray(4), 0) }
        assertThrows<IllegalArgumentException> { Nonce(ByteArray(12), 0) }
        Nonce(ByteArray(8), 0)
    }

    @Test
    @DisplayName("increment produces 13-byte nonce (8 prefix + 5 counter)")
    fun incrementLength() {
        val nonce = Nonce(ByteArray(8), 0)
        assertThat(nonce.increment(true)).hasLength(13)
        assertThat(nonce.increment(false)).hasLength(13)
    }

    @Test
    @DisplayName("increment advances sqn")
    fun incrementAdvancesSqn() {
        val nonce = Nonce(ByteArray(8), 0)
        nonce.increment(true)
        assertThat(nonce.sqn).isEqualTo(1)
        nonce.increment(true)
        assertThat(nonce.sqn).isEqualTo(2)
    }

    @Test
    @DisplayName("podReceiving=true clears bit 7 of first counter byte")
    fun podReceivingTrue() {
        val nonce = Nonce(ByteArray(8), 0)
        val result = nonce.increment(true)
        assertThat(result[8].toInt() and 0x80).isEqualTo(0)
    }

    @Test
    @DisplayName("podReceiving=false sets bit 7 of first counter byte")
    fun podReceivingFalse() {
        val nonce = Nonce(ByteArray(8), 0)
        val result = nonce.increment(false)
        assertThat(result[8].toInt() and 0x80).isNotEqualTo(0)
    }

    @Test
    @DisplayName("prefix bytes are preserved in output")
    fun prefixPreserved() {
        val prefix = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val nonce = Nonce(prefix, 0)
        val result = nonce.increment(true)
        assertThat(result.copyOfRange(0, 8)).isEqualTo(prefix)
    }

    @Test
    @DisplayName("different directions produce different nonces for same sqn")
    fun directionDifference() {
        val nonce1 = Nonce(ByteArray(8), 0)
        val nonce2 = Nonce(ByteArray(8), 0)
        val podReceiving = nonce1.increment(true)
        val podSending = nonce2.increment(false)
        assertThat(podReceiving).isNotEqualTo(podSending)
        assertThat(podReceiving[8]).isNotEqualTo(podSending[8])
    }

    @Test
    @DisplayName("sequential increments produce unique nonces")
    fun sequentialUnique() {
        val nonce = Nonce(ByteArray(8), 0)
        val seen = mutableSetOf<List<Byte>>()
        for (i in 0 until 10) {
            val result = nonce.increment(i % 2 == 0)
            assertThat(seen.add(result.toList())).isTrue()
        }
    }
}
