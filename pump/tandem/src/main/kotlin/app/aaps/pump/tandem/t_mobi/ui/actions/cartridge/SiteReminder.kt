package app.aaps.pump.tandem.t_mobi.ui.actions.cartridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.tandem.R
import app.aaps.pump.tandem.t_mobi.ui.util.DottedDivider
import app.aaps.pump.tandem.t_mobi.ui.util.HeaderLineWithBackButton

// TODO SiteReminder: coming in phase 3

@Composable
fun SiteReminder(innerPadding: PaddingValues = PaddingValues(),
             navigateBack: () -> Unit,
             resourceHelper: ResourceHelper
) {

    LazyColumn(
        contentPadding = innerPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp),
        content = {
            item {
                HeaderLineWithBackButton(text= resourceHelper.gs(R.string.sr_title),
                                         onBackClick=navigateBack,
                                         resourceHelper = resourceHelper)
                HorizontalDivider()
            }

            item {
                SRPumpInfoRow(label= resourceHelper.gs(R.string.sr_coming_soon))
            }
            //
            // item {
            //     PumpInfoRow(label= resourceHelper.gs(R.string.pi_pump_sw), value="${apiVersion.majorVersion}.${apiVersion.minorVersion}"    /*pumpInfo.pumpRev*/)
            // }
            //
            // item {
            //     PumpInfoRow(label="ARM S/W " + resourceHelper.gs(R.string.pi_version), value="${pumpInfo.armSwVer}")
            // }
            //
            // item {
            //     PumpInfoRow(label=resourceHelper.gs(R.string.pi_sw_part_num), value="${pumpInfo.partNum}")
            // }
            //
            // item {
            //     PumpInfoRow("ConfigA Bits", value="0x%08X".format(pumpInfo.configABits))
            // }
            //
            // item {
            //     PumpInfoRow("ConfigB Bits", value="0x%08X".format(pumpInfo.configBBits))
            // }
            //
            // item {
            //     PumpInfoRow(label= resourceHelper.gs(R.string.pi_pump_model), value=if (isMobi) "t:Mobi (${pumpInfo.modelNum})" else "t:Slim X2 (${pumpInfo.modelNum})")
            // }
            //
            // item {
            //     PumpInfoRow(label= resourceHelper.gs(R.string.pi_pcba_serial), value="${pumpInfo.pcbaSN}")
            // }

        }

    )
}


@Composable
fun SRPumpInfoRow(label: String, value: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 18.sp)
        //Text(text = value, fontSize = 18.sp)
    }
    DottedDivider(    dotRadius = 1.dp,
                      spaceBetween = 6.dp,)
}
