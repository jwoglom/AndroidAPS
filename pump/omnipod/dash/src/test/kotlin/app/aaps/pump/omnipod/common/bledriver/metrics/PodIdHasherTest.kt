package app.aaps.pump.omnipod.common.bledriver.metrics

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PodIdHasherTest {

    @Test fun `hashPodId returns 8 lowercase hex chars`() {
        val h = PodIdHasher.hashPodId(123456789L)!!
        assertThat(h).hasLength(8)
        assertThat(h).matches(Regex("^[0-9a-f]{8}$").pattern)
    }

    @Test fun `hashPodId is stable for the same input`() {
        assertThat(PodIdHasher.hashPodId(42L)).isEqualTo(PodIdHasher.hashPodId(42L))
    }

    @Test fun `hashPodId differs for different inputs`() {
        assertThat(PodIdHasher.hashPodId(1L)).isNotEqualTo(PodIdHasher.hashPodId(2L))
    }

    @Test fun `hashPodId returns null for null input`() {
        assertThat(PodIdHasher.hashPodId(null)).isNull()
    }

    @Test fun `hashMac normalizes case`() {
        assertThat(PodIdHasher.hashMac("AA:BB:CC:DD:EE:FF"))
            .isEqualTo(PodIdHasher.hashMac("aa:bb:cc:dd:ee:ff"))
    }

    @Test fun `hashMac returns null for null input`() {
        assertThat(PodIdHasher.hashMac(null)).isNull()
    }
}
