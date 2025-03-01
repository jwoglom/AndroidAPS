package app.aaps.pump.tandem.common.driver.connector

import android.content.Context
import dagger.android.HasAndroidInjector
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
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.common.defs.PumpDriverMode
import app.aaps.pump.tandem.common.data.defs.TandemPumpApiVersion
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TandemPumpConnectionManager @Inject constructor(
    val tandemPumpStatus: TandemPumpStatus,
    val tandemPumpUtil: TandemPumpUtil,
    sp: SP,
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rxBus: RxBus,
    context: Context,
    val tandemDataConverter: TandemDataConverter,
    val tandemConnector: TandemPumpConnector
): PumpConnectionManager(tandemPumpStatus, tandemPumpUtil, sp, injector, aapsLogger, rxBus, context) {


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

        //pumpStatus

        tandemPumpUtil.driverStatus = PumpDriverState.Connecting


        // TODO remove when in production
        // if (tandemPumpStatus.pumpDriverMode== PumpDriverMode.Demo) {
        //     aapsLogger.debug(TAG, "Connect to Pump - Dummy")
        //     pumpUtil.driverStatus = PumpDriverState.Ready
        //     return dummyConnector.connectToPump()
        // }

        if (inConnectMode) {
            return false;
        }

        inConnectMode = true

        // TODO handle states
        aapsLogger.debug(TAG, "!!!!!! Connect to Pump")
        pumpUtil.driverStatus = PumpDriverState.Connecting
        // pumpUtil.sleepSeconds(15)

        val connected = tandemConnector.connectToPump()

        if (connected) {
            pumpUtil.driverStatus = PumpDriverState.Connected
            pumpUtil.driverStatus = PumpDriverState.Ready
        } else {
            pumpUtil.driverStatus = PumpDriverState.ErrorCommunicatingWithPump
        }

        inConnectMode = false

        return connected
    }


//     fun connectToPump(deviceMac: String, deviceBonded: Boolean): Boolean {
//
//         if (pumpUtil.driverStatus === PumpDriverState.Ready) {
//             return true
//         }
//
//         if (inConnectMode)
//             return false;
//
//         if (deviceMac.isNullOrEmpty() && !deviceBonded) {
//             return false
//         }
//
//         inConnectMode = true
//
//         // TODO
//         //val deviceMac = "EC:2A:F0:00:8B:8E"
//
//         //sp.getString()
//
//         if (!ypsoPumpBLE.startConnectToYpsoPump(deviceMac)) {
//             inConnectMode = false
//             return false
//         }
//
//         val timeoutTime = System.currentTimeMillis() + (120 * 1000)
//         var timeouted = true
//         var driverStatus: PumpDriverState?
//
//         while (System.currentTimeMillis() < timeoutTime) {
//             SystemClock.sleep(5000)
//
//             driverStatus = pumpUtil.driverStatus
//
//             aapsLogger.debug(TAGCOMM, "connectToPump: " + driverStatus.name)
//
//             if (driverStatus == PumpDriverState.Ready || driverStatus == PumpDriverState.ErrorCommunicatingWithPump) {
//                 timeouted = false
//                 break
//             }
//         }
//
//         inConnectMode = false
//         return true
//
// //         // TODO if initialized use types connection, else use base one
// //
// // //        Thread thread = new Thread() {
// // //            public void run() {
// //         println("Thread Running")
// //         aapsLogger.debug(TAG, "!!!!!! Connect to Pump - Thread running")
// //         ypsopumpUtil.driverStatus = PumpDriverState.Connecting
// //         ypsopumpUtil.sleepSeconds(15)
// //         ypsopumpUtil.driverStatus = PumpDriverState.Connected
// //         ypsopumpUtil.sleepSeconds(5)
// //         ypsopumpUtil.driverStatus = PumpDriverState.EncryptCommunication
// //         ypsopumpUtil.sleepSeconds(5)
// //         ypsopumpUtil.driverStatus = PumpDriverState.Ready
//
// //            }
// //        };
// //
// //        thread.start();
//
//     }

    //fun resetFirmwareVersion() {}

    override fun determineFirmwareVersion() {
        if (tandemPumpStatus.pumpDriverMode== PumpDriverMode.Demo) {
            tandemPumpStatus.tandemPumpFirmware = TandemPumpApiVersion.VERSION_3_5_MOBI;
        }
        // TODO Tandem
    }

    override fun processAdditionalResponseData(commandType: PumpCommandType, responseData: DataCommandResponse<AdditionalResponseDataInterface?>) {
        //TODO("Not yet implemented")
    }

    override fun disconnectFromPump(): Boolean {

        tandemPumpStatus.setLastCommunicationToNow()
        pumpUtil.driverStatus = PumpDriverState.Disconnected

        if (tandemPumpStatus.pumpDriverMode== PumpDriverMode.Demo) {
            aapsLogger.debug(TAG, "disconnect from Pump - DummyConnector")
            return dummyConnector.disconnectFromPump();
        }

        return tandemConnector.disconnectFromPump()
    }


    // fun deliverBolusddd(detailedBolusInfo: DetailedBolusInfo?): DataCommandResponse<AdditionalResponseDataInterface?> {
    //
    //     val responseData: DataCommandResponse<AdditionalResponseDataInterface?> = getConnectorData(PumpCommandType.CustomCommand)
    //     {
    //         getConnector(PumpCommandType.CustomCommand).sendBolus(detailedBolusInfo!!)
    //     }
    //
    //     checkAdditionalResponseData(PumpCommandType.Custom, responseData)
    //
    //     return responseData
    // }


    override fun setCurrentPumpCommandType(commandType: PumpCommandType) {
        pumpUtil.currentCommand = commandType
    }

    override fun resetDriverStatus() {
        tandemPumpUtil.resetDriverStatusToConnected()
    }

    override fun getConnector(commandType: PumpCommandType?): PumpConnectorInterface {

        aapsLogger.debug(TAG, "TANDEMDBG: getConnector for ${commandType}")

        // TODO remove
        if (tandemPumpStatus.pumpDriverMode == PumpDriverMode.Demo) {
            aapsLogger.debug(TAG, "TANDEMDBG: In Demo mode returning Dummy Connector for ${commandType}")
            return dummyConnector
        }

        // TODO extend this when new commands are enabled
        when(commandType) {
            // SetBasalProfile
            // GetTemporaryBasal
            // PumpCommandType.SetTemporaryBasal,
            PumpCommandType.CustomCommand,
            PumpCommandType.GetBasalProfile,
            PumpCommandType.SetBasalProfile,
            PumpCommandType.GetSettings,
            PumpCommandType.GetPumpStatus,
            PumpCommandType.GetRemainingInsulin,
            PumpCommandType.GetTime,
            PumpCommandType.SetTime,
            PumpCommandType.GetBatteryStatus          -> return tandemConnector

            else                    -> return dummyConnector
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






    init {
        // TODO TandemPumpConnectionManager - remove dummyConnector when not needed anymore
        dummyConnector = PumpDummyConnector(pumpStatus, pumpUtil, injector, aapsLogger)
    }
}