package app.aaps.pump.omnipod.common.bledriver.comm.legacy

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.device.BleDeviceManager
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.scan.PodScanner
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.scan.PodScanner as LegacyPodScanner
import app.aaps.pump.omnipod.common.bledriver.metrics.DashMetrics
import app.aaps.pump.omnipod.common.keys.DashBooleanPreferenceKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacyBleDeviceManager @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences
) : BleDeviceManager {

    private val bluetoothAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    override fun ensureBondedIfRequired(podAddress: String): Boolean {
        val useBonding = preferences.get(DashBooleanPreferenceKey.UseBonding)
        val tStart = System.nanoTime()
        var priorBondState: String? = null
        var outcome = "skipped"
        try {
            if (!useBonding) return true
            val adapter = bluetoothAdapter
            if (adapter == null) {
                outcome = "no_adapter"
                return false
            }
            val device = adapter.getRemoteDevice(podAddress)
            priorBondState = bondStateName(device.bondState)
            if (device.bondState != android.bluetooth.BluetoothDevice.BOND_NONE) {
                outcome = "already_bonded"
                return true
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                outcome = "skipped_unsupported"
                return true
            }
            val result = device.createBond()
            aapsLogger.debug(LTag.PUMPBTCOMM, "Bonding with pod resulted $result")
            Thread.sleep(10000)
            // Re-read bond state after the sleep so the outcome reflects what we
            // actually achieved, not just that we called createBond().
            outcome = when (device.bondState) {
                android.bluetooth.BluetoothDevice.BOND_BONDED  -> "bonded"
                android.bluetooth.BluetoothDevice.BOND_BONDING -> "still_bonding"
                else                                           -> if (result) "create_bond_returned_true" else "create_bond_returned_false"
            }
            return true
        } finally {
            DashMetrics.bondPhase(
                priorBondState = priorBondState,
                durationMs = (System.nanoTime() - tStart) / 1_000_000L,
                outcome = outcome,
                useBondingPref = useBonding
            )
        }
    }

    private fun bondStateName(state: Int): String = when (state) {
        android.bluetooth.BluetoothDevice.BOND_NONE    -> "NONE"
        android.bluetooth.BluetoothDevice.BOND_BONDING -> "BONDING"
        android.bluetooth.BluetoothDevice.BOND_BONDED  -> "BONDED"
        else                                           -> "UNKNOWN_$state"
    }

    override fun removeBond(podAddress: String) {
        try {
            if (!preferences.get(DashBooleanPreferenceKey.UseBonding) ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val adapter = bluetoothAdapter
            if (adapter == null) {
                aapsLogger.error(LTag.PUMPBTCOMM, "removeBond: Bluetooth not available, MAC address not found")
                return
            }
            val device = adapter.getRemoteDevice(podAddress)
            val removeBondMethod = device.javaClass.getMethod("removeBond")
            val result = removeBondMethod.invoke(device)
            aapsLogger.debug(LTag.PUMPBTCOMM, "Remove bond resulted $result")
        } catch (t: Throwable) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Unpairing device with address $podAddress failed with error $t")
        }
    }

    override fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null

    override fun createPodScanner(): PodScanner {
        val adapter = bluetoothAdapter ?: throw ConnectException("Bluetooth not available")
        return LegacyPodScanner(aapsLogger, adapter)
    }
}
