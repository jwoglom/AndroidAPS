package app.aaps.pump.tandem.common.comm

//import com.jwoglom.pumpx2.util.timber.LConfigurator

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.pump.common.data.PumpTimeDifferenceDto
import app.aaps.pump.common.defs.PumpDriverMode
import app.aaps.pump.common.defs.PumpDriverState
import app.aaps.pump.common.defs.PumpErrorType
import app.aaps.pump.common.defs.PumpUpdateFragmentType
import app.aaps.pump.common.events.EventPumpFragmentValuesChanged
import app.aaps.pump.tandem.R
import app.aaps.pump.tandem.common.comm.defs.CommunicationListener
import app.aaps.pump.tandem.common.data.defs.TandemNotificationType
import app.aaps.pump.tandem.common.data.defs.TandemPumpApiVersion
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.events.EventHandleQualifyingEvent
import app.aaps.pump.tandem.common.util.PumpX2L
import app.aaps.pump.tandem.common.util.TandemPumpConst
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import com.jwoglom.pumpx2.pump.TandemError
import com.jwoglom.pumpx2.pump.bluetooth.TandemBluetoothHandler
import com.jwoglom.pumpx2.pump.bluetooth.TandemConfig
import com.jwoglom.pumpx2.pump.bluetooth.TandemPump
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.response.authentication.AbstractCentralChallengeResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ApiVersionResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.qualifyingEvent.QualifyingEvent
import com.welie.blessed.BluetoothPeripheral
import org.joda.time.DateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * This is low-level driver that does all communication with pump, with exception of pairing.
 */
