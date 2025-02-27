package app.aaps.pump.tandem.common.process.fill_tubing

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import app.aaps.pump.tandem.common.process.ProcessState
import app.aaps.pump.tandem.common.process.change_cartridge.ChangeCartridgeState

enum class FillTubingState constructor(
    @JvmField val title: String? = null,
    @JvmField val instruction: String? = null,
    @JvmField val statusText: String? = null,
    @JvmField @StringRes val leftButtonText: Int? = null,
    @JvmField @StringRes val rightButtonText: Int? = null,
    @JvmField val leftButtonState: ChangeCartridgeState? = null,
    @JvmField val rightButtonState: ChangeCartridgeState? = null,
    @JvmField val image: String? = null
): ProcessState {

    EXIT
    ,

    START, //

    ;

    override fun getKey() = name
    @StringRes override fun getLeftButtonText(): Int? = leftButtonText
    @StringRes override fun getRightButtonText(): Int? = rightButtonText
    override fun getLeftButtonProcessState(): ProcessState? = leftButtonState
    override fun getRightButtonProcessState(): ProcessState? = rightButtonState
    override fun getTitle(): String? = title
    override fun getInstruction(): String? = instruction
    override fun getStatusText(): String? = statusText
    override fun getImage(): String? = image

}