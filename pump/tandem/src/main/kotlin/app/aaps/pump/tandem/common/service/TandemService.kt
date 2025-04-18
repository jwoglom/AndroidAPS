package app.aaps.pump.tandem.common.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.pump.common.defs.PumpUpdateFragmentType
import app.aaps.pump.common.events.EventPumpFragmentValuesChanged
import app.aaps.pump.tandem.R
import app.aaps.pump.tandem.common.comm.defs.PumpStateX2
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.driver.connector.TandemPumpConnectionManager
import app.aaps.pump.tandem.common.util.TandemPumpConst
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import app.aaps.pump.tandem.t_mobi.TandemMobiPumpPlugin
import com.jwoglom.pumpx2.pump.PumpState
import dagger.android.DaggerService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TandemService : DaggerService() {

    @Inject lateinit var tandemMobiPumpPlugin: TandemMobiPumpPlugin
    @Inject lateinit var aapsLogger: AAPSLogger
    //@Inject lateinit var tandemPumpConnector: TandemPumpConnector
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var tandemUtil: TandemPumpUtil
    @Inject lateinit var pumpStatus: TandemPumpStatus
    @Inject lateinit var context: Context
    @Inject lateinit var blePreCheck: BlePreCheck
    @Inject lateinit var tandemPumpConnectionManager: TandemPumpConnectionManager


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


    fun checkPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permission = ContextCompat.checkSelfPermission(getApplicationContext(),
                                                               Manifest.permission.BLUETOOTH_SCAN)
            //return permission == PackageManager.PERMISSION_GRANTED;
        }
    }


    fun validateParameters(): Boolean {

        checkPermission()

        var pumpConfigured = false

        val useSharedConnection: Boolean = sp.getBoolean(TandemPumpConst.Prefs.UseSharedConnection, true)

        if (useSharedConnection) {
            aapsLogger.info(LTag.PUMP, "PumpConfig: Shared Connection Use")

            val sharedConnectionString = sp.getString(TandemPumpConst.Prefs.SharedConnectionData, "")
            var notFound = false

            aapsLogger.info(LTag.PUMP, "PumpConfig: Shared Connection: ${sharedConnectionString}")

            if (sharedConnectionString.isNullOrEmpty()) {
                notFound = true
                aapsLogger.error(LTag.PUMP, "PumpConfig: Shared Connection Use: Data empty")
            } else {

                val sharedConnectionData : PumpStateX2 = tandemUtil.gson.fromJson(sharedConnectionString, PumpStateX2::class.java)

                if (sharedConnectionData.jpakeDerivedSecret.isEmpty()) {
                    notFound = true
                    aapsLogger.error(LTag.PUMP, "PumpConfig: Shared Connection Use: Data NOT Valid")
                } else {

                    if (isSharedConfigurationAlreadyApplied(sharedConnectionData)) {
                        aapsLogger.info(LTag.PUMP, "PumpConfig: Shared Connection looks like it is the same. No setting of this information.")
                    } else {
                        aapsLogger.info(LTag.PUMP, "PumpConfig: Setting Shared Connection Data. NEW")

                        sp.putInt(TandemPumpConst.Prefs.PumpPairStatus, 100)
                        sp.putString(TandemPumpConst.Prefs.PumpAddress, sharedConnectionData.savedBluetoothMAC)
                        sp.getString(TandemPumpConst.Prefs.PumpPairCode, sharedConnectionData.pairingCode)

                        if (!sharedConnectionData.pumpSerialNum.isNullOrEmpty()) {
                            sp.putString(TandemPumpConst.Prefs.PumpSerial, "" + sharedConnectionData.pumpSerialNum)
                            pumpStatus.serialNumber = (sharedConnectionData.pumpSerialNum.toLong())
                        }

                        PumpState.importState(context, sharedConnectionString)
                    }
                }
            } // else

            if (notFound) {
                pumpStatus.errorDescription = rh.gs(R.string.tandem_error_not_bonded)
                rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.None))
                return false
            }
        }


        pumpAddress = sp.getString(TandemPumpConst.Prefs.PumpAddress, "")

        val pumpBondStatus = sp.getInt(TandemPumpConst.Prefs.PumpPairStatus, -1)

        aapsLogger.debug(LTag.PUMP, "PumpConfig: Pump Mobi [address=$pumpAddress,bondStatus=$pumpBondStatus]")

        pumpConfigured = (!pumpAddress!!.isEmpty() &&
            pumpBondStatus == 100 &&
            !tandemUtil.preventConnect)

        aapsLogger.debug(LTag.PUMP, "Service: Validation of parameters - Pump Configured: $pumpConfigured")

        if (!pumpConfigured) {
            pumpStatus.errorDescription = rh.gs(R.string.tandem_error_not_bonded)
            rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.None))
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

    fun isSharedConfigurationAlreadyApplied(sharedConnectionData: PumpStateX2): Boolean {

        // check our internal stuff
        val address = sp.getStringOrNull(TandemPumpConst.Prefs.PumpAddress, null)
        val pairCode = sp.getStringOrNull(TandemPumpConst.Prefs.PumpPairCode, null)

        return (sp.getInt(TandemPumpConst.Prefs.PumpPairStatus, 0)==100 &&
            address!=null && address.equals(sharedConnectionData.savedBluetoothMAC) &&
            pairCode!=null && pairCode.equals(sharedConnectionData.pairingCode) &&
            PumpState.getJpakeDerivedSecret(context).equals(sharedConnectionData.jpakeDerivedSecret) &&
            PumpState.getJpakeServerNonce(context).equals(sharedConnectionData.jpakeServerNonce) &&
            PumpState.getSavedBluetoothMAC(context).equals(sharedConnectionData.savedBluetoothMAC) &&
            PumpState.getPairingCode(context).equals(sharedConnectionData.pairingCode)
            //PumpState.getPumpAPIVersion(context).equals(sharedConnectionData.savedBluetoothMAC) &&
            )
    }






    fun connectToPump(): Boolean {
        if (!this.tandemPumpConnectionManager.isConnected()) {
            val status = this.tandemPumpConnectionManager.connectToPump()
            aapsLogger.info(LTag.PUMP, "SRV: connected=$status")
            return status
        } else {
            return true;
        }
    }

    fun disconnectFromPump() {
        if (this.tandemPumpConnectionManager.isConnected()) {
            val status = this.tandemPumpConnectionManager.disconnectFromPump()
            aapsLogger.info(LTag.PUMP, "SRV: disconnectFromPump: connected=$status")
        }
    }


    fun isConnected(): Boolean  {
        return this.tandemPumpConnectionManager.isConnected()
    }

    override fun onBind(p0: Intent?): IBinder {
        return mBinder
    }
}