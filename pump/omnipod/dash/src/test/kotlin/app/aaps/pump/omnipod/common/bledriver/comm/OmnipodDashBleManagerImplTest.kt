package app.aaps.pump.omnipod.common.bledriver.comm

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.CouldNotSendCommandException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.SessionEstablishmentException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.device.BleDeviceManager
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.scan.BleDiscoveredDevice
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.scan.PodScanner
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnection
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.session.BleConnectionFactory
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.comm.pair.LTKExchanger
import app.aaps.pump.omnipod.common.bledriver.comm.pair.PairResult
import app.aaps.pump.omnipod.common.bledriver.comm.session.CommandReceiveSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.session.CommandSendErrorConfirming
import app.aaps.pump.omnipod.common.bledriver.comm.session.CommandSendErrorSending
import app.aaps.pump.omnipod.common.bledriver.comm.session.CommandSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionState
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionWaitCondition
import app.aaps.pump.omnipod.common.bledriver.comm.session.Connected
import app.aaps.pump.omnipod.common.bledriver.comm.session.EapSqn
import app.aaps.pump.omnipod.common.bledriver.comm.session.NotConnected
import app.aaps.pump.omnipod.common.bledriver.comm.session.Session
import app.aaps.pump.omnipod.common.bledriver.event.PodEvent
import app.aaps.pump.omnipod.common.bledriver.pod.command.GetVersionCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.base.Command
import app.aaps.pump.omnipod.common.bledriver.pod.response.Response
import app.aaps.pump.omnipod.common.bledriver.pod.response.VersionResponse
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManagerImpl
import app.aaps.pump.omnipod.common.keys.DashStringNonPreferenceKey
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Observable
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

class OmnipodDashBleManagerImplTest : TestBase() {

    @Mock lateinit var config: Config
    @Mock lateinit var preferences: Preferences

    private lateinit var podState: OmnipodDashPodStateManagerImpl
    private lateinit var connection: ScriptedBleConnection
    private lateinit var connectionFactory: ScriptedBleConnectionFactory
    private lateinit var deviceManager: ScriptedBleDeviceManager
    private lateinit var bleManager: OmnipodDashBleManagerImpl

    @BeforeEach
    fun setUp() {
        whenever(preferences.getIfExists(DashStringNonPreferenceKey.PodState)).thenReturn(null)
        whenever(config.DEBUG).thenReturn(false)

        podState = OmnipodDashPodStateManagerImpl(aapsLogger, rxBus, preferences)
        connection = ScriptedBleConnection()
        connectionFactory = ScriptedBleConnectionFactory(connection)
        deviceManager = ScriptedBleDeviceManager()
        bleManager = OmnipodDashBleManagerImpl(
            aapsLogger = aapsLogger,
            podState = podState,
            config = config,
            bleConnectionFactory = connectionFactory,
            bleDeviceManager = deviceManager
        )
    }

    @Test
    fun `connect emits lifecycle events and establishes session`() {
        prepareStoredPod()
        connection.session = mock()
        connection.establishSessionResults.addLast(null)

        val observer = bleManager.connect(timeoutMs = 1234L).test()

        observer.assertComplete()
        observer.assertNoErrors()
        assertThat(observer.values()).hasSize(4)
        assertThat(observer.values()[0]).isSameInstanceAs(PodEvent.BluetoothConnecting)
        assertThat((observer.values()[1] as PodEvent.BluetoothConnected).bluetoothAddress).isEqualTo(TEST_ADDRESS)
        assertThat(observer.values()[2]).isSameInstanceAs(PodEvent.EstablishingSession)
        assertThat(observer.values()[3]).isSameInstanceAs(PodEvent.Connected)
        assertThat(connectionFactory.createdAddresses).containsExactly(TEST_ADDRESS)
        assertThat(connection.connectCalls).isEqualTo(1)
        assertThat(connection.connectWaitConditions.single().timeoutMs).isEqualTo(1234L)
        assertThat(connection.establishSessionCalls).hasSize(1)
        assertThat(podState.successfulConnections).isEqualTo(1)
        assertThat(podState.eapAkaSequenceNumber).isEqualTo(2L)
        assertThat(bleManager.getStatus()).isSameInstanceAs(Connected)
    }

    @Test
    fun `connect reuses an existing active connection`() {
        prepareStoredPod()
        connection.state = Connected
        connection.session = mock()

        val observer = bleManager.connect().test()

        observer.assertComplete()
        observer.assertNoErrors()
        assertThat(observer.values()).hasSize(2)
        assertThat(observer.values()[0]).isSameInstanceAs(PodEvent.BluetoothConnecting)
        assertThat((observer.values()[1] as PodEvent.AlreadyConnected).bluetoothAddress).isEqualTo(TEST_ADDRESS)
        assertThat(connection.connectCalls).isEqualTo(0)
        assertThat(connection.establishSessionCalls).isEmpty()
    }

