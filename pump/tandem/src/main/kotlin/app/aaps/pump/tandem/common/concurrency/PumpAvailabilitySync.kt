package app.aaps.pump.tandem.common.concurrency

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Observer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.common.defs.PumpRunningState
import app.aaps.pump.tandem.common.driver.tandemDataStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps [PumpAvailabilityState] in sync with observed pump-side state.
 *
 * The pump itself is the source of truth for "can I accept a bolus / TBR right now?". Existing
 * response handlers in TandemUICommunication / TandemPumpConnector already update
 * [tandemDataStore.pumpRunningState], [tandemDataStore.inChangeCartridgeMode], and
 * [tandemDataStore.pumpConnected] when pump messages arrive. This component observes those three
 * LiveData sources and projects their combined state onto [PumpAvailabilityState] via a single
 * mapping function.
 *
 * Mapping (highest-priority match wins):
 *   - not connected                           → Unknown
 *   - in cartridge change                     → DeliveryDisabled
 *   - pumpRunningState == Suspended           → DeliveryDisabled
 *   - pumpRunningState == Running             → DeliveryEnabled
 *   - everything else (including null state)  → Unknown
 *
 * Note: [tandemDataStore.inChangeCartridgeMode] is only refreshed by the UI flow today (LoadStatus
 * messages are not part of the AAPS Loop status read). When AAPS is the only thing talking to the
 * pump, cartridge-change detection here falls back on `pumpRunningState == Suspended`, which the
 * pump reports during cartridge change. See plan §"Audit findings".
 */
@Singleton
class PumpAvailabilitySync @Inject constructor(
    private val availability: PumpAvailabilityState,
    private val logger: AAPSLogger
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val anyChange = Observer<Any?> { recompute() }

    init {
        // observeForever requires the main thread.
        mainHandler.post {
            tandemDataStore.pumpRunningState.observeForever(anyChange)
            tandemDataStore.inChangeCartridgeMode.observeForever(anyChange)
            tandemDataStore.pumpConnected.observeForever(anyChange)
            recompute()
            logger.debug(LTag.PUMP, "PumpAvailabilitySync: started observing pump-state sources")
        }
    }

    private fun recompute() {
        val connected = tandemDataStore.pumpConnected.value == true
        val inCartridge = tandemDataStore.inChangeCartridgeMode.value == true
        val running = tandemDataStore.pumpRunningState.value

        when {
            !connected -> availability.markUnknown("not connected")
            inCartridge -> availability.markDisabled("cartridge change")
            running == PumpRunningState.Suspended -> availability.markDisabled("pump suspended")
            running == PumpRunningState.Running   -> availability.markEnabled("pump running")
            else -> availability.markUnknown("running state=$running")
        }
    }
}
