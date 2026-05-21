package app.aaps.pump.omnipod.common.bledriver.comm.blessed

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.bledriver.comm.blessed.scan.BlessedPodScanner
import app.aaps.pump.omnipod.common.bledriver.comm.blessed.session.BlessedBondingHelper
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.device.BleDeviceManager
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.scan.PodScanner
import app.aaps.pump.omnipod.common.keys.DashBooleanPreferenceKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlessedBleDeviceManager @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences
) : BleDeviceManager {

    private val bluetoothAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    override fun ensureBondedIfRequired(podAddress: String): Boolean {
        if (!preferences.get(DashBooleanPreferenceKey.UseBonding)) return true
        return BlessedBondingHelper.createBondIfNeeded(context, podAddress, aapsLogger)
    }

    override fun removeBond(podAddress: String) {
        if (!preferences.get(DashBooleanPreferenceKey.UseBonding)) return
        BlessedBondingHelper.removeBond(context, podAddress, aapsLogger)
    }

    override fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null

    override fun createPodScanner(): PodScanner = BlessedPodScanner(context, aapsLogger)
}
