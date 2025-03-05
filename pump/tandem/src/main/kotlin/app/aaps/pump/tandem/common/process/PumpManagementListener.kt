package app.aaps.pump.tandem.common.process

interface PumpManagementListener {

    fun sendDebugInfo(text: String)
    fun sendStatusInfo(text: String, withDelay: Boolean = false)

    fun sendLongActionComplete()

}