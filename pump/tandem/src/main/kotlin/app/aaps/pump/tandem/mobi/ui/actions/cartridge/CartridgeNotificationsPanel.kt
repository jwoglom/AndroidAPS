@file:OptIn(ExperimentalMaterial3Api::class)

package app.aaps.pump.tandem.mobi.ui.actions.cartridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.tandem.R
import app.aaps.pump.tandem.mobi.ui.util.AlertBanner
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.AlarmStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.AlertStatusRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pump notifications panel: card with refresh, AlertBanner when populated, green-check empty state. */
@Composable
fun CartridgeNotificationsPanel(
    notifications: List<Any>,
    sendPumpCommands: (List<Message>) -> Boolean,
    refreshScope: CoroutineScope,
    resourceHelper: ResourceHelper,
) {
    var checking by remember { mutableStateOf(false) }

    fun refreshNotifications() {
        if (checking) return
        checking = true
        refreshScope.launch {
            sendPumpCommands(listOf(AlertStatusRequest(), AlarmStatusRequest()))
            withContext(Dispatchers.IO) { Thread.sleep(500) }
            checking = false
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = resourceHelper.gs(R.string.ca_notifications_panel_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            IconButton(onClick = ::refreshNotifications, enabled = !checking) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = resourceHelper.gs(R.string.ca_notifications_refresh)
                )
            }
        }
        if (notifications.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32) // Material Green 800
                )
                Text(
                    text = if (checking)
                        resourceHelper.gs(R.string.ca_notifications_checking)
                    else
                        resourceHelper.gs(R.string.ca_notifications_none),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            AlertBanner(
                notifications = notifications,
                sendPumpCommands = sendPumpCommands,
                refreshScope = refreshScope,
                resourceHelper = resourceHelper
            )
        }
    }
}
