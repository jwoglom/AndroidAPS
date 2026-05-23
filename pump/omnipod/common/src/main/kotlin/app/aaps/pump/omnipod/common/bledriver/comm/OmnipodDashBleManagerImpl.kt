package app.aaps.pump.omnipod.common.bledriver.comm

import android.bluetooth.BluetoothManager
import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.BusyException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.CouldNotSendCommandException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.FailedToConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.MessageIOException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.NotConnectedException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.SessionEstablishmentException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.scan.PodScanner
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnection
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnectionFactory
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.device.BleDeviceManager
import app.aaps.pump.omnipod.common.bledriver.comm.pair.LTKExchanger
import app.aaps.pump.omnipod.common.bledriver.comm.session.CommandAckError
import app.aaps.pump.omnipod.common.bledriver.comm.session.CommandReceiveError
import app.aaps.pump.omnipod.common.bledriver.comm.session.CommandReceiveSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.session.CommandSendErrorConfirming
import app.aaps.pump.omnipod.common.bledriver.comm.session.CommandSendErrorSending
import app.aaps.pump.omnipod.common.bledriver.comm.session.CommandSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.session.Connected
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionState
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionWaitCondition
import app.aaps.pump.omnipod.common.bledriver.comm.session.EapSqn
import app.aaps.pump.omnipod.common.bledriver.comm.session.NotConnected
import app.aaps.pump.omnipod.common.bledriver.comm.session.Session
import app.aaps.pump.omnipod.common.bledriver.event.PodEvent
import app.aaps.pump.omnipod.common.bledriver.metrics.DashMetrics
import app.aaps.pump.omnipod.common.bledriver.metrics.EnvProbe
import app.aaps.pump.omnipod.common.bledriver.metrics.SessionContextHolder
import app.aaps.pump.omnipod.common.bledriver.pod.command.base.Command
import app.aaps.pump.omnipod.common.bledriver.pod.response.Response
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import io.reactivex.rxjava3.core.Observable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class OmnipodDashBleManagerImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val podState: OmnipodDashPodStateManager,
    private val config: Config,
    private val bleConnectionFactory: BleConnectionFactory,
    private val bleDeviceManager: BleDeviceManager,
    private val context: Context,
    private val receiverStatusStore: ReceiverStatusStore,
) : OmnipodDashBleManager {

    private val busy = AtomicBoolean(false)
    private var connection: BleConnection? = null
    private val ids = Ids(podState)

    override fun sendCommand(cmd: Command, responseType: KClass<out Response>): Observable<PodEvent> =
        Observable.create { emitter ->
            if (!busy.compareAndSet(false, true)) {
                throw BusyException()
            }
            try {
                val session = assertSessionEstablished()

                emitter.onNext(PodEvent.CommandSending(cmd))
                connection?.requestRssiSample("pre_cmd")
                DashMetrics.commandAttempt(
                    commandType = cmd.commandType.name,
                    seq = cmd.sequenceNumber.toInt(),
                    expectedResponseType = responseType.simpleName
                )
                /*
                    if (Random.nextBoolean()) {
                        // XXX use this to test "failed to confirm" commands
                        emitter.onNext(PodEvent.CommandSendNotConfirmed(cmd))
                        emitter.tryOnError(MessageIOException("XXX random failure to test unconfirmed commands"))
                        return@create
                    }
    */
                when (session.sendCommand(cmd)) {
                    is CommandSendErrorSending -> {
                        DashMetrics.commandResult("send_error_sending")
                        emitter.tryOnError(CouldNotSendCommandException())
                        return@create
                    }

                    is CommandSendSuccess ->
                        emitter.onNext(PodEvent.CommandSent(cmd))

                    is CommandSendErrorConfirming ->
                        emitter.onNext(PodEvent.CommandSendNotConfirmed(cmd))
                }
                DashMetrics.commandSendDone()
                /*
                if (Random.nextBoolean()) {
                    // XXX use this commands confirmed with success
                    emitter.tryOnError(MessageIOException("XXX random failure to test unconfirmed commands"))
                    return@create
                }*/
                when (val readResult = session.readAndAckResponse()) {
                    is CommandReceiveSuccess -> {
                        DashMetrics.commandResult("ok")
                        emitter.onNext(PodEvent.ResponseReceived(cmd, readResult.result))
                    }

                    is CommandAckError       -> {
                        DashMetrics.commandResult("ack_error")
                        emitter.onNext(PodEvent.ResponseReceived(cmd, readResult.result))
                    }

                    is CommandReceiveError   -> {
                        DashMetrics.commandResult("receive_error")
                        emitter.tryOnError(MessageIOException("Could not read response: $readResult"))
                        return@create
                    }
                }
                emitter.onComplete()
            } catch (ex: Exception) {
                if (SessionContextHolder.current()?.commandInFlight != null) {
                    DashMetrics.commandResult("exception")
                }
                DashMetrics.explicitDisconnect("error_recovery", false)
                endMetricsSession("error_recovery")
                disconnectInternal(false)
                emitter.tryOnError(ex)
            } finally {
                busy.set(false)
            }
        }

    private fun assertSessionEstablished(): Session {
        val conn = assertConnected()
        return conn.session
            ?: throw NotConnectedException("Missing session")
    }

    override fun getStatus(): ConnectionState {
        return connection?.connectionState()
            ?: NotConnected
    }

    // used for sync connections
    override fun connect(timeoutMs: Long): Observable<PodEvent> {
        return connect(ConnectionWaitCondition(timeoutMs = timeoutMs))
    }

    // used for async connections
    override fun connect(stopConnectionLatch: CountDownLatch): Observable<PodEvent> {
        return connect(ConnectionWaitCondition(stopConnection = stopConnectionLatch))
    }

    private fun connect(connectionWaitCond: ConnectionWaitCondition): Observable<PodEvent> = Observable
        .create { emitter ->
            if (!busy.compareAndSet(false, true)) {
                throw BusyException()
            }
            try {
                startMetricsSession("connect")
                emitter.onNext(PodEvent.BluetoothConnecting)

                val podAddress =
                    podState.bluetoothAddress
                        ?: throw FailedToConnectException("Missing bluetoothAddress, activate the pod first")

                if (!bleDeviceManager.isBluetoothAvailable()) {
                    throw ConnectException("Bluetooth not available")
                }

                if (!bleDeviceManager.ensureBondedIfRequired(podAddress)) {
                    throw ConnectException("Bluetooth not available or bonding failed")
                }

                val conn = connection
                    ?: bleConnectionFactory.createConnection(podAddress)
                connection = conn
                if (conn.connectionState() is Connected && conn.session != null) {
                    emitter.onNext(PodEvent.AlreadyConnected(podAddress))
                    endMetricsSession("already_connected")
                    emitter.onComplete()
                    return@create
                }

                conn.connect(connectionWaitCond)

                emitter.onNext(PodEvent.BluetoothConnected(podAddress))
                emitter.onNext(PodEvent.EstablishingSession)
                establishSession(1.toByte())
                emitter.onNext(PodEvent.Connected)

                emitter.onComplete()
            } catch (ex: Exception) {
                DashMetrics.explicitDisconnect("error_recovery", false)
                endMetricsSession("error_recovery")
                disconnectInternal(false)
                emitter.tryOnError(ex)
            } finally {
                busy.set(false)
            }
        }

    private fun establishSession(msgSeq: Byte) {
        DashMetrics.setLifecycle("session_setup")
        val tStart = System.nanoTime()
        var resyncCount = 0
        var outcome: String = "success"
        try {
            val conn = assertConnected()

            val ltk = assertPaired()

            val eapSqn = podState.increaseEapAkaSequenceNumber()

            var newSqn = conn.establishSession(ltk, msgSeq, ids, eapSqn)

            if (newSqn != null) {
                resyncCount++
                DashMetrics.eapResync("first", EapSqn(eapSqn).toLong(), newSqn.toLong())
                aapsLogger.info(LTag.PUMPBTCOMM, "Updating EAP SQN to: $newSqn")
                podState.eapAkaSequenceNumber = newSqn.toLong()
                val secondSqn = podState.increaseEapAkaSequenceNumber()
                newSqn = conn.establishSession(ltk, msgSeq, ids, secondSqn)
                if (newSqn != null) {
                    resyncCount++
                    DashMetrics.eapResync("second", EapSqn(secondSqn).toLong(), newSqn.toLong())
                    outcome = "resync_twice"
                    throw SessionEstablishmentException("Received resynchronization SQN for the second time")
                }
                outcome = "resync_once"
            }
            podState.successfulConnections++
            podState.commitEapAkaSequenceNumber()
        } catch (ex: SessionEstablishmentException) {
            if (outcome == "success") {
                outcome = classifyEapAkaError(ex.message)
            }
            throw ex
        } finally {
            val durationMs = (System.nanoTime() - tStart) / 1_000_000L
            DashMetrics.eapAkaPhase(durationMs, outcome, resyncCount)
            if (outcome == "success" || outcome == "resync_once") {
                DashMetrics.sessionReady((System.nanoTime() - (SessionContextHolder.current()?.tStartMonoNs ?: System.nanoTime())) / 1_000_000L)
                connection?.requestRssiSample("ready")
            }
        }
    }

    private fun classifyEapAkaError(message: String?): String = when {
        message == null                              -> "timeout"
        message.contains("RES mismatch")             -> "res_mismatch"
        message.contains("MacS mismatch")            -> "macs_mismatch"
        message.contains("incorrect EAP identifier") -> "identifier_mismatch"
        message.contains("Could not")                -> "timeout"
        else                                         -> "error"
    }

    private fun assertPaired(): ByteArray {
        return podState.ltk
            ?: throw FailedToConnectException("Missing LTK, activate the pod first")
    }

    private fun assertConnected(): BleConnection {
        return connection
            ?: throw FailedToConnectException("connection lost")
    }

    override fun pairNewPod(): Observable<PodEvent> = Observable.create { emitter ->
        if (!busy.compareAndSet(false, true)) {
            throw BusyException()
        }
        try {
            if (podState.ltk != null) {
                emitter.onNext(PodEvent.AlreadyPaired)
                emitter.onComplete()
                return@create
            }
            startMetricsSession("pair")
            aapsLogger.info(LTag.PUMPBTCOMM, "Starting new pod activation")

            emitter.onNext(PodEvent.Scanning)
            val podScanner = bleDeviceManager.createPodScanner()
            val podAddress = podScanner.scanForPod(
                PodScanner.SCAN_FOR_SERVICE_UUID,
                PodScanner.POD_ID_NOT_ACTIVATED
            ).address
            podState.bluetoothAddress = podAddress

            emitter.onNext(PodEvent.BluetoothConnecting)
            val conn = bleConnectionFactory.createConnection(podAddress)
            connection = conn
            conn.connect(ConnectionWaitCondition(timeoutMs = BleConnection.DEFAULT_CONNECT_TIMEOUT_MS))
            emitter.onNext(PodEvent.BluetoothConnected(podAddress))

            emitter.onNext(PodEvent.Pairing)
            val mIO = conn.msgIO ?: throw ConnectException("Connection lost")
            val ltkExchanger = LTKExchanger(
                aapsLogger,
                config,
                mIO,
                ids,
            )
            val pairResult = ltkExchanger.negotiateLTK()
            emitter.onNext(PodEvent.Paired(ids.podId))
            podState.updateFromPairing(ids.podId, pairResult)
            if (config.DEBUG) {
                aapsLogger.info(LTag.PUMPCOMM, "Got LTK: ${pairResult.ltk.toHex()}")
            }
            emitter.onNext(PodEvent.EstablishingSession)
            establishSession(pairResult.msgSeq)
            podState.successfulConnections++
            emitter.onNext(PodEvent.Connected)
            emitter.onComplete()
        } catch (ex: Exception) {
            DashMetrics.explicitDisconnect("error_recovery", false)
            endMetricsSession("error_recovery")
            disconnectInternal(false)
            emitter.tryOnError(ex)
        } finally {
            busy.set(false)
        }
    }

    override fun disconnect(closeGatt: Boolean) {
        DashMetrics.explicitDisconnect("external_request", closeGatt)
        endMetricsSession("clean_finish")
        disconnectInternal(closeGatt)
    }

    private fun disconnectInternal(closeGatt: Boolean) {
        connection?.disconnect(closeGatt)
            ?: aapsLogger.info(LTag.PUMPBTCOMM, "Trying to disconnect a null connection")
    }

    private fun startMetricsSession(reason: String) {
        val now = System.currentTimeMillis()
        val priorSecs = SessionContextHolder.lastSessionEndEpochMs?.let { (now - it) / 1000L }
        val podAgeMin = podState.activationTime?.let { (now - it) / 60_000L }
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        DashMetrics.sessionStart(
            reason = reason,
            priorSecondsSinceLastSession = priorSecs,
            btAdapterEnabled = bleDeviceManager.isBluetoothAvailable(),
            priorSessionOutcome = SessionContextHolder.lastSessionEndReason,
            podAgeMinutes = podAgeMin,
            batteryLevelPct = receiverStatusStore.batteryLevel.takeIf { it in 0..100 },
            appState = EnvProbe.appState(context),
            podUniqueIdAtStart = podState.uniqueId,
            bluetoothAddressAtStart = podState.bluetoothAddress,
            powerSaveMode = EnvProbe.powerSaveMode(context),
            deviceIdleMode = EnvProbe.deviceIdleMode(context),
            locationServicesOn = EnvProbe.locationServicesOn(context),
            bluetoothAdapterState = EnvProbe.bluetoothAdapterState(adapter),
            isCharging = receiverStatusStore.isCharging,
        )
    }

    private fun endMetricsSession(reason: String, hciStatus: Int? = null) {
        DashMetrics.sessionEnd(
            endReason = reason,
            hciStatusAtDisconnect = hciStatus,
            successfulConnections = podState.successfulConnections,
            connectionAttempts = podState.connectionAttempts,
            eapAkaSequenceNumber = podState.eapAkaSequenceNumber
        )
    }

    override fun removeBond() {
        val address = podState.bluetoothAddress
        if (address == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "removeBond: MAC address not found")
            return
        }
        bleDeviceManager.removeBond(address)
    }

    companion object {

        const val CONTROLLER_ID = 4242 // TODO read from preferences or somewhere else.
    }
}
