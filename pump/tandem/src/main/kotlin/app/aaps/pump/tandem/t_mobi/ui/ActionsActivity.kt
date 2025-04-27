@file:OptIn(
    ExperimentalMaterial3Api::class
)


package app.aaps.pump.tandem.t_mobi.ui

//import androidx.compose.material3.BottomSheetState
//import androidx.compose.material3.BottomSheetValue
import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.ui.compose.ComposeUiProvider
import app.aaps.core.interfaces.ui.compose.DaggerComponentActivity
import app.aaps.pump.tandem.common.comm.ui.TandemUICommunication
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.driver.connector.TandemPumpConnector
import app.aaps.pump.tandem.common.driver.tandemDataStore
import app.aaps.pump.tandem.di.TandemComposeUiComponent
import app.aaps.pump.tandem.t_mobi.ui.actions.Actions
import app.aaps.pump.tandem.t_mobi.ui.actions.PumpInfo
import app.aaps.pump.tandem.t_mobi.ui.actions.cartridge.CartridgeActions
import app.aaps.pump.tandem.t_mobi.ui.actions.other.SendType
import app.aaps.pump.tandem.t_mobi.ui.theme.TMobiScreensTheme
import app.aaps.shared.tests.AAPSLoggerTest
import com.jwoglom.pumpx2.pump.messages.Message
import javax.inject.Inject

// var dataStore = DataStore()
// val LocalDataStore = compositionLocalOf { dataStore }

class ActionsActivity : DaggerComponentActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var tandemPumpStatus: TandemPumpStatus
    @Inject lateinit var context: Context
    @Inject lateinit var tandemPumpConnector: TandemPumpConnector


    //lateinit var tandemCommunicationManager: TandemCommunicationManager



    var sectionState: ActionsLandingSection = ActionsLandingSection.ACTIONS
    var navController: NavHostController? = null
    var TAG = LTag.PUMPCOMM
    lateinit var tandemUICommunication : TandemUICommunication


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val composeUiComponent = (application as ComposeUiProvider)
            .getComposeUiModule("tandem") as TandemComposeUiComponent

        composeUiComponent.inject(this)


        tandemUICommunication = TandemUICommunication(dataStore = tandemDataStore,
                                                      pumpStatus = tandemPumpStatus,
                                                      context = context,
                                                      aapsLogger= aapsLogger)

        //tandemCommunicationManager = tandemPumpConnector.getCommunicationManager()

        enableEdgeToEdge()
        setContent {

            var selectedItem by remember { mutableStateOf(sectionState) }
            val scaffoldState = rememberBottomSheetScaffoldState()

            TMobiScreensTheme {
                Scaffold(
                    content = { innerPadding ->
                        BottomSheetScaffold(
                            scaffoldState = scaffoldState,
                            sheetContent = {
                                Text("Hidden Bottom sheet !")
                            },
                            sheetPeekHeight = 0.dp
                            //backgroundColor = MaterialTheme.colorScheme.background,
                        ) {
                            Box(Modifier.fillMaxHeight()) {
                                when (selectedItem) {


                                    ActionsLandingSection.ACTIONS -> {
                                        Actions(
                                            innerPadding = innerPadding,
                                            navController = navController,
                                            sendPumpCommands = { type, messages -> sendPumpCommands(type, messages) },
                                            aapsLogger = aapsLogger,
                                            navigateToPumpInfo = {
                                                selectedItem = ActionsLandingSection.PUMP_INFO
                                            },
                                            navigateToCartridgeActions = {
                                                selectedItem = ActionsLandingSection.CARTRIDGE_ACTIONS
                                            }
                                        )
                                    }



                                    ActionsLandingSection.PUMP_INFO -> {
                                        PumpInfo(
                                            innerPadding = innerPadding,
                                            navigateBack = {
                                                selectedItem = ActionsLandingSection.ACTIONS
                                            },
                                        )
                                    }

                                    ActionsLandingSection.CARTRIDGE_ACTIONS -> {
                                        CartridgeActions(
                                            innerPadding = innerPadding,
                                            navController = navController,
                                            //sendMessage = sendMessage,
                                            sendPumpCommands = { type, messages -> sendPumpCommands(type, messages) },
                                            //historyLogViewModel = historyLogViewModel,
                                            navigateBack = {
                                                selectedItem = ActionsLandingSection.ACTIONS
                                            },
                                        )
                                    }


                                } // when
                            } // box
                        } //
                    }
                ) // scaffold
            } // theme

        }
    }

    override fun onResume() {
        super.onResume()
        //this.tandemUICommunication.tandemCommunicationManager = tandemPumpConnector.getCommunicationManager() //
    }




    private fun sendPumpCommands(type: SendType, msgs: List<Message>): Boolean {

        if (tandemDataStore.pumpConnected.value==false) {
            aapsLogger.warn(TAG, "sendPumpCommands not possible, because pump is not yet connected")
            return false
        }

        val sb = StringBuilder()

        for (msg in msgs) {
            sb.append(", ${msg.javaClass.name}")
        }

        val listText = sb.substring(2);

        aapsLogger.warn(TAG, "PumpCommands to Send [type=${type}, commands=${listText}]")

        for (msg in msgs) {
            tandemUICommunication.sendCommand(msg)
        }

        return true

    }




}

// HACK: subpages should have the same label as an item appearing in the nav
// so that item appears as selected when it is navigated to within the app
enum class ActionsLandingSection(val label: String, val icon: ImageVector, val showInNav: Boolean) {

    ACTIONS("Actions", Icons.Filled.Create, true),
    CARTRIDGE_ACTIONS("Actions", Icons.Filled.Create, false),
    PUMP_INFO("Pump Info", Icons.Filled.Create, false),
    ;
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
                    aapsLogger = AAPSLoggerTest(),
                    //sendMessage = {_, _ -> },
                    sendPumpCommands = {_, _ -> true},
                )
            }
        }
    }
}