@file:OptIn(ExperimentalMaterial3Api::class)

package app.aaps.pump.tandem.mobi.ui.actions.cartridge

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
import androidx.compose.material.icons.filled.Settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import app.aaps.pump.common.test.ResourceHelperTest
import app.aaps.pump.tandem.common.driver.LocalTandemDataStore
import app.aaps.pump.tandem.mobi.ui.actions.setUpPreviewState
import app.aaps.pump.tandem.mobi.ui.util.Line
import app.aaps.pump.tandem.mobi.ui.util.intervalOf
import app.aaps.pump.tandem.mobi.ui.theme.TMobiScreensTheme
import app.aaps.pump.tandem.mobi.ui.util.HeaderLineWithBackButton
import app.aaps.shared.tests.AAPSLoggerTest
import app.aaps.pump.tandem.R
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LoadStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LoadStatusResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class MenuAvailability(val enabled: Boolean, val disabledReason: String? = null)

private fun changeCartridgeAvailability(
    loadStatus: LoadStatusResponse?,
    resourceHelper: ResourceHelper,
): MenuAvailability {
    if (loadStatus?.isLoadingActive != true) return MenuAvailability(true)
    return when (loadStatus.loadState) {
        LoadStatusResponse.LoadState.PRIME_TUBING ->
            MenuAvailability(false, resourceHelper.gs(R.string.ca_disabled_fill_tubing_in_progress))
        LoadStatusResponse.LoadState.PRIME_CANNULA ->
            MenuAvailability(false, resourceHelper.gs(R.string.ca_disabled_fill_cannula_in_progress))
        else -> MenuAvailability(true)
    }
}

private fun fillTubingAvailability(
    loadStatus: LoadStatusResponse?,
    resourceHelper: ResourceHelper,
): MenuAvailability {
    if (loadStatus?.isLoadingActive != true) return MenuAvailability(true)
    return when (loadStatus.loadState) {
        LoadStatusResponse.LoadState.CHANGE_CARTRIDGE,
        LoadStatusResponse.LoadState.LOAD_CARTRIDGE ->
            MenuAvailability(false, resourceHelper.gs(R.string.ca_disabled_change_in_progress))
        LoadStatusResponse.LoadState.PRIME_CANNULA ->
            MenuAvailability(false, resourceHelper.gs(R.string.ca_disabled_fill_cannula_in_progress))
        else -> MenuAvailability(true)
    }
}

private fun fillCannulaAvailability(
    loadStatus: LoadStatusResponse?,
    resourceHelper: ResourceHelper,
): MenuAvailability {
    if (loadStatus?.isLoadingActive != true) return MenuAvailability(true)
    return when (loadStatus.loadState) {
        LoadStatusResponse.LoadState.CHANGE_CARTRIDGE,
        LoadStatusResponse.LoadState.LOAD_CARTRIDGE ->
            MenuAvailability(false, resourceHelper.gs(R.string.ca_disabled_change_in_progress))
        LoadStatusResponse.LoadState.PRIME_TUBING ->
            MenuAvailability(false, resourceHelper.gs(R.string.ca_disabled_fill_tubing_in_progress))
        else -> MenuAvailability(true)
    }
}

@Composable
private fun GatedMenuItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    availability: MenuAvailability,
    onClick: () -> Unit,
) {
    val alpha = if (availability.enabled) 1f else 0.45f
    val itemColors = ListItemDefaults.colors(
        headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        leadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        supportingColor = MaterialTheme.colorScheme.error,
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.TopStart)
    ) {
        ListItem(
            colors = itemColors,
            headlineContent = { Text(text = title) },
            supportingContent = {
                availability.disabledReason?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
            },
            leadingContent = {
                Icon(icon, contentDescription = null)
            },
            modifier = if (availability.enabled) Modifier.clickable { onClick() } else Modifier,
        )
    }
}

