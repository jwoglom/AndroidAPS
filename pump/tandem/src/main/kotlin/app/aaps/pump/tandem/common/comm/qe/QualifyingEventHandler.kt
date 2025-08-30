package app.aaps.pump.tandem.common.comm.qe

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.common.defs.PumpUpdateFragmentType
import app.aaps.pump.common.events.EventPumpFragmentValuesChanged
import app.aaps.pump.tandem.common.comm.TandemDataConverter
import app.aaps.pump.tandem.common.data.defs.QualifyingEventsFilter
import app.aaps.pump.tandem.common.data.defs.QualifyingEventsRange
import app.aaps.pump.tandem.common.database.TandemPumpDatabase
import app.aaps.pump.tandem.common.database.data.DbDataHandler
import app.aaps.pump.tandem.common.database.data.entity.TandemQualifyingEventEntity
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.events.EventDatabaseAddQEData
import app.aaps.pump.tandem.common.events.EventHandleQualifyingEvent
import app.aaps.pump.tandem.common.keys.TandemStringPreferenceKey
import app.aaps.pump.tandem.common.util.PumpX2L
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import com.jwoglom.pumpx2.pump.messages.response.qualifyingEvent.QualifyingEvent
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QualifyingEventHandler @Inject constructor(
    val tandemPumpStatus: TandemPumpStatus,
    //val dbDataHandler: DbDataHandler,
    val rxBus: RxBus,
    val aapsLogger: AAPSLogger,
    val aapsSchedulers: AapsSchedulers,
    val preferences: Preferences
    ) {

    @Suppress("PropertyName")
    val TAG = LTag.PUMP


    fun handleEventReceivedFromPump(event : EventHandleQualifyingEvent) {

        aapsLogger.info(TAG, "handleEventReceivedFromPump: ${event.events}")

        val sb = StringBuilder()
        var first = true
        val listOfEvents : MutableList<TandemQualifyingEventEntity> = mutableListOf()

        for (qualifyingEvent in event.events) {

            listOfEvents.add(TandemQualifyingEventEntity(pumpSerial = tandemPumpStatus.serialNumber.toInt(),
                                                         dateTime = event.dateTime,
                                                         name = qualifyingEvent.name))
            // TODOX description - at the moment we don't add any special details, since that would
            //    require extra reading. Not sure if this is even needed, so letting it of for now
            if (!first) sb.append(", ") else first = false

            sb.append(qualifyingEvent.name)
        }

        rxBus.send(EventDatabaseAddQEData(listOfEvents))

        // send notifications
        // TODO custom_1 will be removed when Custom_2 is fully implemented
        tandemPumpStatus.lastQualifyingEventsInfo = sb.toString()
        rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Custom_1))

        tandemPumpStatus.semaphoreEvents = true
        rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Custom_2))
    }


    fun filterQualifyingEventsAndLimit(qeItems: List<TandemQualifyingEventEntity>, itemLimit: Int): List<TandemQualifyingEventEntity> {
        val filteredQeItems = filterQualifyingEvents(qeItems)

        return if (itemLimit==0) {
            filteredQeItems
        } else {
            if (filteredQeItems.size>15) {
                filteredQeItems.subList(0,14)
            } else {
                filteredQeItems
            }
        }
    }


    fun filterQualifyingEvents(qeItems: List<TandemQualifyingEventEntity>): List<TandemQualifyingEventEntity> {
        val qeFilter = QualifyingEventsFilter.valueOf(preferences.get(TandemStringPreferenceKey.QualifyingEventsFilterPref))

        if (qeFilter==QualifyingEventsFilter.AAPS_RELEVANT) {
            return filterEvents(qeItems)
        } else {
            return qeItems
        }
    }

    fun filterEvents(qeItems: List<TandemQualifyingEventEntity>): List<TandemQualifyingEventEntity> {
        val outList: MutableList<TandemQualifyingEventEntity> = mutableListOf()

        for (entity in qeItems) {
            if (isAapsRelevant(QualifyingEvent.valueOf(entity.name))) {
                outList.add(entity)
            }
        }

        aapsLogger.info(TAG, "QualifyingEvents filtering [before=${qeItems.size}, after=${outList.size}]")

        return outList
    }


    fun isAapsRelevant(event: QualifyingEvent): Boolean {
        return when (event) {
            QualifyingEvent.ALERT,
            QualifyingEvent.ALARM,
            QualifyingEvent.REMINDER,
            QualifyingEvent.MALFUNCTION,
            QualifyingEvent.HOME_SCREEN_CHANGE,
            QualifyingEvent.PUMP_SUSPEND,
            QualifyingEvent.PUMP_RESUME,
            QualifyingEvent.TIME_CHANGE,
            QualifyingEvent.BASAL_CHANGE,
            QualifyingEvent.BOLUS_CHANGE,
            QualifyingEvent.PROFILE_CHANGE,
            QualifyingEvent.BATTERY,
            QualifyingEvent.REMAINING_INSULIN,
            QualifyingEvent.SUSPEND_COMM,
            QualifyingEvent.ACTIVE_SEGMENT_CHANGE,
            QualifyingEvent.BOLUS_PERMISSION_REVOKED -> {
                true
            }

            QualifyingEvent.CGM_ALERT,
            QualifyingEvent.BASAL_IQ_STATUS,
            QualifyingEvent.IOB_CHANGE,
            QualifyingEvent.BG,
            QualifyingEvent.EXTENDED_BOLUS_CHANGE,
            QualifyingEvent.CGM_CHANGE,
            QualifyingEvent.BASAL_IQ,
            QualifyingEvent.CONTROL_IQ_INFO,
            QualifyingEvent.CONTROL_IQ_SLEEP         -> {
                false
            }

        }
    }

}