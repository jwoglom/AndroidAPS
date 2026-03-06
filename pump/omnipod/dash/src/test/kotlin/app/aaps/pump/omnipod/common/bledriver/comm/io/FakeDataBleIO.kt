package app.aaps.pump.omnipod.common.bledriver.comm.io

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

/**
 * Fake DataBleIO for unit testing MessageIO.
 */
class FakeDataBleIO(
    incomingPackets: BlockingQueue<ByteArray> = LinkedBlockingQueue()
) : FakeBleCharacteristicIO(incomingPackets), DataBleIO
