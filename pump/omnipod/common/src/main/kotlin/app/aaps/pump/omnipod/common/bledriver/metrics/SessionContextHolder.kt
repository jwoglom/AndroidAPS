package app.aaps.pump.omnipod.common.bledriver.metrics

object SessionContextHolder {

    @Volatile private var current: SessionContext? = null

    @Volatile var lastSessionEndEpochMs: Long? = null
    @Volatile var lastSessionEndReason: String? = null

    fun current(): SessionContext? = current

    fun set(ctx: SessionContext?) { current = ctx }

    fun clearAfterEnd(reason: String) {
        current = null
        lastSessionEndEpochMs = System.currentTimeMillis()
        lastSessionEndReason = reason
    }
}
