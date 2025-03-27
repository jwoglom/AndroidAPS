package app.aaps.pump.tandem.t_mobi.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.pump.tandem.t_mobi.ui.actions.Actions
import app.aaps.pump.tandem.t_mobi.ui.actions.other.DataStore
import app.aaps.pump.tandem.t_mobi.ui.theme.TMobiScreensTheme

var dataStore = DataStore()
val LocalDataStore = compositionLocalOf { dataStore }

class ActionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TMobiScreensTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Actions(
                        innerPadding = PaddingValues(0.dp),
                        navigateToPumpInfo = {},
                        navigateToCartridgeActions = {},
                        sendMessage = {_, _ -> },
                        sendPumpCommands = {_, _ -> },
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TMobiScreensTheme {
        TMobiScreensTheme {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Actions(
                    innerPadding = PaddingValues(0.dp),
                    navigateToPumpInfo = {

                    },
                    navigateToCartridgeActions = {

                    },
                    sendMessage = {_, _ -> },
                    sendPumpCommands = {_, _ -> },
                )
            }
        }
    }
}