package app.aaps.pump.tandem.t_mobi.mgr

import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import javax.inject.Inject
import javax.inject.Singleton

// this class is intended for any mobi specific actions (priming, fill canual, etc)
@Singleton
class TandemMobiManager @Inject constructor(
    val pumpStatus: TandemPumpStatus
) {





}