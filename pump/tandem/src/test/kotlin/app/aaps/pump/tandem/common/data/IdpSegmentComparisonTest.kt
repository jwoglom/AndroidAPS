package app.aaps.pump.tandem.common.data

import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSegmentResponse
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The guard that lets a profile push skip its delete+write phase. It gates ~30 s of BLE and a
 * window in which the pump holds a partially deleted profile, so a false positive is the dangerous
 * direction: it would leave the pump on a stale profile while AAPS reports success. Every test
 * that expects `false` is guarding that direction.
 */
class IdpSegmentComparisonTest {

    private fun dto(startTime: Int, basalRate: Int, carbRatio: Long = 12_000, targetBG: Int = 100, isf: Int = 50) =
        IDPSegmentDto().also {
            it.profileStartTime = startTime
            it.profileBasalRate = basalRate
            it.profileCarbRatio = carbRatio
            it.profileTargetBG = targetBG
            it.profileISF = isf
        }

    /** Same five fields as [dto], in the pumpx2 response's constructor order. */
    private fun onPump(
        index: Int, startTime: Int, basalRate: Int,
        carbRatio: Long = 12_000, targetBG: Int = 100, isf: Int = 50
    ) = IDPSegmentResponse(1, index, startTime, basalRate, carbRatio, targetBG, isf, 0)

    private fun pumpMap(vararg segments: IDPSegmentResponse) =
        segments.withIndex().associate { (i, s) -> i to s }

    @Test
    fun `identical profiles match`() {
        val requested = listOf(dto(0, 920), dto(120, 956), dto(180, 719))
        val pump = pumpMap(onPump(0, 0, 920), onPump(1, 120, 956), onPump(2, 180, 719))
        assertTrue(IdpSegmentComparison.pumpAlreadyHas(requested, pump))
    }

    @Test
    fun `a single differing basal rate does not match`() {
        // The scene case: same segment boundaries, every rate scaled by the percentage.
        val requested = listOf(dto(0, 644), dto(120, 956))
        val pump = pumpMap(onPump(0, 0, 920), onPump(1, 120, 956))
        assertFalse(IdpSegmentComparison.pumpAlreadyHas(requested, pump))
    }

    @Test
    fun `a differing start time does not match`() {
        val requested = listOf(dto(0, 920), dto(150, 956))
        val pump = pumpMap(onPump(0, 0, 920), onPump(1, 120, 956))
        assertFalse(IdpSegmentComparison.pumpAlreadyHas(requested, pump))
    }

    @Test
    fun `a differing carb ratio, target or ISF does not match`() {
        val pump = pumpMap(onPump(0, 0, 920))
        assertFalse(IdpSegmentComparison.pumpAlreadyHas(listOf(dto(0, 920, carbRatio = 11_000)), pump))
        assertFalse(IdpSegmentComparison.pumpAlreadyHas(listOf(dto(0, 920, targetBG = 110)), pump))
        assertFalse(IdpSegmentComparison.pumpAlreadyHas(listOf(dto(0, 920, isf = 45)), pump))
    }

    @Test
    fun `fewer segments on the pump than requested does not match`() {
        val requested = listOf(dto(0, 920), dto(120, 956))
        assertFalse(IdpSegmentComparison.pumpAlreadyHas(requested, pumpMap(onPump(0, 0, 920))))
    }

    @Test
    fun `more segments on the pump than requested does not match`() {
        // The stale-tail case: the pump keeps segments the new profile no longer has, so the
        // delete phase must still run.
        val requested = listOf(dto(0, 920))
        val pump = pumpMap(onPump(0, 0, 920), onPump(1, 120, 956))
        assertFalse(IdpSegmentComparison.pumpAlreadyHas(requested, pump))
    }

    @Test
    fun `an empty request never matches`() {
        assertFalse(IdpSegmentComparison.pumpAlreadyHas(emptyList(), emptyMap()))
        assertFalse(IdpSegmentComparison.pumpAlreadyHas(emptyList(), pumpMap(onPump(0, 0, 920))))
    }

    @Test
    fun `a gap in the pump segment indices does not match`() {
        val requested = listOf(dto(0, 920), dto(120, 956))
        val pump = mapOf(0 to onPump(0, 0, 920), 5 to onPump(5, 120, 956))
        assertFalse(IdpSegmentComparison.pumpAlreadyHas(requested, pump))
    }

    @Test
    fun `an over-long profile is compared only over the segments that would be written`() {
        // The driver clamps to MAX_SEGMENTS, so segment 17 is never written and must not be
        // allowed to force a pointless rewrite of the 16 that are already correct.
        val requested = (0 until IdpSegmentComparison.MAX_SEGMENTS + 2).map { dto(it * 60, 900 + it) }
        val pump = pumpMap(*Array(IdpSegmentComparison.MAX_SEGMENTS) { onPump(it, it * 60, 900 + it) })
        assertTrue(IdpSegmentComparison.pumpAlreadyHas(requested, pump))
    }

    @Test
    fun `an over-long profile differing inside the written range does not match`() {
        val requested = (0 until IdpSegmentComparison.MAX_SEGMENTS + 2).map { dto(it * 60, 900 + it) }
        val pump = pumpMap(*Array(IdpSegmentComparison.MAX_SEGMENTS) { onPump(it, it * 60, if (it == 3) 1 else 900 + it) })
        assertFalse(IdpSegmentComparison.pumpAlreadyHas(requested, pump))
    }
}
