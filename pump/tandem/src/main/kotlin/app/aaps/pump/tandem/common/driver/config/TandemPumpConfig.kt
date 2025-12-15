package app.aaps.pump.tandem.common.driver.config

// TODO: is the PIN saved correctly?
class TandemPumpConfig constructor(var isMobi: Boolean) {

    companion object {

        @JvmStatic
        val pumpPin: String
            get() = ""

    }



}