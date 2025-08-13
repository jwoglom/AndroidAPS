@file:OptIn(ExperimentalMaterial3Api::class)

package app.aaps.pump.tandem.t_mobi.ui.actions.cartridge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings

// import androidx.compose.material3.pulltorefresh.PullRefreshIndicator
// import androidx.compose.material3.pulltorefresh.pullRefresh
// import androidx.compose.material3.pulltorefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.common.test.ResourceHelperTest
import app.aaps.pump.tandem.common.driver.LocalTandemDataStore
import app.aaps.pump.tandem.t_mobi.ui.actions.other.BasalStatus
import app.aaps.pump.tandem.t_mobi.ui.actions.other.SendType
import app.aaps.pump.tandem.t_mobi.ui.actions.setUpPreviewState
import app.aaps.pump.tandem.t_mobi.ui.util.DecimalOutlinedText
import app.aaps.pump.tandem.t_mobi.ui.util.LifecycleStateObserver
import app.aaps.pump.tandem.t_mobi.ui.util.Line
import app.aaps.pump.tandem.t_mobi.ui.util.intervalOf
import app.aaps.pump.tandem.t_mobi.ui.theme.TMobiScreensTheme
import app.aaps.pump.tandem.t_mobi.ui.util.HeaderLineWithBackButton
import app.aaps.shared.tests.AAPSLoggerTest

import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.request.control.EnterChangeCartridgeModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.EnterFillTubingModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ExitChangeCartridgeModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ExitFillTubingModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.FillCannulaRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.UnknownMobiOpcode20Request
import com.jwoglom.pumpx2.pump.messages.response.controlStream.EnterChangeCartridgeModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.ExitFillTubingModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillCannulaStateStreamResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun CartridgeActions(
    innerPadding: PaddingValues = PaddingValues(),
    //navController: NavHostController? = null,
    sendPumpCommands: (List<Message>) -> Boolean,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    _changeCartridgeMenuState: Boolean = false,
    _fillTubingMenuState: Boolean = false,
    _fillCannulaMenuState: Boolean = false,
    navigateToSiteReminder: () -> Unit,
    navigateBack: () -> Unit
) {
    //val coroutineScope = rememberCoroutineScope()

    //var showChangeCartridgeMenu by remember { mutableStateOf(_changeCartridgeMenuState) }
    //var showFillTubingMenu by remember { mutableStateOf(_fillTubingMenuState) }
    //var showFillCannulaMenu by remember { mutableStateOf(_fillCannulaMenuState) }

    val ds = LocalTandemDataStore.current
    @Suppress("PropertyName")
    val TAG = LTag.PUMP

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }


    fun fetchDataStoreFields() {
        sendPumpCommands(cartridgeActionsCommands)
    }

    // fun waitForLoaded() = refreshScope.launch {
    //     var sinceLastFetchTime = 0
    //     while (true) {
    //         withContext(Dispatchers.IO) {
    //             Thread.sleep(250)
    //         }
    //         sinceLastFetchTime += 250
    //     }
    //     refreshing = false
    // }

    fun refresh() = refreshScope.launch {
        aapsLogger.info(TAG, "reloading CartridgeActions with force")
        refreshing = true

        sendPumpCommands(cartridgeActionsCommands)

        withContext(Dispatchers.IO) {
            Thread.sleep(250)
        }

        refreshing = false
    }

    //val state = rememberPullRefreshState(refreshing, ::refresh)
    val pullRefreshState = rememberPullToRefreshState()

    // LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = {
    //     refreshScope.cancel()
    // }) {
    //     Timber.i("reloading CartridgeActions from onStart lifecyclestate")
    //     fetchDataStoreFields(SendType.STANDARD)
    // }

    LaunchedEffect(intervalOf(60)) {
        aapsLogger.info(TAG,"reloading CartridgeActions from interval")
        //fetchDataStoreFields(SendType.STANDARD)
        refresh()
    }

    // LaunchedEffect(refreshing) {
    //     waitForLoaded()
    // }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(isRefreshing = refreshing,
                           state = pullRefreshState,
                           onRefresh = { refresh() })
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
                    HeaderLineWithBackButton(text= "Cartridge Actions",
                                             onBackClick=navigateBack,
                                             backgroundColor = Color.LightGray)
                    HorizontalDivider()
                }

                item {
                    ChangeCartridge(innerPadding = innerPadding,
                               sendPumpCommands = sendPumpCommands,
                               resourceHelper = resourceHelper,
                               refreshScope = refreshScope)
                }

                item {
                    FillTubing(innerPadding = innerPadding,
                                sendPumpCommands = sendPumpCommands,
                                resourceHelper = resourceHelper,
                                refreshScope = refreshScope)
                }

                item {
                    FillCannula(innerPadding = innerPadding,
                                sendPumpCommands = sendPumpCommands,
                                resourceHelper = resourceHelper,
                                refreshScope = refreshScope)
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        ListItem(
                            headlineContent = { Text(
                                "Site Reminder"
                            )},
                            supportingContent = {
                            },
                            leadingContent = {
                                Icon(Icons.Filled.Info, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                navigateToSiteReminder()
                            }
                        )
                    }
                }


                item {
                    Line("\n")
                }
            }
        )
    }
}

val cartridgeActionsCommands = listOf(
    HomeScreenMirrorRequest(),
    TimeSinceResetRequest(),
    UnknownMobiOpcode20Request()
)


@Preview(showBackground = true)
@Composable
private fun DefaultPreview() {
    TMobiScreensTheme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalTandemDataStore.current)
            CartridgeActions(
                sendPumpCommands = { _ -> true},
                navigateBack = {},
                navigateToSiteReminder = {},
                resourceHelper = ResourceHelperTest(),
                aapsLogger = AAPSLoggerTest()
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DefaultPreviewChangeCartridge_InsulinNotStopped() {
    TMobiScreensTheme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalTandemDataStore.current)
            LocalTandemDataStore.current.basalStatus.value = BasalStatus.ON
            CartridgeActions(
                sendPumpCommands = { _ -> true},
                _changeCartridgeMenuState = true,
                navigateBack = {},
                navigateToSiteReminder = {},
                resourceHelper = ResourceHelperTest(),
                aapsLogger = AAPSLoggerTest()
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DefaultPreviewChangeCartridge_InsulinStopped() {
    TMobiScreensTheme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalTandemDataStore.current)
            LocalTandemDataStore.current.basalStatus.value = BasalStatus.PUMP_SUSPENDED
            CartridgeActions(
                sendPumpCommands = { _ -> true},
                _changeCartridgeMenuState = true,
                navigateBack = {},
                navigateToSiteReminder = {},
                resourceHelper = ResourceHelperTest(),
                aapsLogger = AAPSLoggerTest()
            )
        }
    }
}