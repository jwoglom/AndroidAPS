@file:OptIn(ExperimentalMaterial3Api::class)

package app.aaps.pump.tandem.t_mobi.ui.data

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.lifecycle.Observer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.common.test.ResourceHelperTest
import app.aaps.pump.tandem.R
import app.aaps.pump.tandem.common.data.defs.RefreshData
import app.aaps.pump.tandem.common.driver.LocalTandemDataStore
import app.aaps.pump.tandem.common.driver.tandemDataStore
import app.aaps.pump.tandem.t_mobi.ui.actions.setUpPreviewState
import app.aaps.pump.tandem.t_mobi.ui.theme.TMobiScreensTheme
import app.aaps.pump.tandem.t_mobi.ui.util.HeaderLineWithBackButton
import app.aaps.pump.tandem.t_mobi.ui.util.intervalOf
import app.aaps.shared.tests.AAPSLoggerTest

import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.NotificationBundle
import com.jwoglom.pumpx2.pump.messages.request.control.DismissNotificationRequest

import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlarmStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMAlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.MalfunctionStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ReminderStatusResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// TODO Ui:Notifications - translate notifications items

@Composable
fun Notifications(
    innerPadding: PaddingValues = PaddingValues(),
    aapsLogger: AAPSLogger,
    sendPumpCommands: (List<Message>) -> Boolean,
    refreshMainAppData: (RefreshData) -> Unit,
    navigateBack: () -> Unit,
    resourceHelper: ResourceHelper
) {

    val ds = LocalTandemDataStore.current

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val TAG = LTag.PUMPCOMM

    // fun fetchDataStoreFields() {
    //     sendPumpCommands(notificationsCommands)
    // }

    // fun waitForLoaded() = refreshScope.launch {
    //     var sinceLastFetchTime = 0
    //     while (true) {
    //         val nullFields = notificationsFields.filter { field -> field.value == null }.toSet()
    //         if (nullFields.isEmpty()) {
    //             break
    //         }
    //
    //         aapsLogger.info(TAG, "Notifications loading: remaining ${nullFields.size}: ${notificationsFields.map { it.value }}")
    //         if (sinceLastFetchTime >= 2500) {
    //             aapsLogger.info(TAG, "Notifications loading re-fetching with cache")
    //             fetchDataStoreFields()
    //             sinceLastFetchTime = 0
    //         }
    //
    //         withContext(Dispatchers.IO) {
    //             Thread.sleep(250)
    //         }
    //         sinceLastFetchTime += 250
    //     }
    //     aapsLogger.info(TAG, "Notifications loading done: ${notificationsFields.map { it.value }}")
    //     refreshing = false
    // }

    fun refresh() = refreshScope.launch {
        aapsLogger.info(TAG, "Reloading Notifications")
        refreshing = true

        //notificationsFields.forEach { field -> field.value = null }
        sendPumpCommands(notificationsCommands)

        // workaround for indicator not disappearing after
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
    //     aapsLogger.info(TAG, "reloading Notifications from onStart lifecyclestate")
    //     fetchDataStoreFields(SendType.STANDARD)
    // }

    LaunchedEffect(intervalOf(60)) {
        aapsLogger.info(TAG, "Reloading Notifications from interval")
        refreshMainAppData(RefreshData.SEMAPHORE_NOTIFICATIONS)
        refresh()
    }

    // LaunchedEffect(refreshing) {
    //     waitForLoaded()
    // }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(isRefreshing = refreshing,
                           state= pullRefreshState,
                           onRefresh = { refresh() })
    ) {
        PullToRefreshDefaults.Indicator(
            isRefreshing = refreshing,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
        )

        val notifications = remember { mutableStateListOf<Any>()}
        ds.notificationBundle.observe(androidx.lifecycle.compose.LocalLifecycleOwner.current, Observer {
            ds.notificationBundle.value?.let {
                notifications.clear()
                notifications.addAll(it.get().toTypedArray())
            }
        })

        LazyColumn(
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 0.dp),
            content = {
                item {
                    HeaderLineWithBackButton(text=resourceHelper.gs(R.string.data_notifications), onBackClick=navigateBack, backgroundColor = Color.LightGray)
                    HorizontalDivider()
                }

                aapsLogger.info(TAG, "Notifications fetched: $notifications")
                notifications.forEach {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .wrapContentSize(Alignment.TopStart)
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        when (it) {
                                            is AlertStatusResponse.AlertResponseType -> "Alert: ${it.name}"
                                            is ReminderStatusResponse.ReminderType -> "Reminder: ${it.name}"
                                            is AlarmStatusResponse.AlarmResponseType -> "Alarm: ${it.name}"
                                            is CGMAlertStatusResponse.CGMAlert -> "CGM Alert: ${it.name}"
                                            is MalfunctionStatusResponse -> "MALFUNCTION: ${it.errorString}"
                                            else -> "$it"
                                        }
                                    )
                                },
                                supportingContent = {
                                    when (it) {
                                        is AlertStatusResponse.AlertResponseType -> Text(
                                            it.description ?: ""
                                        )
                                        is AlarmStatusResponse.AlarmResponseType -> Text(
                                            it.description ?: ""
                                        )
                                        is MalfunctionStatusResponse -> Text("This alert cannot be cleared and DIY app developers cannot assist you with this problem.\nFor further instructions please contact Tandem technical support and reference the above code.")
                                        else -> {}
                                    }
                                },
                                leadingContent = {
                                    when (it) {
                                        is AlertStatusResponse.AlertResponseType -> Icon(
                                            Icons.Filled.Info,
                                            contentDescription = "Alert"
                                        )

                                        is ReminderStatusResponse.ReminderType -> Icon(
                                            Icons.Filled.Info,
                                            contentDescription = "Reminder"
                                        )

                                        is AlarmStatusResponse.AlarmResponseType -> Icon(
                                            Icons.Filled.Warning,
                                            contentDescription = "Alarm"
                                        )

                                        is CGMAlertStatusResponse.CGMAlert -> Icon(
                                            Icons.Filled.Info,
                                            contentDescription = "CGM Alert"
                                        )

                                        is MalfunctionStatusResponse -> Icon(
                                            Icons.Filled.Warning,
                                            contentDescription = "Malfunction"
                                        )
                                    }
                                },
                                modifier = Modifier.clickable {
                                    refreshScope.launch {
                                        when (it) {
                                            is AlertStatusResponse.AlertResponseType -> {
                                                sendPumpCommands(
                                                    listOf(
                                                        DismissNotificationRequest(
                                                            DismissNotificationRequest.NotificationType.ALERT,
                                                            it.bitmask().toLong()
                                                        )
                                                    )
                                                )
                                            }

                                            is ReminderStatusResponse.ReminderType -> {
                                                sendPumpCommands(
                                                    listOf(
                                                        DismissNotificationRequest(
                                                            DismissNotificationRequest.NotificationType.REMINDER,
                                                            it.id().toLong()
                                                        )
                                                    )
                                                )
                                            }

                                            is AlarmStatusResponse.AlarmResponseType -> {
                                                sendPumpCommands(
                                                    listOf(
                                                        DismissNotificationRequest(
                                                            DismissNotificationRequest.NotificationType.ALARM,
                                                            it.bitmask().toLong()
                                                        )
                                                    )
                                                )
                                            }

                                            is CGMAlertStatusResponse.CGMAlert -> {
                                                sendPumpCommands(
                                                    listOf(
                                                        DismissNotificationRequest(
                                                            DismissNotificationRequest.NotificationType.CGM_ALERT,
                                                            it.id().toLong()
                                                        )
                                                    )
                                                )
                                            }
                                        }

                                        withContext(Dispatchers.IO) {
                                            Thread.sleep(500)
                                            refresh()
                                        }
                                    }
                                }
                            )
                        }
                    }
                } // notification for each

            }
        )
    }
}

val notificationsCommands = listOf(
    HomeScreenMirrorRequest(),
    *NotificationBundle.allRequests().toTypedArray()
)

// val notificationsFields = listOf(
//     tandemDataStore.notificationBundle
// )

@Preview(showBackground = true)
@Composable
private fun DefaultPreview_Notifications() {
    TMobiScreensTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalTandemDataStore.current)
            Notifications(
                sendPumpCommands = { _ -> true},
                aapsLogger = AAPSLoggerTest(),
                navigateBack = {},
                resourceHelper = ResourceHelperTest(),
                refreshMainAppData = {}
            )
        }
    }
}

