package app.aaps.pump.tandem.common.service

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.pump.common.defs.PumpUpdateFragmentType
import app.aaps.pump.common.events.EventPumpFragmentValuesChanged
import app.aaps.pump.tandem.R
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.driver.connector.TandemPumpConnector
import app.aaps.pump.tandem.common.util.TandemPumpConst
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import app.aaps.pump.tandem.t_mobi.TandemMobiPumpPlugin
import dagger.android.DaggerService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TandemService : DaggerService() {

    @Inject lateinit var tandemMobiPumpPlugin: TandemMobiPumpPlugin
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var tandemPumpConnector: TandemPumpConnector
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var tandemUtil: TandemPumpUtil
    @Inject lateinit var pumpStatus: TandemPumpStatus


    inner class LocalBinder : Binder() {
        val serviceInstance: TandemService
            get() = this@TandemService
    }

    var pumpAddress: String? = null

    private val mBinder: IBinder = LocalBinder()

    var configurationValid : Boolean = false
    var connected: Boolean = false
    var isInitialized = connected && configurationValid


    override fun onCreate() {
        super.onCreate()
        aapsLogger.debug(LTag.PUMPCOMM, "Tandem Service newly created")
    }


    fun validateParameters(): Boolean {

        pumpAddress = sp.getString(TandemPumpConst.Prefs.PumpAddress, "")

        val pumpBondStatus = sp.getInt(TandemPumpConst.Prefs.PumpPairStatus, -1)

        aapsLogger.debug(LTag.PUMP, "Pump Mobi [address=$pumpAddress,bondStatus=$pumpBondStatus]")

        val pumpConfigured = (!pumpAddress!!.isEmpty() &&
            pumpBondStatus == 100 &&
            !tandemUtil.preventConnect)

        aapsLogger.debug(LTag.PUMP, "Service: Validation of parameters - Pump Configured: $pumpConfigured")

        if (!pumpConfigured) {
            pumpStatus.errorDescription = rh.gs(R.string.tandem_error_not_bonded)
            rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Full))
        } else {
            if (!pumpStatus.errorDescription.isNullOrEmpty()) {
                pumpStatus.errorDescription = null
                rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Configuration))
            }
        }

        this.configurationValid = pumpConfigured

        aapsLogger.info(LTag.PUMP, "SRV: configurationValid=$configurationValid")

        return pumpConfigured

    }




    fun connectToPump(): Boolean {
        if (!this.tandemPumpConnector.isConnected()) {
            val status = this.tandemPumpConnector.connectToPump()
            aapsLogger.info(LTag.PUMP, "SRV: connected=$status")
            return status
        } else {
            return true;
        }
    }

    fun disconnectFromPump() {
        if (this.tandemPumpConnector.isConnected()) {
            val status = this.tandemPumpConnector.disconnectFromPump()
            aapsLogger.info(LTag.PUMP, "SRV: disconnectFromPump: connected=$status")
        }
    }

    override fun onBind(p0: Intent?): IBinder {
        return mBinder
    }
}