package app.aaps.pump.tandem.t_mobi.ui.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.pump.tandem.common.driver.LocalTandemDataStore
import app.aaps.pump.tandem.t_mobi.ui.theme.TMobiScreensTheme
import app.aaps.pump.tandem.t_mobi.ui.util.DottedDivider
import app.aaps.pump.tandem.t_mobi.ui.util.HeaderLine
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpVersionResponse


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PumpInfo(innerPadding: PaddingValues = PaddingValues(),
             navigateBack: () -> Unit
             ) {

    val pumpInfo = LocalTandemDataStore.current.pumpVersionResponse.value!!

    @Composable
    fun pumpInfoRow(label: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 18.sp)
            Text(text = value, fontSize = 18.sp)
        }
        DottedDivider(    dotRadius = 1.dp,
        spaceBetween = 6.dp,)
    }

    LazyColumn(
        contentPadding = innerPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp),
        content = {
            item {
                HeaderLine("Pump Info")
                HorizontalDivider()
            }

            item {
                pumpInfoRow("Serial Number", "${pumpInfo.serialNum}")
            }

            item {
                pumpInfoRow("Pump Software", pumpInfo.pumpRev)
            }

            item {
                pumpInfoRow("ARM S/W Version", "${pumpInfo.armSwVer}")
            }

            item {
                pumpInfoRow("S/W Part Number", "${pumpInfo.partNum}")
            }

            item {
                pumpInfoRow("ConfigA Bits", "0x%08X".format(pumpInfo.configABits))
            }

            item {
                pumpInfoRow("ConfigB Bits", "0x%08X".format(pumpInfo.configBBits))
            }

            item {
                pumpInfoRow("Pump Model", "${pumpInfo.modelNum}")
            }

            item {
                pumpInfoRow("PCBA serial", "${pumpInfo.pcbaSN}")
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.BottomStart)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                "Back"
                            )
                        },
                        supportingContent = {
                        },
                        leadingContent = {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            navigateBack()
                        }
                    )
                }
            }

        }

    )
}


@Preview(showBackground = true)
@Composable
private fun DefaultPreview_PumpInfo() {
    TMobiScreensTheme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalTandemDataStore.current)
            var pumpVersion = PumpVersionResponse()
            pumpVersion.parse(
                byteArrayOf(-107,-28,-126,-4,0,0,0,0,0,0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    12,
                    -100,
                    20,
                    0,
                    22,
                    122,
                    15,
                    0,
                    48,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    -52,
                    -109,
                    105,
                    14,
                    48,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    -32,
                    81,
                    15,
                    0
                ))
            System.out.println(pumpVersion.cargo[24])
            LocalTandemDataStore.current.pumpVersionResponse.value = pumpVersion
            PumpInfo(
                innerPadding = PaddingValues(),
                //navController = null,
                navigateBack = { }
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
fun MyComposablePreview() {
    MyComposable()
}


@Composable
fun MyComposable() {
    Text("Hello Preview")
}