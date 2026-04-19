package app.aaps.pump.tandem.mobi.ui.wizard

import android.bluetooth.BluetoothManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.tandem.R
import app.aaps.pump.tandem.common.comm.maint.TandemPairingManager
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.util.PumpX2L
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import app.aaps.pump.tandem.mobi.ui.theme.TMobiScreensTheme
import androidx.compose.runtime.collectAsState
import app.aaps.pump.common.events.EventPumpForceDisconnect
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.android.support.DaggerAppCompatActivity
import javax.inject.Inject
import kotlin.system.exitProcess

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

        // Initialize BLE scanner in ViewModel
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager?
        viewModel.initializeBLEScanner(bluetoothManager?.adapter)

        // Check if this is a re-pairing request
        val isRePairing = intent.getBooleanExtra(EXTRA_IS_RE_PAIRING, false)
        if (isRePairing) {
            needsPairingReset = true
            viewModel.startRePairing()
        }

        // Determine start destination based on existing pairing
        val startDestination = if (!isRePairing && viewModel.hasExistingPairing()) {
            // Load existing pump info into state and start at existing pump screen
            viewModel.loadExistingPumpInfo()
            WizardRoutes.EXISTING_PUMP
        } else {
            WizardRoutes.INTRODUCTION
        }

        setContent {
            TMobiScreensTheme {
                val navController = rememberNavController()

                WizardContent(
                    navController = navController,
                    viewModel = viewModel,
                    startDestination = startDestination,
                    onFinish = { finish() },
                    onFinishAndRestart = { showRestartDialogAndRestart() },
                    onCreatePairingManager = { address -> createPairingManager(address) },
                    onRequestPumpDisconnect = { handleExistingPumpRemoval() }
                )
            }
        }
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
       // val dummyActivity = object : PumpBLEConfigActivity() {}

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
            //activity = dummyActivity,
            pumpX2L = pumpX2L,
            aapsSchedulers = aapsSchedulers
        ).also { manager ->
            viewModel.setPairingManager(manager)
            // Note: Pairing data already cleared by handleExistingPumpRemoval if needed
            // No need to clear again here
        }

        needsPairingReset = false

        return pairingManager
    }

    override fun onDestroy() {
        super.onDestroy()
        pairingManager?.shutdownPairingManager()
    }

    /**
     * Handle removal of existing pump: disconnect and clear pairing data
     */
    private fun handleExistingPumpRemoval() {
        aapsLogger.info(LTag.PUMP, "Disconnecting existing pump and clearing pairing data")

        // Send event to disconnect the pump via the service
        rxBus.send(EventPumpForceDisconnect())

        // Give the disconnect event time to be processed, then clear all pairing data
        Handler(Looper.getMainLooper()).postDelayed({
            // Clear ALL pairing data using TandemPumpUtil
            tandemPumpUtil.clearAllPairingData()

            // Mark that we need to reset pairing
            needsPairingReset = true
            // Note: State is reset by viewModel.onConfirmRemoveExistingPump() with isRePairing=false

            aapsLogger.info(LTag.PUMP, "Existing pump removed, ready for new pairing")
        }, 1000) // Increased to 1 second to ensure disconnect completes
    }

    /**
     * Show restart dialog and restart AndroidAPS after user confirms
     */
    private fun showRestartDialogAndRestart() {
        aapsLogger.info(LTag.PUMP, "Pairing complete, showing restart dialog")

        MaterialAlertDialogBuilder(this, app.aaps.core.ui.R.style.DialogTheme)
            .setMessage(resourceHelper.gs(R.string.tandem_wizard_restart_message))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                aapsLogger.info(LTag.PUMP, "User confirmed restart, restarting AndroidAPS")
                Handler(Looper.getMainLooper()).postDelayed({
                    exitProcess(0)
                }, 500)
            }
            .setCancelable(false)
            .show()
    }

    companion object {
        const val EXTRA_IS_RE_PAIRING = "is_re_pairing"
    }
}

@Composable
private fun WizardContent(
    navController: NavHostController,
    viewModel: TandemMobiConnectionWizardViewModel,
    startDestination: String,
    onFinish: () -> Unit,
    onFinishAndRestart: () -> Unit,
    onCreatePairingManager: (String) -> Unit,
    onRequestPumpDisconnect: () -> Unit
) {
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
                // Progress indicator based on current route
                val currentRoute = navController.currentBackStackEntryFlow.collectAsState(initial = navController.currentBackStackEntry)
                val progress = calculateProgress(currentRoute.value?.destination?.route)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            WizardNavHost(
                navController = navController,
                viewModel = viewModel,
                startDestination = startDestination,
                onFinish = onFinish,
                onFinishAndRestart = onFinishAndRestart,
                onCreatePairingManager = onCreatePairingManager,
                onRequestPumpDisconnect = onRequestPumpDisconnect
            )
        }
    }
}

private fun calculateProgress(route: String?): Float {
    return when (route) {
        WizardRoutes.EXISTING_PUMP -> 0.0f
        WizardRoutes.INTRODUCTION -> 0.0f
        WizardRoutes.DEVICE_LIST -> 0.25f
        WizardRoutes.ENTER_PIN -> 0.5f
        WizardRoutes.PAIRING, WizardRoutes.ERROR -> 0.75f
        WizardRoutes.COMPLETE -> 1.0f
        else -> 0.0f
    }
}
