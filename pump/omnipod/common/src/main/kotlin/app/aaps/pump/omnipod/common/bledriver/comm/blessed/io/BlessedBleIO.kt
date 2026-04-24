package app.aaps.pump.omnipod.common.bledriver.comm.blessed.io

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.common.bledriver.comm.blessed.callbacks.BlessedBleCallbacks
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandRTS
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleCharacteristicIO
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmResult
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendErrorConfirming
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendErrorSending
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendResult
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CharacteristicType
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.WriteConfirmationError
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.WriteConfirmationSuccess
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.WriteType
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Blessed Kotlin implementation of BleCharacteristicIO using BluetoothPeripheral.
 */
open class BlessedBleIO(
    private val aapsLogger: AAPSLogger,
    private val characteristic: BluetoothGattCharacteristic,
    protected val incomingPackets: BlockingQueue<ByteArray>,
    private val peripheral: BluetoothPeripheral,
    private val blessedCallbacks: BlessedBleCallbacks,
    private val type: CharacteristicType
) : BleCharacteristicIO {

    override fun receivePacket(timeoutMs: Long): ByteArray? {
        return try {
            val packet = incomingPackets.poll(timeoutMs, TimeUnit.MILLISECONDS)
            if (packet == null) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Timeout reading $type packet")
            }
            packet
        } catch (e: InterruptedException) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Interrupted while reading packet: $e")
            null
        }
    }

    override fun sendAndConfirmPacket(payload: ByteArray): BleSendResult {
        aapsLogger.debug(LTag.PUMPBTCOMM, "BlessedBleIO: Sending on $type: ${payload.toHex()}")
        blessedCallbacks.flushConfirmationQueue()
        val sent = peripheral.writeCharacteristic(characteristic, payload, WriteType.WITH_RESPONSE)
        if (!sent) {
            return BleSendErrorSending("Could not writeCharacteristic on $type")
        }
        return when (val confirmation = blessedCallbacks.confirmWrite(
            payload,
            characteristic.uuid.toString(),
            BleCharacteristicIO.DEFAULT_IO_TIMEOUT_MS
        )) {
            is WriteConfirmationError   -> BleSendErrorConfirming(confirmation.msg)
            is WriteConfirmationSuccess -> BleSendSuccess
        }
    }

    override fun flushIncomingQueue(): Boolean {
        var foundRTS = false
        do {
            val found = incomingPackets.poll()?.also {
                aapsLogger.warn(LTag.PUMPBTCOMM, "BlessedBleIO: queue not empty, flushing: ${it.toHex()}")
                if (it.isNotEmpty() && it[0] == BleCommandRTS.data[0]) {
                    foundRTS = true
                }
            }
        } while (found != null)
        return foundRTS
    }

    override fun readyToRead(): BleSendResult {
        val sent = peripheral.startNotify(characteristic, true) // true = use indications
        if (!sent) {
            throw ConnectException("Could not startNotify on $type")
        }
        val descriptorUuid = characteristic.descriptors.firstOrNull()?.uuid?.toString()
            ?: "00002902-0000-1000-8000-00805f9b34fb" // Client Characteristic Configuration
        val confirmation = blessedCallbacks.confirmWrite(
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE,
            descriptorUuid,
            BleCharacteristicIO.DEFAULT_IO_TIMEOUT_MS
        )
        return when (confirmation) {
            is WriteConfirmationError   -> throw ConnectException(confirmation.msg)
            is WriteConfirmationSuccess -> BleSendSuccess
        }
    }
}
