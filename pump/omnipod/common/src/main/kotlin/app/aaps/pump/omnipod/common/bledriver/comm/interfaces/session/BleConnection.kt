package app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session

import app.aaps.pump.omnipod.common.bledriver.comm.Ids
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionState
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionWaitCondition
import app.aaps.pump.omnipod.common.bledriver.comm.session.DisconnectHandler
import app.aaps.pump.omnipod.common.bledriver.comm.session.EapSqn
import app.aaps.pump.omnipod.common.bledriver.comm.session.Session

/**
 * Abstraction for BLE GATT connection lifecycle.
 * Implemented by Bluetooth library-specific adapters.
 */
interface BleConnection : DisconnectHandler {

    val session: Session?
    val msgIO: MessageIO?

    fun connect(connectionWaitCond: ConnectionWaitCondition)
    fun disconnect(closeGatt: Boolean)
    fun connectionState(): ConnectionState
    fun establishSession(ltk: ByteArray, msgSeq: Byte, ids: Ids, eapSqn: ByteArray): EapSqn?

    /**
     * Issue an async RSSI read tagged with `sampleContext`. The rssi_sample
     * metric is emitted from the GATT callback. No-op if there's no live GATT.
     */
    fun requestRssiSample(sampleContext: String)

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 30000L
    }
}
