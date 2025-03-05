package app.aaps.pump.common.defs

import com.google.gson.annotations.Expose

open class TempBasalPair constructor(
    @Expose var insulinRate : Double,
    @Expose var isPercent : Boolean,
    @Expose var durationMinutes : Int,
    var start: Long? = null
) {

    private var end: Long? = null
    var isActive: Boolean = false


    init {
        if (start!=null) {
            this.end = start!! + (durationMinutes * 60 * 1000)
        }
    }



    // constructor(insulinRate: Double, isPercent: Boolean, durationMinutes: Int) {
    //     this.insulinRate = insulinRate
    //     this.isPercent = isPercent
    //     this.durationMinutes = durationMinutes
    // }

    fun setStartTime(startTime: Long?) {
        start = startTime
    }

    fun setEndTime(endTime: Long?) {
        end = endTime
    }

    override fun toString(): String {
        return ("TempBasalPair [" + "Rate=" + insulinRate + ", DurationMinutes=" + durationMinutes + ", IsPercent="
            + isPercent + "]")
    }
}