    @Test
    fun `connect retries once when session establishment requests resynchronization`() {
        prepareStoredPod()
        connection.session = mock()
        connection.establishSessionResults.addLast(EapSqn(20))
        connection.establishSessionResults.addLast(null)

        val observer = bleManager.connect().test()

        observer.assertComplete()
        observer.assertNoErrors()
        assertThat(connection.establishSessionCalls).hasSize(2)
        assertThat(connection.establishSessionCalls[0].eapSqn.toLong()).isEqualTo(2L)
        assertThat(connection.establishSessionCalls[1].eapSqn.toLong()).isEqualTo(21L)
        assertThat(podState.eapAkaSequenceNumber).isEqualTo(21L)
        assertThat(podState.successfulConnections).isEqualTo(1)
    }

    @Test
    fun `connect fails after a second resynchronization request`() {
        prepareStoredPod()
        connection.session = mock()
        connection.establishSessionResults.addLast(EapSqn(20))
        connection.establishSessionResults.addLast(EapSqn(21))

        val observer = bleManager.connect().test()

        observer.assertNotComplete()
        observer.assertError(SessionEstablishmentException::class.java)
        assertThat(connection.disconnectCalls).containsExactly(false)
        assertThat(podState.successfulConnections).isEqualTo(0)
        assertThat(bleManager.getStatus()).isSameInstanceAs(NotConnected)
    }

    @Test
    fun `sendCommand emits sending sent and response events on success`() {
        val session = mock<Session>()
        val command = testCommand(sequenceNumber = 5)
        val response = testResponse()
        doReturn(CommandSendSuccess).whenever(session).sendCommand(command)
        doReturn(CommandReceiveSuccess(response)).whenever(session).readAndAckResponse()
        primeConnectedSession(session)

        val observer = bleManager.sendCommand(command, VersionResponse::class).test()

        observer.assertComplete()
        observer.assertNoErrors()
        assertThat(observer.values()).hasSize(3)
        assertThat(observer.values()[0]).isInstanceOf(PodEvent.CommandSending::class.java)
        assertThat(observer.values()[1]).isInstanceOf(PodEvent.CommandSent::class.java)
        val responseEvent = observer.values()[2] as PodEvent.ResponseReceived
        assertThat(responseEvent.command).isEqualTo(command)
        assertThat(responseEvent.response).isEqualTo(response)
        assertThat(connection.disconnectCalls).isEmpty()
    }

    @Test
    fun `sendCommand maps send failures to CouldNotSendCommandException and disconnects`() {
        val session = mock<Session>()
        val command = testCommand(sequenceNumber = 6)
        doReturn(CommandSendErrorSending("send failed")).whenever(session).sendCommand(command)
        primeConnectedSession(session)

        val observer = bleManager.sendCommand(command, VersionResponse::class).test()

        observer.assertNotComplete()
        observer.assertError(CouldNotSendCommandException::class.java)
        assertThat(observer.values()).hasSize(1)
        assertThat(observer.values().single()).isInstanceOf(PodEvent.CommandSending::class.java)
        assertThat(connection.disconnectCalls).containsExactly(false)
    }

    @Test
    fun `sendCommand surfaces unconfirmed sends but still forwards the response when one arrives`() {
        val session = mock<Session>()
        val command = testCommand(sequenceNumber = 7)
        val response = testResponse()
        doReturn(CommandSendErrorConfirming("confirm failed")).whenever(session).sendCommand(command)
        doReturn(CommandReceiveSuccess(response)).whenever(session).readAndAckResponse()
        primeConnectedSession(session)

        val observer = bleManager.sendCommand(command, VersionResponse::class).test()

        observer.assertComplete()
        observer.assertNoErrors()
        assertThat(observer.values()).hasSize(3)
        assertThat(observer.values()[0]).isInstanceOf(PodEvent.CommandSending::class.java)
        assertThat(observer.values()[1]).isInstanceOf(PodEvent.CommandSendNotConfirmed::class.java)
        assertThat(observer.values()[2]).isInstanceOf(PodEvent.ResponseReceived::class.java)
    }

