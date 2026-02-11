@file:OptIn(ExperimentalMaterial3Api::class)

package app.aaps.pump.tandem.mobi.ui.actions.cartridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.common.defs.PumpRunningState
import app.aaps.pump.common.test.ResourceHelperTest
import app.aaps.pump.tandem.R
import app.aaps.core.ui.R as Rco
import app.aaps.pump.tandem.common.driver.LocalTandemDataStore
import app.aaps.pump.tandem.mobi.ui.actions.setUpPreviewState
import app.aaps.pump.tandem.mobi.ui.theme.TMobiScreensTheme
import app.aaps.pump.tandem.mobi.ui.util.AlertBanner
import app.aaps.pump.tandem.mobi.ui.util.HeaderLineWithBackButton
import app.aaps.pump.tandem.mobi.ui.util.intervalOf
import app.aaps.shared.tests.AAPSLoggerTest
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.EnterChangeCartridgeModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ExitChangeCartridgeModeRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.AlertStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.UnknownMobiOpcode20Request
import com.jwoglom.pumpx2.pump.messages.response.controlStream.EnterChangeCartridgeModeStateStreamResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChangeCartridgeScreen(
    innerPadding: PaddingValues = PaddingValues(),
    sendPumpCommands: (List<Message>) -> Boolean,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    navigateBack: () -> Unit
) {
    val ds = LocalTandemDataStore.current
    @Suppress("PropertyName")
    val TAG = LTag.PUMP

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }

    fun fetchData() {
        sendPumpCommands(changeCartridgeScreenCommands)
    }

    fun refresh() = refreshScope.launch {
        aapsLogger.info(TAG, "reloading ChangeCartridgeScreen with force")
        refreshing = true

        sendPumpCommands(changeCartridgeScreenCommands)

        withContext(Dispatchers.IO) {
            Thread.sleep(250)
        }

        refreshing = false
    }

    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(intervalOf(60)) {
        aapsLogger.info(TAG, "reloading ChangeCartridgeScreen from interval")
        refresh()
    }

    // Poll for alerts every 10 seconds during change cartridge mode
    val inChangeCartridgeMode = ds.inChangeCartridgeMode.observeAsState()
    LaunchedEffect(inChangeCartridgeMode.value, intervalOf(10)) {
        if (inChangeCartridgeMode.value == true) {
            sendPumpCommands(listOf(AlertStatusRequest()))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                isRefreshing = refreshing,
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
                    HeaderLineWithBackButton(
                        text = resourceHelper.gs(R.string.cc_title),
                        onBackClick = navigateBack,
                        resourceHelper = resourceHelper
                    )
                    HorizontalDivider()
                }

                item {
                    ChangeCartridgeContent(
                        innerPadding = innerPadding,
                        sendPumpCommands = sendPumpCommands,
                        resourceHelper = resourceHelper,
                        refreshScope = refreshScope
                    )
                }
            }
        )
    }
}

@Composable
private fun ChangeCartridgeContent(
    innerPadding: PaddingValues,
    sendPumpCommands: (List<Message>) -> Boolean,
    resourceHelper: ResourceHelper,
    refreshScope: kotlinx.coroutines.CoroutineScope
) {
    val ds = LocalTandemDataStore.current
    val pumpRunningState = ds.pumpRunningState.observeAsState()
    val inChangeCartridgeMode = ds.inChangeCartridgeMode.observeAsState()
    val enterChangeCartridgeState = ds.enterChangeCartridgeState.observeAsState()
    val detectingCartridgeState = ds.detectingCartridgeState.observeAsState()
    val actionAlerts = ds.actionAlerts.observeAsState()

    fun sendPumpCommand(msg: Message) {
        sendPumpCommands(listOf(msg))
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp),
        content = {
            // Alert banner - display at top if alerts exist
            if (!actionAlerts.value.isNullOrEmpty()) {
                item {
                    AlertBanner(
                        alerts = actionAlerts.value!!,
                        onDismiss = { alert ->
                            sendPumpCommands(listOf(
                                com.jwoglom.pumpx2.pump.messages.request.control.DismissNotificationRequest(
                                    com.jwoglom.pumpx2.pump.messages.request.control.DismissNotificationRequest.NotificationType.ALERT,
                                    alert.bitmask().toLong()
                                )
                            ))
                        },
                        resourceHelper = resourceHelper
                    )
                }
            }

            // Status text
            item {
                if (detectingCartridgeState.value != null) {
                    if (detectingCartridgeState.value?.isComplete == true) {
                        Text(text = resourceHelper.gs(R.string.cc_complete))
                        Text("\n")
                        Text(text = resourceHelper.gs(R.string.common_percent_complete, "${detectingCartridgeState.value?.percentComplete}"))
                    } else {
                        Text(text = resourceHelper.gs(R.string.cc_detect_insulin_cart))
                        Text("\n")
                        Text(text = resourceHelper.gs(R.string.common_percent_complete, "${detectingCartridgeState.value?.percentComplete}"))
                    }
                } else if (enterChangeCartridgeState.value?.state == EnterChangeCartridgeModeStateStreamResponse.ChangeCartridgeState.READY_TO_CHANGE) {
                    Text(text = resourceHelper.gs(R.string.cc_can_remove_cart))
                    Text("\n")
                    Text(text = resourceHelper.gs(R.string.cc_when_inserted_press))
                } else if (inChangeCartridgeMode.value == true) {
                    Text(text = resourceHelper.gs(R.string.cc_preparing_cc))
                    Text("\n")
                } else if (pumpRunningState.value == PumpRunningState.Suspended) {
                    Text(text = resourceHelper.gs(R.string.ca_disconnect_pump_from_site, resourceHelper.gs(R.string.cc_btn_begin)))
                    Text("\n")
                } else {
                    Text(text = resourceHelper.gs(R.string.ca_before_stop_delivery, resourceHelper.gs(R.string.cc_action)))
                    Text("\n")
                }
            }

            // Action buttons
            item {
                if (detectingCartridgeState.value?.isComplete == true) {
                    // Cartridge change complete - no action needed
                } else if (enterChangeCartridgeState.value?.state == EnterChangeCartridgeModeStateStreamResponse.ChangeCartridgeState.READY_TO_CHANGE) {
                    TextButton(
                        onClick = {
                            refreshScope.launch {
                                sendPumpCommand(ExitChangeCartridgeModeRequest())
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = resourceHelper.gs(R.string.cc_btn_cart_inserted))
                    }
                } else if (inChangeCartridgeMode.value != true) {
                    TextButton(
                        onClick = {
                            refreshScope.launch {
                                sendPumpCommand(EnterChangeCartridgeModeRequest())
                            }
                        },
                        enabled = pumpRunningState.value == PumpRunningState.Suspended,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = resourceHelper.gs(R.string.cc_btn_begin))
                    }
                }
            }
        }
    )
}

val changeCartridgeScreenCommands = listOf(
    HomeScreenMirrorRequest(),
    TimeSinceResetRequest(),
    UnknownMobiOpcode20Request()
)

@Preview(showBackground = true)
@Composable
private fun ChangeCartridgeScreenPreview() {
    TMobiScreensTheme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalTandemDataStore.current)
            ChangeCartridgeScreen(
                sendPumpCommands = { _ -> true },
                navigateBack = {},
                resourceHelper = ResourceHelperTest(),
                aapsLogger = AAPSLoggerTest()
            )
        }
    }
}
