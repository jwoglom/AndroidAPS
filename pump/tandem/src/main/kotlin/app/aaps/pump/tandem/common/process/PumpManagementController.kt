package app.aaps.pump.tandem.common.process

interface PumpManagementController {

    fun startShortAction(action: PumpManagementAction): Any?
    fun startLongAction(action: PumpManagementAction)

    fun startOperations(pumpManagementListener: PumpManagementListener)
    fun endOperations()


}