@Composable
fun CartridgeActions(
    innerPadding: PaddingValues = PaddingValues(),
    sendPumpCommands: (List<Message>) -> Boolean,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    navigateToChangeCartridge: () -> Unit,
    navigateToFillTubing: () -> Unit,
    navigateToFillCannula: () -> Unit,
    navigateToSiteReminder: () -> Unit,
    navigateBack: () -> Unit,
    showHeader: Boolean = true
) {

    val ds = LocalTandemDataStore.current
    @Suppress("PropertyName")
    val TAG = LTag.PUMP

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }
    var hasAutoResumed by rememberSaveable { mutableStateOf(false) }

    fun refresh() = refreshScope.launch {
        aapsLogger.info(TAG, "reloading CartridgeActions with force")
        refreshing = true
        sendPumpCommands(cartridgeActionsCommands)
        withContext(Dispatchers.IO) { Thread.sleep(250) }
        refreshing = false
    }

    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        aapsLogger.info(TAG, "Initial LoadStatus poll on CartridgeActions")
        sendPumpCommands(listOf(LoadStatusRequest()))
    }

    LaunchedEffect(intervalOf(60)) {
        aapsLogger.info(TAG, "reloading CartridgeActions from interval")
        refresh()
    }

    val loadStatus = ds.loadStatus.observeAsState()

    // Auto-resume into the active sub-screen exactly once per entry
    LaunchedEffect(loadStatus.value) {
        if (hasAutoResumed) return@LaunchedEffect
        val ls = loadStatus.value ?: return@LaunchedEffect
        if (!ls.isLoadingActive) return@LaunchedEffect
        when (ls.loadState) {
            LoadStatusResponse.LoadState.CHANGE_CARTRIDGE,
            LoadStatusResponse.LoadState.LOAD_CARTRIDGE -> {
                hasAutoResumed = true
                aapsLogger.info(TAG, "auto-resuming into ChangeCartridge from loadState=${ls.loadState}")
                navigateToChangeCartridge()
            }
            LoadStatusResponse.LoadState.PRIME_TUBING -> {
                hasAutoResumed = true
                aapsLogger.info(TAG, "auto-resuming into FillTubing from loadState=${ls.loadState}")
                navigateToFillTubing()
            }
            LoadStatusResponse.LoadState.PRIME_CANNULA -> {
                hasAutoResumed = true
                aapsLogger.info(TAG, "auto-resuming into FillCannula from loadState=${ls.loadState}")
                navigateToFillCannula()
            }
            else -> Unit
        }
    }

    val changeCartridgeMenu = changeCartridgeAvailability(loadStatus.value, resourceHelper)
    val fillTubingMenu = fillTubingAvailability(loadStatus.value, resourceHelper)
    val fillCannulaMenu = fillCannulaAvailability(loadStatus.value, resourceHelper)

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
                if (showHeader) {
                    item {
                        HeaderLineWithBackButton(
                            text = resourceHelper.gs(R.string.ca_label),
                            onBackClick = navigateBack,
                            resourceHelper = resourceHelper
                        )
                        HorizontalDivider()
                    }
                }

                item {
                    GatedMenuItem(
                        title = resourceHelper.gs(R.string.cc_title),
                        icon = Icons.Filled.Settings,
                        availability = changeCartridgeMenu,
                        onClick = {
                            refreshScope.launch {
                                hasAutoResumed = true
                                ds.enterChangeCartridgeState.value = null
                                ds.detectingCartridgeState.value = null
                                sendPumpCommands(listOf(TimeSinceResetRequest()))
                                navigateToChangeCartridge()
                            }
                        }
                    )
                }

                item {
                    GatedMenuItem(
                        title = resourceHelper.gs(R.string.ft_title),
                        icon = Icons.Filled.Settings,
                        availability = fillTubingMenu,
                        onClick = {
                            refreshScope.launch {
                                hasAutoResumed = true
                                ds.fillTubingState.value = null
                                ds.exitFillTubingState.value = null
                                ds.inFillTubingMode.value = false
                                sendPumpCommands(listOf(TimeSinceResetRequest()))
                                navigateToFillTubing()
                            }
                        }
                    )
                }

                item {
                    GatedMenuItem(
                        title = resourceHelper.gs(R.string.fc_title),
                        icon = Icons.Filled.Settings,
                        availability = fillCannulaMenu,
                        onClick = {
                            refreshScope.launch {
                                hasAutoResumed = true
                                ds.fillCannulaState.value = null
                                sendPumpCommands(listOf(TimeSinceResetRequest()))
                                navigateToFillCannula()
                            }
                        }
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        ListItem(
                            headlineContent = { Text(
                                text = resourceHelper.gs(R.string.sr_title)
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
    LoadStatusRequest()
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
                navigateToChangeCartridge = {},
                navigateToFillTubing = {},
                navigateToFillCannula = {},
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
            CartridgeActions(
                sendPumpCommands = { _ -> true},
                navigateBack = {},
                navigateToChangeCartridge = {},
                navigateToFillTubing = {},
                navigateToFillCannula = {},
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
            CartridgeActions(
                sendPumpCommands = { _ -> true},
                navigateBack = {},
                navigateToChangeCartridge = {},
                navigateToFillTubing = {},
                navigateToFillCannula = {},
                navigateToSiteReminder = {},
                resourceHelper = ResourceHelperTest(),
                aapsLogger = AAPSLoggerTest()
            )
        }
    }
}