    @Test
    fun `pairNewPod scans connects negotiates ltk and stores pod state`() {
        val pairedLtk = ByteArray(16) { (it + 1).toByte() }
        val pairResult = PairResult(pairedLtk, 9.toByte())
        connection.msgIO = mock<MessageIO>()
        connection.session = mock()
        connection.establishSessionResults.addLast(null)

        mockConstruction(LTKExchanger::class.java) { mock, _ ->
            doReturn(pairResult).whenever(mock).negotiateLTK()
        }.use {
            val observer = bleManager.pairNewPod().test()

            observer.awaitDone(1, TimeUnit.SECONDS)
            observer.assertComplete()
            observer.assertNoErrors()
            assertThat(observer.values()).hasSize(7)
            assertThat(observer.values()[0]).isSameInstanceAs(PodEvent.Scanning)
            assertThat(observer.values()[1]).isSameInstanceAs(PodEvent.BluetoothConnecting)
            assertThat((observer.values()[2] as PodEvent.BluetoothConnected).bluetoothAddress).isEqualTo(TEST_ADDRESS)
            assertThat(observer.values()[3]).isSameInstanceAs(PodEvent.Pairing)
            val pairedEvent = observer.values()[4] as PodEvent.Paired
            assertThat(observer.values()[5]).isSameInstanceAs(PodEvent.EstablishingSession)
            assertThat(observer.values()[6]).isSameInstanceAs(PodEvent.Connected)
            assertThat(deviceManager.scannedAddresses).containsExactly(TEST_ADDRESS)
            assertThat(connectionFactory.createdAddresses).containsExactly(TEST_ADDRESS)
            assertThat(podState.bluetoothAddress).isEqualTo(TEST_ADDRESS)
            assertThat(podState.ltk).isEqualTo(pairedLtk)
            assertThat(podState.uniqueId).isEqualTo(pairedEvent.uniqueId.toLong())
            assertThat(connection.connectCalls).isEqualTo(1)
        }
    }

    private fun prepareStoredPod() {
        podState.bluetoothAddress = TEST_ADDRESS
        podState.ltk = ByteArray(16) { 0x01.toByte() }
    }

    private fun primeConnectedSession(session: Session) {
        prepareStoredPod()
        connection.state = Connected
        connection.session = session

        bleManager.connect().test().assertComplete().assertNoErrors()
    }

    private fun testCommand(sequenceNumber: Short): Command =
        GetVersionCommand.Builder()
            .setUniqueId(GetVersionCommand.DEFAULT_UNIQUE_ID)
            .setSequenceNumber(sequenceNumber)
            .build()

    private fun testResponse(): Response =
        VersionResponse(Hex.decodeHex("0115040A00010300040208146CC1000954D400FFFFFFFF"))

    private data class EstablishSessionCall(
        val ltk: ByteArray,
        val msgSeq: Byte,
        val ids: Ids,
        val eapSqn: EapSqn
    )

    private class FakeBleDiscoveredDevice(
        override val address: String
    ) : BleDiscoveredDevice

    private class ScriptedPodScanner(
        private val address: String
    ) : PodScanner {
        override fun scanForPod(serviceUUID: String?, podID: Long): BleDiscoveredDevice {
            return FakeBleDiscoveredDevice(address)
        }
    }

    private class ScriptedBleDeviceManager(
        var bluetoothAvailable: Boolean = true,
        var ensureBondedResult: Boolean = true,
        var scannerAddress: String = TEST_ADDRESS
    ) : BleDeviceManager {
        val removedBondAddresses = mutableListOf<String>()
        val scannedAddresses = mutableListOf<String>()

        override fun ensureBondedIfRequired(podAddress: String): Boolean = ensureBondedResult

        override fun removeBond(podAddress: String) {
            removedBondAddresses += podAddress
        }

        override fun isBluetoothAvailable(): Boolean = bluetoothAvailable

        override fun createPodScanner(): PodScanner {
            scannedAddresses += scannerAddress
            return ScriptedPodScanner(scannerAddress)
        }
    }

    private class ScriptedBleConnectionFactory(
        private val connection: ScriptedBleConnection
    ) : BleConnectionFactory {
        val createdAddresses = mutableListOf<String>()

        override fun createConnection(podAddress: String): BleConnection {
            createdAddresses += podAddress
            return connection
        }
    }

    private class ScriptedBleConnection : BleConnection {
        override var session: Session? = null
        override var msgIO: MessageIO? = null

        var state: ConnectionState = NotConnected
        var connectCalls: Int = 0
        val connectWaitConditions = mutableListOf<ConnectionWaitCondition>()
        val disconnectCalls = mutableListOf<Boolean>()
        val establishSessionResults = ArrayDeque<EapSqn?>()
        val establishSessionCalls = mutableListOf<EstablishSessionCall>()

        override fun connect(connectionWaitCond: ConnectionWaitCondition) {
            connectCalls++
            connectWaitConditions += connectionWaitCond
            state = Connected
        }

        override fun disconnect(closeGatt: Boolean) {
            disconnectCalls += closeGatt
            state = NotConnected
        }

        override fun connectionState(): ConnectionState = state

        override fun establishSession(ltk: ByteArray, msgSeq: Byte, ids: Ids, eapSqn: ByteArray): EapSqn? {
            establishSessionCalls += EstablishSessionCall(ltk, msgSeq, ids, EapSqn(eapSqn))
            return if (establishSessionResults.isEmpty()) {
                null
            } else {
                establishSessionResults.removeFirst()
            }
        }
    }

    private companion object {
        const val TEST_ADDRESS = "AA:BB:CC:DD:EE:FF"
    }
}
