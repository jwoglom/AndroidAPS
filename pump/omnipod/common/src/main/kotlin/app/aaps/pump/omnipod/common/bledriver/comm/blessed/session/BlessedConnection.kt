package app.aaps.pump.omnipod.common.bledriver.comm.blessed.session

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import app.aaps.core.data.configuration.Constants
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.common.bledriver.comm.Ids
import app.aaps.pump.omnipod.common.bledriver.comm.blessed.callbacks.BlessedBleCallbacks
import app.aaps.pump.omnipod.common.bledriver.comm.blessed.io.BlessedCmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.blessed.io.BlessedDataBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.EnDecrypt
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.FailedToConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CharacteristicType
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.DataBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnection
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.io.IncomingPackets
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.comm.session.Connected
import app.aaps.pump.omnipod.common.bledriver.comm.session.Connecting
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionState
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionWaitCondition
import app.aaps.pump.omnipod.common.bledriver.comm.session.EapSqn
import app.aaps.pump.omnipod.common.bledriver.comm.session.NotConnected
import app.aaps.pump.omnipod.common.bledriver.comm.session.Session
import app.aaps.pump.omnipod.common.bledriver.comm.session.SessionEstablisher
import app.aaps.pump.omnipod.common.bledriver.comm.session.SessionKeys
import app.aaps.pump.omnipod.common.bledriver.comm.session.SessionNegotiationResynchronization
import app.aaps.pump.omnipod.common.bledriver.metrics.DashMetrics
import app.aaps.pump.omnipod.common.bledriver.metrics.EnvProbe
import app.aaps.pump.omnipod.common.bledriver.metrics.SessionContextHolder
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCentralManagerCallback
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.ConnectionPriority
import com.welie.blessed.HciStatus
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Blessed Kotlin implementation of Connection using BluetoothCentralManager.
 */
