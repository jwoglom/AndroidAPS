package app.aaps.pump.tandem.common.comm.maint

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.tandem.common.comm.TandemCommunicationManager
import app.aaps.pump.tandem.common.driver.connector.TandemPumpConnector
import app.aaps.pump.tandem.mobi.TandemMobiPluginVersion
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TandemConnectionFixer @Inject constructor(
    val aapsLogger: AAPSLogger
    
){

    val TAG = LTag.PUMPBTCOMM
    var running = false

    val enabled = TandemMobiPluginVersion.connectionFixerEnabled

    fun startConnectionFix(communicationManager: TandemCommunicationManager) {
        if (!enabled) {
            aapsLogger.warn(TAG, "CF: StartConnectionFix disabled - experimental code not yet ready for use")
            return
        }

        aapsLogger.error(TAG, "CF: Start ConnectionFix")

        if (running)
            return;

        running = true

        Thread {
            do {
                aapsLogger.error(TAG, "CF: Start ConnectionFix - in run")
                communicationManager.pumpUtil.driverStatus = app.aaps.pump.common.defs.PumpDriverState.Connecting
                communicationManager.rxBus.send(
                    app.aaps.pump.common.events.EventPumpFragmentValuesChanged(
                        app.aaps.pump.common.defs.PumpUpdateFragmentType.None
                    )
                )

                val statusConnection = communicationManager.connect()

                if (statusConnection) {
                    communicationManager.pumpUtil.driverStatus = app.aaps.pump.common.defs.PumpDriverState.Connected
                    communicationManager.rxBus.send(
                        app.aaps.pump.common.events.EventPumpFragmentValuesChanged(
                            app.aaps.pump.common.defs.PumpUpdateFragmentType.None
                        )
                    )
                    running = false
                } else {
                    communicationManager.pumpUtil.driverStatus =
                        app.aaps.pump.common.defs.PumpDriverState.ErrorCommunicatingWithPump
                    communicationManager.rxBus.send(
                        app.aaps.pump.common.events.EventPumpFragmentValuesChanged(
                            app.aaps.pump.common.defs.PumpUpdateFragmentType.None
                        )
                    )
                    Thread.sleep(60000) // wait 60 seconds and retry
                }
            } while (running)

            aapsLogger.error(TAG, "CF: End ConnectionFix - connection fixed")
        }.start()

    }


    fun scheduleNextRetry() {

    }



}
