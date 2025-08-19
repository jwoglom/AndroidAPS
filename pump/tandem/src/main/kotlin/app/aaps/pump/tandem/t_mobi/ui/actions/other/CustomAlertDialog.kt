package app.aaps.pump.tandem.t_mobi.ui.actions.other

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.tandem.R
import app.aaps.core.ui.R as Rco

@Composable
fun CustomAlertDialog(title: String,
                      content: String,
                      resourceHelper: ResourceHelper) {

    var showDialog by remember { mutableStateOf(false) }

    Button(onClick = { showDialog = true }) {
        Text("Show Custom Dialog")
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            text = { Text(content) },
            confirmButton = {
                Button(
                    onClick = { showDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                ) {
                    Text(resourceHelper.gs(Rco.string.yes), color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDialog = false }) {
                    Text(resourceHelper.gs(Rco.string.no))
                }
            },
            shape = RoundedCornerShape(16.dp), // Rounded corners
            containerColor = Color.White
        )
    }
}