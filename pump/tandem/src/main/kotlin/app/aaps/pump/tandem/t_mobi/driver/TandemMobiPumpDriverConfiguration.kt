package app.aaps.pump.tandem.t_mobi.driver

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.pump.tandem.common.driver.config.TandemBLESelector
import app.aaps.pump.tandem.common.driver.config.TandemPumpDriverConfiguration
import javax.inject.Inject

//import javax.inject.Inject

class TandemMobiPumpDriverConfiguration @Inject constructor(
                                        pumpBLESelector: TandemBLESelector,

) : TandemPumpDriverConfiguration(pumpBLESelector,PumpType.TANDEM_T_MOBI_BT) {

    override var logPrefix: String = "TandemMobiPumpPlugin::"


}


