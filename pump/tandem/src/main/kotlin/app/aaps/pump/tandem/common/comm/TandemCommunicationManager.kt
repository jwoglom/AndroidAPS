package app.aaps.pump.tandem.common.comm

//import com.jwoglom.pumpx2.util.timber.LConfigurator

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.pump.common.data.PumpTimeDifferenceDto
import app.aaps.pump.common.defs.PumpDriverMode
import app.aaps.pump.common.defs.PumpErrorType
import app.aaps.pump.common.defs.PumpUpdateFragmentType
import app.aaps.pump.common.events.EventPumpFragmentValuesChanged
import app.aaps.pump.tandem.common.data.defs.TandemNotificationType
import app.aaps.pump.tandem.common.data.defs.TandemPumpApiVersion
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.util.TandemPumpConst
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.bluetooth.TandemBluetoothHandler
import com.jwoglom.pumpx2.pump.bluetooth.TandemConfig
import com.jwoglom.pumpx2.pump.bluetooth.TandemPump
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ApiVersionRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.response.authentication.AbstractCentralChallengeResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ApiVersionResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.qualifyingEvent.QualifyingEvent
import com.jwoglom.pumpx2.util.timber.LConfigurator
import com.welie.blessed.BluetoothPeripheral
import org.joda.time.DateTime
import timber.log.Timber

/**
 * This is low-level driver that does all communication with pump, with exception of pairing.
 */
