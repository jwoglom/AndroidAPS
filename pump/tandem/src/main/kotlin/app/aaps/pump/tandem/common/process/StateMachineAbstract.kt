package app.aaps.pump.tandem.common.process

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.tandem.common.comm.maint.TandemPumpActionManager
import app.aaps.pump.tandem.common.util.TandemPumpUtil

abstract class StateMachineAbstract<T: ProcessState> constructor(
    var tandemPumpActionManager: TandemPumpActionManager,
    var tandemPumpUtil: TandemPumpUtil,
    var resourceHelper: ResourceHelper,
    var aapsLogger: AAPSLogger
) : ActionStateMachine, PumpManagementListener {

    var _currentState : T? = null
    val currentState get() = _currentState!!

    var pumpManagementController : PumpManagementController = tandemPumpActionManager

    var _uiActionListener: UIActionListener? = null
    val uiActionListener get() = _uiActionListener!!

    override var startPumpOperationRequired = true

    override fun getName() = this.javaClass.simpleName



    override fun setUiActionListener(uiActionListener: UIActionListener) {
        this._uiActionListener = uiActionListener
    }


   fun setUi(state: T) {
       if (state.getInstruction()!=null) {
           uiActionListener.setInstructions(state.getInstruction()!!)
       }

       if (state.getTitle()!=null) {
           uiActionListener.setTitle(state.getTitle()!!)
       }

       if (state.getLeftButtonText()==null && state.getRightButtonText()==null) {
           uiActionListener.disableButtonsAndReset()
       } else {
           uiActionListener.enableBothButtons(state.getLeftButtonText(), state.getRightButtonText())
       }

       if (state.getImage()!=null) {
           uiActionListener.setImage(state.getImage()!!)
       }

   }


    abstract fun processStateStart(currentState: T)

    abstract fun saveState(currentState: T)


    override fun receiveButtonClickEventFromUI(buttonText: String) {

        if (this.currentState.getLeftButtonText()==null) {
            processStateStart(this.currentState.getRightButtonProcessState() as T)  // if left button is null then we have only right one
        } else {
            val leftValue = resourceHelper.gs(this.currentState.getLeftButtonText()!!)

            if (buttonText.equals(leftValue)) {
                processStateStart(this.currentState.getLeftButtonProcessState() as T)
            } else {
                processStateStart(this.currentState.getRightButtonProcessState() as T)
            }
        }
        // resourceHelper.gs(this.currentState.getLeftButtonText()!!)
        // // this.currentState.getLeftButtonText()
        //
        // if (buttonText.equals(this.currentState.getLeftButtonText())) {
        //     processStateStart(this.currentState.getLeftButtonProcessState() as T)
        // } else {
        //     processStateStart(this.currentState.getRightButtonProcessState() as T)
        // }
    }


    override fun sendDebugInfo(text: String) {
        uiActionListener.displayLongStatus(text)
    }


    override fun sendStatusInfo(text: String, withDelay: Boolean) {
        uiActionListener.showShortTextStatus(text = text, withDelay = withDelay)
    }




}