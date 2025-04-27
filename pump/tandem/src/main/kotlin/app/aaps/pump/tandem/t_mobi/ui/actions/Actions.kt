@file:OptIn(ExperimentalMaterial3Api::class)

package app.aaps.pump.tandem.t_mobi.ui.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
// import androidx.compose.material.pullrefresh.PullRefreshIndicator
// import androidx.compose.material.pullrefresh.pullRefresh
// import androidx.compose.material.pullrefresh.rememberPullRefreshState
//import androidx.compose.material.pullrefresh.PullRefreshIndicator
//import androidx.compose.material.pullrefresh.pullRefresh
//import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.common.defs.PumpRunningState
import app.aaps.pump.tandem.common.comm.ui.TandemUIDataStore
import app.aaps.pump.tandem.t_mobi.ui.actions.other.BasalStatus
import app.aaps.pump.tandem.t_mobi.ui.actions.other.SendType

import app.aaps.pump.tandem.t_mobi.ui.util.HeaderLine
import app.aaps.pump.tandem.t_mobi.ui.util.LifecycleStateObserver
import app.aaps.pump.tandem.t_mobi.ui.util.Line
import app.aaps.pump.tandem.t_mobi.ui.util.intervalOf
import app.aaps.pump.tandem.t_mobi.ui.theme.TMobiScreensTheme
import app.aaps.pump.tandem.common.driver.LocalTandemDataStore
import app.aaps.pump.tandem.common.driver.tandemDataStore
import app.aaps.pump.tandem.t_mobi.ui.util.compactTBRDisplay
import app.aaps.shared.tests.AAPSLoggerTest

// import com.jwoglom.controlx2.LocalDataStore
// import com.jwoglom.controlx2.Prefs
// import com.jwoglom.controlx2.dataStore
// import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
// import com.jwoglom.controlx2.presentation.components.HeaderLine
// import com.jwoglom.controlx2.presentation.components.Line
// import com.jwoglom.controlx2.presentation.screens.TempRatePreview
// import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
// import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
// import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
// import com.jwoglom.controlx2.shared.enums.BasalStatus
// import com.jwoglom.controlx2.shared.enums.UserMode
// import com.jwoglom.controlx2.shared.presentation.intervalOf
// import com.jwoglom.controlx2.shared.util.SendType
// import com.jwoglom.controlx2.util.determinePumpModel
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.ResumePumpingRequest
import com.jwoglom.pumpx2.pump.messages.request.control.StopTempRateRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SuspendPumpingRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TempRateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

// TODO: PullToRefresh functionality is not tested (rewrite from old one)

