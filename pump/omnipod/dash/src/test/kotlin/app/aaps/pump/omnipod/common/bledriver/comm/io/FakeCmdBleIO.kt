package app.aaps.pump.omnipod.common.bledriver.comm.io

import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommand
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandHello
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

/**
 * Fake CmdBleIO for unit testing MessageIO, SessionEstablisher, etc.
 */
class FakeCmdBleIO(
    incomingPackets: BlockingQueue<ByteArray> = LinkedBlockingQueue()
) : FakeBleCharacteristicIO(incomingPackets), CmdBleIO {

    val expectCommandResults: MutableList<BleConfirmResult> = mutableListOf()
    var expectCommandResultIndex: Int = 0
    var helloCallCount: Int = 0

    /** Configure what expectCommandType() returns (in order). */
    fun expectCommandResults(vararg results: BleConfirmResult) {
        expectCommandResults.clear()
        expectCommandResults.addAll(results)
        expectCommandResultIndex = 0
    }

    override fun peekCommand(): ByteArray? = incomingPackets.peek()

    override fun hello() {
        helloCallCount++
        sendAndConfirmPacket(BleCommandHello(4242).data)
    }

    override fun expectCommandType(expected: BleCommand, timeoutMs: Long): BleConfirmResult {
        return if (expectCommandResultIndex < expectCommandResults.size) {
            expectCommandResults[expectCommandResultIndex++]
        } else {
            receivePacket(timeoutMs)?.let {
                if (it.isNotEmpty() && it[0] == expected.data[0]) BleConfirmSuccess
                else BleConfirmIncorrectData(it)
            }
                ?: BleConfirmError("No programmed result and receivePacket returned null")
        }
    }

    override fun reset() {
        super.reset()
        expectCommandResults.clear()
        expectCommandResultIndex = 0
        helloCallCount = 0
    }
}
