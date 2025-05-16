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
import androidx.compose.material.icons.filled.BackupTable
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
// import androidx.compose.material.pullrefresh.PullRefreshIndicator
// import androidx.compose.material.pullrefresh.pullRefresh
// import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.common.test.ResourceHelperTest
import app.aaps.pump.tandem.R

import app.aaps.pump.tandem.common.comm.ui.TandemUIDataStore
import app.aaps.pump.tandem.common.database.data.DatabaseQueryParameters
import app.aaps.pump.tandem.common.database.data.DatabaseTarget
import app.aaps.pump.tandem.common.driver.LocalTandemDataStore
import app.aaps.pump.tandem.common.driver.tandemDataStore
import app.aaps.pump.tandem.t_mobi.ui.actions.other.SendType
import app.aaps.pump.tandem.t_mobi.ui.theme.TMobiScreensTheme
import app.aaps.pump.tandem.t_mobi.ui.util.HeaderLine
import app.aaps.pump.tandem.t_mobi.ui.util.LifecycleStateObserver
import app.aaps.pump.tandem.t_mobi.ui.util.intervalOf
import app.aaps.shared.tests.AAPSLoggerTest
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.NotificationBundle
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TempRateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

@Composable
fun DataDisplayMain(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendPumpCommands: (SendType, List<Message>) -> Boolean,
    //refreshDatabase: (DatabaseTarget, DatabaseQueryParameters) -> Unit,
    aapsLogger: AAPSLogger,
    navigateToPumpHistory: () -> Unit,
    navigateToEvents: () -> Unit,
    navigateToNotifications: () -> Unit,
    resourceHelper: ResourceHelper
) {

    val ds = LocalTandemDataStore.current
    val TAG = LTag.PUMPCOMM


    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }
    var notificationsPresent by remember { mutableStateOf(false) }

    fun fetchDataStoreFields(type: SendType) {
        sendPumpCommands(type, dataCommands)
    }

    // fun waitForLoaded() = refreshScope.launch {
    //     var sinceLastFetchTime = 0
    //     while (true) {
    //         val nullFields = actionsFields.filter { field -> field.value == null }.toSet()
    //         if (nullFields.isEmpty()) {
    //             break
    //         }
    //
    //         aapsLogger.info(TAG, "Actions loading: remaining ${nullFields.size}: ${actionsFields.map { it.value }}")
    //         if (sinceLastFetchTime >= 2500) {
    //             aapsLogger.info(TAG, "Actions loading re-fetching with cache")
    //             fetchDataStoreFields(SendType.CACHED)
    //             sinceLastFetchTime = 0
    //         }
    //
    //         withContext(Dispatchers.IO) {
    //             Thread.sleep(250)
    //         }
    //         sinceLastFetchTime += 250
    //     }
    //     aapsLogger.info(TAG, "Actions loading done: ${actionsFields.map { it.value }}")
    //     refreshing = false
    // }

    fun refresh() = refreshScope.launch {
        aapsLogger.info(TAG, "Reloading Main Data display")
        refreshing = true

        //actionsFields.forEach { field -> field.value = null }
        //fetchDataStoreFields(SendType.BUST_CACHE)
        sendPumpCommands(SendType.STANDARD, dataCommands)

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
    //     aapsLogger.info(TAG, "reloading Actions from onStart lifecyclestate")
    //     fetchDataStoreFields(SendType.STANDARD)
    // }

    LaunchedEffect(intervalOf(60)) {
        aapsLogger.info(TAG, "reloading Actions from interval")
        //fetchDataStoreFields(SendType.STANDARD)
        refresh()
    }

    // LaunchedEffect(refreshing) {
    //     waitForLoaded()
    // }


    ds.notificationsPresent.observe(androidx.lifecycle.compose.LocalLifecycleOwner.current, {
        notificationsPresent = ds.notificationsPresent.value!!
    })


    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(isRefreshing = refreshing,
                           state= pullRefreshState,
                           onRefresh = { refresh() })
        // .pullRefresh(state)
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
                    HeaderLine(text = resourceHelper.gs(R.string.data_data))
                    HorizontalDivider()
                }


                item {
                    //val notificationsFound = ds.notificationsPresent.value!!

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        ListItem(
                            headlineContent = { Text(
                                text = resourceHelper.gs(R.string.data_notifications), color = if (notificationsPresent) Color.Red else Color.Unspecified
                            )
                            },
                            supportingContent = {
                                Text(text = resourceHelper.gs(R.string.data_notifications_desc), color = if (notificationsPresent) Color.Red else Color.Unspecified)
                            },
                            leadingContent = {
                                if (notificationsPresent) {
                                    Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                                } else {
                                    Icon(Icons.Filled.NotificationsNone, contentDescription = null)
                                }
                            },
                            modifier = Modifier.clickable {
                                navigateToNotifications()
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
                            headlineContent = { Text(text = resourceHelper.gs(R.string.data_events))
                            },
                            supportingContent = {
                                Text(text = resourceHelper.gs(R.string.data_events_desc))
                            },
                            leadingContent = {
                                Icon(Icons.Filled.EventAvailable, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                navigateToEvents()
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
                            headlineContent = { Text(text = resourceHelper.gs(R.string.data_pump_history))
                            },
                            supportingContent = {
                                Text(text = resourceHelper.gs(R.string.data_pump_history_desc))
                            },
                            leadingContent = {
                                Icon(Icons.Filled.BackupTable, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                navigateToPumpHistory()
                            }
                        )
                    }
                }
            }
        )
    }
}
val dataCommands = listOf(
    *NotificationBundle.allRequests().toTypedArray()
)

