package app.aaps.pump.tandem.common.process

import androidx.annotation.StringRes

interface UIActionListener {

    fun setSectionName(title: String)
    fun setTitle(title: String)
    fun setInstructions(text: String)
    fun showShortTextStatus(text: String, withDelay: Boolean = false)
    fun setImage(name: String)
    fun disableButtonsAndReset()
    fun enableButton(@StringRes buttonText: Int, rightButton: Boolean)
    fun enableBothButtons(@StringRes leftButton: Int?, @StringRes rightButton: Int?)
    fun closeDialog()
    fun displayLongStatus(text: String)
    fun setCurrentState(processState: ProcessState)

}