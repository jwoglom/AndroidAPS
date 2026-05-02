@file:OptIn(ExperimentalMaterial3Api::class)

package app.aaps.pump.tandem.mobi.ui.actions.cartridge

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Observer
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
import app.aaps.pump.tandem.mobi.ui.util.intervalOf
import app.aaps.shared.tests.AAPSLoggerTest
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.EnterFillTubingModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ExitFillTubingModeRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.AlarmStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.AlertStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LoadStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
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
    navigateBack: () -> Unit,
    showHeader: Boolean = true
) {
    val ds = LocalTandemDataStore.current
    @Suppress("PropertyName")
    val TAG = LTag.PUMP

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }
    var willRestartFill by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var hasDisplayedFlow by remember { mutableStateOf(false) }
    var isStartingFillTubing by remember { mutableStateOf(false) }
    var showDisconnectConfirmDialog by remember { mutableStateOf(false) }

    fun refresh() = refreshScope.launch {
        aapsLogger.info(TAG, "reloading FillTubingScreen with force")
        refreshing = true
        sendPumpCommands(fillTubingScreenCommands)
        withContext(Dispatchers.IO) { Thread.sleep(250) }
        refreshing = false
    }

    LaunchedEffect(intervalOf(60)) {
        aapsLogger.info(TAG, "reloading FillTubingScreen from interval")
        refresh()
    }

    LaunchedEffect(Unit) {
        aapsLogger.info(TAG, "Initial alert/alarm poll on FillTubingScreen")
        sendPumpCommands(listOf(AlertStatusRequest(), AlarmStatusRequest()))
    }

    LaunchedEffect(intervalOf(10)) {
        aapsLogger.info(TAG, "Periodic alert/alarm poll on FillTubingScreen")
        sendPumpCommands(listOf(AlertStatusRequest(), AlarmStatusRequest()))
    }

    val pumpRunningState = ds.pumpRunningState.observeAsState()
    val inFillTubingMode = ds.inFillTubingMode.observeAsState()
    val fillTubingState = ds.fillTubingState.observeAsState()
    val exitFillTubingState = ds.exitFillTubingState.observeAsState()

    val notifications = remember { mutableStateListOf<Any>() }
    ds.notificationBundle.observe(LocalLifecycleOwner.current, Observer {
        ds.notificationBundle.value?.let {
            notifications.clear()
            notifications.addAll(it.get().toTypedArray())
        }
    })

    val isInActiveMode = inFillTubingMode.value == true || exitFillTubingState.value != null
    val hasActiveNotifications = notifications.isNotEmpty()

    fun requestCancelOrBack() {
        if (isInActiveMode) {
            showCancelDialog = true
        } else {
            navigateBack()
        }
    }

    BackHandler { requestCancelOrBack() }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(resourceHelper.gs(R.string.ft_cancel_confirm_title)) },
            text = { Text(resourceHelper.gs(R.string.ft_cancel_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showCancelDialog = false
                    refreshScope.launch {
                        sendPumpCommands(listOf(ExitFillTubingModeRequest()))
                        navigateBack()
                    }
                }) { Text(resourceHelper.gs(R.string.common_cancel)) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(resourceHelper.gs(R.string.common_continue))
                }
            }
        )
    }

    if (showDisconnectConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirmDialog = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
            title = { Text(resourceHelper.gs(R.string.ft_disconnect_confirm_title)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectConfirmDialog = false
                    isStartingFillTubing = true
                    sendPumpCommands(listOf(EnterFillTubingModeRequest()))
                    refreshScope.launch {
                        repeat(5) {
                            if (inFillTubingMode.value == true) return@repeat
                            withContext(Dispatchers.IO) { Thread.sleep(1000) }
                        }
                        isStartingFillTubing = false
                    }
                }) { Text(resourceHelper.gs(Rco.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirmDialog = false }) {
                    Text(resourceHelper.gs(Rco.string.no))
                }
            }
        )
    }

    val totalSteps = 4
    val currentStep = when {
        exitFillTubingState.value?.state == ExitFillTubingModeStateStreamResponse.ExitFillTubingModeState.TUBING_FILLED -> 4
        exitFillTubingState.value != null -> 3
        inFillTubingMode.value == true -> 2
        else -> 1
    }

    CartridgeWorkflowScreen(
        title = resourceHelper.gs(R.string.ft_title),
        innerPadding = innerPadding,
        refreshing = refreshing,
        onRefresh = { refresh() },
        onBack = ::requestCancelOrBack,
        resourceHelper = resourceHelper,
        showHeader = showHeader,
        stepIndicator = {
            WizardStepIndicator(
                currentStep = currentStep,
                totalSteps = totalSteps,
                resourceHelper = resourceHelper,
            )
        },
        header = {
            CartridgeNotificationsPanel(
                notifications = notifications,
                sendPumpCommands = sendPumpCommands,
                refreshScope = refreshScope,
                resourceHelper = resourceHelper,
            )
            if (hasActiveNotifications) {
                Text(
                    text = resourceHelper.gs(R.string.ca_notifications_block_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        },
        body = {
            if (exitFillTubingState.value != null) {
                Text(
                    text = resourceHelper.gs(R.string.ca_status_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (exitFillTubingState.value?.state == ExitFillTubingModeStateStreamResponse.ExitFillTubingModeState.TUBING_FILLED) {
                    if (willRestartFill) {
                        Text(
                            text = resourceHelper.gs(
                                R.string.ca_disconnect_pump_from_site,
                                resourceHelper.gs(R.string.ft_btn_restart)
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        Text(
                            text = resourceHelper.gs(R.string.ft_complete),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    if (willRestartFill) {
                        Text(
                            text = resourceHelper.gs(R.string.ft_restart_text),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        Text(
                            text = resourceHelper.gs(R.string.ft_finalizing_wait),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = resourceHelper.gs(R.string.ft_finalizing_status_NOT_COMPLETE),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else if (inFillTubingMode.value == true) {
                if (fillTubingState.value == null) {
                    Text(
                        text = resourceHelper.gs(R.string.ca_next_step_heading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = resourceHelper.gs(R.string.ft_hold_pump_button),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = resourceHelper.gs(R.string.ft_no_filled_insulin),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (fillTubingState.value?.buttonDown == true) {
                    hasDisplayedFlow = true
                    Text(
                        text = resourceHelper.gs(R.string.ca_status_heading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = resourceHelper.gs(R.string.ft_filling),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = resourceHelper.gs(R.string.ft_keep_holding_button),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (fillTubingState.value?.buttonDown == false) {
                    Text(
                        text = resourceHelper.gs(R.string.ca_next_step_heading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = resourceHelper.gs(R.string.ft_release_confirm_prompt),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = resourceHelper.gs(R.string.ft_stopped_fill_1),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = resourceHelper.gs(R.string.ft_continue_filling),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = resourceHelper.gs(R.string.ft_continue_filling_2),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else if (pumpRunningState.value == PumpRunningState.Suspended) {
                Text(
                    text = resourceHelper.gs(R.string.ca_before_you_start_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = resourceHelper.gs(
                        R.string.ca_disconnect_pump_from_site,
                        resourceHelper.gs(R.string.ft_btn_begin)
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Text(
                    text = resourceHelper.gs(R.string.ca_before_you_start_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = resourceHelper.gs(
                        R.string.ca_before_stop_delivery,
                        resourceHelper.gs(R.string.ft_action)
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        },
        actions = {
            if (exitFillTubingState.value != null) {
                if (exitFillTubingState.value?.state == ExitFillTubingModeStateStreamResponse.ExitFillTubingModeState.TUBING_FILLED) {
                    if (willRestartFill) {
                        PrimaryActionButton(
                            text = resourceHelper.gs(R.string.ft_btn_restart),
                            onClick = {
                                ds.exitFillTubingState.value = null
                                willRestartFill = false
                                sendPumpCommands(listOf(EnterFillTubingModeRequest()))
                            },
                            enabled = pumpRunningState.value == PumpRunningState.Suspended
                        )
                    } else {
                        PrimaryActionButton(
                            text = resourceHelper.gs(R.string.common_done),
                            onClick = {
                                ds.completedCartridgeActions.value =
                                    (ds.completedCartridgeActions.value ?: emptySet()) +
                                        CompletedCartridgeAction.FILL_TUBING
                                ds.loadStatus.value = null
                                navigateBack()
                            },
                            enabled = pumpRunningState.value == PumpRunningState.Suspended
                        )
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
                        PrimaryActionButton(
                            text = resourceHelper.gs(R.string.ft_btn_complete),
                            onClick = {
                                refreshScope.launch {
                                    sendPumpCommands(listOf(ExitFillTubingModeRequest()))
                                }
                            },
                            enabled = pumpRunningState.value == PumpRunningState.Suspended && hasDisplayedFlow,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        )
                    }
                }
            } else {
                PrimaryActionButton(
                    text = resourceHelper.gs(R.string.ft_btn_begin),
                    onClick = { showDisconnectConfirmDialog = true },
                    enabled = pumpRunningState.value == PumpRunningState.Suspended && !hasActiveNotifications,
                    loading = isStartingFillTubing
                )
            }
        }
    )
}

val fillTubingScreenCommands = listOf(
    HomeScreenMirrorRequest(),
    TimeSinceResetRequest(),
    LoadStatusRequest()
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
