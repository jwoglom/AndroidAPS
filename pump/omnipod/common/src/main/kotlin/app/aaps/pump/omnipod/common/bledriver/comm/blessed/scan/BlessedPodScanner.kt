package app.aaps.pump.omnipod.common.bledriver.comm.blessed.scan

import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ScanException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ScanFailFoundTooManyException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.scan.BleDiscoveredDevice
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.scan.PodScanner
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.scan.BleDiscoveredDevice as LegacyBleDiscoveredDevice
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.scan.DiscoveredInvalidPodException
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCentralManagerCallback
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.ScanFailure
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Blessed Kotlin implementation of PodScanner using BluetoothCentralManager.
 */
class BlessedPodScanner(
    private val context: Context,
    private val logger: AAPSLogger
) : PodScanner {

    @Throws(InterruptedException::class, ScanException::class)
    override fun scanForPod(serviceUUID: String?, podID: Long): BleDiscoveredDevice {
        val found = ConcurrentHashMap<String, ScanResult>()
        var scanFailed: ScanFailure? = null

        val callback = object : BluetoothCentralManagerCallback() {
            override fun onDiscovered(peripheral: BluetoothPeripheral, scanResult: ScanResult) {
                logger.debug(LTag.PUMPBTCOMM, "Blessed scan found: ${scanResult.device.address}")
                found[scanResult.device.address] = scanResult
            }

            override fun onScanFailed(scanFailure: ScanFailure) {
                logger.warn(LTag.PUMPBTCOMM, "Blessed scan failed: $scanFailure")
                scanFailed = scanFailure
            }
        }

        val handler = Handler(Looper.getMainLooper())
        val centralManager = BluetoothCentralManager(context, callback, handler)

        try {
            val serviceUuid = UUID.fromString(serviceUUID)
            logger.debug(LTag.PUMPBTCOMM, "Blessed scanning for service: $serviceUuid")
            centralManager.scanForPeripheralsWithServices(setOf(serviceUuid))
            Thread.sleep(SCAN_DURATION_MS.toLong())
            centralManager.stopScan()

            Thread.sleep(200)

            scanFailed?.let { throw ScanException(it.value) }

            val collected = mutableListOf<BleDiscoveredDevice>()
            for (result in found.values) {
                result.scanRecord?.let { scanRecord ->
                    try {
                        collected.add(LegacyBleDiscoveredDevice(result, scanRecord, podID))
                        logger.debug(LTag.PUMPBTCOMM, "Blessed found matching pod: ${result.device.address}")
                    } catch (e: DiscoveredInvalidPodException) {
                        logger.debug(LTag.PUMPBTCOMM, "Blessed: pod not matching $e")
                    }
                }
            }

            return when {
                collected.isEmpty() -> throw ScanException("Not found")
                collected.size > 1  -> throw ScanFailFoundTooManyException(collected)
                else                -> collected[0]
            }
        } finally {
            centralManager.close()
        }
    }

    companion object {
        private const val SCAN_DURATION_MS = 5000L
    }
}
