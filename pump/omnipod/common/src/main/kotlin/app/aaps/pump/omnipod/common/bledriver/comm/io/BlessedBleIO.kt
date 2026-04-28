package app.aaps.pump.omnipod.common.bledriver.comm.io

import app.aaps.pump.omnipod.common.bledriver.comm.callbacks.WriteConfirmationError
import app.aaps.pump.omnipod.common.bledriver.comm.callbacks.WriteConfirmationSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.BleCharacteristicIO
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.common.bledriver.comm.callbacks.BlessedBleCallbacks
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandRTS
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.metrics.DashMetrics
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
                DashMetrics.bleReadTimeout(type.name, timeoutMs)
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
        val tStart = System.nanoTime()
        val sent = peripheral.writeCharacteristic(characteristic, payload, WriteType.WITH_RESPONSE)
        if (!sent) {
            DashMetrics.bleWrite(type.name, (System.nanoTime() - tStart) / 1_000_000L, "writeCharacteristic_returned_false")
            DashMetrics.gattError("write", "writeCharacteristic_returned_false", type.name)
            return BleSendErrorSending("Could not writeCharacteristic on $type")
        }
        val result = when (val confirmation = blessedCallbacks.confirmWrite(
            payload,
            characteristic.uuid.toString(),
            BleCharacteristicIO.DEFAULT_IO_TIMEOUT_MS
        )) {
            is WriteConfirmationError -> BleSendErrorConfirming(confirmation.msg)
            is WriteConfirmationSuccess -> BleSendSuccess
        }
        val ackMs = (System.nanoTime() - tStart) / 1_000_000L
        if (result is BleSendErrorConfirming) {
            DashMetrics.bleWrite(type.name, ackMs, result.msg)
        } else {
            DashMetrics.bleWrite(type.name, ackMs, null)
        }
        return result
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
        // Blessed may deliver onDescriptorWrite or onNotificationStateUpdate - wait for confirmation
        val descriptorUuid = characteristic.descriptors.firstOrNull()?.uuid?.toString()
            ?: "00002902-0000-1000-8000-00805f9b34fb" // Client Characteristic Configuration
        val confirmation = blessedCallbacks.confirmWrite(
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE,
            descriptorUuid,
            BleCharacteristicIO.DEFAULT_IO_TIMEOUT_MS
        )
        return when (confirmation) {
            is WriteConfirmationError -> throw ConnectException(confirmation.msg)
            is WriteConfirmationSuccess -> BleSendSuccess
        }
    }
}
