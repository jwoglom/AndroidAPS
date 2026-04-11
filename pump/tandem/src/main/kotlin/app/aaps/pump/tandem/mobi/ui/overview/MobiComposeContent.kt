package app.aaps.pump.tandem.mobi.ui.overview

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.mobi.ui.ActionsLandingSection
import app.aaps.pump.tandem.mobi.ui.DataLandingSection
import app.aaps.pump.tandem.mobi.ui.TandemUiController
import app.aaps.pump.tandem.mobi.ui.actions.Actions
import app.aaps.pump.tandem.mobi.ui.actions.PumpInfo
import app.aaps.pump.tandem.mobi.ui.actions.cartridge.CartridgeActions
import app.aaps.pump.tandem.mobi.ui.actions.cartridge.ChangeCartridgeScreen
import app.aaps.pump.tandem.mobi.ui.actions.cartridge.FillCannulaScreen
import app.aaps.pump.tandem.mobi.ui.actions.cartridge.FillTubingScreen
import app.aaps.pump.tandem.mobi.ui.actions.cartridge.SiteReminder
import app.aaps.pump.tandem.mobi.ui.data.DataDisplayMain
import app.aaps.pump.tandem.mobi.ui.data.History
import app.aaps.pump.tandem.mobi.ui.data.Notifications
import app.aaps.pump.tandem.mobi.ui.data.QualifyingEvents

private enum class MobiScreen {
    OVERVIEW,
    DATA,
    DATA_NOTIFICATIONS,
    DATA_EVENTS,
    DATA_HISTORY,

    ACTIONS,
    ACTIONS_CARTRIDGE_ACTIONS,
    ACTIONS_CHANGE_CARTRIDGE,
    ACTIONS_FILL_TUBING,
    ACTIONS_FILL_CANNULA,
    ACTIONS_PUMP_INFO,
    ACTIONS_SITE_REMINDER

}

