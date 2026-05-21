package app.aaps.pump.omnipod.common.bledriver.comm.blessed.callbacks

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CharacteristicType
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CharacteristicType.Companion.byValue
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.WriteConfirmation
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.WriteConfirmationError
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.WriteConfirmationSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.io.IncomingPackets
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.BluetoothPeripheralCallback
import com.welie.blessed.GattStatus
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Blessed Kotlin equivalent of BleCommCallbacks. Implements BluetoothPeripheralCallback
 * and provides the same semantics (connection latch, service discovery latch, write confirmation, incoming packets).
 */
class BlessedBleCallbacks(
    private val aapsLogger: AAPSLogger,
    val incomingPackets: IncomingPackets,
) : BluetoothPeripheralCallback() {

    private var serviceDiscoveryComplete: CountDownLatch = CountDownLatch(1)
    private val writeQueue: BlockingQueue<WriteConfirmation> = LinkedBlockingQueue()

    fun signalServiceDiscoveryComplete() {
        serviceDiscoveryComplete.countDown()
    }

    fun waitForServiceDiscovery(timeoutMs: Long): Boolean {
        return try {
            serviceDiscoveryComplete.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Interrupted while waiting for ServiceDiscovery")
            false
        }
    }

    override fun onServicesDiscovered(peripheral: BluetoothPeripheral) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Blessed onServicesDiscovered")
        signalServiceDiscoveryComplete()
    }

    override fun onCharacteristicUpdate(
        peripheral: BluetoothPeripheral,
        value: ByteArray,
        characteristic: BluetoothGattCharacteristic,
        status: GattStatus
    ) {
        if (status != GattStatus.SUCCESS) return
        val characteristicType = try {
            byValue(characteristic.uuid.toString())
        } catch (e: IllegalArgumentException) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Unknown characteristic: ${characteristic.uuid}")
            return
        }
        aapsLogger.debug(LTag.PUMPBTCOMM, "Blessed onCharacteristicUpdate: $characteristicType / ${value.toHex()}")
        incomingPackets.byCharacteristicType(characteristicType).add(value)
    }

    override fun onCharacteristicWrite(
        peripheral: BluetoothPeripheral,
        value: ByteArray,
        characteristic: BluetoothGattCharacteristic,
        status: GattStatus
    ) {
        onWrite(status, characteristic.uuid.toString(), value)
    }

    override fun onDescriptorWrite(
        peripheral: BluetoothPeripheral,
        value: ByteArray,
        descriptor: BluetoothGattDescriptor,
        status: GattStatus
    ) {
        onWrite(status, descriptor.uuid.toString(), value)
    }

    private fun onWrite(status: GattStatus, uuid: String, value: ByteArray) {
        val writeConfirmation = when {
            status != GattStatus.SUCCESS ->
                WriteConfirmationError("Write status: $status")

            else                        -> {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Blessed onWrite value ${value.toHex()}")
                WriteConfirmationSuccess(uuid, value)
            }
        }
        writeQueue.offer(writeConfirmation)
    }

    fun confirmWrite(expectedPayload: ByteArray, expectedUUID: String, timeoutMs: Long): WriteConfirmation {
        return try {
            when (val received = writeQueue.poll(timeoutMs, TimeUnit.MILLISECONDS)) {
                null                       ->
                    WriteConfirmationError("Timeout waiting for writeConfirmation")

                is WriteConfirmationSuccess ->
                    if (expectedPayload.contentEquals(received.payload) && expectedUUID == received.uuid) {
                        received
                    } else {
                        aapsLogger.warn(LTag.PUMPBTCOMM, "Could not confirm write. Got ${received.payload.toHex()} Expected: ${expectedPayload.toHex()}")
                        WriteConfirmationError("Received incorrect writeConfirmation")
                    }

                is WriteConfirmationError   ->
                    received
            }
        } catch (e: InterruptedException) {
            WriteConfirmationError("Interrupted waiting for confirmation")
        }
    }

    fun flushConfirmationQueue() {
        if (writeQueue.isNotEmpty()) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Write queue should be empty, found: ${writeQueue.size}")
            writeQueue.clear()
        }
    }

    fun resetConnection() {
        serviceDiscoveryComplete.countDown()
        serviceDiscoveryComplete = CountDownLatch(1)
        flushConfirmationQueue()
    }
}
