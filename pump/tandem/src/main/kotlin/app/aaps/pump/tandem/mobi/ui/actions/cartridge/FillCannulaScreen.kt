@file:OptIn(ExperimentalMaterial3Api::class)

package app.aaps.pump.tandem.mobi.ui.actions.cartridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.font.FontWeight
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
import app.aaps.pump.tandem.mobi.ui.util.DecimalOutlinedText
import app.aaps.pump.tandem.mobi.ui.util.HeaderLineWithBackButton
import app.aaps.pump.tandem.mobi.ui.util.intervalOf
import app.aaps.shared.tests.AAPSLoggerTest
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.request.control.FillCannulaRequest
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

        withContext(Dispatchers.IO) {
            Thread.sleep(250)
        }

        refreshing = false
    }

    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(intervalOf(60)) {
        aapsLogger.info(TAG, "reloading FillCannulaScreen from interval")
        refresh()
    }

    // Poll for alerts and alarms immediately on screen open
    LaunchedEffect(Unit) {
        aapsLogger.info(TAG, "Initial alert/alarm poll on FillCannulaScreen")
        sendPumpCommands(listOf(AlertStatusRequest(), AlarmStatusRequest()))
    }

    // Poll for alerts and alarms every 10 seconds while on the Fill Cannula screen
    LaunchedEffect(intervalOf(10)) {
        aapsLogger.info(TAG, "Periodic alert/alarm poll on FillCannulaScreen")
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
        val fillCannulaState = ds.fillCannulaState.observeAsState()

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
            if (showHeader) {
                HeaderLineWithBackButton(
                    text = resourceHelper.gs(R.string.fc_title),
                    onBackClick = navigateBack,
                    resourceHelper = resourceHelper
                )
                HorizontalDivider()
            }

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
            }

            // Spacer to push button to bottom
            Spacer(modifier = Modifier.weight(1f))

            // Action button at bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (fillCannulaState.value != null) {
                    if (fillCannulaState.value?.state == FillCannulaStateStreamResponse.FillCannulaState.CANNULA_FILLED) {
                        Button(
                            onClick = {
                                navigateBack()
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
                    }
                } else if (pumpRunningState.value == PumpRunningState.Suspended) {
                    Button(
                        onClick = {
                            refreshScope.launch {
                                cannulaFillAmount.let {
                                    if (allowedCannulaFillAmount(it)) {
                                        sendPumpCommand(FillCannulaRequest(InsulinUnit.from1To1000(it).toInt()))
                                    }
                                }
                            }
                        },
                        enabled = pumpRunningState.value == PumpRunningState.Suspended && cannulaFillAmount != null && allowedCannulaFillAmount(cannulaFillAmount),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = if (allowedCannulaFillAmount(cannulaFillAmount))
                                resourceHelper.gs(R.string.fc_btn_fill_cannula_u, cannulaFillAmount!!)
                            else
                                resourceHelper.gs(R.string.fc_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
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
