package app.aaps.pump.tandem.common.comm.maint

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.tandem.common.comm.TandemCommunicationManager
import app.aaps.pump.tandem.common.driver.connector.TandemPumpConnector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TandemConnectionFixer @Inject constructor(
    val aapsLogger: AAPSLogger
    
){

    val TAG = LTag.PUMPBTCOMM
    var running = false

    val enabled = false

    fun startConnectionFix(communicationManager: TandemCommunicationManager) {

        if (!enabled) {
            aapsLogger.warn(TAG, "StartConnectionFix disabled - experimental code not yet ready for use")
            return
        }

        aapsLogger.error(TAG, "Start ConnectionFix")

        if (running)
            return;

        do {
            aapsLogger.error(TAG, "Start ConnectionFix - in run")

            //tandemPumpConnector
            val statusConnection  = communicationManager.connect()

            if (statusConnection) {
                running = false
            }

            Thread.sleep(60000) // wait 60 seconds and retry

        } while (running)

        aapsLogger.error(TAG, "End ConnectionFix - connection fixed")

    }


    fun scheduleNextRetry() {

    }



}