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
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.common.defs.BolusData
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.common.defs.PumpDriverMode
import app.aaps.pump.common.defs.PumpUpdateFragmentType
import app.aaps.pump.common.defs.TempBasalPair
import app.aaps.pump.common.driver.connector.commands.data.CustomCommandTypeInterface
import app.aaps.pump.common.events.EventPumpFragmentValuesChanged
import app.aaps.pump.tandem.common.data.defs.TandemPumpApiVersion
import app.aaps.pump.tandem.common.driver.connector.def.TandemCustomCommand
import app.aaps.pump.tandem.common.driver.connector.def.TandemCustomCommand.*
import app.aaps.pump.tandem.common.driver.connector.response.AlarmStatusDto
import app.aaps.pump.tandem.common.driver.connector.response.AlertStatusDto
import app.aaps.pump.tandem.common.driver.connector.response.PumpVersionDto
import app.aaps.pump.tandem.common.keys.TandemStringPreferenceKey
import app.aaps.pump.tandem.common.util.TandemPumpConst
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
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
    var preferences: Preferences,
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



    //@Suppress("PropertyName")
    // @Suppress("PropertyName")
    // val TAG= LTag.PUMPCOMM

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

        aapsLogger.debug(TAG, "getConnector for ${commandType}")

        when(commandType) {
            PumpCommandType.GetTemporaryBasal,
            PumpCommandType.SetTemporaryBasal,
            PumpCommandType.CancelTemporaryBasal,
            PumpCommandType.CustomCommand,
            PumpCommandType.GetBasalProfile,
            PumpCommandType.SetBasalProfile,
            PumpCommandType.GetSettings,
            PumpCommandType.GetPumpStatus,
            PumpCommandType.GetRemainingInsulin,
            PumpCommandType.GetTime,
            PumpCommandType.SetTime,
            PumpCommandType.GetBolus,
            PumpCommandType.SetBolus,
            PumpCommandType.CancelBolus,
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
                if (responseData.value!=null) {
                    val tbr = responseData.value as TempBasalPair
                    tandemPumpStatus.currentTempBasal = tbr
                }
            }
            PumpCommandType.GetBolus -> {
                if (responseData.value!=null) {
                    val bolusData = responseData.value as BolusData
                    tandemPumpStatus.tandemLastBolus = bolusData
                    aapsLogger.debug(TAG, "Last Bolus Data: $bolusData")
                }
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
                    preferences.put(TandemStringPreferenceKey.PumpSerial, "" + tandemPumpStatus.serialNumber)
                    rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Configuration))
                }
            }
            GET_ALARMS -> {
                val alarms = responseData.value as AlarmStatusDto
                tandemPumpStatus.tandemAlarms = alarms.alarms
                if (!alarms.alarms.isEmpty()) {
                    tandemPumpStatus.semaphoreNotifications = true
                    tandemPumpStatus.semaphoreNeedsRefresh = true
                }
            }
            GET_ALERTS -> {
                val alerts = responseData.value as AlertStatusDto
                tandemPumpStatus.tandemAlerts = alerts.alerts
                // TODO extend GetAlerts functionality to filter auto confirmed entries
                if (!alerts.alerts.isEmpty()) {
                    // TODO needs to take in account anything that is self dismissed
                    tandemPumpStatus.semaphoreNotifications = true
                    tandemPumpStatus.semaphoreNeedsRefresh = true
                }

                // TODO only start if configuration enabled...
                if (alerts.alerts.contains(AlertStatusResponse.AlertResponseType.MIN_BASAL_ALERT2)) {
                    executeCustomCommand(DISMISS_ALERT,
                                         AlertStatusResponse.AlertResponseType.MIN_BASAL_ALERT2.bitmask().toLong())
                }

                // LOW_POWER_ALERT     LOW_POWER_ALERT2

                // MIN_BASAL_ALERT2
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