package app.aaps.pump.omnipod.common.bledriver.comm.io

import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CharacteristicType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests CharacteristicType — UUID mapping for CMD and DATA characteristics.
 */
class CharacteristicTypeTest {

    @Test
    @DisplayName("CMD value is correct UUID string")
    fun cmdValue() {
        assertThat(CharacteristicType.CMD.value).isEqualTo("1a7e2441-e3ed-4464-8b7e-751e03d0dc5f")
    }

    @Test
    @DisplayName("DATA value is correct UUID string")
    fun dataValue() {
        assertThat(CharacteristicType.DATA.value).isEqualTo("1a7e2442-e3ed-4464-8b7e-751e03d0dc5f")
    }

    @Test
    @DisplayName("byValue finds CMD")
    fun byValueCmd() {
        assertThat(CharacteristicType.byValue("1a7e2441-e3ed-4464-8b7e-751e03d0dc5f"))
            .isEqualTo(CharacteristicType.CMD)
    }

    @Test
    @DisplayName("byValue finds DATA")
    fun byValueData() {
        assertThat(CharacteristicType.byValue("1a7e2442-e3ed-4464-8b7e-751e03d0dc5f"))
            .isEqualTo(CharacteristicType.DATA)
    }

    @Test
    @DisplayName("byValue with unknown UUID throws IllegalArgumentException")
    fun byValueUnknown() {
        assertThrows<IllegalArgumentException> {
            CharacteristicType.byValue("00001234-0000-1000-8000-00805f9b34fb")
        }
    }

    @Test
    @DisplayName("uuid property produces valid UUID")
    fun uuidProperty() {
        val cmdUuid = CharacteristicType.CMD.uuid
        assertThat(cmdUuid.toString()).isEqualTo("1a7e2441-e3ed-4464-8b7e-751e03d0dc5f")
    }
}