class MobiComposeContent(
    private val pluginName: String,
    private val tandemPumpStatus: TandemPumpStatus,
    private val aapsLogger: AAPSLogger,
    private val resourceHelper: ResourceHelper,
    private val tandemUiController: TandemUiController,

) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val overviewViewModel: MobiOverviewViewModel = hiltViewModel()
        //val wizardViewModel: DanaRPairWizardViewModel = hiltViewModel()

        // Navigation state
        var currentScreen by remember { mutableStateOf(MobiScreen.OVERVIEW) }

        // Dialogs
        var showUnpairDialog by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        // errorMessage?.let { msg ->
        //     OkDialog(
        //         title = stringResource(R.string.pumperror),
        //         message = msg,
        //         onDismiss = { errorMessage = null }
        //     )
        // }



        // Toolbar configuration
        //val historyTitle = stringResource(R.string.pump_history)
        //val userOptionsTitle = stringResource(R.string.danar_pump_settings)
        //val pairingTitle = stringResource(R.string.pairing)

        val overviewNavIcon: @Composable () -> Unit = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        }
        val subScreenNavIcon: @Composable () -> Unit = {
            IconButton(onClick = { currentScreen = MobiScreen.OVERVIEW }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        }
        val settingsAction: @Composable RowScope.() -> Unit = {
            onSettings?.let { action ->
                IconButton(onClick = action) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        }

        LaunchedEffect(currentScreen) {
            setToolbarConfig(
                when (currentScreen) {
                    MobiScreen.OVERVIEW     -> ToolbarConfig(title = pluginName, navigationIcon = overviewNavIcon, actions = settingsAction)
                    // MobiScreen.PAIR_WIZARD  -> ToolbarConfig(title = pairingTitle, navigationIcon = subScreenNavIcon, actions = {})
                    // MobiScreen.HISTORY      -> ToolbarConfig(title = historyTitle, navigationIcon = subScreenNavIcon, actions = {})
                    // MobiScreen.USER_OPTIONS -> ToolbarConfig(title = userOptionsTitle, navigationIcon = subScreenNavIcon, actions = {})
                    MobiScreen.ACTIONS      -> ToolbarConfig(title = "action", navigationIcon = {}, actions = {})
                    MobiScreen.DATA         -> ToolbarConfig(title = "data", navigationIcon = {}, actions = {})
                    MobiScreen.DATA_NOTIFICATIONS -> ToolbarConfig(title = "notif", navigationIcon = {}, actions = {})
                    MobiScreen.DATA_EVENTS -> ToolbarConfig(title = "eventss", navigationIcon = {}, actions = {})
                    MobiScreen.DATA_HISTORY -> ToolbarConfig(title = "hist", navigationIcon = {}, actions = {})

                    // TODO missing items
                    else -> ToolbarConfig(title = "else", navigationIcon = {}, actions = {})
                }
            )
        }

        // Handle one-time events from overview
        LaunchedEffect(overviewViewModel) {
            overviewViewModel.events.collect { event ->
                when (event) {
                    MobiOverviewEvent.OpenEvents           -> currentScreen = MobiScreen.DATA_EVENTS
                    MobiOverviewEvent.OpenHistory          -> currentScreen = MobiScreen.DATA_HISTORY
                    MobiOverviewEvent.OpenNotification     -> currentScreen = MobiScreen.DATA_NOTIFICATIONS
                    MobiOverviewEvent.StartActions         -> currentScreen = MobiScreen.ACTIONS
                    MobiOverviewEvent.StartData            -> currentScreen = MobiScreen.DATA
                }
            }
        }

        when (currentScreen) {

            MobiScreen.OVERVIEW      -> {
                MobiOverviewScreen(
                    viewModel = overviewViewModel
                )
            }

            MobiScreen.DATA -> {
                DataDisplayMain(
                    sendPumpCommands = { messages -> tandemUiController.sendPumpCommands(messages) },
                    aapsLogger = aapsLogger,
                    resourceHelper = resourceHelper,
                    navigateToPumpHistory = {
                        currentScreen = MobiScreen.DATA_HISTORY
                    },
                    navigateToEvents = {
                        currentScreen = MobiScreen.DATA_EVENTS
                    },
                    navigateToNotifications = {
                        currentScreen = MobiScreen.DATA_NOTIFICATIONS
                    },
                )
            }

            MobiScreen.DATA_HISTORY -> {
                History(
                    refreshDatabase = { target, queryParameters -> tandemUiController.refreshDatabase(target, queryParameters) },
                    refreshMainAppData = { refreshData -> tandemUiController.refreshMainAppData(refreshData = refreshData)},
                    aapsLogger = aapsLogger,
                    resourceHelper = resourceHelper,
                    navigateBack = {
                        currentScreen = MobiScreen.DATA
                    },
                )
            }

            MobiScreen.DATA_EVENTS -> {
                QualifyingEvents (
                    aapsLogger = aapsLogger,
                    resourceHelper = resourceHelper,
                    refreshDatabase = { target, queryParameters -> tandemUiController.refreshDatabase(target, queryParameters) },
                    refreshMainAppData = { refreshData -> tandemUiController.refreshMainAppData(refreshData = refreshData)},
                    navigateBack = {
                        currentScreen = MobiScreen.DATA
                    },
                )
            }

            MobiScreen.DATA_NOTIFICATIONS -> {
                Notifications(
                    resourceHelper = resourceHelper,
                    aapsLogger = aapsLogger,
                    sendPumpCommands = { messages -> tandemUiController.sendPumpCommands(messages) },
                    refreshMainAppData = { refreshData -> tandemUiController.refreshMainAppData(refreshData = refreshData)},
                    navigateBack = {
                        currentScreen = MobiScreen.DATA
                    }
                )
            }

            MobiScreen.ACTIONS -> {
                Actions(
                    sendPumpCommands = { messages -> tandemUiController.sendPumpCommands(messages) },
                    aapsLogger = aapsLogger,
                    resourceHelper = resourceHelper,
                    navigateToPumpInfo = {
                        currentScreen = MobiScreen.ACTIONS_PUMP_INFO
                    },
                    navigateToCartridgeActions = {
                        currentScreen = MobiScreen.ACTIONS_CARTRIDGE_ACTIONS
                    }
                )
            }

            MobiScreen.ACTIONS_PUMP_INFO -> {
                PumpInfo(
                    resourceHelper = resourceHelper,
                    tandemPumpStatus = tandemPumpStatus,
                    navigateBack = {
                        currentScreen = MobiScreen.ACTIONS
                    },
                )
            }

            MobiScreen.ACTIONS_CARTRIDGE_ACTIONS -> {
                CartridgeActions(
                    aapsLogger = aapsLogger,
                    sendPumpCommands = { messages -> tandemUiController.sendPumpCommands(messages) },
                    resourceHelper = resourceHelper,
                    navigateToChangeCartridge = {
                        currentScreen = MobiScreen.ACTIONS_CHANGE_CARTRIDGE
                    },
                    navigateToFillTubing = {
                        currentScreen = MobiScreen.ACTIONS_FILL_TUBING
                    },
                    navigateToFillCannula = {
                        currentScreen = MobiScreen.ACTIONS_FILL_CANNULA
                    },
                    navigateToSiteReminder = {
                        currentScreen = MobiScreen.ACTIONS_SITE_REMINDER
                    },
                    navigateBack = {
                        currentScreen = MobiScreen.ACTIONS
                    },
                )
            }


            MobiScreen.ACTIONS_CHANGE_CARTRIDGE -> {
                ChangeCartridgeScreen(
                    aapsLogger = aapsLogger,
                    sendPumpCommands = { messages -> tandemUiController.sendPumpCommands(messages) },
                    resourceHelper = resourceHelper,
                    navigateBack = {
                        currentScreen = MobiScreen.ACTIONS_CARTRIDGE_ACTIONS
                    },
                )
            }


            MobiScreen.ACTIONS_FILL_TUBING -> {
                FillTubingScreen(
                    aapsLogger = aapsLogger,
                    sendPumpCommands = { messages -> tandemUiController.sendPumpCommands(messages) },
                    resourceHelper = resourceHelper,
                    navigateBack = {
                        currentScreen = MobiScreen.ACTIONS_CARTRIDGE_ACTIONS
                    },
                )
            }


            MobiScreen.ACTIONS_FILL_CANNULA -> {
                FillCannulaScreen(
                    aapsLogger = aapsLogger,
                    sendPumpCommands = { messages -> tandemUiController.sendPumpCommands(messages) },
                    resourceHelper = resourceHelper,
                    navigateBack = {
                        currentScreen = MobiScreen.ACTIONS_CARTRIDGE_ACTIONS
                    }
                )
            }


            MobiScreen.ACTIONS_SITE_REMINDER -> {
                SiteReminder(
                    resourceHelper = resourceHelper,
                    aapsLogger = aapsLogger,
                    navigateBack = {
                        currentScreen = MobiScreen.ACTIONS_CARTRIDGE_ACTIONS
                    }
                )
            }

        }
    }
}
