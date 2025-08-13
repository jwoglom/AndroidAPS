package app.aaps.pump.tandem.common.comm

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.pump.common.data.PumpTimeDifferenceDto
import app.aaps.pump.common.defs.PumpDriverState
import app.aaps.pump.common.defs.PumpErrorType
import app.aaps.pump.common.defs.PumpUpdateFragmentType
import app.aaps.pump.common.events.EventPumpFragmentValuesChanged
import app.aaps.pump.tandem.R
import app.aaps.pump.tandem.common.comm.defs.CommunicationListener
import app.aaps.pump.tandem.common.comm.ui.TandemUIDataStore
import app.aaps.pump.tandem.common.data.defs.TandemNotificationType
import app.aaps.pump.tandem.common.data.defs.TandemPumpApiVersion
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.driver.tandemDataStore
import app.aaps.pump.tandem.common.events.EventHandleQualifyingEvent
import app.aaps.pump.tandem.common.keys.TandemIntPreferenceKey
import app.aaps.pump.tandem.common.keys.TandemStringPreferenceKey
import app.aaps.pump.tandem.common.util.PumpX2L
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.TandemError
import com.jwoglom.pumpx2.pump.bluetooth.TandemBluetoothHandler
import com.jwoglom.pumpx2.pump.bluetooth.TandemConfig
import com.jwoglom.pumpx2.pump.bluetooth.TandemPump
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.response.authentication.AbstractCentralChallengeResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ApiVersionResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpVersionResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.qualifyingEvent.QualifyingEvent
import com.welie.blessed.BluetoothPeripheral
import org.joda.time.DateTime

import javax.inject.Inject

/**
 * This is low-level driver that does all communication with pump, with exception of pairing.
 */