class BlessedConnection(
    private val podAddress: String,
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val context: Context,
    private val podState: OmnipodDashPodStateManager,
    private val receiverStatusStore: ReceiverStatusStore
) : BleConnection {

    private val incomingPackets = IncomingPackets()
    private val blessedCallbacks = BlessedBleCallbacks(aapsLogger, incomingPackets)

    private var centralManager: BluetoothCentralManager? = null
    private var peripheral: BluetoothPeripheral? = null
    private var connectionSucceeded = false
    private val connectionStateRef = AtomicReference<ConnectionState>(NotConnected)

    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?

    private var _connectionWaitCond: ConnectionWaitCondition? = null

    // HCI status reported by the central callback on a failed/lost connection,
    // surfaced into connect_phase.hci_status_code (mirrors the raw-GATT driver's
    // bleCommCallbacks.lastConnectionStatus).
    @Volatile
    private var lastConnectFailHci: Int? = null

    private var rssiPollScheduler: ScheduledExecutorService? = null
    private var rssiPollFuture: ScheduledFuture<*>? = null

    @Volatile
    override var session: Session? = null

    @Volatile
    override var msgIO: MessageIO? = null

    @Synchronized
    override fun connect(connectionWaitCond: ConnectionWaitCondition) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Blessed connecting connectionWaitCond=$connectionWaitCond")
        _connectionWaitCond = connectionWaitCond
        podState.connectionAttempts++
        podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTING
        connectionSucceeded = false
        lastConnectFailHci = null
        connectionStateRef.set(Connecting)
        val connectionLatch = CountDownLatch(1)

        val handler = Handler(Looper.getMainLooper())
        val centralCallback = object : BluetoothCentralManagerCallback() {
            override fun onConnected(connectedPeripheral: BluetoothPeripheral) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Blessed onConnected")
                connectionSucceeded = true
                connectionStateRef.set(Connected)
                DashMetrics.connectionStateChange(BluetoothProfile.STATE_CONNECTED, BluetoothGatt.GATT_SUCCESS)
                connectionLatch.countDown()
            }

            override fun onConnectionFailed(peripheral: BluetoothPeripheral, status: HciStatus) {
                aapsLogger.warn(LTag.PUMPBTCOMM, "Blessed onConnectionFailed: $status")
                connectionStateRef.set(NotConnected)
                lastConnectFailHci = status.value.takeIf { it != BluetoothGatt.GATT_SUCCESS }
                DashMetrics.connectionStateChange(BluetoothProfile.STATE_DISCONNECTED, status.value)
                if (status.value != BluetoothGatt.GATT_SUCCESS) {
                    DashMetrics.gattError("connect", status.value.toString(), null)
                }
                connectionLatch.countDown()
            }

            override fun onDisconnected(peripheral: BluetoothPeripheral, status: HciStatus) {
                aapsLogger.info(LTag.PUMPBTCOMM, "Blessed onDisconnected: $status")
                connectionStateRef.set(NotConnected)
                DashMetrics.connectionStateChange(BluetoothProfile.STATE_DISCONNECTED, status.value)
                if (status.value != BluetoothGatt.GATT_SUCCESS) {
                    DashMetrics.gattError("connect", status.value.toString(), null)
                }
                onConnectionLost(status.value)
            }
        }

        val manager = BluetoothCentralManager(context, centralCallback, handler)
        centralManager = manager

        // Connect phase — try-as-expression yields the connected peripheral; the
        // finally always emits connect_phase regardless of outcome.
        DashMetrics.setLifecycle("connect")
        val tConnectStart = System.nanoTime()
        var connectOutcome: String? = null
        val pod: BluetoothPeripheral = try {
            val p = manager.getPeripheral(podAddress) ?: run {
                aapsLogger.warn(LTag.PUMPBTCOMM, "Blessed getPeripheral returned null for $podAddress")
                connectOutcome = "failed"
                throw FailedToConnectException("Could not get peripheral for $podAddress")
            }
            manager.connect(p, blessedCallbacks)
            val connected = waitForConnection(connectionLatch, connectionWaitCond)
            if (!connected || !connectionSucceeded) {
                connectOutcome = if (!connected) "timeout" else "failed"
                manager.cancelConnection(p)
                podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED
                _connectionWaitCond = null
                throw FailedToConnectException(podAddress)
            }
            connectOutcome = "success"
            p
        } finally {
            DashMetrics.connectPhase(
                durationMs = (System.nanoTime() - tConnectStart) / 1_000_000L,
                outcome = connectOutcome ?: "failed",
                hciStatusCode = lastConnectFailHci
            )
        }

        peripheral = pod
        podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTED

        // Discover phase — try-as-expression yields the (cmd, data) characteristics;
        // the finally always emits discover_phase.
        DashMetrics.setLifecycle("discover")
        val tDiscoverStart = System.nanoTime()
        var discoverOutcome = "in_progress"
        val (cmdChar, dataChar) = try {
            val discoveryTimeout = connectionWaitCond.timeoutMs?.let { it - 2000 } ?: MIN_DISCOVERY_TIMEOUT_MS
            if (!blessedCallbacks.waitForServiceDiscovery(maxOf(discoveryTimeout, MIN_DISCOVERY_TIMEOUT_MS))) {
                discoverOutcome = "timeout"
                disconnect(true)
                throw FailedToConnectException("Service discovery timeout")
            }
            val serviceUuid = UUID.fromString("1a7e4024-e3ed-4464-8b7e-751e03d0dc5f")
            val c = pod.getCharacteristic(serviceUuid, CharacteristicType.CMD.uuid)
                ?: run {
                    discoverOutcome = "characteristic_missing"
                    throw ConnectException("CMD characteristic not found")
                }
            val d = pod.getCharacteristic(serviceUuid, CharacteristicType.DATA.uuid)
                ?: run {
                    discoverOutcome = "characteristic_missing"
                    throw ConnectException("DATA characteristic not found")
                }
            discoverOutcome = "success"
            c to d
        } finally {
            DashMetrics.discoverPhase(
                durationMs = (System.nanoTime() - tDiscoverStart) / 1_000_000L,
                outcome = discoverOutcome,
                servicesCount = pod.services.size,
                cmdCharFound = discoverOutcome == "success",
                dataCharFound = discoverOutcome == "success"
            )
        }

        val cmdBleIO: CmdBleIO = BlessedCmdBleIO(
            aapsLogger,
            cmdChar,
            incomingPackets.cmdQueue,
            pod,
            blessedCallbacks
        )
        val dataBleIO: DataBleIO = BlessedDataBleIO(
            aapsLogger,
            dataChar,
            incomingPackets.dataQueue,
            pod,
            blessedCallbacks
        )
        msgIO = MessageIO(aapsLogger, cmdBleIO, dataBleIO)

        pod.requestConnectionPriority(ConnectionPriority.HIGH)

        cmdBleIO.hello()
        cmdBleIO.readyToRead()
        dataBleIO.readyToRead()
        startRssiPolling()
        _connectionWaitCond = null
    }

    override fun requestRssiSample(sampleContext: String) {
        val perf = peripheral ?: return
        blessedCallbacks.enqueueRssiTag(sampleContext)
        if (!perf.readRemoteRssi()) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "readRemoteRssi returned false for tag=$sampleContext")
            DashMetrics.gattOpRejected("read_rssi", null)
        }
    }

    private fun startRssiPolling() {
        stopRssiPolling()
        val exec = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "dash-rssi-poll").apply { isDaemon = true }
        }
        rssiPollScheduler = exec
        // Guard against the timer outliving its session: capture sessionId at
        // schedule time and skip if SessionContextHolder has moved on.
        val sessionId = SessionContextHolder.current()?.sessionId
        rssiPollFuture = exec.scheduleAtFixedRate(
            {
                try {
                    val ctx = SessionContextHolder.current()
                    if (ctx == null || ctx.sessionId != sessionId) return@scheduleAtFixedRate
                    // Don't compete with an in-flight command for the GATT op queue.
                    if (ctx.commandInFlight != null) return@scheduleAtFixedRate
                    requestRssiSample("idle_poll")
                    sampleEnvIfChanged()
                } catch (_: Throwable) {
                    // Never let timer task throw — it would cancel future runs silently.
                }
            },
            RSSI_POLL_INTERVAL_MS, RSSI_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS
        )
    }

    private fun sampleEnvIfChanged() {
        val adapter = bluetoothManager?.adapter
        DashMetrics.envSampleIfChanged(
            batteryLevelPct = receiverStatusStore.batteryLevel.takeIf { it in 0..100 },
            appState = EnvProbe.appState(context),
            powerSaveMode = EnvProbe.powerSaveMode(context),
            deviceIdleMode = EnvProbe.deviceIdleMode(context),
            locationServicesOn = EnvProbe.locationServicesOn(context),
            bluetoothAdapterState = EnvProbe.bluetoothAdapterState(adapter),
            isCharging = receiverStatusStore.isCharging
        )
    }

    private fun stopRssiPolling() {
        rssiPollFuture?.cancel(false)
        rssiPollFuture = null
        rssiPollScheduler?.shutdownNow()
        rssiPollScheduler = null
    }

    @Synchronized
    override fun disconnect(closeGatt: Boolean) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Blessed disconnecting closeGatt=$closeGatt")
        stopRssiPolling()
        val perf = peripheral
        val manager = centralManager
        if (!closeGatt && perf != null) {
            manager?.cancelConnection(perf)
            connectionStateRef.set(NotConnected)
            podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED
        } else {
            if (perf != null) {
                manager?.cancelConnection(perf)
            }
            blessedCallbacks.resetConnection()
            connectionStateRef.set(NotConnected)
            centralManager?.close()
            centralManager = null
            peripheral = null
            session = null
            msgIO = null
            podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED
        }
    }

    override fun connectionState(): ConnectionState = connectionStateRef.get()

    override fun establishSession(ltk: ByteArray, msgSeq: Byte, ids: Ids, eapSqn: ByteArray): EapSqn? {
        val mIO = msgIO ?: throw ConnectException("Connection lost")
        val eapAkaExchanger = SessionEstablisher(aapsLogger, config, mIO, ltk, eapSqn, ids, msgSeq)
        return when (val keys = eapAkaExchanger.negotiateSessionKeys()) {
            is SessionNegotiationResynchronization -> {
                if (config.DEBUG) {
                    aapsLogger.info(LTag.PUMPCOMM, "EAP AKA resynchronization: ${keys.synchronizedEapSqn}")
                }
                keys.synchronizedEapSqn
            }

            is SessionKeys                         -> {
                if (config.DEBUG) {
                    aapsLogger.info(LTag.PUMPCOMM, "CK: ${keys.ck.toHex()}")
                    aapsLogger.info(LTag.PUMPCOMM, "msgSequenceNumber: ${keys.msgSequenceNumber}")
                    aapsLogger.info(LTag.PUMPCOMM, "Nonce: ${keys.nonce}")
                }
                val enDecrypt = EnDecrypt(aapsLogger, keys.nonce, keys.ck)
                session = Session(aapsLogger, mIO, ids, sessionKeys = keys, enDecrypt = enDecrypt)
                null
            }
        }
    }

    private fun waitForConnection(latch: CountDownLatch, cond: ConnectionWaitCondition): Boolean {
        return try {
            when {
                cond.timeoutMs != null      -> latch.await(cond.timeoutMs!!, TimeUnit.MILLISECONDS)
                cond.stopConnection != null -> {
                    val start = System.currentTimeMillis()
                    while (!latch.await(STOP_CONNECTING_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS)) {
                        if (cond.stopConnection!!.count == 0L) throw ConnectException("stopConnecting called")
                        if ((System.currentTimeMillis() - start) / 1000 > MAX_WAIT_FOR_CONNECTION_SECONDS) {
                            throw ConnectException("connection timeout")
                        }
                    }
                    true
                }

                else                        -> false
            }
        } catch (e: InterruptedException) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Interrupted while waiting for connection")
            latch.await(0, TimeUnit.MILLISECONDS)
            false
        } catch (e: ConnectException) {
            throw e
        }
    }

    // This will be called from a different thread !!!
    override fun onConnectionLost(status: Int) {
        aapsLogger.info(LTag.PUMPBTCOMM, "Blessed connection lost with status: $status")
        if (status != BluetoothGatt.GATT_SUCCESS) {
            DashMetrics.unexpectedDisconnect(
                hciStatus = status,
                whereInLifecycle = null,
                commandInFlight = null
            )
            DashMetrics.sessionEnd(
                endReason = "unexpected_disconnect",
                hciStatusAtDisconnect = status,
                successfulConnections = podState.successfulConnections,
                connectionAttempts = podState.connectionAttempts,
                eapAkaSequenceNumber = podState.eapAkaSequenceNumber
            )
        }
        _connectionWaitCond?.stopConnection?.let {
            if (it.count > 0) it.countDown()
        }
        disconnect(true)
    }

    companion object {
        const val BASE_CONNECT_TIMEOUT_MS = 10000L
        const val MIN_DISCOVERY_TIMEOUT_MS = 10000L
        const val STOP_CONNECTING_CHECK_INTERVAL_MS = 500L
        const val MAX_WAIT_FOR_CONNECTION_SECONDS = Constants.PUMP_MAX_CONNECTION_TIME_IN_SECONDS + 10
        private const val RSSI_POLL_INTERVAL_MS = 30_000L
    }
}
