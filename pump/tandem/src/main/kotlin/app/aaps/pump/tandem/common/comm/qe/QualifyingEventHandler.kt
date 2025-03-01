package app.aaps.pump.tandem.common.comm.qe

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.pump.common.defs.PumpUpdateFragmentType
import app.aaps.pump.common.events.EventPumpFragmentValuesChanged
import app.aaps.pump.tandem.common.comm.TandemDataConverter
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.events.EventHandleQualifyingEvent
import app.aaps.pump.tandem.common.util.PumpX2L
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QualifyingEventHandler @Inject constructor(
    val tandemPumpStatus: TandemPumpStatus,
    val rxBus: RxBus,
    val aapsLogger: AAPSLogger
    ) {


    fun handleEventReceivedFromPump(event : EventHandleQualifyingEvent) {

        aapsLogger.info(LTag.PUMP, "handleEventReceivedFromPump: {}", event.events)

        val sb = StringBuilder()
        var first = true

        for (qualifyingEvent in event.events) {
            if (!first) sb.append(", ") else first = false

            sb.append(qualifyingEvent.name)
        }

        tandemPumpStatus.lastQualifyingEventsInfo = sb.toString()

        rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Custom_1))

        // L0: ignore
        // L1: we receive events and just list them in fragment     <---
        // L2:
        //     A: preprocess events, determine important
        //     B: (add ignore list) ones
        //     C: send CustomPumpCommand to retrieve more data about them
        // L3: handle QE do changes on pump "configuration"
        // L4: store them in database
        // L5: view them from database




        // TODO not sure what this is for
        // Timber.i("onReceiveQualifyingEvent: $events")
        // Toast.makeText(this@CommService, "Events: $events", Toast.LENGTH_SHORT).show()
        // events?.forEach { event ->
        //     event.suggestedHandlers.forEach {
        //         Timber.i("onReceiveQualifyingEvent: running handler for $event message: ${it.get()}")
        //         command(it.get())
        //     }
        // }



    }



    // class TandemPumpConnector @Inject constructor(var tandemPumpStatus: TandemPumpStatus,
    //                                               var context: Context,
    //                                               var tandemPumpUtil: TandemPumpUtil,
    //                                               injector: HasAndroidInjector,
    //                                               var rxBus: RxBus,
    //                                               var resourceHelper: ResourceHelper,
    //                                               var sp: SP,
    //                                               aapsLogger: AAPSLogger,
    //                                               val pumpX2L: PumpX2L,
    //                                               private var tandemDataConverter: TandemDataConverter
    //

}