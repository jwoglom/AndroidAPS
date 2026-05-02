@file:OptIn(ExperimentalMaterial3Api::class)

package app.aaps.pump.tandem.mobi.ui.actions.cartridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.tandem.R
import app.aaps.pump.tandem.mobi.ui.util.NotificationDismissPills
import com.jwoglom.pumpx2.pump.messages.Message
import kotlinx.coroutines.CoroutineScope

/** Inline pill ("✓ No active Pump notifications.") for the empty state. */
@Composable
fun CartridgeNotificationsPanel(
    resourceHelper: ResourceHelper,
) {
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
                text = resourceHelper.gs(R.string.ca_notifications_none),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Modal blocking the workflow when pump notifications are active. Title becomes
 * the action; AlertBanner inside renders the dismissible alert/alarm rows. Back
 * press / scrim are blocked so the screen-level BackHandler decides what to do.
 */
@Composable
fun CartridgeNotificationsBlockingDialog(
    notifications: List<Any>,
    sendPumpCommands: (List<Message>) -> Boolean,
    refreshScope: CoroutineScope,
    resourceHelper: ResourceHelper,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        icon = {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = resourceHelper.gs(R.string.ca_dismiss_notifications_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            NotificationDismissPills(
                notifications = notifications,
                sendPumpCommands = sendPumpCommands,
                refreshScope = refreshScope,
            )
        },
        confirmButton = {},
    )
}
