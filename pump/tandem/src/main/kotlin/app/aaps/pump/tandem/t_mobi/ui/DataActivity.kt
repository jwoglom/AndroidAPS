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
import androidx.compose.material.icons.filled.BackupTable
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Notifications
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
import androidx.navigation.NavHostController
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.compose.ComposeUiProvider
import app.aaps.core.interfaces.ui.compose.DaggerComponentActivity
import app.aaps.pump.common.test.ResourceHelperTest
import app.aaps.pump.tandem.common.comm.ui.TandemUICommunication
import app.aaps.pump.tandem.common.data.defs.RefreshData
import app.aaps.pump.tandem.common.database.data.defs.DatabaseQueryParameters
import app.aaps.pump.tandem.common.database.data.defs.DatabaseTarget
import app.aaps.pump.tandem.common.database.data.DbDataHandler
import app.aaps.pump.tandem.common.database.data.dto.TandemQualifyingEventDto
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.driver.connector.TandemPumpConnector
import app.aaps.pump.tandem.common.driver.tandemDataStore
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import app.aaps.pump.tandem.di.TandemComposeUiComponent
import app.aaps.pump.tandem.t_mobi.ui.actions.Actions
import app.aaps.pump.tandem.t_mobi.ui.data.DataDisplayMain
import app.aaps.pump.tandem.t_mobi.ui.data.History
import app.aaps.pump.tandem.t_mobi.ui.data.Notifications
import app.aaps.pump.tandem.t_mobi.ui.data.QualifyingEvents
import app.aaps.pump.tandem.t_mobi.ui.theme.TMobiScreensTheme
import app.aaps.shared.tests.AAPSLoggerTest
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.response.qualifyingEvent.QualifyingEvent
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject


