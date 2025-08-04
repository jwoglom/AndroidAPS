package app.aaps.pump.tandem.common.comm.history

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.tandem.common.comm.ui.TandemUICommunication
import app.aaps.pump.tandem.common.data.history.HistoryRange
import app.aaps.pump.tandem.common.data.history.HistorySummaryDto
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.keys.TandemStringNonPreferenceKey
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogStatusRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogStreamResponse


/*
    How this works:
    FIRST READ
    - on first read we get min, max (HistoryLogStatusRequest) and create HistorySummary and save it
    - we start retreiving data: we will retrieve 5 x 200 = 1000 records per reading
                                            (reading will be done together with status every 5 minutes)
    - once reading is done we create HistoryRange and add it to missed range

    MAIN RULE
    - when we get data that surpasses 44 days we modify missedRanges and modifiedStartRecord

    SUBSEQUENT READS
    - read min, max (HistoryLogStatusRequest) and check if new records there
    - NO: read missedRanges and read next 1000 items
    - YES: read new data, and remaining range is used to read old data, if there is more than
            1000 new records add new missedRange or modify the current one

    - missedRanges need to be updated regularly...




 */



// TODO not AAPS Ready
class HistoryRetriever constructor(
    val communication: TandemUICommunication,
    val pumpStatus: TandemPumpStatus,
    val aapsLogger: AAPSLogger,
    val pumpUtil: TandemPumpUtil,
    val preferences: Preferences

    ) {

    //var tandemUIDataStore = LocalTandemDataStore.current

    var lastDbRecord: Integer? = null
    var firstDbRecord: Integer? = null

    val RECORDS_RETRIEVAL_AMOUNT = 1000
    val CHUNK_SIZE = 200




    //var lastPumpRecord: Long? = null
    //var firstPumpRecord: Long? = null

    val TAG = LTag.PUMPCOMM

    init {
        communication.historyRetriever = this
    }

    //var status =

    var isFirstReading = false
    var historySummaryDto : HistorySummaryDto? = null

    fun startDataRetrieval()  {
        val summaryData = preferences.get(TandemStringNonPreferenceKey.HistorySummaryData)
        if (summaryData.isBlank()) {
            isFirstReading = true
        } else {
            historySummaryDto = pumpUtil.gsonRegular.fromJson(summaryData, HistorySummaryDto::class.java)
        }

        communication.sendCommand(HistoryLogStatusRequest())
    }




    // fun startDataRetrieval()  {
    //     readStatusFromDatabase()
    //     communication.sendCommand(HistoryLogStatusRequest())
    // }

    // private fun readStatusFromDatabase() {
    //     // TODO HST read last record
    //
    //     // HistoryLogRequest
    // }


    fun receivedStatus(message: HistoryLogStatusResponse) {
        // this.lastPumpRecord = message.lastSequenceNum
        // this.firstPumpRecord = message.firstSequenceNum

        aapsLogger.error(TAG, "HST: Got LogStatusResponse: ${message}")

        val listOfChunks: MutableList<HistoryRequestInfo> = mutableListOf()

        if (historySummaryDto==null) {

            aapsLogger.error(TAG, "HST: First read")

            var remainingRange = HistoryRange(message.firstSequenceNum, message.lastSequenceNum-RECORDS_RETRIEVAL_AMOUNT)

            historySummaryDto = HistorySummaryDto(serialNumber = pumpStatus.serialNumber.toInt(),
                                                      startRecord = message.firstSequenceNum,
                                                      lastRecord = message.lastSequenceNum,
                                                      modifiedStartRecord = message.firstSequenceNum,
                                                      missedRanges = arrayListOf(remainingRange)
                                                      )

            // get last 1000 records
            listOfChunks.addAll(prepareChunks(message.lastSequenceNum-RECORDS_RETRIEVAL_AMOUNT,
                                              message.lastSequenceNum))

            // - on first read we get min, max (HistoryLogStatusRequest) and create HistorySummary and save it
            // - we start retreiving data: we will retrieve 5 x 200 = 1000 records per reading
            //     (reading will be done together with status every 5 minutes)
            // - once reading is done we create HistoryRange and add it to missed range

            // this.currentRequest = HistoryRequestInfo(startSequence = message.lastSequenceNum-200,
            //                                          endSequence = message.lastSequenceNum,
            //                                          numberOfLogs = 200)

        } else {

            aapsLogger.error(TAG, "HST: Non-First read")

            val diff = message.lastSequenceNum - historySummaryDto!!.lastRecord

            if (diff==0L) {
                // no new records
                listOfChunks.addAll(getNextChunks(1000))
            } else if (diff<1000) {
                // less than 1000 new records
                listOfChunks.addAll(prepareChunks(historySummaryDto!!.lastRecord+1,
                                                  message.lastSequenceNum))
                // take something from missedRanges and update missed Ranger
                listOfChunks.addAll(getNextChunks(diff))
            } else {

                listOfChunks.addAll(prepareChunks(message.lastSequenceNum-RECORDS_RETRIEVAL_AMOUNT,
                                                  message.lastSequenceNum))

                val remainingRange = HistoryRange(historySummaryDto!!.lastRecord+1,
                                                  message.lastSequenceNum-RECORDS_RETRIEVAL_AMOUNT-1)

                // set remaining into missedRanges
                historySummaryDto!!.missedRanges.add(remainingRange)
            }


            historySummaryDto!!.activeProcessing.addAll(listOfChunks)
            historySummaryDto!!.lastRecord = message.lastSequenceNum
            saveSummary()

            // SUBSEQUENT READS
            //     - read min, max (HistoryLogStatusRequest) and check if new records there
            // - NO: read missedRanges and read next 1000 items
            // - YES: read new data, and remaining range is used to read old data, if there is more than
            // 1000 new records add new missedRange or modify the current one

        }

        if (listOfRequests.size==0) {

        } else {
            aapsLogger.info(TAG, "Start first retrieval (listOfRequests=${listOfRequests.size})")
            // start reading
            executeNextLogGet(listOfRequests)
        }

    }

    private fun getNextChunks(howManyEntriesDoWeNeed: Long): Collection<HistoryRequestInfo> {
        TODO("Not yet implemented")

        if (historySummaryDto!!.missedRanges.size==0) {
            return listOf()
        }


    }

    fun getNewestChunk() {

    }




    fun prepareChunks(startRange: Long, endRange: Long): List<HistoryRequestInfo> {
        TODO("Not implemented prepareChucks...")
        return listOf()
    }

    fun saveSummary() {
        // TODO("save summary information")
        aapsLogger.error(TAG, "HST: Save Summary NOT IMPLEMENTED.")
    }



    var currentRequest: HistoryRequestInfo?  = null
    var listOfRequests = ArrayDeque<HistoryRequestInfo>()

    var listOfMissingItemsInChunk = ArrayDeque<HistoryRequestInfo>()


    // fun receivedStatusSS(message: HistoryLogStatusResponse) {
    //     this.lastPumpRecord = message.lastSequenceNum
    //     this.firstPumpRecord = message.firstSequenceNum
    //
    //     aapsLogger.error(TAG, "Got LogStatusResponse: ${message}")
    //
    //     aapsLogger.error(TAG, "Start Data Retriaval: N/A")
    //
    //     // determine if empty, then get
    //     //  comprae to history,
    //     //    if first reading get FRE
    //     //    not: get All Chunks from last retreival to now
    //
    //     this.currentRequest = HistoryRequestInfo(startSequence = message.lastSequenceNum-200,
    //                                              endSequence = message.lastSequenceNum,
    //                                              numberOfLogs = 200)
    //
    //
    //     //this.communication.sendCommand(HistoryLogRequest(message.lastSequenceNum-200, 200))
    //
    //     //
    //     listOfRequests.add(currentRequest!!)
    //
    //
    //
    //
    //     executeNextLogGet(listOfRequests)
    //
    // }

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

        aapsLogger.error(TAG, "HST: Number of Logs: ${currentRequest!!.historyLogMap.size}")

        if (this.currentRequest!!.chunkComplete()) {
            processChunkComplete()
            return
        }

        enableLastMessageWatchdog()

    }


    private fun processChunkComplete() {

        aapsLogger.error(TAG, "HST: Chunk Complete: ${currentRequest!!.historyLogMap.size}")

        if (this.currentRequest!!.chunkHasAllItems()) {
            aapsLogger.error(TAG, "HST: Chunk Has All Items: ${currentRequest!!.historyLogMap.size}")

            // TODO add to database
        } else {
            aapsLogger.error(TAG, "HST: Chunk Has MISSING Items: ${currentRequest!!.historyLogMap.size}")
            // TODO add missing items
        }


        disableLastMessageWatchdog()


        if (listOfMissingItemsInChunk.size!=0) {
            // TODO start new retrieval

        } else {
            if (listOfRequests.size!=0) {
                aapsLogger.info(TAG, "HST: Next chunk retrieval (listOfRequests=${listOfRequests.size})")
                executeNextLogGet(listOfRequests)
            } else {
                aapsLogger.info(TAG, "HST: No more chunks to get... Exiting")
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