package app.aaps.pump.tandem.common.data

import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSegmentResponse

/**
 * Compares the segments AAPS wants written against the ones already on the pump, so an
 * unchanged profile push can be skipped.
 *
 * AAPS re-pushes a profile whenever a previous push failed to be *recorded* — a lost command
 * callback, a driver timeout — even though the write itself reached the pump. The retry is then
 * pure cost: on a Tandem Mobi each segment is its own ~1 s BLE round trip, so re-writing 16
 * segments is ~30 s during which the pump sits with a partially deleted profile (the driver
 * deletes segments 1..n-1 before writing the new ones).
 */
internal object IdpSegmentComparison {

    /**
     * Segments a Mobi IDP can hold. The driver clamps a longer profile to this, so only the first
     * [MAX_SEGMENTS] of a requested profile are ever written — and only those may be compared.
     */
    const val MAX_SEGMENTS = 16

    /**
     * True when [onPump] already holds exactly what writing [requested] would produce.
     *
     * Compares only the five fields the driver actually writes (`SetIDPSegmentRequest`):
     * start time, basal rate, carb ratio, target BG and ISF. Segment index is the map key, and
     * `idpId` is a property of the profile rather than of a segment, so neither is compared.
     *
     * Conservative by construction: anything it cannot line up — differing segment counts, a
     * missing index, an empty request — returns false and the normal write proceeds.
     */
    fun pumpAlreadyHas(requested: List<IDPSegmentDto>, onPump: Map<Int, IDPSegmentResponse>): Boolean {
        val effective = requested.take(MAX_SEGMENTS)
        if (effective.isEmpty() || effective.size != onPump.size) return false
        for ((index, want) in effective.withIndex()) {
            val have = onPump[index] ?: return false
            if (want.profileStartTime != have.profileStartTime) return false
            if (want.profileBasalRate != have.profileBasalRate) return false
            if (want.profileCarbRatio != have.profileCarbRatio) return false
            if (want.profileTargetBG != have.profileTargetBG) return false
            if (want.profileISF != have.profileISF) return false
        }
        return true
    }
}
