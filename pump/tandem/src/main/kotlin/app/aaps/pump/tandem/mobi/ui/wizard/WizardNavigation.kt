package app.aaps.pump.tandem.mobi.ui.wizard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import app.aaps.pump.tandem.common.events.PairingError
import app.aaps.pump.tandem.mobi.ui.wizard.screens.ConnectionCompleteScreen
import app.aaps.pump.tandem.mobi.ui.wizard.screens.DeviceListScreen
import app.aaps.pump.tandem.mobi.ui.wizard.screens.EnterPINScreen
import app.aaps.pump.tandem.mobi.ui.wizard.screens.ErrorScreen
import app.aaps.pump.tandem.mobi.ui.wizard.screens.IntroductionScreen
import app.aaps.pump.tandem.mobi.ui.wizard.screens.PairingScreen

object WizardRoutes {
    const val INTRODUCTION = "introduction"
    const val DEVICE_LIST = "device_list"
    const val ENTER_PIN = "enter_pin"
    const val PAIRING = "pairing"
    const val ERROR = "error"
    const val COMPLETE = "complete"
}

@Composable
fun WizardNavHost(
    navController: NavHostController,
    viewModel: TandemMobiConnectionWizardViewModel,
    startDestination: String = WizardRoutes.INTRODUCTION,
    onFinish: () -> Unit,
    onCreatePairingManager: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Introduction Screen
        composable(WizardRoutes.INTRODUCTION) {
            IntroductionScreen(
                isRePairing = state.isRePairing,
                onBeginPairing = {
                    navController.navigate(WizardRoutes.DEVICE_LIST)
                }
            )
        }

        // Device List Screen
        composable(WizardRoutes.DEVICE_LIST) {
            DeviceListScreen(
                scannedDevices = state.scannedDevices,
                isScanning = state.isScanning,
                currentlySelectedAddress = state.deviceAddress,
                currentlySelectedName = state.deviceName,
                onStartScan = viewModel::startDeviceScan,
                onStopScan = viewModel::stopDeviceScan,
                onDeviceSelected = { device ->
                    viewModel.onDeviceSelectedFromList(device)
                    onCreatePairingManager(device.address)
                    navController.navigate(WizardRoutes.ENTER_PIN)
                },
                onBack = { onFinish() }
            )
        }

        // Enter PIN Screen
        composable(WizardRoutes.ENTER_PIN) {
            EnterPINScreen(
                deviceName = state.deviceName,
                deviceAddress = state.deviceAddress,
                enteredPIN = state.enteredPIN,
                onPINChanged = viewModel::onPINChanged,
                onNext = {
                    if (state.enteredPIN.length == 6) {
                        viewModel.startPairingWithCode(state.enteredPIN)
                        navController.navigate(WizardRoutes.PAIRING)
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // Pairing Screen
        composable(WizardRoutes.PAIRING) {
            PairingScreen(
                pairingStatus = state.pairingStatus,
                pairingLabel = state.pairingLabel
            )

            // Listen for pairing events and navigate accordingly
            LaunchedEffect(state.pairingStatus, state.pairingError) {
                when {
                    state.pairingStatus == 100 && navController.currentDestination?.route == WizardRoutes.PAIRING -> {
                        navController.navigate(WizardRoutes.COMPLETE) {
                            popUpTo(WizardRoutes.INTRODUCTION) { inclusive = true }
                        }
                    }
                    state.pairingError != null && navController.currentDestination?.route == WizardRoutes.PAIRING -> {
                        navController.navigate(WizardRoutes.ERROR)
                    }
                }
            }
        }

        // Error Screen
        composable(WizardRoutes.ERROR) {
            ErrorScreen(
                error = state.pairingError ?: PairingError.UnknownError,
                retryCount = state.retryCount,
                onEditPIN = {
                    viewModel.onEditPIN()
                    navController.popBackStack(WizardRoutes.ENTER_PIN, inclusive = false)
                },
                onRetry = {
                    viewModel.onRetryPairing()
                    navController.popBackStack(WizardRoutes.PAIRING, inclusive = false)
                },
                onCancelAndRescan = {
                    viewModel.onCancelAndRescan()
                    navController.popBackStack(WizardRoutes.DEVICE_LIST, inclusive = false)
                }
            )
        }

        // Complete Screen
        composable(WizardRoutes.COMPLETE) {
            ConnectionCompleteScreen(
                pumpSerial = state.pairedPumpSerial,
                pumpName = state.pairedPumpName,
                pumpApiVersion = state.pairedPumpApiVersion,
                onFinish = onFinish
            )
        }
    }
}
