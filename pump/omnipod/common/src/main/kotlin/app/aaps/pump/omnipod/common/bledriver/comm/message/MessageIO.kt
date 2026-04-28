package app.aaps.pump.omnipod.common.bledriver.comm.message

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommand
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandAbort
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandCTS
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandFail
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandNack
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandRTS
import app.aaps.pump.omnipod.common.bledriver.comm.command.BleCommandSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.io.BleConfirmError
import app.aaps.pump.omnipod.common.bledriver.comm.io.BleConfirmIncorrectData
import app.aaps.pump.omnipod.common.bledriver.comm.io.BleConfirmSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.io.BleSendErrorConfirming
import app.aaps.pump.omnipod.common.bledriver.comm.io.BleSendErrorSending
import app.aaps.pump.omnipod.common.bledriver.comm.io.BleSendResult
import app.aaps.pump.omnipod.common.bledriver.comm.io.BleSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.CmdBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.DataBleIO
import app.aaps.pump.omnipod.common.bledriver.comm.packet.BlePacket
import app.aaps.pump.omnipod.common.bledriver.comm.packet.PayloadJoiner
import app.aaps.pump.omnipod.common.bledriver.comm.packet.PayloadSplitter
import app.aaps.pump.omnipod.common.bledriver.metrics.DashMetrics

sealed class MessageSendResult
object MessageSendSuccess : MessageSendResult()
data class MessageSendErrorSending(val msg: String, val cause: Throwable? = null) : MessageSendResult() {
    constructor(e: BleSendResult) : this("Could not send packet: $e")
}

data class MessageSendErrorConfirming(val msg: String, val cause: Throwable? = null) : MessageSendResult() {
    constructor(e: BleSendResult) : this("Could not confirm packet: $e")
}

sealed class PacketReceiveResult
data class PacketReceiveSuccess(val payload: ByteArray) : PacketReceiveResult()
data class PacketReceiveError(val msg: String) : PacketReceiveResult()

