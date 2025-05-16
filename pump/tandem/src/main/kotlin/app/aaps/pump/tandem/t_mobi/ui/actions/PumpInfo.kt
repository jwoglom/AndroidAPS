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
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.common.test.ResourceHelperTest
import app.aaps.pump.tandem.R
import app.aaps.pump.tandem.common.driver.LocalTandemDataStore
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.t_mobi.ui.theme.TMobiScreensTheme
import app.aaps.pump.tandem.t_mobi.ui.util.DottedDivider
import app.aaps.pump.tandem.t_mobi.ui.util.HeaderLine
import app.aaps.pump.tandem.t_mobi.ui.util.HeaderLineWithBackButton
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpVersionResponse

// TODO PumpInfo  (90% implemented)

@Composable
fun PumpInfo(innerPadding: PaddingValues = PaddingValues(),
             navigateBack: () -> Unit,
             tandemPumpStatus: TandemPumpStatus? = null,
             resourceHelper: ResourceHelper
             ) {

    val pumpInfo = LocalTandemDataStore.current.pumpVersionResponse.value!!
    val apiVersion = LocalTandemDataStore.current.apiVersionResponse.value!!
    val isMobi = if (tandemPumpStatus==null) true else tandemPumpStatus.tandemPumpFirmware.isMobi()



    LazyColumn(
        contentPadding = innerPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp),
        content = {
            item {
                HeaderLineWithBackButton(text= resourceHelper.gs(R.string.pi_title), onBackClick=navigateBack, backgroundColor = Color.LightGray)
                HorizontalDivider()
            }

            item {
                PumpInfoRow(label= resourceHelper.gs(R.string.pump_serial_number), "${pumpInfo.serialNum}")
            }

            item {
                PumpInfoRow(label= resourceHelper.gs(R.string.pi_pump_sw), value="${apiVersion.majorVersion}.${apiVersion.minorVersion}"    /*pumpInfo.pumpRev*/)
            }

            item {
                PumpInfoRow(label="ARM S/W " + resourceHelper.gs(R.string.pi_version), value="${pumpInfo.armSwVer}")
            }

            item {
                PumpInfoRow(label=resourceHelper.gs(R.string.pi_sw_part_num), value="${pumpInfo.partNum}")
            }

            item {
                PumpInfoRow("ConfigA Bits", value="0x%08X".format(pumpInfo.configABits))
            }

            item {
                PumpInfoRow("ConfigB Bits", value="0x%08X".format(pumpInfo.configBBits))
            }

            item {
                PumpInfoRow(label= resourceHelper.gs(R.string.pi_pump_model), value=if (isMobi) "t:Mobi (${pumpInfo.modelNum})" else "t:Slim X2 (${pumpInfo.modelNum})")
            }

            item {
                PumpInfoRow(label= resourceHelper.gs(R.string.pi_pcba_serial), value="${pumpInfo.pcbaSN}")
            }


        }

    )
}


@Composable
fun PumpInfoRow(label: String, value: String) {
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
                navigateBack = { },
                tandemPumpStatus = null,
                resourceHelper = ResourceHelperTest()
            )
        }
    }
}
