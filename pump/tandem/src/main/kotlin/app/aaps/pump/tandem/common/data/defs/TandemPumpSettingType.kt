package app.aaps.pump.tandem.common.data.defs

import app.aaps.pump.common.defs.PumpConfigurationTypeInterface

enum class TandemPumpSettingType : PumpConfigurationTypeInterface {
    CONTROL_IQ_ENABLED,
    BASAL_LIMIT,
    MAX_BOLUS,
    BASAL_IQ_ENABLED


    ;

    override fun getKey() = name

}