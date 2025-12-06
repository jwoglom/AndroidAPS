package app.aaps.pump.tandem.mobi.ui.wizard

import android.bluetooth.BluetoothDevice
import app.aaps.pump.tandem.common.events.PairingError

/**
 * State for the Tandem Mobi connection wizard
 */
data class TandemMobiWizardState(
    val currentStep: WizardStep = WizardStep.Introduction,
    val selectedDevice: BluetoothDevice? = null,
    val deviceAddress: String = "",
    val deviceName: String = "",
    val enteredPIN: String = "",
    val pairingStatus: Int = -1,
    val pairingError: PairingError? = null,
    val retryCount: Int = 0,
    val isRePairing: Boolean = false,
    val pairedPumpSerial: String = "",
    val pairedPumpName: String = ""
)

/**
 * Steps in the connection wizard
 */
sealed class WizardStep {
    object Introduction : WizardStep()
    object SelectDevice : WizardStep()
    object EnterPIN : WizardStep()
    object Pairing : WizardStep()
    data class Error(val error: PairingError) : WizardStep()
    object Complete : WizardStep()
}