class TandemCommunicationManager @Inject constructor(
    var context: Context,
    var resourceHelper: ResourceHelper,
    var aapsLogger: AAPSLogger,
    var rxBus: RxBus,
    var sp: SP,
    var pumpUtil: TandemPumpUtil,
    var pumpStatus: TandemPumpStatus,
    var pumpConfig: TandemConfig,
    var timberTree: PumpX2L
) : TandemPump(context, pumpConfig) {

    lateinit var peripheral: BluetoothPeripheral
    var connected = false
    //var inConnectMode = false
    var errorConnecting = false
    var commandRequestModeRunning = false

    var commandRequest: Message? = null
    var commandResponse: Message? = null
    var communicationListener : CommunicationListener? = null
        set(value) {  if (value==null)
            operationMode=OperationMode.StandardOperation
        else
            operationMode=OperationMode.ExternalListenerOperation
            field = value
        }

    var responses: MutableMap<Int, Message> = mutableMapOf()

    var bluetoothHandler: TandemBluetoothHandler? = null

    var TAG = LTag.PUMPBTCOMM

    var operationMode: OperationMode = OperationMode.None

    var currentAddress : String? = null

    // fun setNewAddress(btAddress: String) {
    //     if (currentAddress==null) {
    //         this.currentAddress = btAddress
    //     } else {
    //         if (!this.currentAddress.equals(btAddress)) {
    //             this.currentAddress = btAddress
    //             this.bluetoothHandler = null
    //         }
    //
    //     }
    //
    // }


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
        // inConnectMode = true
        operationMode = OperationMode.ConnectionMode
        bluetoothHandler!!.startScan()

        while (operationMode == OperationMode.ConnectionMode) {
            aapsLogger.info(TAG, "TANDEMDBG: inConnectMode")
            Thread.sleep(2000)

            if (connected || errorConnecting) {
                aapsLogger.info(TAG, "TANDEMDBG: connected: ${connected} error: ${errorConnecting}")
                // inConnectMode = false
                operationMode = OperationMode.StandardOperation
                //return connected;
            }
        }

        return connected
    }


    fun disconnect(): Boolean {

        // aapsLogger.info(TAG, "TANDEMDBG: disconnect() called")

        // if (pumpStatus.pumpDriverMode== PumpDriverMode.Demo) {
        //     aapsLogger.info(TAG, "TANDEMDBG: disconnect() - Faked")
        //     return false
        // }


        aapsLogger.info(TAG, "TANDEMDBG: disconnect ")

        if (bluetoothHandler!=null) {
            bluetoothHandler!!.stop()
            //connected = false
        }
        connected = false
        operationMode = OperationMode.None
        // inConnectMode = false
        return connected
    }


    private fun createBluetoothHandler(): TandemBluetoothHandler? {
        if (bluetoothHandler != null) {
            return bluetoothHandler
        }
        aapsLogger.info(TAG, "createBluetoothHandler for Communication")
        //LConfigurator.enableTimber()

        runOnUiThread {
            bluetoothHandler = TandemBluetoothHandler.getInstance(context, this, timberTree);
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
        pumpUtil.driverStatus = PumpDriverState.Handshaking
        super.onInitialPumpConnection(peripheral)
    }

    override fun onPumpConnected(peripheral: BluetoothPeripheral?) {
        aapsLogger.info(TAG, "TANDEMDBG: onPumpConnected: $peripheral")

        super.onPumpConnected(peripheral)
    }

    // TODO refactor sendCommand, it should not return Message directly (See ChangeFillManager), but
    //   return CommandResponse




    /**
     * Sends command to the pump, if driver is in preventConnect mode any messages will be ignored,
     * unless we specify forceSend. Force send should be used only for ChangeFillManager
     */
    fun sendCommand(request: Message, forceSend: Boolean = false): Message? {
        var times = 0

        if (pumpUtil.preventConnect) {
            // TODO handle pumpUtil.preventConnect mode
        }

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


    fun sendCommandWithListener(request: Message): Message? {
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



    var apiVersionResponseReceived = false


    override fun onReceiveMessage(peripheral: BluetoothPeripheral, message: Message) {
        aapsLogger.info(LTag.PUMPBTCOMM, "TANDEMDBG: Received Response: ${message.opCode()} - ${message.javaClass.name} ")

        when(operationMode) {
            OperationMode.ConnectionMode            -> receiveMessageInConnectMode(message)
            OperationMode.StandardOperation         -> receiveMessageInStandardMode(message)
            OperationMode.ExternalListenerOperation -> communicationListener!!.onReceiveMessage(message)
            else -> {
                aapsLogger.error("We are in None operation mode and we received Pump Message.")
            }
        }
    }


    fun receiveMessageInConnectMode(message: Message) {

        if (message is ApiVersionResponse) {

            val apiVersionResponse = message
            val apiVersion = TandemPumpApiVersion.getApiVersionFromResponse(apiVersionResponse)

            aapsLogger.info(LTag.PUMPCOMM, "Api Version: ${apiVersionResponse.majorVersion}.${apiVersionResponse.minorVersion} : ${apiVersion.name} ")

            pumpStatus.tandemPumpFirmware = apiVersion

            sp.putString(TandemPumpConst.Prefs.PumpApiVersion, apiVersion.name)

            rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Configuration))

        } else if (message is TimeSinceResetResponse) {

            val timeSince : TimeSinceResetResponse = message

            aapsLogger.info(LTag.PUMPCOMM, "TimeSinceResetResponse: ${message}")

            val dtPumpInstant = timeSince.currentTimeInstant

            val zonedDateTime: ZonedDateTime = dtPumpInstant.atZone(ZoneId.of("Europe/Dublin"))
            //println("ZonedDateTime: $zonedDateTime")

            aapsLogger.info(LTag.PUMPCOMM, "Pump Time: Zoned Time ${zonedDateTime}")

            val dtPump = DateTime(dtPumpInstant.toEpochMilli())

            val pumpTimeDifference = PumpTimeDifferenceDto(DateTime.now(), dtPump)
            pumpStatus.pumpTime = pumpTimeDifference

            var timeDiffJson = pumpUtil.gson.toJson(pumpTimeDifference)

            aapsLogger.info(LTag.PUMPCOMM, "Pump Time: ${timeDiffJson}")

            pumpStatus.pumpTime!!.displayTime(gson = pumpUtil.gson, aapsLogger = aapsLogger)

            // TODO check Pump Serial   N-8

            pumpUtil.driverStatus = PumpDriverState.Connected

            this.connected = true

        }
    }

    fun receiveMessageInStandardMode(message: Message) {
        if (this.commandRequest==null) {
            aapsLogger.error(LTag.PUMPCOMM, "TANDEMDBG: No Command Requested, but received message [code=${message.opCode()}]")
        } else {
            aapsLogger.info(LTag.PUMPCOMM, "TANDEMDBG: receivedMessage [code=${message.opCode()},expected=${commandRequest!!.getResponseOpCode()},class=${message::class.simpleName}]")

            if (message.opCode() == commandRequest!!.getResponseOpCode()) {
                aapsLogger.info(LTag.PUMPCOMM, "TANDEMDBG: Response received ${message.opCode()}")
                this.commandResponse = message
            } else {
                if (message is ApiVersionResponse) {
                    this.apiVersionResponseReceived = true
                    aapsLogger.error(LTag.PUMPCOMM, "Received ApiVersionResponse - problem with communication.")
                } else if (message is TimeSinceResetResponse) {
                    this.apiVersionResponseReceived = false
                    pumpStatus.errorDescription = resourceHelper.gs(R.string.tandem_error_problem_with_request)
                    rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.None))
                    // TODO fix
                } else {
                    aapsLogger.info(LTag.PUMPCOMM, "TANDEMDBG: discarding Message [code=${message.opCode()}")
                }

            }
        }

    }


    override fun onReceiveQualifyingEvent(peripheral: BluetoothPeripheral, events: Set<QualifyingEvent>) {
        aapsLogger.info(TAG, "TANDEMDBG: onReceiveQualifyingEvent: %s (creating AAPS event)", events)

        rxBus.send(EventHandleQualifyingEvent(events))



    }



    override fun onWaitingForPairingCode(peripheral: BluetoothPeripheral?, centralChallenge: AbstractCentralChallengeResponse?) {
        aapsLogger.info(TAG, "TandemCommMgr: onWaitingForPairingCode ")
        val pairingCode = sp.getStringOrNull(TandemPumpConst.Prefs.PumpPairCode, null)
        //aapsLogger.info(TAG, "TandemCommMgr: onWaitingForPairingCode. Pairing Code: ${pairingCode} ")

        if (pairingCode.isNullOrBlank()) {
            aapsLogger.error(LTag.PUMPCOMM, "TandemCommMgr: onWaitingForPairingCode. It seems you Pairing code was not saved.")
            sendInvalidPairingCodeError()
            return
        }

        pair(peripheral, centralChallenge, pairingCode)
    }


    // TODO 1.4.4
    // override fun onInvalidPairingCode(peripheral: BluetoothPeripheral?, resp: PumpChallengeResponse?) {
    //     aapsLogger.error(TAG, "TANDEMDBG: onInvalidPairingCode() - PairingCode seems to be no longer valid.")
    //     sendInvalidPairingCodeError()
    // }

    override fun onPumpCriticalError(peripheral: BluetoothPeripheral?, reason: TandemError?) {
        aapsLogger.error(TAG, "Pump Critical Error: ${reason}")

        pumpStatus.errorDescription = resourceHelper.gs(R.string.tandem_error_pump_critical_error,
                                                        if (reason==null) "Unknown" else reason.message)
        rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.None))

        super.onPumpCriticalError(peripheral, reason)
    }


    fun sendInvalidPairingCodeError() {
        sp.putInt(TandemPumpConst.Prefs.PumpPairStatus, -2)
        pumpUtil.errorType = PumpErrorType.PumpPairInvalidPairCode

        pumpUtil.sendNotification(TandemNotificationType.InvalidPairingCodeReconfigure)

        this.errorConnecting = true
    }


    // TODO class

    enum class OperationMode {
        None,
        ConnectionMode,
        StandardOperation,
        ExternalListenerOperation,
    }


}