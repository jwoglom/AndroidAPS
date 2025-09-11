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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.common.defs.PumpRunningState
import app.aaps.pump.tandem.R
import app.aaps.core.ui.R as Rco
import app.aaps.pump.tandem.common.driver.LocalTandemDataStore
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.EnterFillTubingModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ExitFillTubingModeRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.response.controlStream.ExitFillTubingModeStateStreamResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun FillTubing(innerPadding: PaddingValues,
               _fillTubingMenuState: Boolean = false,
                resourceHelper: ResourceHelper,
                sendPumpCommands: (List<Message>) -> Boolean,
                refreshScope : CoroutineScope
) {

    var showFillTubingMenu by remember { mutableStateOf(_fillTubingMenuState) }
    val ds = LocalTandemDataStore.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.TopStart)
    ) {
        ListItem(
            headlineContent = {
                Text(text = resourceHelper.gs(R.string.ft_title))
            },
            supportingContent = {
            },
            leadingContent = {
                Icon(Icons.Filled.Settings, contentDescription = null)
            },
            modifier = Modifier.clickable {
                refreshScope.launch {
                    ds.fillTubingState.value = null
                    ds.exitFillTubingState.value = null
                    ds.inFillTubingMode.value = false
                    sendPumpCommands(listOf(TimeSinceResetRequest()))
                    showFillTubingMenu = true
                }
            }
        )

        DropdownMenu(
            expanded = showFillTubingMenu,
            onDismissRequest = {  },
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        ) {
            val pumpRunningState = ds.pumpRunningState.observeAsState()
            val inFillTubingMode = ds.inFillTubingMode.observeAsState()
            val fillTubingState = ds.fillTubingState.observeAsState()
            val exitFillTubingState = ds.exitFillTubingState.observeAsState()

            AlertDialog(
                onDismissRequest = {
                    if (inFillTubingMode.value == false) {
                        showFillTubingMenu = false
                    }
                },
                title = {
                    Text(text = resourceHelper.gs(R.string.ft_title))
                },
                text = {
                    LazyColumn(
                        contentPadding = innerPadding,
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(horizontal = 0.dp),
                        content = {
                            if (exitFillTubingState.value != null) {
                                if (exitFillTubingState.value?.state == ExitFillTubingModeStateStreamResponse.ExitFillTubingModeState.TUBING_FILLED) {
                                    item {
                                        Text(text = resourceHelper.gs(R.string.ft_complete))
                                    }
                                } else {
                                    item {
                                        Text(text = resourceHelper.gs(R.string.ft_finalizing_wait))
                                        Text(text = "\n\n")
                                        Text(text = resourceHelper.gs(R.string.ft_finalizing_status_NOT_COMPLETE))
                                    }
                                }
                            } else if (inFillTubingMode.value == true) {
                                if (fillTubingState.value == null) {
                                    item {
                                        Text(text = resourceHelper.gs(R.string.ft_hold_pump_button))
                                        Text(text = "\n\n")
                                        Text(text = resourceHelper.gs(R.string.ft_no_filled_insulin))
                                    }
                                } else if (fillTubingState.value?.buttonDown == true) {
                                    item {
                                        Text(text = resourceHelper.gs(R.string.ft_filling))
                                    }
                                } else if (fillTubingState.value?.buttonDown == false) {
                                    item {
                                        Text(text = resourceHelper.gs(R.string.ft_stopped_fill_1))
                                        Text(text = "\n\n")
                                        Text(text = resourceHelper.gs(R.string.ft_stopped_fill_2))
                                    }
                                }
                            } else if (pumpRunningState.value == PumpRunningState.Suspended) {
                                item {
                                    Text(text = resourceHelper.gs(R.string.ca_disconnect_pump_from_site,
                                                                  resourceHelper.gs(R.string.ft_btn_begin)))
                                    Text("\n")
                                }
                            } else {
                                item {
                                    Text(text = resourceHelper.gs(R.string.ca_before_stop_delivery,
                                                                  resourceHelper.gs(R.string.ft_action)))
                                    Text("\n")
                                }
                            }
                        }
                    )
                },
                dismissButton = {
                    if (inFillTubingMode.value == true ||
                        exitFillTubingState.value?.state == ExitFillTubingModeStateStreamResponse.ExitFillTubingModeState.TUBING_FILLED) {
                    } else {
                        TextButton(
                            onClick = {
                                showFillTubingMenu = false
                            },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text(text = resourceHelper.gs(Rco.string.cancel))
                        }
                    }
                },
                confirmButton = {
                    if (exitFillTubingState.value != null) {
                        if (exitFillTubingState.value?.state == ExitFillTubingModeStateStreamResponse.ExitFillTubingModeState.TUBING_FILLED) {
                            TextButton(
                                onClick = {
                                    showFillTubingMenu = false
                                },
                                enabled = pumpRunningState.value == PumpRunningState.Suspended,
                                modifier = Modifier.padding(top = 16.dp)
                            ) {
                                Text(text = resourceHelper.gs(R.string.common_done))
                            }
                        }
                    } else if (inFillTubingMode.value == true) {
                        if (fillTubingState.value?.buttonDown == false) {
                            TextButton(
                                onClick = {
                                    refreshScope.launch {
                                        sendPumpCommands(listOf(ExitFillTubingModeRequest()))
                                    }
                                },
                                enabled = pumpRunningState.value == PumpRunningState.Suspended,
                                modifier = Modifier.padding(top = 16.dp)
                            ) {
                                Text(text = resourceHelper.gs(R.string.ft_btn_complete))
                            }
                        }
                    } else {
                        TextButton(
                            onClick = {
                                refreshScope.launch {
                                    sendPumpCommands(listOf(EnterFillTubingModeRequest()))
                                }
                            },
                            enabled = pumpRunningState.value == PumpRunningState.Suspended,
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text(text = resourceHelper.gs(R.string.ft_btn_begin))
                        }
                    }
                }
            )

        }

    }


}