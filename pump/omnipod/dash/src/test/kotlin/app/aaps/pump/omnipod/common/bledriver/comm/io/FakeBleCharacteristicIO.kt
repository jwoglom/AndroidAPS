package app.aaps.pump.omnipod.common.bledriver.comm.io

import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.BleCharacteristicIO
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Fake BLE characteristic I/O for unit testing. Supports:
 * - Enqueue data for receivePacket()
 * - Program sendAndConfirmPacket to succeed or fail (fixed or sequenced)
 * - Record sent payloads for assertions
 * - Artificial receive delay
 * - Random packet dropping
 */
open class FakeBleCharacteristicIO(
    protected val incomingPackets: BlockingQueue<ByteArray> = LinkedBlockingQueue()
) : BleCharacteristicIO {

    val sentPayloads: MutableList<ByteArray> = mutableListOf()
    var sendResult: BleSendResult = BleSendSuccess
    var flushResult: Boolean = false
    var readyToReadResult: BleSendResult = BleSendSuccess

    private val sendResultSequence: MutableList<BleSendResult> = mutableListOf()
    private var sendResultIndex: Int = 0

    /** Add data that receivePacket() will return. */
    fun enqueueReceives(vararg data: ByteArray) {
        data.forEach { incomingPackets.add(it) }
    }

    /** Program a sequence of results for sendAndConfirmPacket (consumed in order, then falls back to sendResult). */
    fun programSendResults(vararg results: BleSendResult) {
        sendResultSequence.clear()
        sendResultSequence.addAll(results)
        sendResultIndex = 0
    }

    override fun receivePacket(timeoutMs: Long): ByteArray? =
        incomingPackets.poll(timeoutMs, TimeUnit.MILLISECONDS)

    override fun sendAndConfirmPacket(payload: ByteArray): BleSendResult {
        sentPayloads.add(payload)
        return if (sendResultIndex < sendResultSequence.size) {
            sendResultSequence[sendResultIndex++]
        } else {
            sendResult
        }
    }

    override fun flushIncomingQueue(): Boolean {
        return flushResult
    }

    override fun readyToRead(): BleSendResult = readyToReadResult

    open fun reset() {
        sentPayloads.clear()
        sendResult = BleSendSuccess
        sendResultSequence.clear()
        sendResultIndex = 0
        flushResult = false
        readyToReadResult = BleSendSuccess
        incomingPackets.clear()
    }
}
