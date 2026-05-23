package app.aaps.pump.omnipod.common.bledriver.comm.legacy.session

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.SystemClock
import app.aaps.core.data.configuration.Constants
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.common.bledriver.comm.Ids
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.EnDecrypt
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.FailedToConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io.CharacteristicType
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnection
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.callbacks.BleCommCallbacks
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.io.CmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.io.DataBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.io.IncomingPackets
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.comm.session.STOP_CONNECTING_CHECK_INTERVAL_MS
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionState
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionWaitCondition
import app.aaps.pump.omnipod.common.bledriver.comm.session.Connected
import app.aaps.pump.omnipod.common.bledriver.comm.session.DisconnectHandler
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class Connection(
    private val podDevice: BluetoothDevice,
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val context: Context,
    private val podState: OmnipodDashPodStateManager,
    private val receiverStatusStore: ReceiverStatusStore
) : BleConnection, DisconnectHandler {

    private val incomingPackets = IncomingPackets()
    private val bleCommCallbacks = BleCommCallbacks(aapsLogger, incomingPackets, this)
    private var gattConnection: BluetoothGatt? = null

    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?

    private var _connectionWaitCond: ConnectionWaitCondition? = null

    private var rssiPollScheduler: ScheduledExecutorService? = null
    private var rssiPollFuture: ScheduledFuture<*>? = null

    @Volatile
    override var session: Session? = null

    @Volatile
    override var msgIO: MessageIO? = null

    @Synchronized
    override fun connect(connectionWaitCond: ConnectionWaitCondition) {
        aapsLogger.debug("Connecting connectionWaitCond=$connectionWaitCond")
        _connectionWaitCond = connectionWaitCond
        podState.connectionAttempts++
        podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTING

        DashMetrics.setLifecycle("connect")
        val tConnectStart = System.nanoTime()
        var connectOutcome: String? = null
        var connectHciStatus: Int? = null
        try {
            val autoConnect = false
            var gatt = gattConnection
            if (gatt == null) {
                gatt = podDevice.connectGatt(context, autoConnect, bleCommCallbacks, BluetoothDevice.TRANSPORT_LE)
                if (gatt == null) {
                    connectOutcome = "failed"
                    Thread.sleep(SLEEP_WHEN_FAILING_TO_CONNECT_GATT) // Do not retry too often
                    throw FailedToConnectException("connectGatt() returned null")
                }
                gattConnection = gatt
            } else if (!gatt.connect()) {
                connectOutcome = "failed"
                throw FailedToConnectException("connect() returned false")
            }
            val before = SystemClock.elapsedRealtime()
            if (waitForConnection(connectionWaitCond) !is Connected) {
                connectOutcome = "timeout"
                connectHciStatus = bleCommCallbacks.lastConnectionStatus?.takeIf { it != android.bluetooth.BluetoothGatt.GATT_SUCCESS }
                podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED
                _connectionWaitCond = null
                throw FailedToConnectException(podDevice.address)
            }
            connectOutcome = "success"
            val waitedMs = SystemClock.elapsedRealtime() - before
            val timeoutMs = connectionWaitCond.timeoutMs
            if (timeoutMs != null) {
                var newTimeout = timeoutMs - waitedMs
                if (newTimeout < MIN_DISCOVERY_TIMEOUT_MS) {
                    newTimeout = MIN_DISCOVERY_TIMEOUT_MS
                }
                connectionWaitCond.timeoutMs = newTimeout
            }
            podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTED
        } finally {
            DashMetrics.connectPhase(
                durationMs = (System.nanoTime() - tConnectStart) / 1_000_000L,
                outcome = connectOutcome ?: "failed",
                hciStatusCode = connectHciStatus
            )
        }

        DashMetrics.setLifecycle("discover")
        val tDiscoverStart = System.nanoTime()
        var discoverOutcome: String = "in_progress"
        val discoverer = ServiceDiscoverer(aapsLogger, gattConnection!!, bleCommCallbacks, this)
        val discovered = try {
            val map = discoverer.discoverServices(connectionWaitCond)
            discoverOutcome = if (map.containsKey(CharacteristicType.CMD) && map.containsKey(CharacteristicType.DATA)) "success"
            else "characteristic_missing"
            map
        } catch (ex: Throwable) {
            discoverOutcome = if (bleCommCallbacks.lastServicesDiscoveredStatus == null) "timeout" else "characteristic_missing"
            throw ex
        } finally {
            val servicesCount = gattConnection?.services?.size ?: 0
            DashMetrics.discoverPhase(
                durationMs = (System.nanoTime() - tDiscoverStart) / 1_000_000L,
                outcome = discoverOutcome,
                servicesCount = servicesCount,
                cmdCharFound = discoverOutcome == "success",
                dataCharFound = discoverOutcome == "success"
            )
        }
        val gatt = gattConnection!!
        val cmdBleIO = CmdBleIO(
            aapsLogger,
            discovered.getValue(CharacteristicType.CMD),
            incomingPackets.cmdQueue,
            gatt,
            bleCommCallbacks
        )
        val dataBleIO = DataBleIO(
            aapsLogger,
            discovered.getValue(CharacteristicType.DATA),
            incomingPackets.dataQueue,
            gatt,
            bleCommCallbacks
        )
        msgIO = MessageIO(aapsLogger, cmdBleIO, dataBleIO)
        //  val ret = gattConnection.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        // aapsLogger.info(LTag.PUMPBTCOMM, "requestConnectionPriority: $ret")
        cmdBleIO.hello()
        cmdBleIO.readyToRead()
        dataBleIO.readyToRead()
        startRssiPolling()
        _connectionWaitCond = null
    }

    override fun requestRssiSample(sampleContext: String) {
        val gatt = gattConnection ?: return
        bleCommCallbacks.enqueueRssiTag(sampleContext)
        if (!gatt.readRemoteRssi()) {
            // gatt couldn't queue the op; pop the tag we just pushed
            aapsLogger.debug(LTag.PUMPBTCOMM, "readRemoteRssi returned false for tag=$sampleContext")
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
            bluetoothAdapterState = EnvProbe.bluetoothAdapterState(adapter)
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
        aapsLogger.debug(LTag.PUMPBTCOMM, "Disconnecting closeGatt=$closeGatt")
        stopRssiPolling()
        if (!closeGatt && gattConnection != null) {
            // Disconnect first, then close gatt
            gattConnection?.disconnect()
            // Set connection state to DISCONNECTED immediately to prevent reconnection attempts
            podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED
        } else {
            // Call with closeGatt=true only when ble is already disconnected or there is no connection
            gattConnection?.close()
            bleCommCallbacks.resetConnection()
            gattConnection = null
            session = null
            msgIO = null
            podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED
        }
    }

    private fun waitForConnection(connectionWaitCond: ConnectionWaitCondition): ConnectionState {
        aapsLogger.debug(LTag.PUMPBTCOMM, "waitForConnection connectionWaitCond=$connectionWaitCond")
        try {
            connectionWaitCond.timeoutMs?.let {
                bleCommCallbacks.waitForConnection(it)
            }
            val startWaiting = System.currentTimeMillis()
            connectionWaitCond.stopConnection?.let {
                while (!bleCommCallbacks.waitForConnection(STOP_CONNECTING_CHECK_INTERVAL_MS)) {
                    if (it.count == 0L) {
                        throw ConnectException("stopConnecting called")
                    }
                    val secondsElapsed = (System.currentTimeMillis() - startWaiting) / 1000
                    if (secondsElapsed > MAX_WAIT_FOR_CONNECTION_SECONDS) {
                        throw ConnectException("connection timeout")
                    }
                }
            }
        } catch (e: InterruptedException) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Interrupted while waiting for connection")
        }
        return connectionState()
    }

    override fun connectionState(): ConnectionState {
        val connectionState = bluetoothManager?.getConnectionState(podDevice, BluetoothProfile.GATT)
        aapsLogger.debug(LTag.PUMPBTCOMM, "GATT connection state: $connectionState")
        if (connectionState != BluetoothProfile.STATE_CONNECTED) {
            return NotConnected
        }
        return Connected
    }

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

            is SessionKeys -> {
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

    // This will be called from a different thread !!!
    override fun onConnectionLost(status: Int) {
        aapsLogger.info(LTag.PUMPBTCOMM, "Lost connection with status: $status")
        if (status != android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
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
        // Check if waiting for connection, if so, stop waiting
        _connectionWaitCond?.stopConnection?.let {
            if (it.count > 0) {
                it.countDown()
            }
        }
        // BLE disconnected, so need to close gatt
        disconnect(true)
    }

    companion object {
        const val MIN_DISCOVERY_TIMEOUT_MS = 10000L
        const val MAX_WAIT_FOR_CONNECTION_SECONDS = Constants.PUMP_MAX_CONNECTION_TIME_IN_SECONDS + 10
        const val SLEEP_WHEN_FAILING_TO_CONNECT_GATT = 10000L
        private const val RSSI_POLL_INTERVAL_MS = 30_000L
    }
}
