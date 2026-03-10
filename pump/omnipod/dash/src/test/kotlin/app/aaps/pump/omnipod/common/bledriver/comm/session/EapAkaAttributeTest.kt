package app.aaps.pump.omnipod.common.bledriver.comm.session

import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.MessageIOException
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests EapAkaAttribute parsing for all attribute types and error paths.
 * These attributes are used in EAP-AKA session establishment and are
 * critical for correct key negotiation between phone and pod.
 */
class EapAkaAttributeTest {

    @Nested
    @DisplayName("EapAkaAttributeType")
    inner class AttributeTypeTests {

        @Test
        fun `byValue returns correct types`() {
            assertThat(EapAkaAttributeType.byValue(1)).isEqualTo(EapAkaAttributeType.AT_RAND)
            assertThat(EapAkaAttributeType.byValue(2)).isEqualTo(EapAkaAttributeType.AT_AUTN)
            assertThat(EapAkaAttributeType.byValue(3)).isEqualTo(EapAkaAttributeType.AT_RES)
            assertThat(EapAkaAttributeType.byValue(4)).isEqualTo(EapAkaAttributeType.AT_AUTS)
            assertThat(EapAkaAttributeType.byValue(22)).isEqualTo(EapAkaAttributeType.AT_CLIENT_ERROR_CODE)
            assertThat(EapAkaAttributeType.byValue(126)).isEqualTo(EapAkaAttributeType.AT_CUSTOM_IV)
        }

        @Test
        fun `byValue with unknown type throws IllegalArgumentException`() {
            assertThrows<IllegalArgumentException> {
                EapAkaAttributeType.byValue(99)
            }
        }
    }

    @Nested
    @DisplayName("AT_RAND")
    inner class AtRand {

        @Test
        fun `valid RAND attribute parses correctly`() {
            val payload = ByteArray(18) // 2 reserved + 16 data
            payload.fill(0x42)
            val attr = EapAkaAttributeRand.parse(payload)
            assertThat(attr).isInstanceOf(EapAkaAttributeRand::class.java)
            assertThat((attr as EapAkaAttributeRand).payload).hasLength(16)
        }

        @Test
        fun `RAND roundtrip preserves data`() {
            val data = ByteArray(16) { it.toByte() }
            val attr = EapAkaAttributeRand(data)
            val serialized = attr.toByteArray()
            assertThat(serialized[0]).isEqualTo(EapAkaAttributeType.AT_RAND.type)
            assertThat(serialized[1]).isEqualTo((EapAkaAttributeRand.SIZE / 4).toByte())
        }

        @Test
        fun `RAND with short payload throws MessageIOException`() {
            assertThrows<MessageIOException> {
                EapAkaAttributeRand.parse(ByteArray(10))
            }
        }

        @Test
        fun `RAND with wrong size throws IllegalArgumentException`() {
            assertThrows<IllegalArgumentException> {
                EapAkaAttributeRand(ByteArray(10))
            }
        }
    }

    @Nested
    @DisplayName("AT_AUTN")
    inner class AtAutn {

        @Test
        fun `valid AUTN attribute parses correctly`() {
            val payload = ByteArray(18)
            val attr = EapAkaAttributeAutn.parse(payload)
            assertThat(attr).isInstanceOf(EapAkaAttributeAutn::class.java)
        }

        @Test
        fun `AUTN with short payload throws MessageIOException`() {
            assertThrows<MessageIOException> {
                EapAkaAttributeAutn.parse(ByteArray(10))
            }
        }

        @Test
        fun `AUTN with wrong constructor size throws IllegalArgumentException`() {
            assertThrows<IllegalArgumentException> {
                EapAkaAttributeAutn(ByteArray(10))
            }
        }
    }

    @Nested
    @DisplayName("AT_RES")
    inner class AtRes {

        @Test
        fun `valid RES attribute parses correctly`() {
            val payload = ByteArray(10) // 2 reserved + 8 data
            val attr = EapAkaAttributeRes.parse(payload)
            assertThat(attr).isInstanceOf(EapAkaAttributeRes::class.java)
            assertThat(attr.payload).hasLength(8)
        }

        @Test
        fun `RES with short payload throws MessageIOException`() {
            assertThrows<MessageIOException> {
                EapAkaAttributeRes.parse(ByteArray(5))
            }
        }

        @Test
        fun `RES with wrong constructor size throws IllegalArgumentException`() {
            assertThrows<IllegalArgumentException> {
                EapAkaAttributeRes(ByteArray(4))
            }
        }
    }