// val actionsFields = listOf(
// //     tandemDataStore.basalStatus,
// // //    dataStore.controlIQMode,
// //     tandemDataStore.tempRateActive,
// //     tandemDataStore.tempRateDetails,
// )

@Preview(showBackground = true)
@Composable
private fun DataDisplayPreview_NoNotification() {
    TMobiScreensTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalTandemDataStore.current)
            DataDisplayMain(
                sendPumpCommands = {_, _ -> true},
                //refreshDatabase = { _,_ -> },
                aapsLogger = AAPSLoggerTest(),
                navigateToNotifications = {},
                navigateToPumpHistory = {},
                navigateToEvents = {},
                resourceHelper = ResourceHelperTest()
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun DataDisplayPreview_WithNotification() {
    TMobiScreensTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalTandemDataStore.current)
            tandemDataStore.notificationsPresent.value = true
            DataDisplayMain(
                sendPumpCommands = {_, _ -> true},
                //refreshDatabase = { _,_ -> },
                aapsLogger = AAPSLoggerTest(),
                navigateToNotifications = {},
                navigateToPumpHistory = {},
                navigateToEvents = {},
                resourceHelper = ResourceHelperTest()
            )
        }
    }
}



//
//@Preview(showBackground = true)
//@Composable
//private fun DefaultPreviewInsulinActive_StopMenuOpen() {
//    TMobiScreensTheme() {
//        Surface(
//            modifier = Modifier.fillMaxSize(),
//            color = Color.White,
//        ) {
//            setUpPreviewState(LocalTandemDataStore.current)
//            Actions(
//                sendMessage = { _, _ -> },
//                sendPumpCommands = {_, _ -> true},
//                _suspendInsulinMenuState = true,
//                //openTempRateWindow = {},
//                //navigateToCgmActions = {},
//                aapsLogger = AAPSLoggerTest(),
//                navigateToCartridgeActions = {},
//                navigateToPumpInfo = {}
//            )
//        }
//    }
//}
//
//@Preview(showBackground = true)
//@Composable
//private fun DefaultPreviewInsulinSuspended() {
//    TMobiScreensTheme() {
//        Surface(
//            modifier = Modifier.fillMaxSize(),
//            color = Color.White,
//        ) {
//            setUpPreviewState(LocalTandemDataStore.current)
//            LocalTandemDataStore.current.basalStatus.value = BasalStatus.PUMP_SUSPENDED
//            Actions(
//                sendMessage = { _, _ -> },
//                sendPumpCommands = {_, _ -> true},
//                //openTempRateWindow = {},
//                //navigateToCgmActions = {},
//                aapsLogger = AAPSLoggerTest(),
//                navigateToCartridgeActions = {},
//                navigateToPumpInfo = {}
//            )
//        }
//    }
//}


fun setUpPreviewState(ds: TandemUIDataStore) {
    ds.pumpConnected.value = true
    ds.pumpLastConnectionTimestamp.value = Instant.now().minusSeconds(120)
    ds.batteryPercent.value = 50
    ds.cartridgeRemainingUnits.value = 100
}