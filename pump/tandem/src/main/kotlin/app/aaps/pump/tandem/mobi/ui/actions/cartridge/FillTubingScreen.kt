@file:OptIn(ExperimentalMaterial3Api::class)

package app.aaps.pump.tandem.mobi.ui.actions.cartridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
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
import com.jwoglom.pumpx2.pump.messages.request.control.EnterFillTubingModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ExitFillTubingModeRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.AlarmStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.AlertStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.UnknownMobiOpcode20Request
import com.jwoglom.pumpx2.pump.messages.response.controlStream.ExitFillTubingModeStateStreamResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FillTubingScreen(
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
    var willRestartFill by remember { mutableStateOf(false) }

    fun refresh() = refreshScope.launch {
        aapsLogger.info(TAG, "reloading FillTubingScreen with force")
        refreshing = true

        sendPumpCommands(fillTubingScreenCommands)

        withContext(Dispatchers.IO) {
            Thread.sleep(250)
        }

        refreshing = false
    }

    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(intervalOf(60)) {
        aapsLogger.info(TAG, "reloading FillTubingScreen from interval")
        refresh()
    }

    // Poll for alerts and alarms immediately on screen open
    LaunchedEffect(Unit) {
        aapsLogger.info(TAG, "Initial alert/alarm poll on FillTubingScreen")
        sendPumpCommands(listOf(AlertStatusRequest(), AlarmStatusRequest()))
    }

    // Poll for alerts and alarms every 10 seconds while on the Fill Tubing screen
    LaunchedEffect(intervalOf(10)) {
        aapsLogger.info(TAG, "Periodic alert/alarm poll on FillTubingScreen")
        sendPumpCommands(listOf(AlertStatusRequest(), AlarmStatusRequest()))
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

        val pumpRunningState = ds.pumpRunningState.observeAsState()
        val inFillTubingMode = ds.inFillTubingMode.observeAsState()
        val fillTubingState = ds.fillTubingState.observeAsState()
        val exitFillTubingState = ds.exitFillTubingState.observeAsState()

        // Observe notification bundle for alerts/alarms
        val notifications = remember { mutableStateListOf<Any>() }
        ds.notificationBundle.observe(LocalLifecycleOwner.current, Observer {
            ds.notificationBundle.value?.let {
                notifications.clear()
                notifications.addAll(it.get().toTypedArray())
            }
        })

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
                text = resourceHelper.gs(R.string.ft_title),
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
                if (exitFillTubingState.value != null) {
                    if (exitFillTubingState.value?.state == ExitFillTubingModeStateStreamResponse.ExitFillTubingModeState.TUBING_FILLED) {
                        if (willRestartFill) {
                            Text(text = resourceHelper.gs(R.string.ca_disconnect_pump_from_site,
                                                          resourceHelper.gs(R.string.ft_btn_restart)))
                            Text("\n")
                        } else {
                            Text(text = resourceHelper.gs(R.string.ft_complete))
                        }
                    } else {
                        if (willRestartFill) {
                            Text(text = resourceHelper.gs(R.string.ft_restart_text))
                        } else {
                            Text(text = resourceHelper.gs(R.string.ft_finalizing_wait))
                        }
                        Text(text = "\n\n")
                        Text(text = resourceHelper.gs(R.string.ft_finalizing_status_NOT_COMPLETE))
                    }
                } else if (inFillTubingMode.value == true) {
                    if (fillTubingState.value == null) {
                        Text(text = resourceHelper.gs(R.string.ft_hold_pump_button))
                        Text(text = "\n\n")
                        Text(text = resourceHelper.gs(R.string.ft_no_filled_insulin))
                    } else if (fillTubingState.value?.buttonDown == true) {
                        Text(text = resourceHelper.gs(R.string.ft_filling))
                    } else if (fillTubingState.value?.buttonDown == false) {
                        Text(text = resourceHelper.gs(R.string.ft_stopped_fill_1))
                        Text(text = "\n\n")
                        Text(text = resourceHelper.gs(R.string.ft_continue_filling))
                        Text(text = "\n\n")
                        Text(text = resourceHelper.gs(R.string.ft_continue_filling_2))
                    }
                } else if (pumpRunningState.value == PumpRunningState.Suspended) {
                    Text(text = resourceHelper.gs(R.string.ca_disconnect_pump_from_site,
                                                  resourceHelper.gs(R.string.ft_btn_begin)))
                    Text("\n")
                } else {
                    Text(text = resourceHelper.gs(R.string.ca_before_stop_delivery,
                                                  resourceHelper.gs(R.string.ft_action)))
                    Text("\n")
                }
            }

            // Spacer to push button to bottom
            Spacer(modifier = Modifier.weight(1f))

            // Action buttons at bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (exitFillTubingState.value != null) {
                    if (exitFillTubingState.value?.state == ExitFillTubingModeStateStreamResponse.ExitFillTubingModeState.TUBING_FILLED) {
                        if (willRestartFill) {
                            Button(
                                onClick = {
                                    ds.exitFillTubingState.value = null
                                    willRestartFill = false
                                    sendPumpCommands(listOf(EnterFillTubingModeRequest()))
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
                                    text = resourceHelper.gs(R.string.ft_btn_restart),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    navigateBack()
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
                                    text = resourceHelper.gs(R.string.common_done),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                } else if (inFillTubingMode.value == true) {
                    if (fillTubingState.value?.buttonDown == false) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    refreshScope.launch {
                                        sendPumpCommands(listOf(ExitFillTubingModeRequest()))
                                        willRestartFill = true
                                    }
                                },
                                enabled = pumpRunningState.value == PumpRunningState.Suspended,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text(
                                    text = resourceHelper.gs(R.string.ft_btn_restart),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Button(
                                onClick = {
                                    refreshScope.launch {
                                        sendPumpCommands(listOf(ExitFillTubingModeRequest()))
                                    }
                                },
                                enabled = pumpRunningState.value == PumpRunningState.Suspended,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = resourceHelper.gs(R.string.ft_btn_complete),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            refreshScope.launch {
                                sendPumpCommands(listOf(EnterFillTubingModeRequest()))
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
                            text = resourceHelper.gs(R.string.ft_btn_begin),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

val fillTubingScreenCommands = listOf(
    HomeScreenMirrorRequest(),
    TimeSinceResetRequest(),
    UnknownMobiOpcode20Request()
)

@Preview(showBackground = true)
@Composable
private fun FillTubingScreenPreview() {
    TMobiScreensTheme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalTandemDataStore.current)
            FillTubingScreen(
                sendPumpCommands = { _ -> true },
                navigateBack = {},
                resourceHelper = ResourceHelperTest(),
                aapsLogger = AAPSLoggerTest()
            )
        }
    }
}
