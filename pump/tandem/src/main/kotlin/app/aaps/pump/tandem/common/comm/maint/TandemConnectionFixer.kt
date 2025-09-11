package app.aaps.pump.tandem.common.comm.maint

import app.aaps.pump.tandem.common.driver.connector.TandemPumpConnector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TandemConnectionFixer @Inject constructor(
    tandemPumpConnector: TandemPumpConnector,
    
){

    var running = false

    fun startConnectionFix() {

        if (running)
            return;

        do {





        } while (running)

    }


    fun scheduleNextRetry() {

    }



}