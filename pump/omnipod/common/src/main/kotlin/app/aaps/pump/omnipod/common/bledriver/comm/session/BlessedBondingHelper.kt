package app.aaps.pump.omnipod.common.bledriver.comm.session

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCentralManagerCallback
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.BluetoothPeripheralCallback
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Helper for Blessed Kotlin bonding operations (createBond, removeBond).
 * Uses a temporary BluetoothCentralManager for each operation.
 */
object BlessedBondingHelper {

    private const val BOND_TIMEOUT_MS = 15_000L

    /**
     * Create bond with device at address if not already bonded.
     * Blocks until bonding succeeds, fails, or times out.
     * @return true if bonded (or was already bonded), false on failure/timeout
     */
    fun createBondIfNeeded(
        context: Context,
        address: String,
        aapsLogger: AAPSLogger
    ): Boolean {
        val bondLatch = CountDownLatch(1)
        var bonded = false

        val handler = Handler(Looper.getMainLooper())
        val centralCallback = object : BluetoothCentralManagerCallback() {
            override fun onConnected(peripheral: BluetoothPeripheral) {}
            override fun onConnectionFailed(peripheral: BluetoothPeripheral, status: com.welie.blessed.HciStatus) {}
            override fun onDisconnected(peripheral: BluetoothPeripheral, status: com.welie.blessed.HciStatus) {}
        }

        val peripheralCallback = object : BluetoothPeripheralCallback() {
            override fun onBondingSucceeded(peripheral: BluetoothPeripheral) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Blessed onBondingSucceeded")
                bonded = true
                bondLatch.countDown()
            }

            override fun onBondingFailed(peripheral: BluetoothPeripheral) {
                aapsLogger.warn(LTag.PUMPBTCOMM, "Blessed onBondingFailed")
                bondLatch.countDown()
            }
        }

        val manager = BluetoothCentralManager(context, centralCallback, handler)
        try {
            val peripheral = manager.getPeripheral(address)
            if (peripheral == null) {
                aapsLogger.warn(LTag.PUMPBTCOMM, "Blessed createBond: getPeripheral returned null")
                return false
            }
            when (peripheral.bondState) {
                com.welie.blessed.BondState.BONDED -> {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Device already bonded")
                    return true
                }
                com.welie.blessed.BondState.BONDING -> {
                    bondLatch.await(BOND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    return bonded
                }
                else -> {
                    manager.createBond(peripheral, peripheralCallback)
                    bondLatch.await(BOND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    return bonded
                }
            }
        } finally {
            manager.close()
        }
    }

    /**
     * Remove bond with device at address using Blessed API.
     */
    fun removeBond(context: Context, address: String, aapsLogger: AAPSLogger): Boolean {
        val handler = Handler(Looper.getMainLooper())
        val centralCallback = object : BluetoothCentralManagerCallback() {
            override fun onConnected(peripheral: BluetoothPeripheral) {}
            override fun onConnectionFailed(peripheral: BluetoothPeripheral, status: com.welie.blessed.HciStatus) {}
            override fun onDisconnected(peripheral: BluetoothPeripheral, status: com.welie.blessed.HciStatus) {}
        }
        val manager = BluetoothCentralManager(context, centralCallback, handler)
        return try {
            val result = manager.removeBond(address)
            aapsLogger.debug(LTag.PUMPBTCOMM, "Blessed removeBond resulted $result")
            result
        } finally {
            manager.close()
        }
    }
}
