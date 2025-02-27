package app.aaps.pump.tandem.common.process.change_cartridge

import app.aaps.pump.tandem.common.process.PumpManagementAction

enum class ChangeCartridgePumpMgmtAction : PumpManagementAction {
    CHECK_PUMP_STATE,
    SUSPEND_PUMP,
    ENTER_CHANGE_CARTRIDGE_MODE,
    EXIT_CHANGE_CARTRIDGE_MODE
    ;

    override fun getKey() = name

}