package app.aaps.pump.omnipod.common.bledriver.comm.io

import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.DataBleIO
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.omnipod.common.bledriver.comm.callbacks.BleCommCallbacks
import java.util.concurrent.BlockingQueue

/**
 * Android BLE implementation of DataBleIO using BluetoothGatt.
 */
class AndroidDataBleIO(
    logger: AAPSLogger,
    characteristic: BluetoothGattCharacteristic,
    incomingPackets: BlockingQueue<ByteArray>,
    gatt: BluetoothGatt,
    bleCommCallbacks: BleCommCallbacks
) : BleIO(
    logger,
    characteristic,
    incomingPackets,
    gatt,
    bleCommCallbacks,
    CharacteristicType.DATA
), DataBleIO