class MessageIO(
    private val aapsLogger: AAPSLogger,
    private val cmdBleIO: CmdBleIO,
    private val dataBleIO: DataBleIO,
) {

    private val receivedOutOfOrder = LinkedHashMap<Byte, ByteArray>()
    var maxMessageReadTries = 3
    var messageReadTries = 0

    @Suppress("ReturnCount")
    fun sendMessage(msg: MessagePacket): MessageSendResult {
        val tStart = System.nanoTime()
        var packetCount = 0
        var payloadBytes = 0
        var outcome: String = "in_progress"
        try {
            val foundRTS = cmdBleIO.flushIncomingQueue()
            if (foundRTS) {
                val receivedMessage = receiveMessage(false)
                aapsLogger.warn(LTag.PUMPBTCOMM, "sendMessage received message=$receivedMessage")
                throw IllegalStateException("Received message while trying to send")
            }
            dataBleIO.flushIncomingQueue()

            val rtsSendResult = cmdBleIO.sendAndConfirmPacket(BleCommandRTS.data)
            if (rtsSendResult is BleSendErrorSending) {
                outcome = "rts_send_failed"
                DashMetrics.rtsCtsFailure("rts_send", rtsSendResult.toString())
                return MessageSendErrorSending(rtsSendResult)
            }
            val expectCTS = cmdBleIO.expectCommandType(BleCommandCTS)
            if (expectCTS !is BleConfirmSuccess) {
                outcome = "cts_recv_failed"
                DashMetrics.rtsCtsFailure("cts_recv", expectCTS.toString())
                return MessageSendErrorSending(expectCTS.toString())
            }

            val payload = msg.asByteArray()
            payloadBytes = payload.size
            aapsLogger.debug(LTag.PUMPBTCOMM, "Sending message: ${payload.toHex()}")
            val splitter = PayloadSplitter(payload)
            val packets = splitter.splitInPackets()
            packetCount = packets.size

            for ((index, packet) in packets.withIndex()) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Sending DATA: ${packet.toByteArray().toHex()}")
                val sendResult = dataBleIO.sendAndConfirmPacket(packet.toByteArray())
                val ret = handleSendResult(sendResult, index, packets)
                if (ret !is MessageSendSuccess) {
                    outcome = if (ret is MessageSendErrorConfirming) "data_confirm_failed_last" else "data_send_failed"
                    return ret
                }
                val peek = peekForNack(index, packets)
                if (peek !is MessageSendSuccess) {
                    return if (index == packets.size - 1) {
                        outcome = "data_confirm_failed_last"
                        MessageSendErrorConfirming(peek.toString())
                    } else {
                        outcome = "data_send_failed"
                        MessageSendErrorSending(peek.toString())
                    }
                }
            }

            return when (val expectSuccess = cmdBleIO.expectCommandType(BleCommandSuccess)) {
                is BleConfirmSuccess       -> {
                    outcome = "success"
                    MessageSendSuccess
                }

                is BleConfirmError         -> {
                    outcome = "confirm_error"
                    MessageSendErrorConfirming("Error reading message confirmation: $expectSuccess")
                }

                is BleConfirmIncorrectData ->
                    when (val received = (BleCommand.parse((expectSuccess.payload)))) {
                        is BleCommandFail -> {
                            // this can happen if CRC does not match
                            outcome = "received_fail"
                            DashMetrics.crcMismatch("tx_pod_reported", null)
                            MessageSendErrorSending("Received FAIL after sending message")
                        }

                        else              -> {
                            outcome = "confirm_unknown"
                            MessageSendErrorConfirming("Received confirmation message: $received")
                        }
                    }
            }
        } finally {
            val ms = (System.nanoTime() - tStart) / 1_000_000L
            DashMetrics.messageSend(packetCount, payloadBytes, ms, outcome)
        }
    }

    @Suppress("ReturnCount")
    fun receiveMessage(readRTS: Boolean = true): MessagePacket? {
        val tStart = System.nanoTime()
        var packetCount = 0
        var payloadBytes = 0
        var outOfOrderCount = 0
        var outcome: String = "in_progress"
        try {
            if (readRTS) {
                val expectRTS = cmdBleIO.expectCommandType(BleCommandRTS, MESSAGE_READ_TIMEOUT_MS)
                if (expectRTS !is BleConfirmSuccess) {
                    outcome = "rts_recv_failed"
                    DashMetrics.rtsCtsFailure("rts_recv", expectRTS.toString())
                    aapsLogger.warn(LTag.PUMPBTCOMM, "Error reading RTS: $expectRTS")
                    return null
                }
            }

            val sendResult = cmdBleIO.sendAndConfirmPacket(BleCommandCTS.data)
            if (sendResult !is BleSendSuccess) {
                outcome = "cts_send_failed"
                DashMetrics.rtsCtsFailure("cts_send", sendResult.toString())
                aapsLogger.warn(LTag.PUMPBTCOMM, "Error sending CTS: $sendResult")
                return null
            }
            readReset()
            var expected: Byte = 0
            try {
                val firstPacket = expectBlePacket(0)
                if (firstPacket !is PacketReceiveSuccess) {
                    outcome = "first_packet_failed"
                    aapsLogger.warn(LTag.PUMPBTCOMM, "Error reading first packet:$firstPacket")
                    return null
                }
                packetCount = 1
                payloadBytes = firstPacket.payload.size
                val joiner = PayloadJoiner(firstPacket.payload)
                maxMessageReadTries = joiner.fullFragments * 2 + 2
                for (i in 1 until joiner.fullFragments + 1) {
                    expected++
                    val nackOnTimeout = !joiner.oneExtraPacket && i == joiner.fullFragments // last packet
                    val packet = expectBlePacket(expected, nackOnTimeout)
                    if (packet !is PacketReceiveSuccess) {
                        outcome = "packet_failed"
                        aapsLogger.warn(LTag.PUMPBTCOMM, "Error reading packet:$packet")
                        return null
                    }
                    packetCount++
                    payloadBytes += packet.payload.size
                    joiner.accumulate(packet.payload)
                }
                if (joiner.oneExtraPacket) {
                    expected++
                    val packet = expectBlePacket(expected, true)
                    if (packet !is PacketReceiveSuccess) {
                        outcome = "packet_failed"
                        aapsLogger.warn(LTag.PUMPBTCOMM, "Error reading packet:$packet")
                        return null
                    }
                    packetCount++
                    payloadBytes += packet.payload.size
                    joiner.accumulate(packet.payload)
                }
                val fullPayload = joiner.finalize()
                cmdBleIO.sendAndConfirmPacket(BleCommandSuccess.data)
                outcome = "success"
                return MessagePacket.parse(fullPayload)
            } catch (e: IncorrectPacketException) {
                outcome = "incorrect_packet"
                aapsLogger.warn(LTag.PUMPBTCOMM, "Received incorrect packet: $e")
                cmdBleIO.sendAndConfirmPacket(BleCommandAbort.data)
                return null
            } catch (e: CrcMismatchException) {
                outcome = "crc_mismatch"
                DashMetrics.crcMismatch("rx", null)
                aapsLogger.warn(LTag.PUMPBTCOMM, "CRC mismatch: $e")
                cmdBleIO.sendAndConfirmPacket(BleCommandFail.data)
                return null
            } finally {
                outOfOrderCount = receivedOutOfOrder.size
                readReset()
            }
        } finally {
            val ms = (System.nanoTime() - tStart) / 1_000_000L
            DashMetrics.messageReceive(packetCount, payloadBytes, ms, outcome, outOfOrderCount)
        }
    }

    private fun handleSendResult(sendResult: BleSendResult, index: Int, packets: List<BlePacket>): MessageSendResult {
        return when {
            sendResult is BleSendSuccess                                      ->
                MessageSendSuccess

            index == packets.size - 1 && sendResult is BleSendErrorConfirming ->
                MessageSendErrorConfirming("Error confirming last DATA packet $sendResult")

            else                                                              ->
                MessageSendErrorSending("Error sending DATA: $sendResult")
        }
    }

    private fun peekForNack(index: Int, packets: List<BlePacket>): MessageSendResult {
        val peekCmd = cmdBleIO.peekCommand()
            ?: return MessageSendSuccess

        return when (val receivedCmd = BleCommand.parse(peekCmd)) {
            is BleCommandNack -> {
                DashMetrics.nackPacket("received", receivedCmd.idx.toInt(), "pod_requested_resend")
                // // Consume NACK
                val received = cmdBleIO.receivePacket()
                if (received == null) {
                    MessageSendErrorSending(received.toString())
                } else {
                    val sendResult = dataBleIO.sendAndConfirmPacket(packets[receivedCmd.idx.toInt()].toByteArray())
                    handleSendResult(sendResult, index, packets)
                }
            }

            BleCommandSuccess -> {
                if (index == packets.size - 1)
                    MessageSendSuccess
                else
                    MessageSendErrorSending("Received SUCCESS before sending all the data. $index")
            }

            else              ->
                MessageSendErrorSending("Received unexpected command: ${peekCmd.toHex()}")
        }
    }

    @Suppress("ReturnCount")
    private fun expectBlePacket(index: Byte, nackOnTimeout: Boolean = false): PacketReceiveResult {
        receivedOutOfOrder[index]?.let {
            return PacketReceiveSuccess(it)
        }
        var packetTries = 0
        while (messageReadTries < maxMessageReadTries && packetTries < MAX_PACKET_READ_TRIES) {
            messageReadTries++
            packetTries++
            val received = dataBleIO.receivePacket()
            if (received == null || received.isEmpty()) {
                if (nackOnTimeout) {
                    DashMetrics.nackPacket("sent", index.toInt(), "timeout")
                    cmdBleIO.sendAndConfirmPacket(BleCommandNack(index).data)
                }
                aapsLogger.info(
                    LTag.PUMPBTCOMM,
                    "Error reading index: $index. Received: $received. NackOnTimeout: " +
                        "$nackOnTimeout"
                )
                continue
            }
            if (received[0] == index) {
                return PacketReceiveSuccess(received)
            }
            receivedOutOfOrder[received[0]] = received
            DashMetrics.nackPacket("sent", index.toInt(), "out_of_order")
            cmdBleIO.sendAndConfirmPacket(BleCommandNack(index).data)
        }
        return PacketReceiveError("Reached the maximum number tries to read a packet")
    }

    private fun readReset() {
        maxMessageReadTries = 3
        messageReadTries = 0
        receivedOutOfOrder.clear()
    }

    companion object {

        private const val MAX_PACKET_READ_TRIES = 4
        private const val MESSAGE_READ_TIMEOUT_MS = 5000.toLong()
    }
}
