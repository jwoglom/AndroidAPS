package app.aaps.pump.omnipod.common.bledriver.comm.scan

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ScanException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ScanFailFoundTooManyException
import app.aaps.pump.omnipod.common.bledriver.metrics.DashMetrics
import java.util.Arrays

class PodScanner(private val logger: AAPSLogger, private val bluetoothAdapter: BluetoothAdapter) {

    @Throws(InterruptedException::class, ScanException::class)
    fun scanForPod(serviceUUID: String?, podID: Long): BleDiscoveredDevice {
        DashMetrics.setLifecycle("scan")
        val tStart = System.nanoTime()
        var failureReason: String? = null
        var pickedRssi: Int? = null
        val scanCollector = ScanCollector(logger, podID)
        try {
            val scanner = bluetoothAdapter.bluetoothLeScanner
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString(serviceUUID))
                .build()
            val scanSettings = ScanSettings.Builder()
                .setLegacy(false)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            logger.debug(LTag.PUMPBTCOMM, "Scanning with filters: $filter settings$scanSettings")
            scanner.startScan(Arrays.asList(filter), scanSettings, scanCollector)
            Thread.sleep(SCAN_DURATION_MS.toLong())
            scanner.flushPendingScanResults(scanCollector)
            scanner.stopScan(scanCollector)
            scanCollector.lastScanFailureCode?.let { failureReason = "scan_failure_$it" }
            val collected = scanCollector.collect()
            if (collected.isEmpty()) {
                if (failureReason == null) failureReason = "not_found"
                throw ScanException("Not found")
            } else if (collected.size > 1) {
                if (failureReason == null) failureReason = "too_many"
                throw ScanFailFoundTooManyException(collected)
            }
            pickedRssi = collected[0].scanResult.rssi
            return collected[0]
        } finally {
            DashMetrics.scanPhase(
                durationMs = (System.nanoTime() - tStart) / 1_000_000L,
                candidatesFound = scanCollector.candidatesFound,
                foundPodRssi = pickedRssi,
                scanFailureReason = failureReason
            )
        }
    }

    companion object {

        const val SCAN_FOR_SERVICE_UUID = "00004024-0000-1000-8000-00805F9B34FB"
        const val POD_ID_NOT_ACTIVATED = 0xFFFFFFFEL
        private const val SCAN_DURATION_MS = 5000
    }
}
