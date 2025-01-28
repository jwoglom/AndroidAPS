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
import info.nightscout.pump.common.defs.PumpDriverState
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.common.defs.PumpDriverMode
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

    //private val fabricPrivacy: FabricPrivacy
    // private val baseConnector // YpsoPumpBaseConnector
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

        // TODO remove when in production
        if (tandemPumpStatus.pumpDriverMode== PumpDriverMode.Demo) {
            aapsLogger.debug(TAG, "Connect to Pump - Dummy")
            pumpUtil.driverStatus = PumpDriverState.Ready
            return dummyConnector.connectToPump()
        }





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

        // pumpUtil.driverStatus = PumpDriverState.Connected
        // pumpUtil.sleepSeconds(5)
        // pumpUtil.driverStatus = PumpDriverState.EncryptCommunication
        // pumpUtil.sleepSeconds(5)
        // pumpUtil.driverStatus = PumpDriverState.Ready


        // Thread thread = new Thread() {
        //     public void run() {
        //         println("Thread Running")
        //         aapsLogger.debug(TAG, "!!!!!! Connect to Pump - Thread running")
        //         ypsopumpUtil.driverStatus = PumpDriverState.Connecting
        //         ypsopumpUtil.sleepSeconds(15)
        //         ypsopumpUtil.driverStatus = PumpDriverState.Connected
        //         ypsopumpUtil.sleepSeconds(5)
        //         ypsopumpUtil.driverStatus = PumpDriverState.EncryptCommunication
        //         ypsopumpUtil.sleepSeconds(5)
        //         ypsopumpUtil.driverStatus = PumpDriverState.Ready
        //
        //     }
        // };
        //
        // thread.start();

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


    fun disconnectFromPumpX3453(): Boolean {

        // TODO handle states
        return dummyConnector.disconnectFromPump()



        // var driverStatus: PumpDriverState? = pumpUtil.driverStatus
        //
        // when (driverStatus) {
        //     PumpDriverState.NotInitialized,
        //     PumpDriverState.Initialized                -> {
        //         aapsLogger.warn(TAGCOMM, "disconnectFromPump. Pump is in weird state ($driverStatus.name), exiting.")
        //         return true;
        //     }
        //
        //     PumpDriverState.ErrorCommunicatingWithPump -> {
        //         val errorType = pumpUtil.errorType
        //         aapsLogger.warn(TAGCOMM, "disconnectFromPump. Pump is in error ($errorType.name), exiting.")
        //         return true;
        //     }
        //
        //     PumpDriverState.Connecting,
        //     PumpDriverState.EncryptCommunication,
        //     PumpDriverState.ExecutingCommand,
        //     PumpDriverState.Busy,
        //     PumpDriverState.Suspended,
        //     PumpDriverState.Connected                  -> {
        //         aapsLogger.warn(TAGCOMM, "disconnectFromPump. Pump seems to be in unallowed state ($driverStatus.name), exiting and setting to Sleep.")
        //         pumpUtil.driverStatus = PumpDriverState.Sleeping
        //         return;
        //     }
        //
        //     PumpDriverState.Disconnecting              -> {
        //         aapsLogger.warn(TAGCOMM, "disconnectFromPump. Pump is already disconnecting, exiting.")
        //         return true
        //     }
        //
        //     PumpDriverState.Sleeping,
        //     PumpDriverState.Disconnected,
        //     PumpDriverState.Ready                        -> return true
        //
        //     null -> {}
        // }
        //
        // if (inDisconnectMode)
        //     return true
        //
        // inDisconnectMode = true
        //
        // ypsoPumpBLE.disconnectFromYpsoPump()
        //
        // val timeoutTime = System.currentTimeMillis() + (120 * 1000)
        // var timeouted = true
        // //var driverStatus: PumpDriverState? = null
        //
        // while (System.currentTimeMillis() < timeoutTime) {
        //     SystemClock.sleep(5000)
        //
        //     driverStatus = pumpUtil.driverStatus
        //
        //     aapsLogger.debug(TAGCOMM, "disconnectFromPump: " + driverStatus.name)
        //
        //     if (driverStatus == PumpDriverState.Disconnected || driverStatus == PumpDriverState.Sleeping) {
        //         timeouted = false
        //         break
        //     }
        // }
        //
        // if (driverStatus == PumpDriverState.Disconnected) {
        //     pumpUtil.driverStatus = PumpDriverState.Sleeping
        // }
        //
        // inDisconnectMode = false

        // //Thread thread = new Thread() {
        // //  public void run() {
        // println("Thread Running")
        // aapsLogger.debug(TAG, "Disconnect from Pump - Thread running")
        // ypsopumpUtil.driverStatus = PumpDriverState.Disconnecting
        // ypsopumpUtil.sleepSeconds(20)
        // ypsopumpUtil.driverStatus = PumpDriverState.Sleeping
        // pumpStatus.setLastCommunicationToNow()
        // }
        //};
    }

    override fun setCurrentPumpCommandType(commandType: PumpCommandType) {
        pumpUtil.currentCommand = commandType
    }

    override fun resetDriverStatus() {
        tandemPumpUtil.resetDriverStatusToConnected()
    }

    override fun getConnector(commandType: PumpCommandType?): PumpConnectorInterface {

        aapsLogger.debug(TAG, "TANDEMDBG: getConnector for ${commandType}")

        if (tandemPumpStatus.pumpDriverMode == PumpDriverMode.Demo) {
            aapsLogger.debug(TAG, "TANDEMDBG: get Dummy Connector for ${commandType}")
            return dummyConnector
        }

        // TODO extend this when new commands are enabled
        when(commandType) {
            //PumpCommandType.GetBasalProfile        -> return tandemConnector
            //PumpCommandType.GetRemainingInsulin    -> return tandemConnector
            //PumpCommandType.GetSettings            -> return tandemConnector

            PumpCommandType.GetBatteryStatus,
            PumpCommandType.GetTime -> return tandemConnector
            else                    -> return dummyConnector
        }
    }

    override fun postProcessConfiguration(valueMap: MutableMap<PumpConfigurationTypeInterface, Any>??) {
        // TODO
        //TODO("postProcessConfiguration Not yet implemented")
        if (valueMap!=null) {
            for (entry in valueMap.entries) {
                aapsLogger.info(TAG, "TANDEMDBG: Settings ${entry.key} = ${entry.value}")
            }
        } else {
            aapsLogger.warn(TAG, "TANDEMDBG: No settings found.")
        }
    }

    // override fun postProcessBasalProfile(value: BasalProfileDto) {
    //     // TODO
    //     //TODO("Not yet implemented")
    // }




    init {
        // TODO TandemPumpConnectionManager - remove dummyConnector when not needed anymore
        dummyConnector = PumpDummyConnector(pumpStatus, pumpUtil, injector, aapsLogger)
    }
}