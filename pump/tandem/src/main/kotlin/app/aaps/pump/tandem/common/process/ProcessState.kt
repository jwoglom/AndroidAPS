package app.aaps.pump.tandem.common.process

import androidx.annotation.IdRes
import androidx.annotation.StringRes

interface ProcessState {
    fun getKey(): String

    @StringRes fun getLeftButtonText(): Int?
    @StringRes fun getRightButtonText(): Int?
    fun getLeftButtonProcessState(): ProcessState?
    fun getRightButtonProcessState(): ProcessState?

    fun getTitle(): String?
    fun getInstruction(): String?
    fun getStatusText(): String?
    fun getImage(): String?

    //@IdRes val leftButtonText: Int?
}