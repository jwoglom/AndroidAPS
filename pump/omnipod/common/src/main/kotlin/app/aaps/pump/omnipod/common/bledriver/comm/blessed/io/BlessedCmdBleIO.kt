package app.aaps.pump.omnipod.common.bledriver.comm.blessed.io

import android.bluetooth.BluetoothGattCharacteristic
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.omnipod.common.bledriver.comm.OmnipodDashBleManagerImpl
import app.aaps.pump.omnipod.common.bledriver.comm.blessed.callbacks.BlessedBleCallbacks
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommand
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandHello
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmError
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmIncorrectData
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmResult
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.BleConfirmSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CharacteristicType
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CmdBleIO
import com.welie.blessed.BluetoothPeripheral
import java.util.concurrent.BlockingQueue

/**
 * Blessed Kotlin implementation of CmdBleIO.
 */
class BlessedCmdBleIO(
    aapsLogger: AAPSLogger,
    characteristic: BluetoothGattCharacteristic,
    incomingPackets: BlockingQueue<ByteArray>,
    peripheral: BluetoothPeripheral,
    blessedCallbacks: BlessedBleCallbacks
) : BlessedBleIO(
    aapsLogger,
    characteristic,
    incomingPackets,
    peripheral,
    blessedCallbacks,
    CharacteristicType.CMD
), CmdBleIO {

    override fun peekCommand(): ByteArray? = incomingPackets.peek()

    override fun hello() {
        sendAndConfirmPacket(BleCommandHello(OmnipodDashBleManagerImpl.CONTROLLER_ID).data)
    }

    override fun expectCommandType(expected: BleCommand, timeoutMs: Long): BleConfirmResult {
        return receivePacket(timeoutMs)?.let {
            if (it.isNotEmpty() && it[0] == expected.data[0])
                BleConfirmSuccess
            else
                BleConfirmIncorrectData(it)
        }
            ?: BleConfirmError("Error reading packet")
    }
}
