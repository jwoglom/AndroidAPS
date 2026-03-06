package app.aaps.pump.omnipod.common.bledriver.comm.io

import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.BleCharacteristicIO
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Fake BLE characteristic I/O for unit testing. Supports:
 * - Enqueue data for receivePacket()
 * - Program sendAndConfirmPacket to succeed or fail
 * - Record sent payloads for assertions
 */
open class FakeBleCharacteristicIO(
    protected val incomingPackets: BlockingQueue<ByteArray> = LinkedBlockingQueue()
) : BleCharacteristicIO {

    val sentPayloads: MutableList<ByteArray> = mutableListOf()
    var sendResult: BleSendResult = BleSendSuccess
    var flushResult: Boolean = false

    /** Add data that receivePacket() will return. */
    fun enqueueReceives(vararg data: ByteArray) {
        data.forEach { incomingPackets.add(it) }
    }

    override fun receivePacket(timeoutMs: Long): ByteArray? =
        incomingPackets.poll(timeoutMs, TimeUnit.MILLISECONDS)

    override fun sendAndConfirmPacket(payload: ByteArray): BleSendResult {
        sentPayloads.add(payload)
        return sendResult
    }

    override fun flushIncomingQueue(): Boolean {
        // Don't clear - tests enqueue expected responses that should remain for receivePacket
        return flushResult
    }

    override fun readyToRead(): BleSendResult = BleSendSuccess
}
