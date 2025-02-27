package app.aaps.pump.tandem.common.process

interface ActionStateMachine {

    var startPumpOperationRequired : Boolean

    fun startStateMachine()

    fun receiveButtonClickEventFromUI(buttonText: String)

    fun getName(): String

    fun setUiActionListener(uiActionListener: UIActionListener)

}