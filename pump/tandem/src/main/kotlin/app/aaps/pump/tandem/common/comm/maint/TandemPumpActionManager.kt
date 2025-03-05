package app.aaps.pump.tandem.common.comm.maint

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.tandem.common.comm.TandemCommunicationManager
import app.aaps.pump.tandem.common.comm.defs.CommunicationListener
import app.aaps.pump.tandem.common.process.PumpManagementAction
import app.aaps.pump.tandem.common.process.PumpManagementController
import app.aaps.pump.tandem.common.process.PumpManagementListener
import app.aaps.pump.tandem.common.process.change_cartridge.ChangeCartridgePumpMgmtAction
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.EnterChangeCartridgeModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SuspendPumpingRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.response.control.SuspendPumpingResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ApiVersionResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HomeScreenMirrorResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import javax.inject.Inject

class TandemPumpActionManager @Inject constructor(var tandemCommunicationManager: TandemCommunicationManager,
                                                  val aapsLogger: AAPSLogger,
                                                  val pumpUtil: TandemPumpUtil
        ) : CommunicationListener, PumpManagementController {

    var commandResponseMode: CommandResponseMode = CommandResponseMode.None
    var tag = LTag.PUMPCOMM

    var commandRequest: Message? = null
    var commandResponse: CommandResponse? = null
    var apiVersionResponseReceived: Boolean = false
    private val TAG = LTag.PUMPCOMM



    fun changeCartridge_Start() {
        tandemCommunicationManager.communicationListener = this
    }

    /**
     * Returns true if action was sucessful, which means that at end pump is not running anymore
     */
    fun changeCartridge_SuspendPump() : Boolean {
        return suspendPump()
    }

    fun changeCartridge_Cancel() {
        tandemCommunicationManager.communicationListener = null
    }




    fun suspendPumpCommand(): Boolean {

        aapsLogger.info(TAG, "Start SuspendPump")
        sendDebugInfoToUi("Sending SuspendPumpingRequest...")
        sendStatusToUi("Trying to stop the pump...")

        val responseResult = sendCommand(SuspendPumpingRequest(), CommandResponseMode.SingleCommand)

        if (responseResult.success) {

            val suspendResponse = responseResult.responseMesage as SuspendPumpingResponse

            aapsLogger.info(TAG, "Received response, which tells that action was ${suspendResponse.isStatusOK()}")

            sendDebugInfoToUi("Received response SuspendPumpingResponse... ")
            sendStatusToUi("Pump is NOT Running anymore.")

            return suspendResponse.isStatusOK()

        } else {
            aapsLogger.error(TAG, "Received ERRORED response for SuspendPumpingRequest")
            return false
        }

    }

    fun suspendPump() : Boolean {

        val isRunning = checkIfPumpIsRunning()

        when (isRunning) {
            null -> {
                aapsLogger.info(TAG, "suspendPump: status of pump could not be determined.")
                return false
            }
            true -> {
                aapsLogger.info(TAG, "Pump is running, so I will try to Stop It.")

                return suspendPumpCommand()

            }
            false -> {
                return true
            }
        }

    }


    // TODO
    fun checkIfPumpIsRunning(): Boolean? {

        aapsLogger.info(TAG, "Check if pump is running (sending HomeScreenMirrorRequest).")
        sendDebugInfoToUi("Check if pump is running (sending HomeScreenMirrorRequest)")
        sendStatusToUi("Check in progress...")

        val responseMessage = sendCommand(HomeScreenMirrorRequest(), CommandResponseMode.SingleCommand)

        if (responseMessage.success) {

            aapsLogger.info(TAG, "Received successful response: ")

            val mirrorResponse = responseMessage.responseMesage as HomeScreenMirrorResponse

            val pumpSuspended = (mirrorResponse.basalStatusIcon == HomeScreenMirrorResponse.BasalStatusIcon.SUSPEND)

            sendDebugInfoToUi("Received HomeScreenMirrorResponse response")

            if (pumpSuspended) {
                sendDebugInfoToUi("Response is showing status SUSPEND")
                sendStatusToUiWithDelay("Pump is SUSPENDED")
            } else {
                sendDebugInfoToUi("Response is showing status ${mirrorResponse.basalStatusIcon.name} which means it is not suspended.")
                sendStatusToUiWithDelay("Pump is Running (${mirrorResponse.basalStatusIcon.name})")
            }

            //sendDebugInfoToUi("Response is showing status ")

            aapsLogger.info(TAG, "Pump is suspended: $pumpSuspended ")

            return !pumpSuspended

        } else {
            aapsLogger.error(TAG, "Received ERRORED response for HomeScreenMirrorRequest")
            sendDebugInfoToUi("Response was in Error.")
            sendStatusToUiWithDelay("Pump response was in Error...")
            return null
        }

    }

    fun sendDebugInfoToUi(text: String) {
        this.pumpManagementListener!!.sendDebugInfo(text)
    }

    fun sendStatusToUi(text: String) {
        this.pumpManagementListener!!.sendStatusInfo(text)
    }

    fun sendStatusToUiWithDelay(text: String) {
        this.pumpManagementListener!!.sendStatusInfo(text, true)
    }


    fun sendCommand(request: Message, commandResponseMode: CommandResponseMode): CommandResponse {

        this.commandResponseMode = commandResponseMode

        if (commandResponseMode==CommandResponseMode.SingleCommand) {

            //this.commandRequestModeRunning = true
            this.commandRequest = request
            this.commandResponse = null

            aapsLogger.info(LTag.PUMPCOMM, "TANDEMDBG: Sending Request: [code=${request.opCode()},class=${request::class.simpleName}]")

            tandemCommunicationManager.sendCommandWithListener(request)

            while (commandResponseMode==CommandResponseMode.SingleCommand) {

                if (commandResponse != null) {
                    this.commandResponseMode=CommandResponseMode.None
                    return commandResponse!!
                }

                pumpUtil.sleep(500)
            }

            //return null
        } else {
            this.commandRequest = request // all valid responses should still have same opNumber
            tandemCommunicationManager.sendCommandWithListener(request)
        }

        return CommandResponse(null, false, "Invalid State or expectation")
    }





    enum class CommandResponseMode constructor(val isStream: Boolean) {
        None(false),
        SingleCommand(false),
        EnterChangeCartridge(true)
    }

    override fun onReceiveMessage(message: Message) {

        if (this.commandResponseMode==CommandResponseMode.SingleCommand) {

            aapsLogger.info(LTag.PUMPCOMM, "TANDEMDBG: receivedMessage [code=${message.opCode()},expected=${commandRequest!!.getResponseOpCode()},class=${message::class.simpleName}]")

            if (message.opCode() == commandRequest!!.getResponseOpCode()) {
                aapsLogger.info(LTag.PUMPCOMM, "TANDEMDBG: Response received ${message.opCode()}")
                this.commandResponse = CommandResponse(message, true)
            } else {
                if (message is ApiVersionResponse) {
                    this.apiVersionResponseReceived = true
                    aapsLogger.error(LTag.PUMPCOMM, "Received ApiVersionResponse - problem with communication.")
                } else if (message is TimeSinceResetResponse) {
                    this.apiVersionResponseReceived = false
                    this.commandResponse = CommandResponse(null, false)
                } else {
                    aapsLogger.info(LTag.PUMPCOMM, "discarding Message [code=${message.opCode()}")
                }
            }
        } else if (this.commandResponseMode.isStream) {

            receiveStreamMessage(message)

        } else {
            aapsLogger.error(tag, "Message ${message.javaClass} received, but not expected.")
        }


    }

    private fun receiveStreamMessage(message: Message) {
        TODO("Not yet implemented")
    }

    class CommandResponse constructor(val responseMesage: Message?,
                                      val success: Boolean,
                                      val reason: String? = null) {


    }

    override fun startShortAction(action: PumpManagementAction): Any? {
        when(action) {
            is ChangeCartridgePumpMgmtAction -> {
                when(action) {
                    ChangeCartridgePumpMgmtAction.CHECK_PUMP_STATE    -> return checkIfPumpIsRunning()
                    ChangeCartridgePumpMgmtAction.SUSPEND_PUMP        -> return suspendPumpCommand()
                    else -> {
                        aapsLogger.error(TAG, "Unknown ChangeCartridgePumpMgmtAction Pump Action (short): $action")
                        return null
                    }
                }
            }
            else -> {
                aapsLogger.error(TAG, "Unknown Pump Action (short): $action")
                return null
            }

        }
    }


    var currentAction: PumpManagementAction? = null

    override fun startLongAction(action: PumpManagementAction) {
        when(action) {
            is ChangeCartridgePumpMgmtAction -> {
                when(action) {
                    ChangeCartridgePumpMgmtAction.ENTER_CHANGE_CARTRIDGE_MODE -> {
                        currentAction = action
                        this.commandResponseMode = CommandResponseMode.EnterChangeCartridge
                        aapsLogger.info(TAG, "${commandResponseMode} starting")

                        sendCommand(EnterChangeCartridgeModeRequest(), CommandResponseMode.EnterChangeCartridge)
                    }
                    ChangeCartridgePumpMgmtAction.EXIT_CHANGE_CARTRIDGE_MODE  -> TODO()
                    else -> return
                }
            }
            else -> return
        }
    }

    var pumpManagementListener: PumpManagementListener? = null

    override fun startOperations(pumpManagementListener: PumpManagementListener) {
        this.pumpManagementListener = pumpManagementListener
        tandemCommunicationManager.communicationListener = this
    }


    override fun endOperations() {
        tandemCommunicationManager.communicationListener = null
    }

    // Change cartridge:
    // * send SuspendPumpingRequest()
    // * send EnterChangeCartridgeModeRequest()
    // * pump sends one or more EnterChangeCartridgeModeStateStreamResponse (stream message sent from pump with no initiating request) to report progress.
    // * Wait for EnterChangeCartridgeModeStateStreamResponse with state=READY_TO_CHANGE (id 2)
    // * Wait for initial call response (EnterChangeCartridgeModeResponse) with status=0
    // Now safe to tell user cartridge can be changed.
    //
    // When user says they are done changing cartridge:
    // * send ExitChangeCartridgeModeRequest()
    // * pump sends one or more DetectingCartridgeStateStreamResponse (stream message sent from pump with no initiating request) to report progress.
    // * multiple DetectingCartridgeStateStreamResponse's returned with a percentage status of detecting insulin amount in the cartridge. Wait for DetectingCartridgeStateStreamResponse with percentageComplete=100 (seems to send 5 messages in 20% intervals -- 20, 40, 60, 80, 100)
    // * Wait for call response (ExitChangeCartridgeModeResponse) with status=0
    // Cartridge fully detected and safe to move on to fill tubing.

    // Fill tubing:
    // * send EnterFillTubingModeRequest()
    // * wait for response EnterFillTubingModeResponse() with status=0
    // * Now, when the pump button is held down, insulin flows through the tubing.
    // * When the pump button is pressed down, we receive FillTubingStateStreamResponse() with buttonState=1. When pump button is released, we receive FillTubingStateStreamResponse() with buttonState=0. The Mobi app uses this to update the UI to tell you to hold down the button or release when you have filled enough insulin.
    // * When in buttonState=0 (aka while user is not actively holding down the button), UI should allow the user to complete the tubing fill process
    //
    //
    // When user done filling tubing:
    // * Send ExitFillTubingModeRequest()
    // * pump sends one or more ExitFillTubingModeStateStreamResponse (stream message sent from pump with no initiating request) to report progress.
    // * Wait for ExitFillTubingModeStateStreamResponse with state=TUBING_FILLED (id 0)
    // * wait for initial call response ExitFillTubingModeResponse() with status=0
    // Now safe to move on to fill cannula.
    //
    // To fill cannula:
    // * send FillCannulaRequest() with primeSizeMilliUnits arg between 0 and 3000 milliunits (i.e. 0 to 3 units)
    // * pump sends one or more FillCannulaStateStreamResponse (stream message sent from pump with no initiating request) to report progress.
    // * Wait for FillCannulaStateStreamResponse with state=CANNULA_FILLED (id 2)
    // * Wait for initial call response FillCannulaResponse with status=0


}