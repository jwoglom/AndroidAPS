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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import app.aaps.pump.tandem.common.driver.LocalTandemDataStore
import app.aaps.pump.tandem.mobi.ui.actions.setUpPreviewState
import app.aaps.pump.tandem.mobi.ui.theme.TMobiScreensTheme
import app.aaps.pump.tandem.mobi.ui.util.AlertBanner
import app.aaps.pump.tandem.mobi.ui.util.DecimalOutlinedText
import app.aaps.pump.tandem.mobi.ui.util.intervalOf
import app.aaps.shared.tests.AAPSLoggerTest
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.request.control.FillCannulaRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ResumePumpingRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SuspendPumpingRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.AlarmStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.AlertStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillCannulaStateStreamResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FillCannulaScreen(
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
    var cannulaFillAmountStr by remember { mutableStateOf<String?>(null) }
    var cannulaFillAmount by remember { mutableStateOf<Double?>(null) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showSuspendDialog by remember { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(false) }
    var isSuspending by remember { mutableStateOf(false) }
    var isResuming by remember { mutableStateOf(false) }

    fun allowedCannulaFillAmount(units: Double?): Boolean {
        return units != null && units > 0 && units <= 3.0
    }

    fun sendPumpCommand(msg: Message) {
        sendPumpCommands(listOf(msg))
    }

    fun refresh() = refreshScope.launch {
        aapsLogger.info(TAG, "reloading FillCannulaScreen with force")
        refreshing = true
        sendPumpCommands(fillCannulaScreenCommands)
        withContext(Dispatchers.IO) { Thread.sleep(250) }
        refreshing = false
    }

    LaunchedEffect(intervalOf(60)) {
        aapsLogger.info(TAG, "reloading FillCannulaScreen from interval")
        refresh()
    }

    LaunchedEffect(Unit) {
        aapsLogger.info(TAG, "Initial alert/alarm poll on FillCannulaScreen")
        sendPumpCommands(listOf(AlertStatusRequest(), AlarmStatusRequest()))
    }

    LaunchedEffect(intervalOf(10)) {
        aapsLogger.info(TAG, "Periodic alert/alarm poll on FillCannulaScreen")
        sendPumpCommands(listOf(AlertStatusRequest(), AlarmStatusRequest()))
    }

    val pumpRunningState = ds.pumpRunningState.observeAsState()
    val fillCannulaState = ds.fillCannulaState.observeAsState()

    val notifications = remember { mutableStateListOf<Any>() }
    ds.notificationBundle.observe(LocalLifecycleOwner.current, Observer {
        ds.notificationBundle.value?.let {
            notifications.clear()
            notifications.addAll(it.get().toTypedArray())
        }
    })

    val isInActiveMode = fillCannulaState.value != null &&
        fillCannulaState.value?.state != FillCannulaStateStreamResponse.FillCannulaState.CANNULA_FILLED
    val hasActiveNotifications = notifications.isNotEmpty()

    fun requestCancelOrBack() {
        if (isInActiveMode) {
            showCancelDialog = true
        } else {
            navigateBack()
        }
    }

    BackHandler(enabled = isInActiveMode) {
        showCancelDialog = true
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(resourceHelper.gs(R.string.fc_cancel_confirm_title)) },
            text = { Text(resourceHelper.gs(R.string.fc_cancel_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showCancelDialog = false
                    navigateBack()
                }) { Text(resourceHelper.gs(R.string.common_cancel)) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(resourceHelper.gs(R.string.common_continue))
                }
            }
        )
    }

    if (showSuspendDialog) {
        AlertDialog(
            onDismissRequest = { showSuspendDialog = false },
            title = { Text(resourceHelper.gs(R.string.ca_suspend_confirm_title)) },
            text = { Text(resourceHelper.gs(R.string.ca_suspend_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showSuspendDialog = false
                    isSuspending = true
                    sendPumpCommand(SuspendPumpingRequest())
                    refreshScope.launch {
                        repeat(5) {
                            if (pumpRunningState.value == PumpRunningState.Suspended) {
                                return@repeat
                            }
                            withContext(Dispatchers.IO) { Thread.sleep(1000) }
                            sendPumpCommand(HomeScreenMirrorRequest())
                        }
                        isSuspending = false
                    }
                }) { Text(resourceHelper.gs(R.string.ca_btn_suspend_insulin)) }
            },
            dismissButton = {
                TextButton(onClick = { showSuspendDialog = false }) {
                    Text(resourceHelper.gs(R.string.common_cancel))
                }
            }
        )
    }

    if (showResumeDialog) {
        AlertDialog(
            onDismissRequest = { showResumeDialog = false },
            title = { Text(resourceHelper.gs(R.string.ca_resume_confirm_title)) },
            text = { Text(resourceHelper.gs(R.string.ca_resume_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showResumeDialog = false
                    isResuming = true
                    sendPumpCommand(ResumePumpingRequest())
                    refreshScope.launch {
                        repeat(5) {
                            if (pumpRunningState.value == PumpRunningState.Running) {
                                return@repeat
                            }
                            withContext(Dispatchers.IO) { Thread.sleep(1000) }
                            sendPumpCommand(HomeScreenMirrorRequest())
                        }
                        isResuming = false
                        navigateBack()
                    }
                }) { Text(resourceHelper.gs(R.string.ca_btn_resume_insulin)) }
            },
            dismissButton = {
                TextButton(onClick = { showResumeDialog = false }) {
                    Text(resourceHelper.gs(R.string.common_cancel))
                }
            }
        )
    }

    CartridgeWorkflowScreen(
        title = resourceHelper.gs(R.string.fc_title),
        innerPadding = innerPadding,
        refreshing = refreshing,
        onRefresh = { refresh() },
        onBack = ::requestCancelOrBack,
        resourceHelper = resourceHelper,
        showHeader = showHeader,
        header = {
            if (hasActiveNotifications) {
                AlertBanner(
                    notifications = notifications,
                    sendPumpCommands = sendPumpCommands,
                    refreshScope = refreshScope,
                    resourceHelper = resourceHelper
                )
                Text(
                    text = resourceHelper.gs(R.string.ca_notifications_block_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        },
        body = {
            if (fillCannulaState.value != null) {
                Text(
                    text = resourceHelper.gs(R.string.ca_status_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (fillCannulaState.value?.state == FillCannulaStateStreamResponse.FillCannulaState.CANNULA_FILLED) {
                    Text(
                        text = resourceHelper.gs(R.string.fc_complete),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    Text(
                        text = resourceHelper.gs(R.string.fc_filling_with, cannulaFillAmount),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = resourceHelper.gs(R.string.fc_filling_state, fillCannulaState.value?.stateId),
                        style = MaterialTheme.typography.bodyMedium
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
                    text = resourceHelper.gs(R.string.fc_cannula_fill_amount),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                DecimalOutlinedText(
                    title = resourceHelper.gs(R.string.fc_fill_amount),
                    value = cannulaFillAmountStr,
                    decimalPlaces = 1,
                    onValueChange = {
                        cannulaFillAmountStr = it
                        cannulaFillAmount = when {
                            it == "" -> null
                            else -> {
                                val d = it.toDoubleOrNull()
                                if (!allowedCannulaFillAmount(d)) {
                                    null
                                } else {
                                    d
                                }
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = resourceHelper.gs(R.string.fc_quick_amount_heading),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "0.1" to resourceHelper.gs(R.string.fc_quick_amount_0_1),
                        "0.3" to resourceHelper.gs(R.string.fc_quick_amount_0_3),
                        "0.5" to resourceHelper.gs(R.string.fc_quick_amount_0_5),
                        "1.0" to resourceHelper.gs(R.string.fc_quick_amount_1_0)
                    ).forEach { (value, label) ->
                        Button(
                            onClick = {
                                cannulaFillAmountStr = value
                                cannulaFillAmount = value.toDouble()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(text = label)
                        }
                    }
                }
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
                        resourceHelper.gs(R.string.fc_action)
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        },
        actions = {
            if (fillCannulaState.value != null) {
                if (fillCannulaState.value?.state == FillCannulaStateStreamResponse.FillCannulaState.CANNULA_FILLED) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PrimaryActionButton(
                            text = resourceHelper.gs(R.string.ca_btn_resume_insulin),
                            onClick = { showResumeDialog = true },
                            enabled = pumpRunningState.value == PumpRunningState.Suspended,
                            loading = isResuming,
                            modifier = Modifier
                                .weight(1.5f)
                                .height(56.dp)
                        )
                        SecondaryActionButton(
                            text = resourceHelper.gs(R.string.common_done),
                            onClick = { navigateBack() },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        )
                    }
                }
            } else {
                if (pumpRunningState.value != PumpRunningState.Suspended) {
                    SecondaryActionButton(
                        text = resourceHelper.gs(R.string.ca_btn_suspend_insulin),
                        onClick = { showSuspendDialog = true },
                        enabled = !hasActiveNotifications,
                        loading = isSuspending
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                PrimaryActionButton(
                    text = if (allowedCannulaFillAmount(cannulaFillAmount))
                        resourceHelper.gs(R.string.fc_btn_fill_cannula_u, cannulaFillAmount!!)
                    else
                        resourceHelper.gs(R.string.fc_title),
                    onClick = {
                        refreshScope.launch {
                            cannulaFillAmount.let {
                                if (allowedCannulaFillAmount(it)) {
                                    sendPumpCommand(FillCannulaRequest(InsulinUnit.from1To1000(it).toInt()))
                                }
                            }
                        }
                    },
                    enabled = pumpRunningState.value == PumpRunningState.Suspended &&
                        cannulaFillAmount != null &&
                        allowedCannulaFillAmount(cannulaFillAmount) &&
                        !hasActiveNotifications
                )
            }
        }
    )
}

val fillCannulaScreenCommands = listOf(
    HomeScreenMirrorRequest(),
    TimeSinceResetRequest()
)

@Preview(showBackground = true)
@Composable
private fun FillCannulaScreenPreview() {
    TMobiScreensTheme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalTandemDataStore.current)
            FillCannulaScreen(
                sendPumpCommands = { _ -> true },
                navigateBack = {},
                resourceHelper = ResourceHelperTest(),
                aapsLogger = AAPSLoggerTest()
            )
        }
    }
}