class TandemCommunicationManager(
    context: Context,
    var resourceHelper: ResourceHelper,
    var aapsLogger: AAPSLogger,
    var rxBus: RxBus,
    //var sp: SP,
    var preferences: Preferences,
    var pumpUtil: TandemPumpUtil,
    var pumpStatus: TandemPumpStatus,
    var pumpConfig: TandemConfig,
    var timberTree: PumpX2L
) : TandemPump(context, pumpConfig) {

    lateinit var peripheral: BluetoothPeripheral
    var connected = false
    var errorConnecting = false
    var commandRequestModeRunning = false

    var dataStore: TandemUIDataStore = tandemDataStore

    var commandRequest: Message? = null
    var commandResponse: Message? = null
    var communicationListener : CommunicationListener? = null
        set(value) {  if (value==null)
            operationMode=OperationMode.StandardOperation
        else
            operationMode=OperationMode.ExternalListenerOperation
            field = value
        }

    //var responses: MutableMap<Int, Message> = mutableMapOf()

    var bluetoothHandler: TandemBluetoothHandler? = null

    var TAG = LTag.PUMPBTCOMM

    var operationMode: OperationMode = OperationMode.None


    fun connect(): Boolean {

        aapsLogger.info(TAG, "connect() ")

        if (bluetoothHandler==null) {
            createBluetoothHandler()
        }

        connected = false
        operationMode = OperationMode.ConnectionMode
        bluetoothHandler!!.startScan()

        while (operationMode == OperationMode.ConnectionMode) {

            Thread.sleep(500)

            if (connected || errorConnecting) {
                aapsLogger.info(TAG, "connected: $connected error: $errorConnecting")
                operationMode = OperationMode.StandardOperation
            }
        }

        runOnUiThread  {
            tandemDataStore.pumpConnected.value = true
        }


        return connected
    }


    fun disconnect(): Boolean {

        aapsLogger.info(TAG, "disconnect()")

        if (bluetoothHandler!=null) {
            bluetoothHandler!!.stop()
            //connected = false
        }
        connected = false
        operationMode = OperationMode.None

        runOnUiThread {
            tandemDataStore.pumpConnected.value = false
        }

        // inConnectMode = false
        return connected
    }


    private fun createBluetoothHandler(): TandemBluetoothHandler? {
        if (bluetoothHandler != null) {
            return bluetoothHandler
        }
        aapsLogger.info(TAG, "createBluetoothHandler for Communication")

        runOnUiThread {
            bluetoothHandler = TandemBluetoothHandler.getInstance(context, this, timberTree)
        }

        while (bluetoothHandler == null) {
            aapsLogger.debug(TAG, "Waiting for bluetoothHandler on ui thread")
            pumpUtil.sleep(500)
        }

        return bluetoothHandler
    }


    override fun onInitialPumpConnection(peripheral: BluetoothPeripheral)  {
        aapsLogger.info(TAG, "onInitialPumpConnection: $peripheral")

        this.peripheral = peripheral
        pumpUtil.driverStatus = PumpDriverState.Handshaking
        super.onInitialPumpConnection(peripheral)
    }


    override fun onPumpConnected(peripheral: BluetoothPeripheral?) {
        aapsLogger.info(TAG, "onPumpConnected: $peripheral")

        super.onPumpConnected(peripheral)
    }



    /**
     * Sends command to the pump, if driver is in preventConnect mode any messages will be ignored,
     * unless we specify forceSend. Force send should be used only for ChangeFillManager
     */
    fun sendCommand(request: Message, forceSend: Boolean = false): Message? {
        var times = 0

        operationMode = OperationMode.StandardOperation

        if (pumpUtil.preventConnect) {
            // TODO handle pumpUtil.preventConnect mode
        }

        while (!::peripheral.isInitialized && times < 10) {
            aapsLogger.warn(LTag.PUMPCOMM, "Waiting for peripheral for sendCommand with ${request.opCode()} - ${request.javaClass.name}")
            pumpUtil.sleep(1000)
            times++
        }

        if (!::peripheral.isInitialized) {
            aapsLogger.warn(LTag.PUMPCOMM, "Failed sendCommand, no peripheral with ${request.opCode()} - ${request.javaClass.name}")
            return null
        }
        this.commandRequestModeRunning = true
        this.commandRequest = request
        this.commandResponse = null

        aapsLogger.info(LTag.PUMPCOMM, "Sending Request: [code=${request.opCode()},class=${request::class.simpleName}]")

        sendCommand(peripheral, request)

        // TODO add timeout
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

        operationMode = OperationMode.ExternalListenerOperation

        aapsLogger.warn(LTag.PUMPCOMM, "STM: sendCommandWithListener sendCommand with [request_code=${request.opCode()},request_class=${request.javaClass.simpleName},connected=$connected]")

        while (!::peripheral.isInitialized && times < 10) {
            aapsLogger.warn(LTag.PUMPCOMM, "STM: Waiting for peripheral for sendCommand with ${request.opCode()} - ${request.javaClass.name}")
            pumpUtil.sleep(1000)
            times++
        }

        if (!::peripheral.isInitialized) {
            aapsLogger.warn(LTag.PUMPCOMM, "STM: Failed sendCommand, no peripheral with ${request.opCode()} - ${request.javaClass.name}")
            return null
        }

        aapsLogger.info(LTag.PUMPCOMM, "STM: Sending Request: [code=${request.opCode()},class=${request::class.simpleName}]")

        sendCommand(peripheral, request)

        return null
    }


    var apiVersionResponseReceived = false


    override fun onReceiveMessage(peripheral: BluetoothPeripheral, message: Message) {
        aapsLogger.info(LTag.PUMPBTCOMM, "Received Response: ${message.opCode()} - ${message.javaClass.simpleName} - Mode: $operationMode")

        when(operationMode) {
            OperationMode.ConnectionMode            -> receiveMessageInConnectMode(message)
            OperationMode.StandardOperation         -> receiveMessageInStandardMode(message)
            OperationMode.ExternalListenerOperation -> communicationListener!!.onReceiveMessage(message)
            else -> {
                aapsLogger.error("We are in None operation mode and we received Pump Message.")
            }
        }
    }

    lateinit var apiVersionResponse: ApiVersionResponse

    fun receiveMessageInConnectMode(message: Message) {

        if (message is ApiVersionResponse) {

            apiVersionResponse = message

            val apiVersion = TandemPumpApiVersion.getApiVersionFromResponse(apiVersionResponse)

            aapsLogger.info(LTag.PUMPCOMM, "Api Version: ${apiVersionResponse.majorVersion}.${apiVersionResponse.minorVersion} : ${apiVersion.name} ")

            pumpStatus.tandemPumpFirmware = apiVersion

            //sp.putString(TandemPumpConst.Prefs.PumpApiVersion, apiVersion.name)
            preferences.put(TandemStringPreferenceKey.PumpApiVersion, apiVersion.name)

            runOnUiThread  {
                dataStore.apiVersionResponse.value = message
            }

            rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Configuration))

        } else if (message is TimeSinceResetResponse) {

            val timeSince : TimeSinceResetResponse = message

            aapsLogger.info(LTag.PUMPCOMM, "TimeSinceResetResponse: ${message}")

            val dtPump = DateTime(timeSince.currentTimeInstant.toEpochMilli())

            val pumpTimeDifference = PumpTimeDifferenceDto(DateTime.now(), dtPump)
            pumpStatus.pumpTime = pumpTimeDifference

            pumpUtil.driverStatus = PumpDriverState.Connected

            this.connected = true
            this.operationMode = OperationMode.StandardOperation

        } else if (message is PumpVersionResponse) {
            // TODO TAF - this needs to be added and this request removed from InitPump

            runOnUiThread  {
                dataStore.pumpVersionResponse.value = message
            }

        }
    }

    fun receiveMessageInStandardMode(message: Message) {
        if (this.commandRequest==null) {
            aapsLogger.error(LTag.PUMPCOMM, "No Command Requested, but received message [code=${message.opCode()}]")
        } else {
            aapsLogger.info(LTag.PUMPCOMM, "ReceivedMessage [code=${message.opCode()},expected=${commandRequest!!.getResponseOpCode()},class=${message::class.simpleName}]")

            if (message.opCode() == commandRequest!!.getResponseOpCode()) {
                aapsLogger.info(LTag.PUMPCOMM, "Response received [code=${message.opCode()},class=${message::class.simpleName}]")
                this.commandResponse = message
            } else {
                if (message is ApiVersionResponse) {
                    this.apiVersionResponseReceived = true
                    aapsLogger.error(LTag.PUMPCOMM, "Received ApiVersionResponse - problem with communication.")
                } else if (message is TimeSinceResetResponse) {
                    if (this.apiVersionResponseReceived) {
                        this.apiVersionResponseReceived = false
                        pumpStatus.errorDescription = resourceHelper.gs(R.string.tandem_error_problem_with_request)
                        rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.None))
                    }
                } else {
                    aapsLogger.info(TAG, "Discarding Message [code=${message.opCode()},class=${message.javaClass.simpleName}]")
                }

            }
        }

    }


    override fun onReceiveQualifyingEvent(peripheral: BluetoothPeripheral, events: Set<QualifyingEvent>) {
        aapsLogger.info(TAG, "QE: onReceiveQualifyingEvent: %s (creating AAPS event)", events)
        rxBus.send(EventHandleQualifyingEvent(events = events, dateTime = System.currentTimeMillis()))
    }



    override fun onWaitingForPairingCode(peripheral: BluetoothPeripheral?, centralChallenge: AbstractCentralChallengeResponse?) {
        aapsLogger.info(TAG, "TandemCommMgr: onWaitingForPairingCode ")

        val pairingCodePS = PumpState.getPairingCode(context)

        if (pairingCodePS==null) {
            aapsLogger.info(TAG, "TandemCommMgr: PumpState doesn't have PairCode, reading from local configuration.")
            val pairingCode = pumpUtil.getStringPreferenceOrDefaultOrNull(TandemStringPreferenceKey.PumpPairCode, null)
            //sp.getStringOrNull(TandemPumpConst.Prefs.PumpPairCode, null)
            //aapsLogger.info(TAG, "TandemCommMgr: onWaitingForPairingCode. Pairing Code: ${pairingCode} ")

            if (pairingCode.isNullOrBlank()) {
                aapsLogger.error(LTag.PUMPCOMM, "TandemCommMgr: onWaitingForPairingCode. It seems you Pairing code was not saved.")
                sendInvalidPairingCodeError()
                return
            }

            pair(peripheral, centralChallenge, pairingCode)
        } else {
            aapsLogger.info(TAG, "TandemCommMgr: Taking PairCode from PumpState.")
            pair(peripheral, centralChallenge, pairingCodePS)
        }
    }

    override fun onPumpCriticalError(peripheral: BluetoothPeripheral?, reason: TandemError?) {
        aapsLogger.error(TAG, "Pump Critical Error: ${reason}")

        pumpStatus.errorDescription = resourceHelper.gs(R.string.tandem_error_pump_critical_error,
                                                        if (reason==null) "Unknown" else reason.message)
        pumpUtil.errorType = PumpErrorType.PumpUnreachable
        rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.None))

        if (reason!=null && reason == TandemError.BT_CONNECTION_FAILED) {
            // TODO
        }

        super.onPumpCriticalError(peripheral, reason)
    }


    fun sendInvalidPairingCodeError() {
        preferences.put(TandemIntPreferenceKey.PumpPairStatus, -2)
        pumpUtil.errorType = PumpErrorType.PumpPairInvalidPairCode

        pumpUtil.sendNotification(TandemNotificationType.InvalidPairingCodeReconfigure)

        this.errorConnecting = true
    }


    fun isPumpFullyConnected() : Boolean {
        return operationMode==OperationMode.StandardOperation ||
            operationMode==OperationMode.ExternalListenerOperation
    }

    fun isListenerEnabled(): Boolean {
        return this.communicationListener!=null
    }

    enum class OperationMode {
        None,
        ConnectionMode,
        StandardOperation,
        ExternalListenerOperation,
    }


}