@Composable
fun Actions(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    //sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Boolean,
    //historyLogViewModel: HistoryLogViewModel? = null,
    _resumeInsulinMenuState: Boolean = false,
    _suspendInsulinMenuState: Boolean = false,
    _stopTempRateMenuState: Boolean = false,
    aapsLogger: AAPSLogger,
    //openTempRateWindow: () -> Unit,
    //navigateToCgmActions: () -> Unit,
    navigateToCartridgeActions: () -> Unit,
    navigateToPumpInfo: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var showResumeInsulinMenu by remember { mutableStateOf(_resumeInsulinMenuState) }
    var showSuspendInsulinMenu by remember { mutableStateOf(_suspendInsulinMenuState) }
    var showStopTempRateMenu by remember { mutableStateOf(_stopTempRateMenuState) }

    val context = LocalContext.current
    val ds = LocalTandemDataStore.current
    val deviceName = ds.setupDeviceName.observeAsState()
    val TAG = LTag.PUMPCOMM

    val tempRateActive = ds.tempRateActive.observeAsState()
    val tempRateDetails = ds.tempRateDetails.observeAsState()

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }

    fun fetchDataStoreFields(type: SendType) {
        sendPumpCommands(type, actionsCommands)
    }

    fun waitForLoaded() = refreshScope.launch {
        //if (!Prefs(context).serviceEnabled()) return@launch
        var sinceLastFetchTime = 0
        while (true) {
            val nullFields = actionsFields.filter { field -> field.value == null }.toSet()
            if (nullFields.isEmpty()) {
                break
            }

            aapsLogger.info(TAG, "Actions loading: remaining ${nullFields.size}: ${actionsFields.map { it.value }}")
            if (sinceLastFetchTime >= 2500) {
                aapsLogger.info(TAG, "Actions loading re-fetching with cache")
                fetchDataStoreFields(SendType.CACHED)
                sinceLastFetchTime = 0
            }

            withContext(Dispatchers.IO) {
                Thread.sleep(250)
            }
            sinceLastFetchTime += 250
        }
        aapsLogger.info(TAG, "Actions loading done: ${actionsFields.map { it.value }}")
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        //if (!Prefs(context).serviceEnabled()) return@launch
        aapsLogger.info(TAG, "reloading Actions with force")
        refreshing = true

        actionsFields.forEach { field -> field.value = null }
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    //val state = rememberPullRefreshState(refreshing, ::refresh)
    val pullRefreshState = rememberPullToRefreshState()


    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = {
        refreshScope.cancel()
    }) {
        aapsLogger.info(TAG, "reloading Actions from onStart lifecyclestate")
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(intervalOf(60)) {
        aapsLogger.info(TAG, "reloading Actions from interval")
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(refreshing) {
        waitForLoaded()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(isRefreshing = refreshing,
                           state= pullRefreshState,
                           onRefresh = { refresh() })
            //.pullRefresh(state)
    ) {
        PullToRefreshDefaults.Indicator(
            isRefreshing = refreshing,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
        )
        LazyColumn(
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 0.dp),
            content = {
                item {
                    HeaderLine("Actions")
                    //Divider()

                    // val model = determinePumpModel(deviceName.value ?: "")
                    // if (model == KnownDeviceModel.TSLIM_X2) {
                    //     Line("Actions are not supported on this device model (${model}). Only remote bolus is supported.")
                    //     Line("")
                    // }
                }
                item {
                    val basalStatus = ds.pumpRunningState.observeAsState()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        ListItem(
                            headlineContent = { Text(
                                when (basalStatus.value) {
                                    PumpRunningState.Unknown, null  -> "Stop / Start Insulin"
                                    PumpRunningState.Suspended -> "Start Insulin"
                                    else                       -> "Stop Insulin"
                                }
                            )},
                            supportingContent = { Text(
                                when (basalStatus.value) {
                                    PumpRunningState.Unknown, null  -> "Stop or resume insulin deliveries"
                                    PumpRunningState.Suspended -> "Resume insulin deliveries"
                                    else                       -> "Stop insulin deliveries"
                                }
                            ) },
                            leadingContent = {
                                Icon(
                                    when (basalStatus.value) {
                                        PumpRunningState.Unknown, null  -> Icons.Filled.Close
                                        PumpRunningState.Suspended -> Icons.Filled.PlayArrow
                                        else                       -> Icons.Filled.Close
                                    },
                                    contentDescription = null,
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = when (basalStatus.value) {
                                    PumpRunningState.Unknown, null  -> ListItemDefaults.containerColor
                                    PumpRunningState.Suspended -> Color.Green.copy(alpha = 0.5F)
                                    else                       -> Color.Red.copy(alpha = 0.5F)
                                }
                            ),
                            modifier = Modifier.clickable {
                                when (basalStatus.value) {
                                    PumpRunningState.Unknown, null  -> {}
                                    PumpRunningState.Suspended -> {showResumeInsulinMenu = true}
                                    else                       -> {showSuspendInsulinMenu = true}
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = showResumeInsulinMenu,
                            onDismissRequest = { showResumeInsulinMenu = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) {

                            AlertDialog(
                                onDismissRequest = {},
                                title = {
                                    Text("Resume insulin")
                                },
                                text = {
                                    Text("Resume all insulin deliveries?")
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            showResumeInsulinMenu = false
                                        },
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Cancel")
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showResumeInsulinMenu = false
                                            sendPumpCommands(SendType.BUST_CACHE, listOf(ResumePumpingRequest()))
                                            refreshScope.launch {
                                                repeat(5) {
                                                    Thread.sleep(1000)
                                                    sendPumpCommands(
                                                        SendType.BUST_CACHE,
                                                        listOf(HomeScreenMirrorRequest())
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Resume insulin")
                                    }
                                }
                            )

                        }

                        DropdownMenu(
                            expanded = showSuspendInsulinMenu,
                            onDismissRequest = { showSuspendInsulinMenu = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) {

                            AlertDialog(
                                onDismissRequest = {},
                                title = {
                                    Text("Stop insulin")
                                },
                                text = {
                                    Text("Suspend all insulin deliveries?")
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            showSuspendInsulinMenu = false
                                        },
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Cancel")
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showSuspendInsulinMenu = false
                                            sendPumpCommands(SendType.BUST_CACHE, listOf(SuspendPumpingRequest()))
                                            refreshScope.launch {
                                                repeat(5) {
                                                    Thread.sleep(1000)
                                                    sendPumpCommands(
                                                        SendType.BUST_CACHE,
                                                        listOf(HomeScreenMirrorRequest())
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Stop insulin")
                                    }
                                }
                            )

                        }

                    }
                }

//                item {
//                    Line("\n")
//                }


                item {
                    HorizontalDivider()
                }

//                item {
//                    Box(
//                        modifier = Modifier
//                            .fillMaxSize()
//                            .wrapContentSize(Alignment.TopStart)
//                    ) {
//                        ListItem(
//                            headlineContent = {
//                                Text("Refresh Pump Delivery State")
//                            },
//                            supportingContent = {
//                            },
//                            leadingContent = {
//                                Icon(Icons.Filled.Refresh, contentDescription = null)
//                            },
//                            modifier = Modifier.clickable {
//                                navigateToCartridgeActions()
////                                CartridgeActions(
////                                    innerPadding = innerPadding,
////                                    navController = navController,
////                                    sendMessage = sendMessage,
////                                    sendPumpCommands = sendPumpCommands,
////                                    //historyLogViewModel = historyLogViewModel,
////                                    navigateBack = {
////                                        //selectedItem = LandingSection.ACTIONS
////                                    },
////                                )
//
//
//                            }
//                        )
//                    }
//                }

                if (tempRateActive.value==true) {

                    item {

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .wrapContentSize(Alignment.TopStart)
                        ) {
                            fun prettyDuration(minutes: Long?): String {
                                return "${minutes?.div(60)}h${minutes?.rem(60)}m"
                            }

                            ListItem(
                                headlineContent = { Text(
                                    when (tempRateActive.value) {
                                        true -> "Stop Temp Rate"
                                        else -> "Start Temp Rate"
                                    }
                                )},
                                supportingContent =  {
                                    when (tempRateActive.value) {
                                        true -> Text("Active: ${compactTBRDisplay(tempRateDetails.value)}")
                                        else -> null
                                    }
                                },
                                leadingContent = {
                                    Icon(
                                        when (tempRateActive.value) {
                                            true -> Icons.Filled.Close
                                            else -> Icons.Filled.Settings
                                        },
                                        contentDescription = null,
                                    )
                                },
                                modifier = Modifier.clickable {
                                    when (tempRateActive.value) {
                                        true -> { showStopTempRateMenu = true }
                                        //false -> { openTempRateWindow() }
                                        else -> {}
                                    }
                                }
                            )
                            DropdownMenu(
                                expanded = showStopTempRateMenu,
                                onDismissRequest = { showStopTempRateMenu = false },
                                modifier = Modifier.fillMaxWidth(),
                            ) {

                                AlertDialog(
                                    onDismissRequest = { showStopTempRateMenu = false },
                                    title = {
                                        Text("Stop Temp Rate")
                                    },
                                    text = {
                                        Text("Stop the active temp rate: ${tempRateDetails.value?.percentage}% for ${prettyDuration(tempRateDetails.value?.duration?.div(60))} beginning ${tempRateDetails.value?.startTimeInstant}")
                                    },
                                    dismissButton = {
                                        TextButton(
                                            onClick = {
                                                showStopTempRateMenu = false
                                            },
                                            modifier = Modifier.padding(top = 16.dp)
                                        ) {
                                            Text("Cancel")
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                refreshScope.launch {
                                                    showStopTempRateMenu = false
                                                    sendPumpCommands(SendType.BUST_CACHE, listOf(
                                                        StopTempRateRequest()
                                                    ))
                                                    withContext(Dispatchers.IO) {
                                                        Thread.sleep(250)
                                                    }
                                                    sendPumpCommands(
                                                        SendType.BUST_CACHE,
                                                        listOf(TempRateRequest())
                                                    )
                                                }
                                            },
                                            modifier = Modifier.padding(top = 16.dp)
                                        ) {
                                            Text("Stop temp rate")
                                        }
                                    }
                                )

                            }
                        }
                    }  // TBR
                }



                item {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        ListItem(
                            headlineContent = { Text(
                                "Cartridge/Canula Setup"
                            )},
                            supportingContent = {
                            },
                            leadingContent = {
                                Icon(Icons.Filled.Settings, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                navigateToCartridgeActions()
                            }
                        )
                    }
                }


                item {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        ListItem(
                            headlineContent = { Text(
                                "Pump Info"
                            )},
                            supportingContent = {
                            },
                            leadingContent = {
                                Icon(Icons.Filled.Info, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                navigateToPumpInfo()
                            }
                        )
                    }
                }

            }
        )
    }
}
val actionsCommands = listOf(
    HomeScreenMirrorRequest(),
//    ControlIQInfoRequestBuilder.create(apiVersion()),
    TempRateRequest()
)

val actionsFields = listOf(
    tandemDataStore.basalStatus,
//    dataStore.controlIQMode,
    tandemDataStore.tempRateActive,
    tandemDataStore.tempRateDetails,
)

@Preview(showBackground = true)
@Composable
private fun DefaultPreviewInsulinActive() {
    TMobiScreensTheme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalTandemDataStore.current)
            Actions(
                sendPumpCommands = {_, _ -> true},
                //openTempRateWindow = {},
                //navigateToCgmActions = {},
                aapsLogger = AAPSLoggerTest(),
                navigateToCartridgeActions = {},
                navigateToPumpInfo = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DefaultPreviewInsulinActive_StopMenuOpen() {
    TMobiScreensTheme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalTandemDataStore.current)
            Actions(
                //sendMessage = { _, _ -> },
                sendPumpCommands = {_, _ -> true},
                _suspendInsulinMenuState = true,
                //openTempRateWindow = {},
                //navigateToCgmActions = {},
                aapsLogger = AAPSLoggerTest(),
                navigateToCartridgeActions = {},
                navigateToPumpInfo = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DefaultPreviewInsulinSuspended() {
    TMobiScreensTheme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalTandemDataStore.current)
            LocalTandemDataStore.current.basalStatus.value = BasalStatus.PUMP_SUSPENDED
            Actions(
                //sendMessage = { _, _ -> },
                sendPumpCommands = {_, _ -> true},
                //openTempRateWindow = {},
                //navigateToCgmActions = {},
                aapsLogger = AAPSLoggerTest(),
                navigateToCartridgeActions = {},
                navigateToPumpInfo = {}
            )
        }
    }
}

// var dataStore = DataStore()
// val LocalDataStore = compositionLocalOf { dataStore }

@Preview(showBackground = true)
@Composable
private fun DefaultPreviewInsulinSuspended_ResumeMenuOpen() {
    TMobiScreensTheme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalTandemDataStore.current)
            //LocalDataStore.current.basalStatus.value = BasalStatus.PUMP_SUSPENDED
            LocalTandemDataStore.current.pumpRunningState.value = PumpRunningState.Suspended
            Actions(
                //sendMessage = { _, _ -> },
                sendPumpCommands = {_, _ -> true},
                _resumeInsulinMenuState = true,
                //openTempRateWindow = {},
                //navigateToCgmActions = {},
                aapsLogger = AAPSLoggerTest(),
                navigateToCartridgeActions = {},
                navigateToPumpInfo = {}
            )
        }
    }
}


fun setUpPreviewState(ds: TandemUIDataStore) {
    // ds.setupDeviceName.value = "tslim X2 ***789"
    // ds.setupDeviceModel.value = "X2"
    ds.pumpConnected.value = true
    ds.pumpLastConnectionTimestamp.value = Instant.now().minusSeconds(120)
    // ds.cgmReading.value = 123
    // ds.cgmDeltaArrow.value = "⬈"
    ds.batteryPercent.value = 50
    // ds.iobUnits.value = 0.5
    ds.cartridgeRemainingUnits.value = 100
    //ds.basalStatus.value = BasalStatus.ON
    //ds.controlIQMode.value = UserMode.EXERCISE
}