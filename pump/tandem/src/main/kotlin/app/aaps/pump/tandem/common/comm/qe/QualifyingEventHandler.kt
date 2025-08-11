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

    val TAG = LTag.PUMP


    fun handleEventReceivedFromPump(event : EventHandleQualifyingEvent) {

        aapsLogger.info(TAG, "handleEventReceivedFromPump: {}", event.events)

        val sb = StringBuilder()
        var first = true
        val listOfEvents : MutableList<TandemQualifyingEventEntity> = mutableListOf()

        for (qualifyingEvent in event.events) {

            listOfEvents.add(TandemQualifyingEventEntity(pumpSerial = tandemPumpStatus.serialNumber.toInt(),
                                                         dateTime = event.dateTime,
                                                         name = qualifyingEvent.name))
            // TODO QualifyingEventHandler - description - phase 3

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

        // L0: ignore
        // L1: we receive events and just list them in fragment     <---
        // L4: store them in database
        // L5: view them from database
        // L2:
        //     A: preprocess events, determine important
        //     B: (add ignore list) ones
        //     C: send CustomPumpCommand to retrieve more data about them
        // L3: handle QE do changes on pump "configuration"
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
            // TODO filter events (configuration = show only AAPS relevant events from pump) - phase 3
            aapsLogger.error(TAG, "Filtering of Qualifying Events is not yet implemented, returning all.")

            return qeItems // this needs to return only filtered data
        } else {
            return qeItems
        }
    }

}