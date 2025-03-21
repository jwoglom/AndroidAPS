package app.aaps.pump.tandem.common.driver.connector

import android.content.Context
import app.aaps.pump.common.driver.connector.mgr.PumpConnectionManager
import app.aaps.pump.common.defs.PumpConfigurationTypeInterface
import app.aaps.pump.common.driver.connector.commands.data.AdditionalResponseDataInterface
import app.aaps.pump.common.driver.connector.commands.response.DataCommandResponse
import app.aaps.pump.common.driver.connector.PumpConnectorInterface
import app.aaps.pump.common.driver.connector.PumpDummyConnector
import app.aaps.pump.common.driver.connector.defs.PumpCommandType
import app.aaps.pump.tandem.common.comm.TandemDataConverter
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import app.aaps.pump.common.defs.PumpDriverState
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.common.defs.PumpDriverMode
import app.aaps.pump.common.defs.TempBasalPair
import app.aaps.pump.common.driver.connector.commands.data.CustomCommandTypeInterface
import app.aaps.pump.tandem.common.data.defs.TandemPumpApiVersion
import app.aaps.pump.tandem.common.driver.connector.def.TandemCustomCommand
import app.aaps.pump.tandem.common.driver.connector.def.TandemCustomCommand.*
import app.aaps.pump.tandem.common.driver.connector.response.PumpVersionDto
import app.aaps.pump.tandem.common.util.TandemPumpConst
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TandemPumpConnectionManager @Inject constructor(
    val tandemPumpStatus: TandemPumpStatus,
    val tandemPumpUtil: TandemPumpUtil,
    sp: SP,
    //injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rxBus: RxBus,
    context: Context,
    val tandemDataConverter: TandemDataConverter,
    val tandemConnector: TandemPumpConnector
): PumpConnectionManager(tandemPumpStatus, tandemPumpUtil, sp, aapsLogger, rxBus, context) {


    //     : PumpConnectorInterface
    //private val selectedConnector: PumpConnectorInterface

    private val dummyConnector: PumpConnectorInterface

    //private val mobiPumpStatus : TandemMobiPumpStatus = pumpStatus as TandemMobiPumpStatus

    //private val tandemConnector: TandemPumpConnector

    private val disposable = CompositeDisposable()
    //private var oldFirmware: TandemPumpApiVersion? = null
    //private var currentFirmware: TandemPumpApiVersion? = null
    //var inConnectMode = false
    //var inDisconnectMode = false



    //val TAG = LTag.PUMPCOMM

    //var lateinit tandemCommunicationManager: TandemCommunicationManager

    // var deviceMac: String? = null
    // var deviceBonded: Boolean = false

    override fun connectToPump(): Boolean {

        aapsLogger.info(LTag.PUMPCOMM, "DUB connectToPump")

        if (inConnectMode) {
            return false;
        }

        if (this.tandemConnector.isConnected()) {
            return true
        }

        inConnectMode = true

        // TODO handle states
        aapsLogger.debug(TAG, "!!!!!! Connect to Pump")
        pumpUtil.driverStatus = PumpDriverState.Connecting
        // pumpUtil.sleepSeconds(15)

        val connected = tandemConnector.connectToPump()

        if (connected) {
            pumpUtil.driverStatus = PumpDriverState.Connected
            //pumpUtil.driverStatus = PumpDriverState.Ready
        } else {
            pumpUtil.driverStatus = PumpDriverState.ErrorCommunicatingWithPump
        }

        inConnectMode = false

        return connected
    }



    override fun determineFirmwareVersion() {
        if (tandemPumpStatus.pumpDriverMode== PumpDriverMode.Demo) {
            tandemPumpStatus.tandemPumpFirmware = TandemPumpApiVersion.VERSION_3_5_MOBI;
        }
        // TODO Tandem
    }



    override fun disconnectFromPump(): Boolean {

        aapsLogger.debug(TAG, "DUB Disconnect from Pump")

        tandemPumpStatus.setLastCommunicationToNow()
        pumpUtil.driverStatus = PumpDriverState.Disconnected

        if (tandemPumpStatus.pumpDriverMode== PumpDriverMode.Demo) {
            aapsLogger.debug(TAG, "disconnect from Pump - DummyConnector")
            return dummyConnector.disconnectFromPump();
        }

        return tandemConnector.disconnectFromPump()
    }


    override fun setCurrentPumpCommandType(commandType: PumpCommandType, customCommandType: CustomCommandTypeInterface?) {
        pumpUtil.currentCommand = commandType
        pumpUtil.customCommandType = customCommandType
    }

    override fun resetDriverStatus() {
        tandemPumpUtil.resetDriverStatusToConnected()
    }

    override fun getConnector(commandType: PumpCommandType?): PumpConnectorInterface {

        aapsLogger.debug(TAG, "TANDEMDBG: getConnector for ${commandType}")

        // TODO extend this when new commands are enabled
        when(commandType) {
            PumpCommandType.GetTemporaryBasal,
            PumpCommandType.SetTemporaryBasal,
            PumpCommandType.CustomCommand,
            PumpCommandType.GetBasalProfile,
            PumpCommandType.SetBasalProfile,
            PumpCommandType.GetSettings,
            PumpCommandType.GetPumpStatus,
            PumpCommandType.GetRemainingInsulin,
            PumpCommandType.GetTime,
            PumpCommandType.SetTime,
            PumpCommandType.GetBatteryStatus        -> return tandemConnector

            else                    -> return dummyConnector
        }
    }


    override fun processAdditionalResponseData(commandType: PumpCommandType, responseData: DataCommandResponse<AdditionalResponseDataInterface?>) {
        when(commandType) {
            PumpCommandType.CancelTemporaryBasal -> {
                tandemPumpStatus.clearTbr()
            }
            PumpCommandType.SetTemporaryBasal -> {
                val tbr = responseData.value as TempBasalPair  // TODO fix
                tandemPumpStatus.currentTempBasal = tbr
            }
            PumpCommandType.GetTemporaryBasal -> {
                val tbr = responseData.value as TempBasalPair
                tandemPumpStatus.currentTempBasal = tbr
            }
            else -> {}
        }
    }


    override fun postProcessCustomCommand(command: CustomCommandTypeInterface, responseData: DataCommandResponse<AdditionalResponseDataInterface?>) {
        val tandemCustomCommand = command as TandemCustomCommand

        when(tandemCustomCommand) {
            GET_PUMP_INFO -> {
                tandemPumpStatus.tandemPumpVersion = responseData.value as PumpVersionDto
                if (tandemPumpStatus.serialNumber==0L) {
                    tandemPumpStatus.serialNumber = tandemPumpStatus.tandemPumpVersion!!.serialNum
                    sp.putString(TandemPumpConst.Prefs.PumpSerial, "" + tandemPumpStatus.serialNumber)
                }
            }
            else -> { }
        }

    }


    override fun postProcessConfiguration(valueMap: MutableMap<PumpConfigurationTypeInterface, Any>?) {
        if (valueMap!=null) {
            for (entry in valueMap.entries) {
                aapsLogger.debug(TAG, "Settings ${entry.key} = ${entry.value}")
            }

            tandemPumpStatus.settings = valueMap

        } else {
            aapsLogger.warn(TAG, "No settings found.")
        }
    }

    fun isConnected(): Boolean {
        return tandemConnector.isConnected()
    }

    init {
        // TODO TandemPumpConnectionManager - remove dummyConnector when not needed anymore
        dummyConnector = PumpDummyConnector(pumpStatus, pumpUtil, /*injector,*/ aapsLogger)
    }
}