package app.aaps.pump.tandem.common.process.change_cartridge

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import app.aaps.pump.tandem.R
import app.aaps.pump.tandem.common.process.ProcessState

/**
 * Change Cartridge States - order of states in file is reversed correct, this is why there are numbers besode them
 */
enum class ChangeCartridgeState constructor(
    @JvmField val title: String? = null,
    @JvmField val instruction: String? = null,
    @JvmField val statusText: String? = null,
    @JvmField @StringRes val leftButtonText: Int? = null,
    @JvmField @StringRes val rightButtonText: Int? = null,
    @JvmField val leftButtonState: ChangeCartridgeState? = null,
    @JvmField val rightButtonState: ChangeCartridgeState? = null,
    @JvmField val image: String? = null
): ProcessState {

    EXIT, // Exit

    SWITCH_TO_FILLING_STATE_MACHINE,

    CARTRIDGE_CHANGED,

    CARTRIDGE_INSTALL_IN_PROGRESS,

    REMOVE_AND_INSTALL_CARTRIDGE,

    PUMP_READY,

    PREPARING_FOR_CARTRIDGE,

    DISCONNECT_INFUSION_SET(title = "Disconnect infusion set",
                            image = "DISCONNECT_INFUSION_SET",
                            instruction = "Make sure the infusion set is disconnected from your body and have a filled cartridge ready.",
                            leftButtonText = R.string.state_machine_common_cancel,
                            leftButtonState = EXIT,
                            rightButtonText = R.string.state_machine_common_continue,
                            rightButtonState = PREPARING_FOR_CARTRIDGE), // 2

    SUSPEND_COMPLETE(title = "Pump Suspend is complete",
                     instruction = "Suspending of your pump was successful. Press 'Continue'.",
                     leftButtonText = R.string.state_machine_common_cancel,
                     leftButtonState = EXIT,
                     rightButtonText = R.string.state_machine_common_continue,
                     rightButtonState = DISCONNECT_INFUSION_SET),

    SUSPEND_FAILED(title = "Pump Suspend failed",
                   instruction = "Suspending of your pump failed. Press 'Cancel' and try again.",
                   leftButtonText = R.string.state_machine_common_cancel,
                   leftButtonState = EXIT),

    SUSPEND_IN_PROGRESS(title = "Pump Suspend in progress",
                        instruction = "We are trying to suspend your pump. Please wait."),

    DO_YOU_WANT_TO_SUSPEND(title = "Do you want to suspend?",
                           instruction = "Your pump is still running. Are you sure you want to do this? Selecting 'No' will exit.",
                           leftButtonText = R.string.state_machine_common_no,
                           leftButtonState = EXIT,
                           rightButtonText = R.string.state_machine_common_yes,
                           rightButtonState = SUSPEND_IN_PROGRESS,
                           image = "QUESTION_MARK"),

    PUMP_ALREADY_SUSPENDED(statusText = "Pump is already suspended. Press 'Continue' to go to next step or 'Cancel' if you want to exit.",
                           leftButtonText = R.string.state_machine_common_cancel,
                           leftButtonState = EXIT,
                           rightButtonText = R.string.state_machine_common_continue,
                           rightButtonState = DISCONNECT_INFUSION_SET), // 1_2B

    INVALID_SUSPEND_CHECK(statusText = "There was problem determining state of pump. Please exit and try again later.",
                          leftButtonText = R.string.state_machine_common_cancel,
                          leftButtonState = EXIT), // 1_2C

    CHECK_PUMP_STATE(title = "Pump State Check",
                  instruction = "We will now try to connect to pump, to determine its state.",
                  image = "SUSPEND_CHECK" ) // 1_1
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