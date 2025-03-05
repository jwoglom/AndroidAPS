package app.aaps.pump.tandem.common.process.change_cartridge

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.tandem.R
import app.aaps.pump.tandem.common.comm.maint.TandemPumpActionManager
import app.aaps.pump.tandem.common.process.StateMachineAbstract
import app.aaps.pump.tandem.common.process.fill_tubing.FillTubingStateMachine
import app.aaps.pump.tandem.common.util.TandemPumpUtil


class ChangeCartridgeStateMachine constructor(
    tandemPumpActionManager: TandemPumpActionManager,
    tandemPumpUtil: TandemPumpUtil,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger
): StateMachineAbstract<ChangeCartridgeState>(
    tandemPumpActionManager = tandemPumpActionManager,
    tandemPumpUtil = tandemPumpUtil,
    resourceHelper = resourceHelper,
    aapsLogger = aapsLogger) {



    // var pumpManagementController : PumpManagementController = tandemChangeFillManager
    //var currentState : ChangeCartridgeStates = ChangeCartridgeStates.CHECK_PUMP_STATE

    // @JvmField var uiActionListener: UIActionListener? = null
    //
    // override var startPumpOperationRequired = true




    override fun startStateMachine() {
        // if (startPumpOperationRequired) {
        //     //pumpManagementController.startOperations(this)
        // }

        pumpManagementController.startOperations(this)

        uiActionListener!!.setSectionName("Change Cartridge")

        //pumpManagementController.

        // TODO retry mechanism - if state found that is not older than a day go to retry
        //   if not go to first step (for now we don't have retry)
        processStateStart(ChangeCartridgeState.CHECK_PUMP_STATE)

    }





    // IMPORTANT: please leave this steps in order they are, I know they could have been group since
    //    most of them call the same method, but for easier reaablty how workflow should be leave them
    //    like this
    override fun processStateStart(currentState: ChangeCartridgeState) {

        uiActionListener.setCurrentState(currentState)
        this._currentState = currentState

        when(currentState) {

            ChangeCartridgeState.CHECK_PUMP_STATE                -> {
                setUi(state = currentState)

                uiActionListener.enableButton(R.string.common_on, false)
                executeShortCommandOnPump(ChangeCartridgePumpMgmtAction.CHECK_PUMP_STATE)
            }

            ChangeCartridgeState.INVALID_SUSPEND_CHECK           -> {
                setUi(state = currentState)
            }

            ChangeCartridgeState.PUMP_ALREADY_SUSPENDED          -> {
                setUi(state = currentState)
            }

            ChangeCartridgeState.DO_YOU_WANT_TO_SUSPEND          -> {
                setUi(state = currentState)
            }

            ChangeCartridgeState.SUSPEND_IN_PROGRESS             -> {
                setUi(state = currentState)
                executeShortCommandOnPump(ChangeCartridgePumpMgmtAction.SUSPEND_PUMP)
            }

            ChangeCartridgeState.SUSPEND_COMPLETE                -> {
                setUi(state = currentState)
            }

            ChangeCartridgeState.SUSPEND_FAILED                  -> {
                setUi(state = currentState)
            }

            ChangeCartridgeState.DISCONNECT_INFUSION_SET         -> {
                uiActionListener!!.setTitle("Disconnect infusion set")
                uiActionListener!!.setImage("DISCONNECT_INFUSION_SET")
                uiActionListener!!.setInstructions("Make sure the infusion set is disconnected from your body and have a filled cartridge ready.")
                //uiActionListener!!.enableBothButtons("Cancel", "Continue")
                saveState(currentState)

            }
            ChangeCartridgeState.PREPARING_FOR_CARTRIDGE         -> {
                uiActionListener!!.setTitle("Preparing for Cartridge")
                uiActionListener!!.setImage("PREPARING")  // animated maybe?
                uiActionListener!!.setInstructions("Waiting for pump.")
               // uiActionListener!!.enableBothButtons("Cancel", "Continue")


                sendLongCommandToPump(ChangeCartridgePumpMgmtAction.ENTER_CHANGE_CARTRIDGE_MODE)

            }
            ChangeCartridgeState.PUMP_READY                      -> {
                uiActionListener!!.showShortTextStatus("Pump is Ready for Cartridge removal. Press 'Continue'.")  // animated maybe?
            //    uiActionListener!!.enableButton("Continue", true)
            }
            ChangeCartridgeState.REMOVE_AND_INSTALL_CARTRIDGE    -> {
                uiActionListener!!.setTitle("Remove and Install Cartridge")
                uiActionListener!!.setImage("REMOVE_AND_INSTALL")  // animated maybe?
                uiActionListener!!.setInstructions("Twist to remove the cartridge then install a filled cartridge.")
            //    uiActionListener!!.enableBothButtons("Cancel", "Continue")


            }
            ChangeCartridgeState.CARTRIDGE_INSTALL_IN_PROGRESS   -> {
                uiActionListener!!.setTitle("Cartridge install in progress")
                uiActionListener!!.setImage("CARTRIDGE_INSTALL_IN_PROGRESS")  // animated maybe?
                uiActionListener!!.setInstructions("Waiting for pump response.")
            //    uiActionListener!!.enableBothButtons("Cancel", "Continue")

                sendLongCommandToPump(ChangeCartridgePumpMgmtAction.EXIT_CHANGE_CARTRIDGE_MODE)

            }
            ChangeCartridgeState.CARTRIDGE_CHANGED               -> {
                uiActionListener!!.setTitle("Cartridge Changed")
                uiActionListener!!.setImage("CARTRIDGE_CHANGED")  // animated maybe?
                uiActionListener!!.setInstructions("Cartridge was changed. You can now exit with Cancel or use Continue to start Tube Filling.")
            //    uiActionListener!!.enableBothButtons("Cancel", "Continue")



            }

            ChangeCartridgeState.EXIT                            -> {
                pumpManagementController.endOperations()
                uiActionListener.closeDialog()
            }

            ChangeCartridgeState.SWITCH_TO_FILLING_STATE_MACHINE -> {
                val fillTubingStateMachine =
                    FillTubingStateMachine(tandemPumpActionManager = tandemPumpActionManager,
                                           tandemPumpUtil = tandemPumpUtil,
                                           resourceHelper = resourceHelper,
                                           aapsLogger = aapsLogger)

                fillTubingStateMachine.startPumpOperationRequired = false
                fillTubingStateMachine.setUiActionListener(uiActionListener = uiActionListener)
                fillTubingStateMachine.startStateMachine()


            }
        }

    }


    // private fun setUi(state: ChangeCartridgeStates) {
    //     if (state.instruction!=null) {
    //         uiActionListener!!.setInstructions(state.instruction)
    //     }
    //
    //     if (state.title!=null) {
    //         uiActionListener!!.setTitle(state.title)
    //     }
    //
    //     if (state.leftButtonText==null && state.rightButtonText==null) {
    //         uiActionListener!!.disableButtonsAndReset()
    //     } else {
    //         uiActionListener!!.enableBothButtons(state.leftButtonText, state.rightButtonText)
    //     }
    //
    //     if (state.image!=null) {
    //         uiActionListener!!.setImage(state.image)
    //     }
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

        pumpManagementController.startLongAction(enterChangeCartridgeMode);
    }



    override fun saveState(currentState: ChangeCartridgeState) {
        //TODO("Not yet implemented")
        // TODO saveState make actions retryable

    }

    var exitButton = "Cancel"

    // override fun receiveButtonClickEventFromUI(buttonText: String) {
    //     if (buttonText.equals(this.currentState.leftButtonText)) {
    //         processStateStart(this.currentState.leftButtonState!!)
    //     } else {
    //         processStateStart(this.currentState.rightButtonState!!)
    //     }
    // }

    // fun receiveEventFromUI(buttonText: String, state: ProcessState) {
    //     var stateInternal = state as ChangeCartridgeStates
    //
    //     if (exitButton.equals(buttonText)) {
    //         processStateStart(ChangeCartridgeStates.EXIT)
    //         return
    //     }
    //
    //
    //     when(stateInternal) {
    //         ChangeCartridgeStates.INVALID_SUSPEND_CHECK         -> {
    //             processStateStart(ChangeCartridgeStates.EXIT)
    //         }
    //         ChangeCartridgeStates.CHECK_PUMP_STATE                 -> TODO()
    //
    //         ChangeCartridgeStates.PUMP_ALREADY_SUSPENDED        -> {
    //             if (exitButton.equals(buttonText)) {
    //                 processStateStart(ChangeCartridgeStates.EXIT)
    //             } else {
    //                 processStateStart(ChangeCartridgeStates.DISCONNECT_INFUSION_SET)
    //             }
    //         }
    //         ChangeCartridgeStates.DO_YOU_WANT_TO_SUSPEND        -> TODO()
    //         ChangeCartridgeStates.SUSPEND_IN_PROGRESS           -> TODO()
    //         ChangeCartridgeStates.SUSPEND_COMPLETE              -> TODO()
    //         ChangeCartridgeStates.SUSPEND_FAILED                -> TODO()
    //         ChangeCartridgeStates.DISCONNECT_INFUSION_SET       -> TODO()
    //         ChangeCartridgeStates.PREPARING_FOR_CARTRIDGE       -> TODO()
    //         ChangeCartridgeStates.PUMP_READY                    -> TODO()
    //         ChangeCartridgeStates.REMOVE_AND_INSTALL_CARTRIDGE  -> TODO()
    //         ChangeCartridgeStates.CARTRIDGE_INSTALL_IN_PROGRESS -> TODO()
    //         ChangeCartridgeStates.CARTRIDGE_CHANGED             -> TODO()
    //
    //         else -> {
    //             aapsLogger.error(LTag.PUMPCOMM, "We received wrong event from UI")
    //         }
    //     }
    // }




    override fun sendLongActionComplete() {

        when(currentState) {
            ChangeCartridgeState.PREPARING_FOR_CARTRIDGE       -> {

            }
            ChangeCartridgeState.CARTRIDGE_CHANGED             -> TODO()
            ChangeCartridgeState.CARTRIDGE_INSTALL_IN_PROGRESS -> TODO()
            ChangeCartridgeState.REMOVE_AND_INSTALL_CARTRIDGE  -> TODO()
            ChangeCartridgeState.PUMP_READY                    -> TODO()
            ChangeCartridgeState.DISCONNECT_INFUSION_SET       -> TODO()
            ChangeCartridgeState.PUMP_ALREADY_SUSPENDED        -> TODO()
            ChangeCartridgeState.INVALID_SUSPEND_CHECK         -> TODO()
            ChangeCartridgeState.CHECK_PUMP_STATE              -> TODO()
            else                                               -> return
        }

        TODO("Not yet implemented")
    }

}