class TandemCommunicationManager constructor(
    var context: Context,
    var aapsLogger: AAPSLogger,
    var rxBus: RxBus,
    var sp: SP,
    var pumpUtil: TandemPumpUtil,
    var pumpStatus: TandemPumpStatus,
    var pumpConfig: TandemConfig

) : TandemPump(context, pumpConfig) {

    lateinit var peripheral: BluetoothPeripheral
    var connected = false
    var inConnectMode = false
    var errorConnecting = false
    var commandRequestModeRunning = false

    var commandRequest: Message? = null
    var commandResponse: Message? = null
    var responses: MutableMap<Int, Message> = mutableMapOf()

    var bluetoothHandler: TandemBluetoothHandler? = null

    var TAG = LTag.PUMPBTCOMM


    fun connect(): Boolean {

        aapsLogger.info(TAG, "TANDEMDBG: connect() called")

        if (pumpStatus.pumpDriverMode== PumpDriverMode.Demo) {
            aapsLogger.info(TAG, "TANDEMDBG: connect() - Faked")
            return true
        }


        aapsLogger.info(TAG, "TANDEMDBG: connect() ")

        if (bluetoothHandler==null) {
            createBluetoothHandler()
        }

        connected = false
        inConnectMode = true
        bluetoothHandler!!.startScan()

        while (inConnectMode) {
            aapsLogger.info(TAG, "TANDEMDBG: inConnectMode")
            Thread.sleep(2000)

            if (connected || errorConnecting) {
                aapsLogger.info(TAG, "TANDEMDBG: connected: ${connected} error: ${errorConnecting}")
                inConnectMode = false
                //return connected;
            }
        }

        return connected
    }


    fun disconnect() {

        aapsLogger.info(TAG, "TANDEMDBG: disconnect() called")

        if (pumpStatus.pumpDriverMode== PumpDriverMode.Demo) {
            aapsLogger.info(TAG, "TANDEMDBG: disconnect() - Faked")
            return
        }


        aapsLogger.info(TAG, "TANDEMDBG: disconnect ")

        if (bluetoothHandler!=null) {
            bluetoothHandler!!.stop()
        }
        connected = false
        inConnectMode = false
    }


    private fun createBluetoothHandler(): TandemBluetoothHandler? {
        if (bluetoothHandler != null) {
            return bluetoothHandler
        }
        aapsLogger.info(TAG, "createBluetoothHandler for Communication")
        LConfigurator.enableTimber()

        runOnUiThread {
            bluetoothHandler = TandemBluetoothHandler.getInstance(context, this);
        }

        while (bluetoothHandler == null) {
            aapsLogger.debug(TAG, "Waiting for bluetoothHandler on ui thread")
            pumpUtil.sleep(500)
        }


        // aapsLogger.info(TAG, "TANDEMDBG: createBluetoothHandler ")
        // bluetoothHandler = TandemBluetoothHandler.getInstance(context, this)
        // aapsLogger.info(TAG, "TANDEMDBG: createBluetoothHandler ${bluetoothHandler}")

        return bluetoothHandler
    }


    override fun onInitialPumpConnection(peripheral: BluetoothPeripheral)  {
        aapsLogger.info(TAG, "TANDEMDBG: onInitialPumpConnection: $peripheral")

        this.peripheral = peripheral
        super.onInitialPumpConnection(peripheral)
    }

    override fun onPumpConnected(peripheral: BluetoothPeripheral?) {
        aapsLogger.info(TAG, "TANDEMDBG: onPumpConnected: $peripheral")
        super.onPumpConnected(peripheral)
    }


    fun sendCommand(request: Message): Message? {
        var times = 0

        while (!::peripheral.isInitialized && times < 10) {
            aapsLogger.warn(LTag.PUMPCOMM, "TANDEMDBG: Waiting for peripheral for sendCommand with ${request.opCode()} - ${request.javaClass.name}")
            pumpUtil.sleep(1000)
            times++
        }

        if (!::peripheral.isInitialized) {
            aapsLogger.warn(LTag.PUMPCOMM, "TANDEMDBG: Failed sendCommand, no peripheral with ${request.opCode()} - ${request.javaClass.name}")
            return null;
        }
        this.commandRequestModeRunning = true
        this.commandRequest = request
        this.commandResponse = null

        aapsLogger.info(LTag.PUMPCOMM, "TANDEMDBG: Sending Request: [code=${request.opCode()},class=${request::class.simpleName}]")

        sendCommand(peripheral, request)

        while(commandRequestModeRunning) {

            if (commandResponse!=null) {
                this.commandRequestModeRunning = false
                return commandResponse
            }

            pumpUtil.sleep(1000)
        }

        return null
    }



    override fun onReceiveMessage(peripheral: BluetoothPeripheral, message: Message) {
        aapsLogger.info(LTag.PUMPBTCOMM, "TANDEMDBG: Received Response: ${message.opCode()} - ${message.javaClass.name} ")

        if (inConnectMode)  {

            if (message is ApiVersionResponse) {

                val apiVersionResponse = message
                val apiVersion = TandemPumpApiVersion.getApiVersionFromResponse(apiVersionResponse)

                aapsLogger.info(LTag.PUMPCOMM, "Api Version: ${apiVersionResponse.majorVersion}.${apiVersionResponse.minorVersion} : ${apiVersion.name} ")

                pumpStatus.tandemPumpFirmware = apiVersion

                sp.putString(TandemPumpConst.Prefs.PumpApiVersion, apiVersion.name)

                rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Configuration))

                // TODO check if PumpApiVersion changed   N-8
                //sp.putString(TandemPumpConst.Prefs.PumpApiVersion, apiVersion.name)
            } else if (message is TimeSinceResetResponse) {

                val timeSince : TimeSinceResetResponse = message

                aapsLogger.info(LTag.PUMPCOMM, "TimeSinceResetResponse: ${message}")

                val dtPump = DateTime().withMillis(timeSince.currentTime * 1000L)

                val pumpTimeDifference = PumpTimeDifferenceDto(DateTime.now(), dtPump)
                pumpStatus.pumpTime = pumpTimeDifference

                var timeDiffJson = pumpUtil.gson.toJson(pumpTimeDifference)

                aapsLogger.info(LTag.PUMPCOMM, "Pump Time: ${timeDiffJson}")

                // TODO check Pump Serial   N-8

                this.connected = true

            }
        } else {

            // if (message.opCode() == commandRequest!!.opCode()) {
            //     this.commandResponse = message
            // }

            if (this.commandRequest==null) {
                aapsLogger.info(LTag.PUMPCOMM, "TANDEMDBG: No Command Requested, but received message [code=${message.opCode()}]")
            } else {
                aapsLogger.info(LTag.PUMPCOMM, "TANDEMDBG: receivedMessage [code=${message.opCode()},expected=${commandRequest!!.getResponseOpCode()},class=${message::class.simpleName}]")

                if (message.opCode() == commandRequest!!.getResponseOpCode()) {
                    aapsLogger.info(LTag.PUMPCOMM, "TANDEMDBG: not in connect mode, but response received ${message.opCode()}")
                    this.commandResponse = message
                } else {
                    aapsLogger.info(LTag.PUMPCOMM, "TANDEMDBG: discarding Message [code=${message.opCode()}")
                }
            }
        }
    }

    override fun onReceiveQualifyingEvent(peripheral: BluetoothPeripheral, events: Set<QualifyingEvent>) {
        aapsLogger.info(TAG, "TANDEMDBG: onReceiveQualifyingEvent: %s", events)
    }


    // TODO 1.4.4 chanlenge type changed
    override fun onWaitingForPairingCode(peripheral: BluetoothPeripheral?, centralChallenge: AbstractCentralChallengeResponse?) {
    // override fun onWaitingForPairingCode(peripheral: BluetoothPeripheral?, centralChallenge: CentralChallengeResponse?) {
        aapsLogger.info(TAG, "TANDEMDBG: onWaitingForPairingCode ")

        val pairingCode = sp.getStringOrNull(TandemPumpConst.Prefs.PumpPairCode, null)

        aapsLogger.info(TAG, "TANDEMDBG: onWaitingForPairingCode. Pairing Code: ${pairingCode} ")

        // if (pairingCode.isNullOrBlank()) {
        //     aapsLogger.error(LTag.PUMPCOMM, "TandemPump: onWaitingForPairingCode. It seems you Pairing code was not saved.")
        //     sendInvalidPairingCodeError()
        //     return
        // }

        // TODO pairingCode needs to be read from user interface
        pair(peripheral, centralChallenge, "158360")

        //pair(peripheral, centralChallenge, pairingCode)
    }


    // TODO 1.4.4
    // override fun onInvalidPairingCode(peripheral: BluetoothPeripheral?, resp: PumpChallengeResponse?) {
    //     aapsLogger.error(TAG, "TANDEMDBG: onInvalidPairingCode() - PairingCode seems to be no longer valid.")
    //     sendInvalidPairingCodeError()
    // }

    // override fun onPumpCriticalError(peripheral: BluetoothPeripheral?, reason: TandemError?) {
    //     super.onPumpCriticalError(peripheral, reason)
    //     aapsLogger.error(TAG, "TANDEMDBUG: CRITICAL ERROR: ${reason}")
    // }


    fun sendInvalidPairingCodeError() {
        sp.putInt(TandemPumpConst.Prefs.PumpPairStatus, -2)
        pumpUtil.errorType = PumpErrorType.PumpPairInvalidPairCode

        pumpUtil.sendNotification(TandemNotificationType.InvalidPairingCodeReconfigure)

        this.errorConnecting = true
    }


}