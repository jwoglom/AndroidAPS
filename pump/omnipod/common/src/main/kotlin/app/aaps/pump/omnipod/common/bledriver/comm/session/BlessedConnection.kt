package app.aaps.pump.omnipod.common.bledriver.comm.session

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.aaps.core.data.configuration.Constants
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.common.bledriver.comm.Ids
import app.aaps.pump.omnipod.common.bledriver.comm.callbacks.BlessedBleCallbacks
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.EnDecrypt
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.FailedToConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.io.BlessedCmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.io.BlessedDataBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.io.CharacteristicType
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.CmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.DataBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.io.IncomingPackets
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.metrics.DashMetrics
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCentralManagerCallback
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.BluetoothPeripheralCallback
import com.welie.blessed.HciStatus
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Blessed Kotlin implementation of Connection using BluetoothCentralManager.
 */
class BlessedConnection(
    private val podAddress: String,
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val context: Context,
    private val podState: OmnipodDashPodStateManager
) : DisconnectHandler {

    private val incomingPackets = IncomingPackets()
    private val blessedCallbacks = BlessedBleCallbacks(aapsLogger, incomingPackets)

    private var centralManager: BluetoothCentralManager? = null
    private var peripheral: BluetoothPeripheral? = null
    private var connectionSucceeded = false
    private val connectionStateRef = AtomicReference<ConnectionState>(NotConnected)

    @Volatile var lastConnectFailHci: Int? = null

    private var _connectionWaitCond: ConnectionWaitCondition? = null

    @Volatile
    var session: Session? = null

    @Volatile
    var msgIO: MessageIO? = null

    @Synchronized
    fun connect(connectionWaitCond: ConnectionWaitCondition) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Blessed connecting connectionWaitCond=$connectionWaitCond")
        _connectionWaitCond = connectionWaitCond
        podState.connectionAttempts++
        podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTING
        connectionSucceeded = false
        connectionStateRef.set(Connecting)
        val connectionLatch = CountDownLatch(1)

        val handler = Handler(Looper.getMainLooper())
        val centralCallback = object : BluetoothCentralManagerCallback() {
            override fun onConnected(connectedPeripheral: BluetoothPeripheral) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Blessed onConnected")
                connectionSucceeded = true
                connectionStateRef.set(Connected)
                connectionLatch.countDown()
            }

            override fun onConnectionFailed(peripheral: BluetoothPeripheral, status: HciStatus) {
                aapsLogger.warn(LTag.PUMPBTCOMM, "Blessed onConnectionFailed: $status")
                lastConnectFailHci = status.value
                connectionStateRef.set(NotConnected)
                connectionLatch.countDown()
            }

            override fun onDisconnected(peripheral: BluetoothPeripheral, status: HciStatus) {
                aapsLogger.info(LTag.PUMPBTCOMM, "Blessed onDisconnected: $status")
                connectionStateRef.set(NotConnected)
                onConnectionLost(status.value)
            }
        }

        val manager = BluetoothCentralManager(context, centralCallback, handler)
        centralManager = manager

        val peripheralOrNull = manager.getPeripheral(podAddress)
            ?: run {
                aapsLogger.warn(LTag.PUMPBTCOMM, "Blessed getPeripheral returned null for $podAddress")
                connectionLatch.countDown()
                null
            }

        DashMetrics.setLifecycle("connect")
        val tConnectStart = System.nanoTime()
        if (peripheralOrNull != null) {
            manager.connect(peripheralOrNull, blessedCallbacks)
            val connected = waitForConnection(connectionLatch, connectionWaitCond)
            if (!connected || !connectionSucceeded) {
                val durationMs = (System.nanoTime() - tConnectStart) / 1_000_000L
                DashMetrics.connectPhase(
                    durationMs = durationMs,
                    outcome = if (!connected) "timeout" else "failed",
                    hciStatusCode = lastConnectFailHci
                )
                manager.cancelConnection(peripheralOrNull)
                podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED
                _connectionWaitCond = null
                throw FailedToConnectException(podAddress)
            }
            val durationMs = (System.nanoTime() - tConnectStart) / 1_000_000L
            DashMetrics.connectPhase(
                durationMs = durationMs,
                outcome = "success",
                hciStatusCode = null
            )
        } else {
            val durationMs = (System.nanoTime() - tConnectStart) / 1_000_000L
            DashMetrics.connectPhase(
                durationMs = durationMs,
                outcome = "peripheral_null",
                hciStatusCode = null
            )
            throw FailedToConnectException("Could not get peripheral for $podAddress")
        }

        peripheral = peripheralOrNull
        podState.bluetoothConnectionState = OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTED

        DashMetrics.setLifecycle("discover")
        val tDiscoverStart = System.nanoTime()
        val discoveryTimeout = connectionWaitCond.timeoutMs?.let { it - 2000 } ?: MIN_DISCOVERY_TIMEOUT_MS
        val discovered = blessedCallbacks.waitForServiceDiscovery(maxOf(discoveryTimeout, MIN_DISCOVERY_TIMEOUT_MS))
        if (!discovered) {
            DashMetrics.discoverPhase(
                durationMs = (System.nanoTime() - tDiscoverStart) / 1_000_000L,
                outcome = "timeout",
                servicesCount = peripheralOrNull.services.size,
                cmdCharFound = null,
                dataCharFound = null
            )
            disconnect(true)
            throw FailedToConnectException("Service discovery timeout")
        }

        val serviceUuid = UUID.fromString("1a7e4024-e3ed-4464-8b7e-751e03d0dc5f")
        val cmdCharOrNull = peripheralOrNull.getCharacteristic(serviceUuid, CharacteristicType.CMD.uuid)
        val dataCharOrNull = peripheralOrNull.getCharacteristic(serviceUuid, CharacteristicType.DATA.uuid)
        DashMetrics.discoverPhase(
            durationMs = (System.nanoTime() - tDiscoverStart) / 1_000_000L,
            outcome = if (cmdCharOrNull != null && dataCharOrNull != null) "success" else "characteristic_missing",
            servicesCount = peripheralOrNull.services.size,
            cmdCharFound = cmdCharOrNull != null,
            dataCharFound = dataCharOrNull != null
        )
        val cmdChar = cmdCharOrNull ?: throw ConnectException("CMD characteristic not found")
        val dataChar = dataCharOrNull ?: throw ConnectException("DATA characteristic not found")

        val cmdBleIO: CmdBleIO = BlessedCmdBleIO(
            aapsLogger,
            cmdChar,
            incomingPackets.cmdQueue,
            peripheralOrNull,
            blessedCallbacks
        )
        val dataBleIO: DataBleIO = BlessedDataBleIO(
            aapsLogger,
            dataChar,
            incomingPackets.dataQueue,
            peripheralOrNull,
            blessedCallbacks
        )
        msgIO = MessageIO(aapsLogger, cmdBleIO, dataBleIO)

        peripheralOrNull.requestConnectionPriority(com.welie.blessed.ConnectionPriority.HIGH)

        cmdBleIO.hello()
        cmdBleIO.readyToRead()
        dataBleIO.readyToRead()
        _connectionWaitCond = null
    }

    @Synchronized
    fun disconnect(closeGatt: Boolean) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Blessed disconnecting closeGatt=$closeGatt")
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

    fun connectionState(): ConnectionState = connectionStateRef.get()

    fun establishSession(ltk: ByteArray, msgSeq: Byte, ids: Ids, eapSqn: ByteArray): EapSqn? {
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

    private fun waitForConnection(latch: CountDownLatch, cond: ConnectionWaitCondition): Boolean {
        return try {
            when {
                cond.timeoutMs != null -> latch.await(cond.timeoutMs!!, TimeUnit.MILLISECONDS)
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
                else -> false
            }
        } catch (e: InterruptedException) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Interrupted while waiting for connection")
            latch.await(0, TimeUnit.MILLISECONDS)
            false
        } catch (e: ConnectException) {
            throw e
        }
    }

    override fun onConnectionLost(status: Int) {
        aapsLogger.info(LTag.PUMPBTCOMM, "Blessed connection lost with status: $status")
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
    }
}
