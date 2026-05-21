package app.aaps.pump.omnipod.common.bledriver.comm.pair

import app.aaps.core.interfaces.configuration.Config
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.MessageIOException
import app.aaps.pump.omnipod.common.bledriver.pod.util.RandomByteGenerator
import app.aaps.pump.omnipod.common.bledriver.pod.util.X25519KeyGenerator
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests KeyExchange error paths — invalid payloads and CMAC mismatch.
 * These errors occur during pairing when the pod sends unexpected data.
 */
class KeyExchangeErrorTest {

    private val logger = AAPSLoggerTest()
    private val config = mock<Config>()
    private lateinit var keyExchange: KeyExchange

    @BeforeEach
    fun setUp() {
        whenever(config.DEBUG).thenReturn(false)
        keyExchange = KeyExchange(logger, config, X25519KeyGenerator(), RandomByteGenerator())
    }

    @Nested
    @DisplayName("updatePodPublicData")
    inner class UpdatePodPublicData {

        @Test
        fun `valid 48-byte payload succeeds`() {
            val payload = ByteArray(48) { it.toByte() }
            keyExchange.updatePodPublicData(payload)
            assertThat(keyExchange.podPublic).hasLength(32)
            assertThat(keyExchange.podNonce).hasLength(16)
        }

        @Test
        fun `payload too short throws MessageIOException`() {
            assertThrows<MessageIOException> {
                keyExchange.updatePodPublicData(ByteArray(32))
            }
        }

        @Test
        fun `payload too long throws MessageIOException`() {
            assertThrows<MessageIOException> {
                keyExchange.updatePodPublicData(ByteArray(64))
            }
        }

        @Test
        fun `empty payload throws MessageIOException`() {
            assertThrows<MessageIOException> {
                keyExchange.updatePodPublicData(ByteArray(0))
            }
        }
    }

    @Nested
    @DisplayName("validatePodConf")
    inner class ValidatePodConf {

        @Test
        fun `mismatched podConf throws MessageIOException`() {
            keyExchange.updatePodPublicData(ByteArray(48) { it.toByte() })

            assertThrows<MessageIOException> {
                keyExchange.validatePodConf(ByteArray(16) { 0xFF.toByte() })
            }
        }

        @Test
        fun `matching podConf succeeds`() {
            keyExchange.updatePodPublicData(ByteArray(48) { it.toByte() })
            keyExchange.validatePodConf(keyExchange.podConf)
        }
    }

    @Nested
    @DisplayName("Key generation")
    inner class KeyGeneration {

        @Test
        fun `LTK is 16 bytes after updatePodPublicData`() {
            keyExchange.updatePodPublicData(ByteArray(48) { it.toByte() })
            assertThat(keyExchange.ltk).hasLength(16)
        }

        @Test
        fun `pdmConf is 16 bytes after updatePodPublicData`() {
            keyExchange.updatePodPublicData(ByteArray(48) { it.toByte() })
            assertThat(keyExchange.pdmConf).hasLength(16)
        }

        @Test
        fun `podConf is 16 bytes after updatePodPublicData`() {
            keyExchange.updatePodPublicData(ByteArray(48) { it.toByte() })
            assertThat(keyExchange.podConf).hasLength(16)
        }

        @Test
        fun `pdmPublic is 32 bytes`() {
            assertThat(keyExchange.pdmPublic).hasLength(32)
        }

        @Test
        fun `pdmNonce is 16 bytes`() {
            assertThat(keyExchange.pdmNonce).hasLength(16)
        }

        @Test
        fun `different pod public data produces different LTK`() {
            val ke1 = KeyExchange(logger, config, X25519KeyGenerator(), RandomByteGenerator())
            val ke2 = KeyExchange(logger, config, X25519KeyGenerator(), RandomByteGenerator())

            ke1.updatePodPublicData(ByteArray(48) { it.toByte() })
            ke2.updatePodPublicData(ByteArray(48) { (it + 1).toByte() })

            assertThat(ke1.ltk).isNotEqualTo(ke2.ltk)
        }
    }
}
