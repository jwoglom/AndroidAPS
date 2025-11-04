@file:OptIn(ExperimentalMaterial3Api::class)

package app.aaps.pump.tandem.t_mobi.ui

import android.content.Context
import android.content.res.Configuration
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.compose.ComposeUiProvider
import app.aaps.core.interfaces.ui.compose.DaggerComponentActivity
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.common.test.ResourceHelperTest
import app.aaps.pump.tandem.common.comm.ui.TandemUICommunication
import app.aaps.pump.tandem.common.data.defs.RefreshData
import app.aaps.pump.tandem.common.driver.LocalTandemDataStore
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.driver.connector.TandemPumpConnector
import app.aaps.pump.tandem.common.driver.tandemDataStore
import app.aaps.pump.tandem.common.keys.TandemLongNonPreferenceKey
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import app.aaps.pump.tandem.di.TandemComposeUiComponent
import app.aaps.pump.tandem.t_mobi.ui.actions.Actions
import app.aaps.pump.tandem.t_mobi.ui.actions.PumpInfo
import app.aaps.pump.tandem.t_mobi.ui.actions.cartridge.CartridgeActions
import app.aaps.pump.tandem.t_mobi.ui.actions.cartridge.SiteReminder
import app.aaps.pump.tandem.t_mobi.ui.theme.TMobiScreensTheme
import app.aaps.shared.tests.AAPSLoggerTest
import com.jwoglom.pumpx2.pump.messages.Message
import javax.inject.Inject


class ActionsActivity : DaggerComponentActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var tandemPumpStatus: TandemPumpStatus
    @Inject lateinit var tandemPumpUtil: TandemPumpUtil
    @Inject lateinit var context: Context
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var tandemPumpConnector: TandemPumpConnector
    @Inject lateinit var resourceHelper: ResourceHelper


    var sectionState: ActionsLandingSection = ActionsLandingSection.ACTIONS

    var TAG = LTag.PUMPCOMM

    lateinit var tandemUICommunication : TandemUICommunication

    val isDarkTheme: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val composeUiComponent = (application as ComposeUiProvider)
            .getComposeUiModule("tandem") as TandemComposeUiComponent

        composeUiComponent.inject(this)

        tandemUICommunication = TandemUICommunication(dataStore = tandemDataStore,
                                                      pumpStatus = tandemPumpStatus,
                                                      context = context,
                                                      pumpUtil = tandemPumpUtil,
                                                      aapsLogger= aapsLogger)

        // val date = sharedPreferences.getLong("test_reminder_date", -1L)
        //
        // aapsLogger.error(TAG, "Loading Reminder Date: $date")
        //
        // if (date != -1L) {
        //     if (date > System.currentTimeMillis()) {
        //         reminderDate = date
        //     }
        // }



        enableEdgeToEdge()
        setContent {

            val ds = LocalTandemDataStore.current
            ds.reminderDateTime.value = tandemPumpStatus.tandemSiteReminder

            var selectedItem by remember { mutableStateOf(sectionState) }
            val scaffoldState = rememberBottomSheetScaffoldState()

            DisposableEffect(Unit) {
                onDispose {
                    aapsLogger.info(LTag.PUMP, "Data Activity was closed. Sending event to refresh.")

                    if (ds.reminderDateTimeUpdated.value == true) {
                        aapsLogger.error(TAG, "Reminder Date Time: ${ds.reminderDateTime.value}")

                        preferences.put(TandemLongNonPreferenceKey.SiteReminderDateTime, ds.reminderDateTime.value!!)
                        tandemPumpStatus.tandemSiteReminder = ds.reminderDateTime.value!!
                    }

                    // we might be able to specify more exactly what here happens but for now this is ok, see DataActivity and method refreshMainAppData
                    tandemPumpUtil.refreshPumpStatus(listOf(RefreshData.PUMP_STATUS,
                                                            RefreshData.PUMP_INSULIN_LEVEL))
                }
            }

            TMobiScreensTheme(darkTheme = tandemPumpUtil.isAAPSDarkTheme(isSystemDarkTheme = isDarkTheme)) {
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
                                            sendPumpCommands = { messages -> sendPumpCommands(messages) },
                                            aapsLogger = aapsLogger,
                                            resourceHelper = resourceHelper,
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
                                            resourceHelper = resourceHelper,
                                            tandemPumpStatus = tandemPumpStatus,
                                            navigateBack = {
                                                selectedItem = ActionsLandingSection.ACTIONS
                                            },
                                        )
                                    }


                                    ActionsLandingSection.CARTRIDGE_ACTIONS -> {
                                        CartridgeActions(
                                            innerPadding = innerPadding,
                                            aapsLogger = aapsLogger,
                                            //navController = navController,
                                            sendPumpCommands = { messages -> sendPumpCommands(messages) },
                                            resourceHelper = resourceHelper,
                                            navigateToSiteReminder = {
                                                selectedItem = ActionsLandingSection.SITE_REMINDER
                                            },
                                            navigateBack = {
                                                selectedItem = ActionsLandingSection.ACTIONS
                                            },
                                        )
                                    }


                                    ActionsLandingSection.SITE_REMINDER -> {
                                        SiteReminder(
                                            innerPadding = innerPadding,
                                            resourceHelper = resourceHelper,
                                            aapsLogger = aapsLogger,
                                            navigateBack = {
                                                selectedItem = ActionsLandingSection.CARTRIDGE_ACTIONS
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
        this.tandemUICommunication.tandemCommunicationManager = tandemPumpConnector.getCommunicationManager()
        this.tandemPumpUtil.preventConnect = true
    }



    override fun onStop() {
        super.onStop()
        this.tandemUICommunication.tandemCommunicationManager = null
        this.tandemPumpUtil.preventConnect = false
    }


    private fun sendPumpCommands(msgs: List<Message>): Boolean {

        if (tandemDataStore.pumpConnected.value==false) {
            aapsLogger.warn(TAG, "sendPumpCommands not possible, because pump is not yet connected")
            return false
        }

        val sb = StringBuilder()

        for (msg in msgs) {
            sb.append(", ${msg.javaClass.name}")
        }

        val listText = sb.substring(2)

        aapsLogger.warn(TAG, "PumpCommands to Send [commands=${listText}]")

        for (msg in msgs) {
            tandemUICommunication.sendCommand(msg)
        }

        return true

    }




}

// HACK: subpages should have the same label as an item appearing in the nav
// so that item appears as selected when it is navigated to within the app
enum class ActionsLandingSection(val label: String, val icon: ImageVector) {

    ACTIONS("Actions", Icons.Filled.Create),
    CARTRIDGE_ACTIONS("Actions", Icons.Filled.Create),
    PUMP_INFO("Pump Info", Icons.Filled.Create),
    SITE_REMINDER("Site Reminder", Icons.Filled.Create)
    ;
}



@Preview(showBackground = true)
@Composable
fun ActionsActivity_Preview() {
    TMobiScreensTheme {
        TMobiScreensTheme {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Actions(
                    innerPadding = PaddingValues(0.dp),
                    navigateToPumpInfo = { },
                    navigateToCartridgeActions = { },
                    aapsLogger = AAPSLoggerTest(),
                    resourceHelper = ResourceHelperTest(),
                    sendPumpCommands = { _ -> true},
                )
            }
        }
    }
}