@file:OptIn(ExperimentalMaterial3Api::class)

package app.aaps.pump.tandem.mobi.ui.actions.cartridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Observer
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
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.AlarmStatusRequest
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

    // Poll for alerts and alarms immediately on screen open
    LaunchedEffect(Unit) {
        aapsLogger.info(TAG, "Initial alert/alarm poll on ChangeCartridgeScreen")
        sendPumpCommands(listOf(AlertStatusRequest(), AlarmStatusRequest()))
    }

    // Poll for alerts and alarms every 10 seconds while on the Change Cartridge screen
    LaunchedEffect(intervalOf(10)) {
        aapsLogger.info(TAG, "Periodic alert/alarm poll on ChangeCartridgeScreen")
        sendPumpCommands(listOf(AlertStatusRequest(), AlarmStatusRequest()))
    }

    // Note: notification bundle is managed globally, so we don't clear it here

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

        val pumpRunningState = ds.pumpRunningState.observeAsState()
        val inChangeCartridgeMode = ds.inChangeCartridgeMode.observeAsState()
        val enterChangeCartridgeState = ds.enterChangeCartridgeState.observeAsState()
        val detectingCartridgeState = ds.detectingCartridgeState.observeAsState()

        // Observe notification bundle for alerts/alarms
        val notifications = remember { mutableStateListOf<Any>() }
        ds.notificationBundle.observe(LocalLifecycleOwner.current, Observer {
            ds.notificationBundle.value?.let {
                notifications.clear()
                notifications.addAll(it.get().toTypedArray())
            }
        })

        fun sendPumpCommand(msg: Message) {
            sendPumpCommands(listOf(msg))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding()
                )
        ) {
            HeaderLineWithBackButton(
                text = resourceHelper.gs(R.string.cc_title),
                onBackClick = navigateBack,
                resourceHelper = resourceHelper
            )
            HorizontalDivider()

            // Alert/Alarm banner - display at top if any exist
            if (notifications.isNotEmpty()) {
                AlertBanner(
                    notifications = notifications,
                    sendPumpCommands = sendPumpCommands,
                    refreshScope = refreshScope,
                    resourceHelper = resourceHelper
                )
            }

            // Status text
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
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

            // Spacer to push button to bottom
            Spacer(modifier = Modifier.weight(1f))

            // Action button at bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (detectingCartridgeState.value?.isComplete == true) {
                    // Cartridge change complete
                    Button(
                        onClick = {
                            refreshScope.launch {
                                navigateBack()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = resourceHelper.gs(R.string.common_done),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } else if (enterChangeCartridgeState.value?.state == EnterChangeCartridgeModeStateStreamResponse.ChangeCartridgeState.READY_TO_CHANGE) {
                    Button(
                        onClick = {
                            refreshScope.launch {
                                sendPumpCommand(ExitChangeCartridgeModeRequest())
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = resourceHelper.gs(R.string.cc_btn_cart_inserted),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } else if (inChangeCartridgeMode.value != true) {
                    Button(
                        onClick = {
                            refreshScope.launch {
                                sendPumpCommand(EnterChangeCartridgeModeRequest())
                            }
                        },
                        enabled = pumpRunningState.value == PumpRunningState.Suspended,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = resourceHelper.gs(R.string.cc_btn_begin),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
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
