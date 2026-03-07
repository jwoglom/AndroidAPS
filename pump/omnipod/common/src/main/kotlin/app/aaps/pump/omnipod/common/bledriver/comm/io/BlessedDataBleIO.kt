package app.aaps.pump.omnipod.common.bledriver.comm.io

import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.DataBleIO
import android.bluetooth.BluetoothGattCharacteristic
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.omnipod.common.bledriver.comm.callbacks.BlessedBleCallbacks
import com.welie.blessed.BluetoothPeripheral
import java.util.concurrent.BlockingQueue

/**
 * Blessed Kotlin implementation of DataBleIO.
 */
class BlessedDataBleIO(
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
    CharacteristicType.DATA
), DataBleIO
