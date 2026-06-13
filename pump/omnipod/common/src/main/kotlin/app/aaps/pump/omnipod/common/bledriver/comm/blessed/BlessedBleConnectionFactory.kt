package app.aaps.pump.omnipod.common.bledriver.comm.blessed

import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.pump.omnipod.common.bledriver.comm.blessed.session.BlessedConnection
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnection
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnectionFactory
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlessedBleConnectionFactory @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val podState: OmnipodDashPodStateManager,
    private val receiverStatusStore: ReceiverStatusStore
) : BleConnectionFactory {

    override fun createConnection(podAddress: String): BleConnection {
        return BlessedConnection(podAddress, aapsLogger, config, context, podState, receiverStatusStore)
    }
}
