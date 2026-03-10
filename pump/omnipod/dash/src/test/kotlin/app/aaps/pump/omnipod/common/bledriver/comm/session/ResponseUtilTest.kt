package app.aaps.pump.omnipod.common.bledriver.comm.session

import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.CouldNotParseResponseException
import app.aaps.pump.omnipod.common.bledriver.pod.response.AlarmStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.NakResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType
import app.aaps.pump.omnipod.common.bledriver.pod.response.SetUniqueIdResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.VersionResponse
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests ResponseUtil.parseResponse() — the dispatcher that routes raw response
 * bytes to the correct Response subclass. Every branch matters because a
 * mis-routed response could cause the app to misinterpret pod state.
 */
class ResponseUtilTest {

    @Nested
    @DisplayName("DefaultStatusResponse dispatch")
    inner class DefaultStatus {

        @Test
        fun `payload starting with 0x1D produces DefaultStatusResponse`() {
            val encoded = hexToBytes("1D1800A02800000463FF0000")
            val response = ResponseUtil.parseResponse(encoded)
            assertThat(response).isInstanceOf(DefaultStatusResponse::class.java)
        }
    }

    @Nested
    @DisplayName("NakResponse dispatch")
    inner class Nak {

        @Test
        fun `payload starting with 0x06 produces NakResponse`() {
            val encoded = hexToBytes("0603070009")
            val response = ResponseUtil.parseResponse(encoded)
            assertThat(response).isInstanceOf(NakResponse::class.java)
        }
    }

    @Nested
    @DisplayName("ActivationResponse dispatch")
    inner class Activation {

        @Test
        fun `payload 0x01 0x15 produces VersionResponse`() {
            val encoded = hexToBytes("0115040A00020701040A00020701044C0000000000000000000000000000000000000000000000")
            val response = ResponseUtil.parseResponse(encoded)
            assertThat(response).isInstanceOf(VersionResponse::class.java)
        }

        @Test
        fun `payload 0x01 0x1B produces SetUniqueIdResponse`() {
            val encoded = hexToBytes("011b13881e0f0002003c040A00020701040A0002070104001392000012AE00001092")
            val response = ResponseUtil.parseResponse(encoded)
            assertThat(response).isInstanceOf(SetUniqueIdResponse::class.java)
        }

        @Test
        fun `payload 0x01 with unknown subtype throws CouldNotParseResponseException`() {
            val encoded = hexToBytes("01FF00000000")
            assertThrows<CouldNotParseResponseException> {
                ResponseUtil.parseResponse(encoded)
            }
        }
    }

    @Nested
    @DisplayName("AdditionalStatusResponse dispatch")
    inner class AdditionalStatus {

        @Test
        fun `payload 0x02 with alarm status type 0x02 produces AlarmStatusResponse`() {
            // payload[0]=0x02 (ADDITIONAL_STATUS_RESPONSE), payload[2]=0x02 (ALARM_STATUS)
            // AlarmStatusResponse needs at least 24 bytes
            val encoded = hexToBytes("021302020000000000000000000000000000000000000000")
            val response = ResponseUtil.parseResponse(encoded)
            assertThat(response).isInstanceOf(AlarmStatusResponse::class.java)
        }

        @Test
        fun `payload 0x02 with page 1 type throws UnsupportedOperationException`() {
            // payload[2]=0x01 (STATUS_RESPONSE_PAGE_1)
            val encoded = hexToBytes("020001010000")
            assertThrows<UnsupportedOperationException> {
                ResponseUtil.parseResponse(encoded)
            }
        }

        @Test
        fun `payload 0x02 with page 3 type throws UnsupportedOperationException`() {
            // payload[2]=0x03 (STATUS_RESPONSE_PAGE_3)
            val encoded = hexToBytes("020001030000")
            assertThrows<UnsupportedOperationException> {
                ResponseUtil.parseResponse(encoded)
            }
        }

        @Test
        fun `payload 0x02 with unknown status type throws CouldNotParseResponseException`() {
            // payload[2]=0xFF (UNKNOWN)
            val encoded = hexToBytes("0200FF000000")
            assertThrows<CouldNotParseResponseException> {
                ResponseUtil.parseResponse(encoded)
            }
        }
    }

    @Nested
    @DisplayName("Unknown response type")
    inner class UnknownType {

        @Test
        fun `payload with unrecognized type byte throws CouldNotParseResponseException`() {
            val encoded = hexToBytes("FF00000000")
            assertThrows<CouldNotParseResponseException> {
                ResponseUtil.parseResponse(encoded)
            }
        }

        @Test
        fun `payload 0x00 throws CouldNotParseResponseException`() {
            val encoded = hexToBytes("0000000000")
            assertThrows<CouldNotParseResponseException> {
                ResponseUtil.parseResponse(encoded)
            }
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