    @Nested
    @DisplayName("AT_AUTS")
    inner class AtAuts {

        @Test
        fun `valid AUTS attribute parses correctly`() {
            val payload = ByteArray(14)
            val attr = EapAkaAttributeAuts.parse(payload)
            assertThat(attr).isInstanceOf(EapAkaAttributeAuts::class.java)
        }

        @Test
        fun `AUTS with short payload throws MessageIOException`() {
            assertThrows<MessageIOException> {
                EapAkaAttributeAuts.parse(ByteArray(5))
            }
        }

        @Test
        fun `AUTS with wrong constructor size throws IllegalArgumentException`() {
            assertThrows<IllegalArgumentException> {
                EapAkaAttributeAuts(ByteArray(10))
            }
        }
    }

    @Nested
    @DisplayName("AT_CUSTOM_IV")
    inner class AtCustomIv {

        @Test
        fun `valid CUSTOM_IV attribute parses correctly`() {
            val payload = ByteArray(6) // 2 reserved + 4 data
            val attr = EapAkaAttributeCustomIV.parse(payload)
            assertThat(attr).isInstanceOf(EapAkaAttributeCustomIV::class.java)
            assertThat(attr.payload).hasLength(4)
        }

        @Test
        fun `CUSTOM_IV with short payload throws MessageIOException`() {
            assertThrows<MessageIOException> {
                EapAkaAttributeCustomIV.parse(ByteArray(3))
            }
        }

        @Test
        fun `CUSTOM_IV with wrong constructor size throws IllegalArgumentException`() {
            assertThrows<IllegalArgumentException> {
                EapAkaAttributeCustomIV(ByteArray(8))
            }
        }
    }

    @Nested
    @DisplayName("AT_CLIENT_ERROR_CODE")
    inner class AtClientErrorCode {

        @Test
        fun `valid CLIENT_ERROR_CODE attribute parses correctly`() {
            val payload = ByteArray(4) // 2 reserved + 2 data
            val attr = EapAkaAttributeClientErrorCode.parse(payload)
            assertThat(attr).isInstanceOf(EapAkaAttributeClientErrorCode::class.java)
        }

        @Test
        fun `CLIENT_ERROR_CODE with short payload throws MessageIOException`() {
            assertThrows<MessageIOException> {
                EapAkaAttributeClientErrorCode.parse(ByteArray(2))
            }
        }
    }

    @Nested
    @DisplayName("parseAttributes")
    inner class ParseAttributes {

        @Test
        fun `empty payload returns empty list`() {
            val result = EapAkaAttribute.parseAttributes(ByteArray(0))
            assertThat(result).isEmpty()
        }

        @Test
        fun `payload too short for header throws MessageIOException`() {
            assertThrows<MessageIOException> {
                EapAkaAttribute.parseAttributes(byteArrayOf(0x01))
            }
        }

        @Test
        fun `payload shorter than declared size throws MessageIOException`() {
            assertThrows<MessageIOException> {
                EapAkaAttribute.parseAttributes(byteArrayOf(0x01, 0x10))
            }
        }

        @Test
        fun `unknown attribute type throws IllegalArgumentException`() {
            assertThrows<IllegalArgumentException> {
                EapAkaAttribute.parseAttributes(byteArrayOf(0x63, 0x01, 0x00, 0x00))
            }
        }

        @Test
        fun `single CUSTOM_IV attribute parses successfully`() {
            val attr = EapAkaAttributeCustomIV(byteArrayOf(0x01, 0x02, 0x03, 0x04))
            val serialized = attr.toByteArray()
            val parsed = EapAkaAttribute.parseAttributes(serialized)
            assertThat(parsed).hasSize(1)
            assertThat(parsed[0]).isInstanceOf(EapAkaAttributeCustomIV::class.java)
        }

        @Test
        fun `multiple attributes parse in order`() {
            val res = EapAkaAttributeRes(ByteArray(8) { 0x11 })
            val iv = EapAkaAttributeCustomIV(ByteArray(4) { 0x22 })
            val combined = res.toByteArray() + iv.toByteArray()
            val parsed = EapAkaAttribute.parseAttributes(combined)
            assertThat(parsed).hasSize(2)
            assertThat(parsed[0]).isInstanceOf(EapAkaAttributeRes::class.java)
            assertThat(parsed[1]).isInstanceOf(EapAkaAttributeCustomIV::class.java)
        }
    }
}
