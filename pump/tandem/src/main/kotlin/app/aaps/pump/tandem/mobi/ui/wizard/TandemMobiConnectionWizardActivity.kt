package app.aaps.pump.tandem.mobi.ui.wizard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.common.ui.PumpBLEConfigActivity
import app.aaps.pump.tandem.R
import app.aaps.pump.tandem.common.comm.maint.TandemPairingManager
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.keys.TandemStringPreferenceKey
import app.aaps.pump.tandem.common.ui.TandemPumpBLEConfigActivity
import app.aaps.pump.tandem.common.util.PumpX2L
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import app.aaps.pump.tandem.mobi.ui.theme.TMobiScreensTheme
import app.aaps.pump.tandem.mobi.ui.wizard.screens.ConnectionCompleteScreen
import app.aaps.pump.tandem.mobi.ui.wizard.screens.EnterPINScreen
import app.aaps.pump.tandem.mobi.ui.wizard.screens.ErrorScreen
import app.aaps.pump.tandem.mobi.ui.wizard.screens.IntroductionScreen
import app.aaps.pump.tandem.mobi.ui.wizard.screens.PairingScreen
import dagger.android.support.DaggerAppCompatActivity
import javax.inject.Inject

/**
 * Jetpack Compose-based wizard for pairing Tandem Mobi pump
 * Follows the pattern of Omnipod Dash activation wizard
 */
class TandemMobiConnectionWizardActivity : DaggerAppCompatActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var tandemPumpUtil: TandemPumpUtil
    @Inject lateinit var pumpStatus: TandemPumpStatus
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var pumpX2L: PumpX2L
    @Inject lateinit var pumpSync: PumpSync

    private lateinit var viewModel: TandemMobiConnectionWizardViewModel
    private var pairingManager: TandemPairingManager? = null
    private var needsPairingReset = false

    // Activity launcher for BLE device selection
    private val selectDeviceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Device was selected, read from preferences
            val address = preferences.get(TandemStringPreferenceKey.PumpAddress)
            val name = preferences.get(TandemStringPreferenceKey.PumpName)

            if (address.isNotEmpty()) {
                aapsLogger.info(LTag.PUMP, "Device selected from BLE config: $name ($address)")
                createPairingManager(address)
                viewModel.onDeviceSelected(address, name)
            } else {
                aapsLogger.error(LTag.PUMP, "No device address found after BLE config activity")
            }
        } else {
            aapsLogger.info(LTag.PUMP, "Device selection cancelled")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ViewModel
        viewModel = TandemMobiConnectionWizardViewModel(
            aapsLogger = aapsLogger,
            rxBus = rxBus,
            aapsSchedulers = aapsSchedulers,
            preferences = preferences,
            tandemPumpUtil = tandemPumpUtil
        )

        // Check if this is a re-pairing request
        val isRePairing = intent.getBooleanExtra(EXTRA_IS_RE_PAIRING, false)
        if (isRePairing) {
            needsPairingReset = true
            viewModel.startRePairing()
        }

        setContent {
            TMobiScreensTheme {
                WizardContent(
                    viewModel = viewModel,
                    onLaunchDeviceSelector = { launchDeviceSelector() },
                    onFinish = { finish() }
                )
            }
        }
    }

    private fun launchDeviceSelector() {
        aapsLogger.info(LTag.PUMP, "Launching device selector")
        val intent = Intent(this, TandemPumpBLEConfigActivity::class.java).apply {
            putExtra(TandemPumpBLEConfigActivity.EXTRA_SELECTION_ONLY, true)
        }
        selectDeviceLauncher.launch(intent)
    }

    private fun createPairingManager(btAddress: String): TandemPairingManager? {
        if (btAddress.isEmpty()) {
            return pairingManager
        }

        pairingManager?.let {
            if (it.btAddress == btAddress) {
                return it
            }
            it.shutdownPairingManager()
        }

        // Create a dummy PumpBLEConfigActivity interface for the pairing manager
        val dummyActivity = object : PumpBLEConfigActivity() {}

        pairingManager = TandemPairingManager(
            context = this,
            aapsLogger = aapsLogger,
            preferences = preferences,
            tandemPumpUtil = tandemPumpUtil,
            btAddress = btAddress,
            resourceHelper = resourceHelper,
            rxBus = rxBus,
            pumpStatus = pumpStatus,
            pumpSync = pumpSync,
            activity = dummyActivity,
            pumpX2L = pumpX2L,
            aapsSchedulers = aapsSchedulers
        ).also { manager ->
            viewModel.setPairingManager(manager)
            // Always clear pairing data at the start of pairing to ensure clean state
            manager.clearPairingData()
        }

        needsPairingReset = false

        return pairingManager
    }

    override fun onDestroy() {
        super.onDestroy()
        pairingManager?.shutdownPairingManager()
    }

    companion object {
        const val EXTRA_IS_RE_PAIRING = "is_re_pairing"
    }
}

@Composable
private fun WizardContent(
    viewModel: TandemMobiConnectionWizardViewModel,
    onLaunchDeviceSelector: () -> Unit,
    onFinish: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            Column {
                Text(
                    text = stringResource(R.string.tandem_wizard_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textAlign = TextAlign.Center
                )
                // Progress indicator
                val progress = when (state.currentStep) {
                    is WizardStep.Introduction -> 0.0f
                    is WizardStep.SelectDevice -> 0.25f
                    is WizardStep.EnterPIN -> 0.5f
                    is WizardStep.Pairing -> 0.75f
                    is WizardStep.Error -> 0.75f // Stay at same progress
                    is WizardStep.Complete -> 1.0f
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (val step = state.currentStep) {
                is WizardStep.Introduction -> {
                    IntroductionScreen(
                        isRePairing = state.isRePairing,
                        onBeginPairing = {
                            viewModel.onIntroductionComplete()
                            onLaunchDeviceSelector()
                        }
                    )
                }
                is WizardStep.SelectDevice -> {
                    // This screen is handled by TandemPumpBLEConfigActivity
                    // We launch it via the activity result launcher
                    // After selection, we'll move to EnterPIN screen
                    Text(
                        text = stringResource(R.string.tandem_wizard_waiting_device_selection),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        textAlign = TextAlign.Center
                    )
                }
                is WizardStep.EnterPIN -> {
                    EnterPINScreen(
                        deviceName = state.deviceName,
                        deviceAddress = state.deviceAddress,
                        enteredPIN = state.enteredPIN,
                        onPINChanged = viewModel::onPINChanged,
                        onNext = viewModel::onPINComplete,
                        onBack = { onLaunchDeviceSelector() }
                    )
                }
                is WizardStep.Pairing -> {
                    PairingScreen(
                        pairingStatus = state.pairingStatus
                    )
                }
                is WizardStep.Error -> {
                    ErrorScreen(
                        error = step.error,
                        retryCount = state.retryCount,
                        onEditPIN = viewModel::onEditPIN,
                        onRetry = viewModel::onRetryPairing,
                        onCancelAndRescan = {
                            viewModel.onCancelAndRescan()
                            onLaunchDeviceSelector()
                        }
                    )
                }
                is WizardStep.Complete -> {
                    ConnectionCompleteScreen(
                        pumpSerial = state.pairedPumpSerial,
                        pumpName = state.pairedPumpName,
                        onFinish = onFinish
                    )
                }
            }
        }
    }
}
