package app.aaps.pump.tandem.mobi.ui.actions.cartridge

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
import com.jwoglom.pumpx2.pump.messages.request.control.EnterChangeCartridgeModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ExitChangeCartridgeModeRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.response.controlStream.EnterChangeCartridgeModeStateStreamResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ChangeCartridge(innerPadding: PaddingValues,
                    _changeCartridgeMenuState: Boolean = false,
                    resourceHelper: ResourceHelper,
                    sendPumpCommands: (List<Message>) -> Boolean,
                    refreshScope : CoroutineScope
) {

    var showChangeCartridgeMenu by remember { mutableStateOf(_changeCartridgeMenuState) }
    val ds = LocalTandemDataStore.current

    fun sendPumpCommand(msg: Message) {
        sendPumpCommands(listOf(msg))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.TopStart)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = resourceHelper.gs(R.string.cc_title)
                )
            },
            supportingContent = {
            },
            leadingContent = {
                Icon(Icons.Filled.Settings, contentDescription = null)
            },
            modifier = Modifier.clickable {
                refreshScope.launch {
                    ds.enterChangeCartridgeState.value = null
                    ds.detectingCartridgeState.value = null
                    sendPumpCommand(TimeSinceResetRequest())
                    showChangeCartridgeMenu = true
                }
            }
        )

        DropdownMenu(
            expanded = showChangeCartridgeMenu,
            onDismissRequest = {  },
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        ) {
            val pumpRunningState = ds.pumpRunningState.observeAsState()
            val inChangeCartridgeMode = ds.inChangeCartridgeMode.observeAsState()
            val enterChangeCartridgeState = ds.enterChangeCartridgeState.observeAsState()
            val detectingCartridgeState = ds.detectingCartridgeState.observeAsState()

            AlertDialog(
                onDismissRequest = {
                    if (inChangeCartridgeMode.value == false) {
                        showChangeCartridgeMenu = false
                    }
                },
                title = {
                    Text(text = resourceHelper.gs(R.string.cc_title))
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
                            if (detectingCartridgeState.value != null) {
                                if (detectingCartridgeState.value?.isComplete == true) {
                                    item {
                                        Text(text = resourceHelper.gs(R.string.cc_complete))
                                        Text("\n")
                                        Text(text = resourceHelper.gs(R.string.common_percent_complete, "${detectingCartridgeState.value?.percentComplete}"))
                                    }
                                } else {
                                    item {
                                        Text(text = resourceHelper.gs(R.string.cc_detect_insulin_cart))
                                        Text("\n")
                                        Text(text = resourceHelper.gs(R.string.common_percent_complete,"${detectingCartridgeState.value?.percentComplete}"))
                                    }
                                }
                            } else if (enterChangeCartridgeState.value?.state == EnterChangeCartridgeModeStateStreamResponse.ChangeCartridgeState.READY_TO_CHANGE) {
                                item {
                                    Text(text = resourceHelper.gs(R.string.cc_can_remove_cart))
                                    Text("\n")
                                    Text(text = resourceHelper.gs(R.string.cc_when_inserted_press))
                                }
                            } else if (inChangeCartridgeMode.value == true) {
                                item {
                                    Text(text = resourceHelper.gs(R.string.cc_preparing_cc))
                                    Text("\n")
                                }
                            } else if (pumpRunningState.value == PumpRunningState.Suspended) {
                                item {
                                    Text(text = resourceHelper.gs(R.string.ca_disconnect_pump_from_site,
                                                                  resourceHelper.gs(R.string.cc_btn_begin)))
                                    Text("\n")
                                }
                            } else {
                                item {
                                    Text(text = resourceHelper.gs(R.string.ca_before_stop_delivery,
                                                                  resourceHelper.gs(R.string.cc_action)))
                                    Text("\n")
                                }
                            }
                        }
                    )
                },
                dismissButton = {
                    if (inChangeCartridgeMode.value == true) {
                        /* */
                    } else if (detectingCartridgeState.value?.isComplete == true) {
                        /* */
                    } else if (enterChangeCartridgeState.value?.state == EnterChangeCartridgeModeStateStreamResponse.ChangeCartridgeState.READY_TO_CHANGE) {
                        /* */
                    } else {
                        TextButton(
                            onClick = {
                                showChangeCartridgeMenu = false
                            },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text(text = resourceHelper.gs(Rco.string.cancel))
                        }
                    }
                },
                confirmButton = {
                    if (detectingCartridgeState.value?.isComplete == true) {
                        TextButton(
                            onClick = {
                                showChangeCartridgeMenu = false
                            },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text(text = resourceHelper.gs(R.string.common_done))
                        }
                    } else if (enterChangeCartridgeState.value?.state == EnterChangeCartridgeModeStateStreamResponse.ChangeCartridgeState.READY_TO_CHANGE) {
                        TextButton(
                            onClick = {
                                refreshScope.launch {
                                    sendPumpCommand(ExitChangeCartridgeModeRequest())
                                }
                            },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text(text = resourceHelper.gs(R.string.cc_btn_cart_inserted))
                        }
                    } else {
                        TextButton(
                            onClick = {
                                refreshScope.launch {
                                    sendPumpCommand(EnterChangeCartridgeModeRequest())
                                }
                            },
                            enabled = pumpRunningState.value == PumpRunningState.Suspended,
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text(text = resourceHelper.gs(R.string.cc_btn_begin))
                        }
                    }
                }
            )

        }

    }



}