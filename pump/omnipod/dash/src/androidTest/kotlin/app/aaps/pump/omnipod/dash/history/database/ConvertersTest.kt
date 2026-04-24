package app.aaps.pump.omnipod.dash.history.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.core.interfaces.profile.Profile
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.dash.history.data.BolusType
import app.aaps.pump.omnipod.dash.history.data.InitialResult
import app.aaps.pump.omnipod.dash.history.data.ResolvedResult
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for Room TypeConverters — validates Gson serialization
 * of Profile.ProfileValue and enum roundtrips with real Android Gson runtime.
 */
@RunWith(AndroidJUnit4::class)
class ConvertersTest {

    private lateinit var converters: Converters

    @Before
    fun setUp() {
        converters = Converters()
    }

    // --- Profile.ProfileValue (Gson) ---

    @Test
    fun profileValues_emptyList_roundtrips() {
        val json = converters.fromBasalValues(emptyList())
        val result = converters.toSegments(json)
        assertThat(result).isEmpty()
    }

    @Test
    fun profileValues_singleSegment_roundtrips() {
        val segments = listOf(Profile.ProfileValue(0, 1.0))
        val json = converters.fromBasalValues(segments)
        val result = converters.toSegments(json)
        assertThat(result).hasSize(1)
        assertThat(result[0].timeAsSeconds).isEqualTo(0)
        assertThat(result[0].value).isEqualTo(1.0)
    }

    @Test
    fun profileValues_multipleSegments_roundtrips() {
        val segments = listOf(
            Profile.ProfileValue(0, 0.8),
            Profile.ProfileValue(3600, 1.2),
            Profile.ProfileValue(7200, 0.5),
            Profile.ProfileValue(86400, 1.0)
        )
        val json = converters.fromBasalValues(segments)
        val result = converters.toSegments(json)
        assertThat(result).hasSize(4)
        assertThat(result[0].value).isEqualTo(0.8)
        assertThat(result[1].timeAsSeconds).isEqualTo(3600)
        assertThat(result[3].value).isEqualTo(1.0)
    }

    @Test
    fun profileValues_nullJson_returnsEmptyList() {
        val result = converters.toSegments(null)
        assertThat(result).isEmpty()
    }

    @Test
    fun profileValues_verySmallRates_preservePrecision() {
        val segments = listOf(Profile.ProfileValue(0, 0.025))
        val json = converters.fromBasalValues(segments)
        val result = converters.toSegments(json)
        assertThat(result[0].value).isEqualTo(0.025)
    }

    // --- BolusType ---

    @Test
    fun bolusType_default_roundtrips() {
        val str = converters.fromBolusType(BolusType.DEFAULT)
        assertThat(converters.toBolusType(str)).isEqualTo(BolusType.DEFAULT)
    }

    @Test
    fun bolusType_smb_roundtrips() {
        val str = converters.fromBolusType(BolusType.SMB)
        assertThat(converters.toBolusType(str)).isEqualTo(BolusType.SMB)
    }

    // --- InitialResult ---

    @Test
    fun initialResult_allValues_roundtrip() {
        for (value in InitialResult.entries) {
            val str = converters.fromInitialResult(value)
            assertThat(converters.toInitialResult(str)).isEqualTo(value)
        }
    }

    // --- ResolvedResult ---

    @Test
    fun resolvedResult_allValues_roundtrip() {
        for (value in ResolvedResult.entries) {
            val str = converters.fromResolvedResult(value)
            assertThat(converters.toResolvedResult(str)).isEqualTo(value)
        }
    }

    @Test
    fun resolvedResult_null_roundtrips() {
        val str = converters.fromResolvedResult(null)
        assertThat(str).isNull()
        assertThat(converters.toResolvedResult(null)).isNull()
    }

    // --- OmnipodCommandType ---

    @Test
    fun omnipodCommandType_allValues_roundtrip() {
        for (value in OmnipodCommandType.entries) {
            val str = converters.fromOmnipodCommandType(value)
            assertThat(converters.toOmnipodCommandType(str)).isEqualTo(value)
        }
    }
}
