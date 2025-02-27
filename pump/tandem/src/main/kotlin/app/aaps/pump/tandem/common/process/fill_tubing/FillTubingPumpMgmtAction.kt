package app.aaps.pump.tandem.common.process.fill_tubing

import app.aaps.pump.tandem.common.process.PumpManagementAction

enum class FillTubingPumpMgmtAction : PumpManagementAction {
    CHECK_PUMP_STATE,
    SUSPEND_PUMP,
    ENTER_CHANGE_CARTRIDGE_MODE,
    EXIT_CHANGE_CARTRIDGE_MODE
    ;

    // TODO

    override fun getKey() = name

}