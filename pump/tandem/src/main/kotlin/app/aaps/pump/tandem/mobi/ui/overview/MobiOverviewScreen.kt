package app.aaps.pump.tandem.mobi.ui.overview

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.ui.compose.pump.PumpOverviewScreen
import app.aaps.pump.tandem.R
import app.aaps.core.ui.R as Rco

@Composable
fun MobiOverviewScreen(
    viewModel: MobiOverviewViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val iconRes = Rco.drawable.ic_tmobi_128

    PumpOverviewScreen(
        state = uiState,
        customContent = {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier
                    .height(157.dp)
                    .width(239.dp),
                contentScale = ContentScale.Fit
            )
        }
    )
}
