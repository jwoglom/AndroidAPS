package app.aaps.pump.tandem.mobi.ui.wizard

import android.bluetooth.BluetoothDevice
import app.aaps.pump.tandem.common.data.defs.TandemPumpApiVersion
import app.aaps.pump.tandem.common.events.PairingError
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ApiVersionResponse

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
    val pairingLabel: String = "",
    val pairingError: PairingError? = null,
    val retryCount: Int = 0,
    val isRePairing: Boolean = false,
    val pairedPumpSerial: String = "",
    val pairedPumpName: String = "",
    val pairedPumpApiVersion: TandemPumpApiVersion = TandemPumpApiVersion.Unknown
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
