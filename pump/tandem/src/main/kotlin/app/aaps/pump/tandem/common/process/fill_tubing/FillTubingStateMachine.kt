package app.aaps.pump.tandem.common.process.fill_tubing

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.tandem.common.comm.maint.TandemChangeFillManager
import app.aaps.pump.tandem.common.process.ActionStateMachine
import app.aaps.pump.tandem.common.process.ProcessState
import app.aaps.pump.tandem.common.process.PumpManagementController
import app.aaps.pump.tandem.common.process.PumpManagementListener
import app.aaps.pump.tandem.common.process.StateMachineAbstract
import app.aaps.pump.tandem.common.process.UIActionListener
import app.aaps.pump.tandem.common.process.change_cartridge.ChangeCartridgePumpMgmtAction
import app.aaps.pump.tandem.common.process.change_cartridge.ChangeCartridgeState
import app.aaps.pump.tandem.common.util.TandemPumpUtil


class FillTubingStateMachine constructor(
    //var uiActionListener: UIActionListener,
    tandemChangeFillManager: TandemChangeFillManager,
    tandemPumpUtil: TandemPumpUtil,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger
): StateMachineAbstract<FillTubingState>(
    tandemChangeFillManager = tandemChangeFillManager,
    tandemPumpUtil = tandemPumpUtil,
    resourceHelper = resourceHelper,
    aapsLogger = aapsLogger) {

    //var pumpManagementController : PumpManagementController = tandemChangeFillManager
    //var currentState : ChangeCartridgeState = ChangeCartridgeState.CHECK_PUMP_STATE

    //@JvmField var uiActionListener: UIActionListener? = null

    //override var startPumpOperationRequired = true


    // override fun setUiActionListener(uiActionListener: UIActionListener) {
    //     this.uiActionListener = uiActionListener
    // }

    override fun saveState(currentState: FillTubingState) {
        TODO("Not yet implemented")
    }

    override fun processStateStart(currentState: FillTubingState) {
        TODO("Not yet implemented")
    }

    override fun startStateMachine() {
        pumpManagementController.startOperations(this)
    }

    override fun receiveButtonClickEventFromUI(buttonText: String) {
        TODO("Not yet implemented")
    }

    override fun getName(): String {
        TODO("Not yet implemented")
    }

    // IMPORTANT: please leave this steps in order they are, I know they could have been group since
    //    most of them call the same method, but for easier reaablty how workflow should be leave them
    //    like this
    fun processStateStart(currentState: ChangeCartridgeState) {

        uiActionListener!!.setCurrentState(currentState)
        //this.currentState = currentState

        // when(currentState) {
        //
        //     // ChangeCartridgeStates.START                         -> {
        //     //     startStateMachine()
        //     //     uiActionListener!!.setSectionName("Change Cartridge")
        //     //
        //     //     // TODO retry mechanism - if state found that is not older than a day go to retry
        //     //     //   if not go to first step (for now we don't have retry)
        //     // }
        //
        //     ChangeCartridgeState.CHECK_PUMP_STATE                -> {
        //
        //
        //         // uiActionListener!!.setTitle("Pump State Check")
        //         // uiActionListener!!.setInstructions("We will now try to connect to pump, to determine its state.")
        //         // uiActionListener!!.disableButtonsAndReset()
        //         // uiActionListener!!.setImage("SUSPEND_CHECK") // TODO maybe send drawable?
        //         setUi(state = currentState)
        //
        //         executeShortCommandOnPump(ChangeCartridgePumpMgmtAction.CHECK_PUMP_STATE)
        //     }
        //
        //     ChangeCartridgeState.INVALID_SUSPEND_CHECK           -> {
        //         // uiActionListener!!.showShortTextStatus("There was problem determining state of pump. Please exit and try again later.")
        //         // uiActionListener!!.enableButton("Cancel", false)
        //         setUi(state = currentState)
        //     }
        //
        //
        //     ChangeCartridgeState.PUMP_ALREADY_SUSPENDED          -> {
        //         // uiActionListener!!.showShortTextStatus("Pump is already suspended. Press 'Continue' to go to next step or Exit if you want to exit.")
        //         // uiActionListener!!.enableButton("Cancel", false)
        //         // uiActionListener!!.enableButton("Continue", true)
        //         setUi(state = currentState)
        //     }
        //     ChangeCartridgeState.DO_YOU_WANT_TO_SUSPEND          -> {
        //         // uiActionListener!!.setTitle("Do you want to suspend?")
        //         // uiActionListener!!.setInstructions("Your pump is still running. Are you sure you want to do this? Selecting 'No' will exit.")
        //         // uiActionListener!!.disableButtonsAndReset()
        //         // uiActionListener!!.enableBothButtons("No", "Yes")
        //         // uiActionListener!!.setImage("QUESTION_MARK") // TODO maybe send drawable?
        //         setUi(state = currentState)
        //
        //     }
        //     ChangeCartridgeState.SUSPEND_IN_PROGRESS             -> {
        //         // uiActionListener!!.setTitle("Pump Suspend in progress")
        //         // uiActionListener!!.setInstructions("We are trying to suspend your pump. Please wait.")
        //         // uiActionListener!!.disableButtonsAndReset()
        //         setUi(state = currentState)
        //
        //         executeShortCommandOnPump(ChangeCartridgePumpMgmtAction.SUSPEND_PUMP)
        //     }
        //     ChangeCartridgeState.SUSPEND_COMPLETE                -> {
        //         uiActionListener!!.setTitle("Pump Suspend is complete")
        //         uiActionListener!!.setInstructions("Suspending of your pump was successful. Press 'Continue'.")
        //         uiActionListener!!.enableBothButtons("Cancel", "Continue")
        //     }
        //     ChangeCartridgeState.SUSPEND_FAILED                  -> {
        //         uiActionListener!!.setTitle("Pump Suspend failed")
        //         uiActionListener!!.setInstructions("Suspending of your pump failed. Press 'Cancel' and try again.")
        //         uiActionListener!!.enableButton("Cancel", false)
        //     }
        //     ChangeCartridgeState.DISCONNECT_INFUSION_SET         -> {
        //         uiActionListener!!.setTitle("Disconnect infusion set")
        //         uiActionListener!!.setImage("DISCONNECT_INFUSION_SET")
        //         uiActionListener!!.setInstructions("Make sure the infusion set is disconnected from your body and have a filled cartridge ready.")
        //         uiActionListener!!.enableBothButtons("Cancel", "Continue")
        //         saveState(currentState)
        //
        //     }
        //     ChangeCartridgeState.PREPARING_FOR_CARTRIDGE         -> {
        //         uiActionListener!!.setTitle("Preparing for Cartridge")
        //         uiActionListener!!.setImage("PREPARING")  // animated maybe?
        //         uiActionListener!!.setInstructions("Waiting for pump.")
        //         uiActionListener!!.enableBothButtons("Cancel", "Continue")
        //
        //
        //         sendLongCommandToPump(ChangeCartridgePumpMgmtAction.ENTER_CHANGE_CARTRIDGE_MODE)
        //
        //     }
        //     ChangeCartridgeState.PUMP_READY                      -> {
        //         uiActionListener!!.showShortTextStatus("Pump is Ready for Cartridge removal. Press 'Continue'.")  // animated maybe?
        //         uiActionListener!!.enableButton("Continue", true)
        //     }
        //     ChangeCartridgeState.REMOVE_AND_INSTALL_CARTRIDGE    -> {
        //         uiActionListener!!.setTitle("Remove and Install Cartridge")
        //         uiActionListener!!.setImage("REMOVE_AND_INSTALL")  // animated maybe?
        //         uiActionListener!!.setInstructions("Twist to remove the cartridge then install a filled cartridge.")
        //         uiActionListener!!.enableBothButtons("Cancel", "Continue")
        //
        //
        //     }
        //     ChangeCartridgeState.CARTRIDGE_INSTALL_IN_PROGRESS   -> {
        //         uiActionListener!!.setTitle("Cartridge install in progress")
        //         uiActionListener!!.setImage("CARTRIDGE_INSTALL_IN_PROGRESS")  // animated maybe?
        //         uiActionListener!!.setInstructions("Waiting for pump response.")
        //         uiActionListener!!.enableBothButtons("Cancel", "Continue")
        //
        //         sendLongCommandToPump(ChangeCartridgePumpMgmtAction.EXIT_CHANGE_CARTRIDGE_MODE)
        //
        //     }
        //     ChangeCartridgeState.CARTRIDGE_CHANGED               -> {
        //         uiActionListener!!.setTitle("Cartridge Changed")
        //         uiActionListener!!.setImage("CARTRIDGE_CHANGED")  // animated maybe?
        //         uiActionListener!!.setInstructions("Cartridge was changed. ")
        //         uiActionListener!!.enableBothButtons("Cancel", "Continue")
        //
        //
        //         TODO()
        //     }
        //
        //     ChangeCartridgeState.EXIT                            -> {
        //         pumpManagementController.endOperations()
        //         uiActionListener!!.closeDialog()
        //     }
        //
        //     ChangeCartridgeState.SWITCH_TO_FILLING_STATE_MACHINE -> TODO()
        //
        //     else -> {}
        // }

    }





    // private fun setUi(state: ProcessState) {
    //     // if (state.instruction!=null) {
    //     //     uiActionListener!!.setInstructions(state.instruction)
    //     // }
    //     //
    //     // if (state.title!=null) {
    //     //     uiActionListener!!.setTitle(state.title)
    //     // }
    //     //
    //     // if (state.getLeftButtonText()==null && state.getRightButtonText()==null) {
    //     //     uiActionListener!!.disableButtonsAndReset()
    //     // } else {
    //     //     uiActionListener!!.enableBothButtons(state.leftButtonText, state.rightButtonText)
    //     // }
    //     //
    //     // if (state.image!=null) {
    //     //     uiActionListener!!.setImage(state.image)
    //     // }
    //
    //
    // }


    private fun executeShortCommandOnPump(checkPumpState: ChangeCartridgePumpMgmtAction) {
        tandemPumpUtil.sleep(500)

        when(checkPumpState) {
            ChangeCartridgePumpMgmtAction.CHECK_PUMP_STATE            -> {
                val result = pumpManagementController.startShortAction(ChangeCartridgePumpMgmtAction.CHECK_PUMP_STATE) as Boolean?

                when(result) {
                    true  -> processStateStart(ChangeCartridgeState.DO_YOU_WANT_TO_SUSPEND)
                    false -> processStateStart(ChangeCartridgeState.PUMP_ALREADY_SUSPENDED)
                    null  -> processStateStart(ChangeCartridgeState.INVALID_SUSPEND_CHECK)
                }
            }
            ChangeCartridgePumpMgmtAction.SUSPEND_PUMP                -> {
                val result = pumpManagementController.startShortAction(ChangeCartridgePumpMgmtAction.CHECK_PUMP_STATE) as Boolean

                when(result) {
                    true  -> processStateStart(ChangeCartridgeState.SUSPEND_COMPLETE)
                    false -> processStateStart(ChangeCartridgeState.SUSPEND_FAILED)
                }

            }
            else -> { return  }
        }


    }

    private fun sendLongCommandToPump(enterChangeCartridgeMode: ChangeCartridgePumpMgmtAction) {
        tandemPumpUtil.sleep(500)



        TODO("Not yet implemented")
    }



    private fun saveState(changeCartridgeState: ChangeCartridgeState) {
        //TODO("Not yet implemented")
        // TODO saveState make actions retryable

    }

    // var exitButton = "Cancel"
    //
    // fun receiveEventFromUI(buttonText: String, state: ProcessState) {
    //     var stateInternal = state as ChangeCartridgeState
    //
    //     if (exitButton.equals(buttonText)) {
    //         processStateStart(ChangeCartridgeState.EXIT)
    //         return
    //     }
    //
    //
    //     when(stateInternal) {
    //         ChangeCartridgeState.INVALID_SUSPEND_CHECK         -> {
    //             processStateStart(ChangeCartridgeState.EXIT)
    //         }
    //         ChangeCartridgeState.CHECK_PUMP_STATE              -> TODO()
    //
    //         ChangeCartridgeState.PUMP_ALREADY_SUSPENDED        -> {
    //             if (exitButton.equals(buttonText)) {
    //                 processStateStart(ChangeCartridgeState.EXIT)
    //             } else {
    //                 processStateStart(ChangeCartridgeState.DISCONNECT_INFUSION_SET)
    //             }
    //         }
    //         ChangeCartridgeState.DO_YOU_WANT_TO_SUSPEND        -> TODO()
    //         ChangeCartridgeState.SUSPEND_IN_PROGRESS           -> TODO()
    //         ChangeCartridgeState.SUSPEND_COMPLETE              -> TODO()
    //         ChangeCartridgeState.SUSPEND_FAILED                -> TODO()
    //         ChangeCartridgeState.DISCONNECT_INFUSION_SET       -> TODO()
    //         ChangeCartridgeState.PREPARING_FOR_CARTRIDGE       -> TODO()
    //         ChangeCartridgeState.PUMP_READY                    -> TODO()
    //         ChangeCartridgeState.REMOVE_AND_INSTALL_CARTRIDGE  -> TODO()
    //         ChangeCartridgeState.CARTRIDGE_INSTALL_IN_PROGRESS -> TODO()
    //         ChangeCartridgeState.CARTRIDGE_CHANGED             -> TODO()
    //
    //         else -> {
    //             aapsLogger.error(LTag.PUMPCOMM, "We received wrong event from UI")
    //         }
    //     }
    // }


    override fun sendDebugInfo(text: String) {
        uiActionListener!!.displayLongStatus(text)
    }

    override fun sendStatusInfo(text: String) {
        uiActionListener!!.showShortTextStatus(text)
    }

    override fun sendLongActionComplete() {

        when(currentState) {
            // ChangeCartridgeState.PREPARING_FOR_CARTRIDGE       -> {
            // }
            // ChangeCartridgeState.CARTRIDGE_CHANGED             -> TODO()
            // ChangeCartridgeState.CARTRIDGE_INSTALL_IN_PROGRESS -> TODO()
            // ChangeCartridgeState.REMOVE_AND_INSTALL_CARTRIDGE  -> TODO()
            // ChangeCartridgeState.PUMP_READY                    -> TODO()
            // ChangeCartridgeState.DISCONNECT_INFUSION_SET       -> TODO()
            // ChangeCartridgeState.PUMP_ALREADY_SUSPENDED        -> TODO()
            // ChangeCartridgeState.INVALID_SUSPEND_CHECK         -> TODO()
            // ChangeCartridgeState.CHECK_PUMP_STATE              -> TODO()
            else                                               -> return
        }

        TODO("Not yet implemented")
    }

}