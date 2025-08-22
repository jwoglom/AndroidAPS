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
import app.aaps.pump.tandem.R
import app.aaps.core.ui.R as Rco
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.tandem.common.driver.LocalTandemDataStore
import app.aaps.pump.tandem.t_mobi.ui.actions.other.BasalStatus
import app.aaps.pump.tandem.t_mobi.ui.util.DecimalOutlinedText
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.request.control.FillCannulaRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillCannulaStateStreamResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun FillCannula(innerPadding: PaddingValues,
                _fillCannulaMenuState: Boolean = false,
                resourceHelper: ResourceHelper,
                sendPumpCommands: (List<Message>) -> Boolean,
                refreshScope : CoroutineScope
) {

    var showFillCannulaMenu by remember { mutableStateOf(_fillCannulaMenuState) }
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
                Text(text = resourceHelper.gs(R.string.fc_title))
            },
            supportingContent = {
            },
            leadingContent = {
                Icon(Icons.Filled.Settings, contentDescription = null)
            },
            modifier = Modifier.clickable {
                refreshScope.launch {
                    ds.fillCannulaState.value = null
                    sendPumpCommand(TimeSinceResetRequest())
                    showFillCannulaMenu = true
                }
            }
        )

        DropdownMenu(
            expanded = showFillCannulaMenu,
            onDismissRequest = {  },
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        ) {
            val basalStatus = ds.basalStatus.observeAsState()
            val fillCannulaState = ds.fillCannulaState.observeAsState()
            var cannulaFillAmountStr by remember { mutableStateOf<String?>(null) }
            var cannulaFillAmount by remember { mutableStateOf<Double?>(null) }

            fun allowedCannulaFillAmount(units: Double?): Boolean {
                return units != null && units > 0 && units <= 3.0
            }

            AlertDialog(
                onDismissRequest = {
                },
                title = {
                    Text(text = resourceHelper.gs(R.string.fc_title))
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
                            if (fillCannulaState.value != null) {
                                if (fillCannulaState.value?.state == FillCannulaStateStreamResponse.FillCannulaState.CANNULA_FILLED) {
                                    item {
                                        Text(text = resourceHelper.gs(R.string.fc_complete))
                                    }
                                } else {
                                    item {
                                        Text(text = resourceHelper.gs(R.string.fc_filling_with, cannulaFillAmount))
                                        Text("\n\n")
                                        Text(text = resourceHelper.gs(R.string.fc_filling_state, fillCannulaState.value?.stateId))
                                    }
                                }

                            } else if (basalStatus.value == BasalStatus.PUMP_SUSPENDED) {
                                item {
                                    Text(text = resourceHelper.gs(R.string.fc_cannula_fill_amount))
                                    Text("\n\n")
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
                                    Text("\n")

                                }
                            } else {
                                item {
                                    Text(text = resourceHelper.gs(R.string.ca_before_stop_delivery,
                                                                  resourceHelper.gs(R.string.fc_action)))
                                    Text("\n")
                                }
                            }
                        }
                    )
                },
                dismissButton = {
                    if (fillCannulaState.value != null) {
                        /* */
                    } else {
                        TextButton(
                            onClick = {
                                showFillCannulaMenu = false
                            },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text(text = resourceHelper.gs(Rco.string.cancel))
                        }
                    }
                },
                confirmButton = {
                    if (fillCannulaState.value != null) {
                        if (fillCannulaState.value?.state == FillCannulaStateStreamResponse.FillCannulaState.CANNULA_FILLED) {
                            TextButton(
                                onClick = {
                                    showFillCannulaMenu = false
                                },
                                modifier = Modifier.padding(top = 16.dp)
                            ) {
                                Text(text = resourceHelper.gs(R.string.common_done))
                            }
                        }
                    } else if (basalStatus.value == BasalStatus.PUMP_SUSPENDED) {
                        TextButton(
                            onClick = {
                                refreshScope.launch {
                                    cannulaFillAmount.let {
                                        if (allowedCannulaFillAmount(it)) {
                                            sendPumpCommand(FillCannulaRequest(InsulinUnit.from1To1000(it).toInt()))
                                        }
                                    }
                                }
                            },
                            enabled = basalStatus.value == BasalStatus.PUMP_SUSPENDED && cannulaFillAmount != null && allowedCannulaFillAmount(cannulaFillAmount),
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            if (allowedCannulaFillAmount(cannulaFillAmount)) {
                                Text(text = resourceHelper.gs(R.string.fc_btn_fill_cannula_u, cannulaFillAmount!!))
                            } else {
                                Text(text = resourceHelper.gs(R.string.fc_title))
                            }
                        }
                    }
                }
            )

        }

    }



}