package app.aaps.pump.tandem.common.comm.history

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.tandem.common.comm.ui.TandemUICommunication
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogStatusRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogStreamResponse

// TODO not AAPS Ready
class HistoryRetriever constructor(
    val communication: TandemUICommunication,
    val aapsLogger: AAPSLogger

    ) {

    //var tandemUIDataStore = LocalTandemDataStore.current

    var lastDbRecord: Integer? = null
    var firstDbRecord: Integer? = null

    val RECORDS_RETRIEVAL_AMOUNT = 1000

    var lastPumpRecord: Long? = null
    var firstPumpRecord: Long? = null

    val TAG = LTag.PUMPCOMM

    init {
        communication.historyRetriever = this
    }

    //var status =

    fun startDataRetrieval()  {
        readStatusFromDatabase()
        communication.sendCommand(HistoryLogStatusRequest())
    }

    private fun readStatusFromDatabase() {
        // TODO HST read last record

        // HistoryLogRequest
    }


    var currentRequest: HistoryRequestInfo?  = null
    var listOfRequests = ArrayDeque<HistoryRequestInfo>()

    var listOfMissingItemsInChunk = ArrayDeque<HistoryRequestInfo>()


    fun receivedStatus(message: HistoryLogStatusResponse) {
        this.lastPumpRecord = message.lastSequenceNum
        this.firstPumpRecord = message.firstSequenceNum

        aapsLogger.error(TAG, "Got LogStatusResponse: ${message}")

        aapsLogger.error(TAG, "Start Data Retriaval: N/A")

        // determine if empty, then get
        // TODO comprae to history,
        //    if first reading get FRE
        //    not: get All Chunks from last retreival to now

        this.currentRequest = HistoryRequestInfo(startSequence = message.lastSequenceNum-200,
                                                 endSequence = message.lastSequenceNum,
                                                 numberOfLogs = 200)


        //this.communication.sendCommand(HistoryLogRequest(message.lastSequenceNum-200, 200))

        // TODO prepare all requests
        listOfRequests.add(currentRequest!!)




        executeNextLogGet(listOfRequests)

    }

//    fun prepareLogGet(queue: ArrayDeque<HistoryRequestInfo>) {
//
//        //executeLogGet(currentRequest!!)
//    }


    fun executeNextLogGet(queue: ArrayDeque<HistoryRequestInfo>) {
        currentRequest = queue.removeFirst()
        this.communication.sendCommand(HistoryLogRequest(currentRequest!!.startSequence, currentRequest!!.numberOfLogs))
    }


    fun receivedLogStreamResponse(message: HistoryLogStreamResponse) {
        aapsLogger.warn(TAG, "Received from Stream: ${message}")

        this.currentRequest!!.historyLogMap.putAll(message.historyLogs.associateBy { it.sequenceNum })

        aapsLogger.error(TAG, "Number of Logs: ${currentRequest!!.historyLogMap.size}")

        if (this.currentRequest!!.chunkComplete()) {
            processChunkComplete()
        }

        enableLastMessageWatchdog()

    }


    private fun processChunkComplete() {

        aapsLogger.error(TAG, "Chunk Complete: ${currentRequest!!.historyLogMap.size}")

        // TODO add to db

        if (this.currentRequest!!.chunkHasAllItems()) {
            aapsLogger.error(TAG, "Chunk Has All Items: ${currentRequest!!.historyLogMap.size}")
        } else {
            aapsLogger.error(TAG, "Chunk Has MISSING Items: ${currentRequest!!.historyLogMap.size}")
            // TODO add missing items





        }


        disableLastMessageWatchdog()


        if (listOfMissingItemsInChunk.size!=0) {
            // TODO start new retrieval

        } else {


            if (listOfRequests.size!=0) {
                // TODO dp new retreival
            }
        }

        // check if first and last received = yes chunk ready
        // the add to db
        // determine missing records (if number != 200)
        //    no:  next chuck
        //    yes: prepare missing records chunks - MRC
        //         repeat retrieval until all MRC here


    }



    private fun disableLastMessageWatchdog() {
        // TODO("Not yet implemented")
    }

    private fun enableLastMessageWatchdog() {
        // TODO when message is received we note the time and wait in special thread if timeout is reached
        //    if watchdog already exists, we just extend time

    }

    fun receivedLogResponse(message: HistoryLogResponse) {
        aapsLogger.error(TAG, "Received LogResponse: ${message}")
    }


}

class HistoryRequestInfo(
    val startSequence: Long,
    val endSequence: Long,
    val numberOfLogs: Int,
    val historyLogMap: MutableMap<Long,HistoryLog> = mutableMapOf()
) {
    fun chunkComplete(): Boolean {
        return historyLogMap.size == numberOfLogs ||
                ( historyLogMap.contains(startSequence) && historyLogMap.contains(endSequence) )
    }

    fun chunkHasAllItems(): Boolean {
        return historyLogMap.size == numberOfLogs
    }
}