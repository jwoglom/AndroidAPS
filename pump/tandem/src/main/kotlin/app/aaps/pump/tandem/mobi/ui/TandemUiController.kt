package app.aaps.pump.tandem.mobi.ui

import android.content.res.Configuration
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.tandem.common.comm.ui.TandemUICommunication
import app.aaps.pump.tandem.common.data.defs.RefreshData
import app.aaps.pump.tandem.common.database.data.DbDataHandler
import app.aaps.pump.tandem.common.database.data.defs.DatabaseQueryParameters
import app.aaps.pump.tandem.common.database.data.defs.DatabaseTarget
import app.aaps.pump.tandem.common.database.data.dto.TandemQualifyingEventDto
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.driver.tandemDataStore
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.response.qualifyingEvent.QualifyingEvent
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

class TandemUiController @Inject constructor(
    var aapsLogger: AAPSLogger,
    var tandemPumpStatus: TandemPumpStatus,
    var tandemPumpUtil: TandemPumpUtil,
    var dbDataHandler: DbDataHandler
)   {


    var TAG = LTag.PUMPCOMM

    val ds = tandemDataStore


    lateinit var tandemUICommunication: TandemUICommunication



    fun sendPumpCommands(msgs: List<Message>): Boolean {

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

        // TODO tandemUICommunication
        for (msg in msgs) {
            // tandemUICommunication.sendCommand(msg)
        }

        return true

    }

    // val isDarkTheme: Boolean
    //     get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
    //         Configuration.UI_MODE_NIGHT_YES

    fun refreshMainAppData(refreshData: RefreshData) {
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


    fun refreshDatabase(databaseTarget: DatabaseTarget, queryParameters: DatabaseQueryParameters) {
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