class DataActivity : DaggerComponentActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var tandemPumpStatus: TandemPumpStatus
    @Inject lateinit var tandemPumpUtil: TandemPumpUtil
    @Inject lateinit var context: Context
    @Inject lateinit var tandemPumpConnector: TandemPumpConnector
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var dbDataHandler: DbDataHandler


    var sectionState: DataLandingSection = DataLandingSection.DATA
    var navController: NavHostController? = null
    var TAG = LTag.PUMPCOMM

    val isDarkTheme: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

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

        enableEdgeToEdge()
        setContent {

            var selectedItem by remember { mutableStateOf(sectionState) }
            val scaffoldState = rememberBottomSheetScaffoldState()

            DisposableEffect(Unit) {
                onDispose {
                    aapsLogger.info(LTag.PUMP, "Data Activity was closed. Sending event to refresh.")
                    tandemPumpUtil.refreshPumpStatus(listOf(RefreshData.SEMAPHORE_EVENTS))
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

                                    DataLandingSection.DATA -> {
                                        DataDisplayMain(
                                            innerPadding = innerPadding,
                                            //navController = navController,
                                            sendPumpCommands = { messages -> sendPumpCommands(messages) },
                                            refreshMainAppData = { refreshData -> refreshMainAppData(refreshData = refreshData)},
                                            aapsLogger = aapsLogger,
                                            resourceHelper = resourceHelper,
                                            navigateToPumpHistory = {
                                                selectedItem = DataLandingSection.DATA_HISTORY
                                            },
                                            navigateToEvents = {
                                                selectedItem = DataLandingSection.DATA_EVENTS
                                            },
                                            navigateToNotifications = {
                                                selectedItem = DataLandingSection.DATA_NOTIFICATIONS
                                            },
                                        )
                                    }

                                    DataLandingSection.DATA_HISTORY -> {
                                        History(
                                            innerPadding = innerPadding,
                                            refreshDatabase = { target, queryParameters -> refreshDatabase(target, queryParameters) },
                                            refreshMainAppData = { refreshData -> refreshMainAppData(refreshData = refreshData)},
                                            aapsLogger = aapsLogger,
                                            resourceHelper = resourceHelper,
                                            navigateBack = {
                                                selectedItem = DataLandingSection.DATA
                                            },
                                        )
                                    }

                                    DataLandingSection.DATA_EVENTS -> {
                                        QualifyingEvents (
                                            innerPadding = innerPadding,
                                            aapsLogger = aapsLogger,
                                            resourceHelper = resourceHelper,
                                            refreshDatabase = { target, queryParameters -> refreshDatabase(target, queryParameters) },
                                            refreshMainAppData = { refreshData -> refreshMainAppData(refreshData = refreshData)},
                                            navigateBack = {
                                                selectedItem = DataLandingSection.DATA
                                            },
                                        )
                                    }


                                    DataLandingSection.DATA_NOTIFICATIONS -> {
                                        Notifications(
                                            innerPadding = innerPadding,
                                            resourceHelper = resourceHelper,
                                            aapsLogger = aapsLogger,
                                            sendPumpCommands = { messages -> sendPumpCommands(messages) },
                                            refreshMainAppData = { refreshData -> refreshMainAppData(refreshData = refreshData)},
                                            navigateBack = {
                                                selectedItem = DataLandingSection.DATA
                                            }
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

        val listText = sb.substring(2);

        aapsLogger.debug(TAG, "PumpCommands to Send [commands=${listText}]")

        for (msg in msgs) {
            tandemUICommunication.sendCommand(msg)
        }

        return true

    }

    val ds = tandemDataStore
    var count = 1


    private fun refreshMainAppData(refreshData: RefreshData) {
        when(refreshData) {
            RefreshData.SEMAPHORE_HISTORY       -> {
                tandemPumpStatus.semaphoreHistory = false
                tandemPumpStatus.semaphoreNeedsRefresh = true
            }
            RefreshData.SEMAPHORE_EVENTS        -> {
                tandemPumpStatus.semaphoreEvents = false
                tandemPumpStatus.semaphoreNeedsRefresh = true
            }
            RefreshData.SEMAPHORE_NOTIFICATIONS -> {
                tandemPumpStatus.semaphoreNotifications = false
                tandemPumpStatus.semaphoreNeedsRefresh = true
            }
            else -> {}
        }
    }


    private fun refreshDatabase(databaseTarget: DatabaseTarget, queryParameters: DatabaseQueryParameters) {
        val jsonParamVal = tandemPumpUtil.gson.toJson(queryParameters)

        aapsLogger.debug(TAG, "refreshDatabase: called with target=${databaseTarget.name} and parameters=$jsonParamVal")

        when(databaseTarget) {
            DatabaseTarget.QUALIFYING_EVENTS -> {

                val currentQEItemsBlocking = dbDataHandler.getCurrentQEItemsBlocking();

                val list: MutableList<TandemQualifyingEventDto> = mutableListOf()

                for (entity in currentQEItemsBlocking) {
                    val instantTime = java.time.Instant.ofEpochMilli(entity.dateTime)

                    aapsLogger.error("QE: " + instantTime)

                    val eventDto = TandemQualifyingEventDto(
                        dateTime = LocalDateTime.ofInstant(instantTime, ZoneId.systemDefault()),
                        name = QualifyingEvent.valueOf(entity.name),
                        description = if (entity.description==null) "" else  entity.description!!
                    )

                    list.add(eventDto)
                }


                val list2 = ds.dataQE.value!!

                list2.clear()
                list2.addAll(list)

                ds.dataQELoaded.value = true

                aapsLogger.error(TAG, "QE Items ${list2.size}")

            }
            DatabaseTarget.PUMP_HISTORY      -> {

                val list = dbDataHandler.getHistoryRecords(queryParameters)

                val list2 = ds.dataHistory.value!!

                list2.clear()
                list2.addAll(list)

                aapsLogger.error(TAG, "History Items ${list2.size}")

                ds.dataHistoryLoaded.value = true
            }
        }

    }


}

// HACK: subpages should have the same label as an item appearing in the nav
// so that item appears as selected when it is navigated to within the app
enum class DataLandingSection(val label: String, val icon: ImageVector) {

    DATA("Data", Icons.Filled.Create),
    DATA_HISTORY("History", Icons.Filled.BackupTable),
    DATA_EVENTS("Events", Icons.Filled.EventAvailable),
    DATA_NOTIFICATIONS("Notifications", Icons.Filled.Notifications),
    ;
}



@Preview(showBackground = true)
@Composable
fun DataActivity_Preview() {
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