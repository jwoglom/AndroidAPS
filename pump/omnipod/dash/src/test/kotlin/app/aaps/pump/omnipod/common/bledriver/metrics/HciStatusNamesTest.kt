package app.aaps.pump.omnipod.common.bledriver.metrics

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HciStatusNamesTest {

    @Test fun `known codes resolve to canonical names`() {
        assertThat(HciStatusNames.lookup(0x00)).isEqualTo("SUCCESS")
        assertThat(HciStatusNames.lookup(0x08)).isEqualTo("CONNECTION_TIMEOUT")
        assertThat(HciStatusNames.lookup(0x13)).isEqualTo("REMOTE_USER_TERMINATED_CONNECTION")
        assertThat(HciStatusNames.lookup(0x16)).isEqualTo("CONNECTION_TERMINATED_BY_LOCAL_HOST")
        assertThat(HciStatusNames.lookup(0x22)).isEqualTo("LMP_RESPONSE_TIMEOUT")
        assertThat(HciStatusNames.lookup(0x3E)).isEqualTo("CONNECTION_FAILED_TO_BE_ESTABLISHED")
        assertThat(HciStatusNames.lookup(0x85)).isEqualTo("ERROR")
    }

    @Test fun `unknown codes return UNKNOWN_0xNN`() {
        assertThat(HciStatusNames.lookup(0xAB)).isEqualTo("UNKNOWN_0xAB")
    }

    @Test fun `null returns UNSET`() {
        assertThat(HciStatusNames.lookup(null)).isEqualTo("UNSET")
    }
}
