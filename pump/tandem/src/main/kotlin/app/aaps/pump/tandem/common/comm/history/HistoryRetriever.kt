package app.aaps.pump.tandem.common.comm.history

import android.content.Context
import android.icu.util.GregorianCalendar
import androidx.annotation.VisibleForTesting
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.common.defs.PumpDriverState
import app.aaps.pump.common.defs.PumpUpdateFragmentType
import app.aaps.pump.common.driver.connector.defs.PumpCommandType
import app.aaps.pump.common.events.EventPumpFragmentValuesChanged
import app.aaps.pump.tandem.common.comm.TandemCommunicationManager
import app.aaps.pump.tandem.common.comm.ui.TandemUICommunication
import app.aaps.pump.tandem.common.data.history.HistoryRange
import app.aaps.pump.tandem.common.data.history.HistoryRequestInfo
import app.aaps.pump.tandem.common.data.history.HistorySummaryDto
import app.aaps.pump.tandem.common.database.data.DbDataHandler
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.driver.connector.TandemPumpConnector
import app.aaps.pump.tandem.common.driver.tandemDataStore
import app.aaps.pump.tandem.common.keys.TandemStringNonPreferenceKey
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import com.jwoglom.pumpx2.pump.messages.helpers.Dates
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogStatusRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.UnknownHistoryLog
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors
import javax.inject.Inject
import javax.inject.Singleton

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

@Singleton
class HistoryRetriever @Inject constructor(
    //val communication: TandemUICommunication,
    val pumpStatus: TandemPumpStatus,
    val tandemPumpConnector: TandemPumpConnector,
    val aapsLogger: AAPSLogger,
    val pumpUtil: TandemPumpUtil,
    val preferences: Preferences,
    val rxBus: RxBus,
    var context: Context,
    val dbDataHandler: DbDataHandler

) {

    private val RECORDS_RETRIEVAL_AMOUNT = 1000  // how many record we retrieve in one go
    private var CHUNK_SIZE = 200 // how many records on each call
    private val HISTORY_LIMIT_IN_DAYS = 44 // how many day of history we are getting
    @Suppress("PropertyName")
    val TAG = LTag.PUMPCOMM

    private var maxDateTimeInSec: Int = 0
    private var historySummaryDto : HistorySummaryDto? = null
    private var currentRequest: HistoryRequestInfo?  = null
    private var listOfRequests = ArrayDeque<HistoryRequestInfo>()
    private var listOfMissingItemsInChunk = ArrayDeque<HistoryRequestInfo>()
    private var downloadRunning = false

    // added
    lateinit var communication: TandemUICommunication

    var progressAllItems = 1000  // how many items we will retrieve
    var progressPreviousChunksItems = 0 // how many were retrieved in previous chunk run
    var progressCurrentChunk = 0 // how many were retrieved in current chunk run
    var knownLogItemsCount = 0 // how many of log items were not of Unknown type

    var silentDownload = false // silent download is for retrieval of last 40 items (for bolus and TBR actions)


    fun downloadHistory(): Boolean {
        communication = TandemUICommunication(dataStore = tandemDataStore,
                                                      pumpStatus = pumpStatus,
                                                      context = context,
                                                      aapsLogger= aapsLogger)

        communication.historyRetriever = this
        this.communication.tandemCommunicationManager = tandemPumpConnector.getCommunicationManager()

        this.silentDownload = false
        val startTime = System.currentTimeMillis()
        resetProgress(1000)
        downloadRunning = true
        startDataRetrieval()

        while(downloadRunning) {
            aapsLogger.error("HST: download running")
            pumpUtil.sleepSeconds(5)
        }

        this.communication.tandemCommunicationManager = null

        setSemaphore()
        endProgress()

        var diffTime = System.currentTimeMillis() - startTime
        diffTime /= 1000

        aapsLogger.error(TAG, "HST: Download finished in $diffTime seconds.")


        dbDataHandler.databaseStatistics() // TODO HistoryRetriever::temporary db stats

        return true
    }


    private fun setSemaphore() {
        aapsLogger.error("setSemaphore: knownItems=$knownLogItemsCount")

        if (knownLogItemsCount>0) {
            if (!pumpStatus.semaphoreHistory) {
                pumpStatus.semaphoreHistory = true
                pumpStatus.semaphoreNeedsRefresh = true
                rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Custom_2))
            }
        }
    }


    fun downloadHistoryRecentItems() {
        // TODO downloadHistoryRecentItems
        this.silentDownload = true
    }


    private fun resetProgress(itemsToRetrive: Int?) {
        if (itemsToRetrive!=null) {
            progressAllItems = itemsToRetrive
        }
        progressPreviousChunksItems = 0
        progressCurrentChunk = 0
        knownLogItemsCount = 0
    }

    private fun startProgress() {
        if (!silentDownload) {
            pumpUtil.currentCommand = PumpCommandType.GetHistoryWithParameters
        }
    }

    private fun endProgress() {
        pumpUtil.currentCommand = null
        pumpUtil.driverStatus = PumpDriverState.Connected
        resetProgress(1000)
        pumpUtil.historyProgress = null
    }

    private fun updateRetrievalProgress() {
        val currentProgress = this.progressCurrentChunk + progressPreviousChunksItems
        val currentProgressString = "${currentProgress}/${this.progressAllItems}"
        aapsLogger.error(TAG, "HST PROGRESS: $currentProgressString")

        if (!silentDownload) {
            pumpUtil.historyProgress = "($currentProgressString)"
        }
    }

    fun startDataRetrieval()  {
        val summaryData = preferences.get(TandemStringNonPreferenceKey.HistorySummaryData)
        if (summaryData.isNotBlank()) {
            historySummaryDto = pumpUtil.gsonRegular.fromJson(summaryData, HistorySummaryDto::class.java)
            aapsLogger.error(TAG, "Initial Summary: $historySummaryDto")
        }

        val gc = GregorianCalendar()
        gc.add(GregorianCalendar.DAY_OF_YEAR, -1*HISTORY_LIMIT_IN_DAYS)

        // pump stores time in seconds from 1st Jan 2008
        val timeFromDate =  gc.timeInMillis/1000 - Dates.JANUARY_1_2008_UNIX_EPOCH

        maxDateTimeInSec = timeFromDate.toInt()

        //aapsLogger.error(TAG, "Max Time Seconds: ${maxDateTimeInSec}")

        startProgress()

        communication.sendCommand(HistoryLogStatusRequest())
    }


    fun receivedStatus(message: HistoryLogStatusResponse) {

        aapsLogger.error(TAG, "HST: Got LogStatusResponse: ${message}")

        if (historySummaryDto==null) {

            aapsLogger.error(TAG, "HST: First read")

            val remainingRange = HistoryRange(message.firstSequenceNum,
                                              message.lastSequenceNum-RECORDS_RETRIEVAL_AMOUNT)

            historySummaryDto = HistorySummaryDto(serialNumber = pumpStatus.serialNumber.toInt(),
                                                  startRecord = message.firstSequenceNum,
                                                  lastRecord = message.lastSequenceNum,
                                                  modifiedStartRecord = message.firstSequenceNum,
                                                  missedRanges = arrayListOf(remainingRange)
            )

            var firstRec = message.lastSequenceNum-RECORDS_RETRIEVAL_AMOUNT

            if (firstRec<0) {
                firstRec = 0
            }

            // get last 1000 records
            listOfRequests.addAll(prepareChunks(firstRec, message.lastSequenceNum))

            // - on first read we get min, max (HistoryLogStatusRequest) and create HistorySummary and save it
            // - we start retreiving data: we will retrieve 5 x 200 = 1000 records per reading
            //     (reading will be done together with status every 5 minutes)
            // - once reading is done we create HistoryRange and add it to missed range

        } else {

            if (silentDownload) {
                doShortHistoryReading(message)
                return
            }

            aapsLogger.error(TAG, "HST: Non-First read")

            val diff = message.lastSequenceNum - historySummaryDto!!.lastRecord

            if (diff==0L) {
                aapsLogger.error(TAG, "HST: Non-First read: No new records.")
                // no new records
                listOfRequests.addAll(getNextChunks(RECORDS_RETRIEVAL_AMOUNT))
            } else if (diff<RECORDS_RETRIEVAL_AMOUNT) {
                aapsLogger.error(TAG, "HST: Non-First read: Less than $RECORDS_RETRIEVAL_AMOUNT new records.")
                // less than 1000 new records
                listOfRequests.addAll(prepareChunks(historySummaryDto!!.lastRecord+1,
                                                    message.lastSequenceNum))

                val howMuchToGet = RECORDS_RETRIEVAL_AMOUNT - diff

                // take something from missedRanges and update missed Ranger
                listOfRequests.addAll(getNextChunks(howMuchToGet.toInt()))

            } else {
                aapsLogger.error(TAG, "HST: Non-First read: We have more than ${RECORDS_RETRIEVAL_AMOUNT} new records.")
                listOfRequests.addAll(prepareChunks(message.lastSequenceNum-RECORDS_RETRIEVAL_AMOUNT,
                                                    message.lastSequenceNum))

                val remainingRange = HistoryRange(message.firstSequenceNum,
                                                  message.lastSequenceNum-RECORDS_RETRIEVAL_AMOUNT)

                // set remaining into missedRanges
                historySummaryDto!!.missedRanges.add(remainingRange)
            }

            historySummaryDto!!.lastRecord = message.lastSequenceNum

            // SUBSEQUENT READS
            //     - read min, max (HistoryLogStatusRequest) and check if new records there
            // - NO: read missedRanges and read next 1000 items
            // - YES: read new data, and remaining range is used to read old data, if there is more than
            // 1000 new records add new missedRange or modify the current one

        }

        historySummaryDto!!.activeProcessing.addAll(listOfRequests)
        saveSummary()

        resetProgress(howManyItemsInNextChunks(listOfRequests))

        aapsLogger.error(TAG, "HST: HistorySummary: ${pumpUtil.gsonRegular.toJson(historySummaryDto)}")

        aapsLogger.info(TAG, "HST: List Of Requests: ${pumpUtil.gsonRegular.toJson(listOfRequests)}")

        if (listOfRequests.isEmpty()) {
            aapsLogger.info(TAG, "HST: There is no new records to retrieve")
            this.downloadRunning = false
        } else {
            aapsLogger.info(TAG, "HST: Start first retrieval (listOfRequests=${listOfRequests.size})")
            // start reading
            executeNextLogGet(listOfRequests)
        }
    }


    private fun doShortHistoryReading(message: HistoryLogStatusResponse) {
        aapsLogger.error(TAG, "HST: doShortHistoryReading NOT IMPLEMENTED")
        TODO("HistoryRetriever::doShortHistoryReading - Not yet implemented (Phase 3)")
    }


    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun getNextChunks(howManyEntriesDoWeNeed: Int): MutableList<HistoryRequestInfo> {

        aapsLogger.error(TAG, "HST getNextChunks (howManyEntriesDoWeNeed=$howManyEntriesDoWeNeed)")

        val newChunks: MutableList<HistoryRequestInfo> = mutableListOf()

        if (historySummaryDto!!.missedRanges.size==0 && historySummaryDto!!.activeProcessing.size==0) {
            aapsLogger.error(TAG, "getNextChunks: No missed ranges found.")
            return newChunks
        }

        // if we have some activeProcessing records we need to re-add them into missed ranges
        if (historySummaryDto!!.activeProcessing.size!=0) {
            val missedRangesFromActiveProcessing = getMissedRangesFromActiveProcessing()
            historySummaryDto!!.missedRanges.addAll(missedRangesFromActiveProcessing)
        }

        var finished = false

        while(!finished) {

            val newestRange: HistoryRange? = getNewestRange()

            if (newestRange==null) {
                finished = true
            } else {

                val itemsCount = howManyItemsInNextChunks(newChunks)

                aapsLogger.debug(TAG, "How Many Items In Next Chunks: $itemsCount")

                if (itemsCount<howManyEntriesDoWeNeed) {

                    val needed = howManyEntriesDoWeNeed - itemsCount

                    val newestChuckAmount: Int = newestRange.getItemAmount()

                    aapsLogger.debug(TAG, "Newest Range: $newestRange")

                    if (newestChuckAmount<needed) {
                        newChunks.addAll(prepareChunksFromRange(newestRange))
                        //finished = true
                    } else if (newestChuckAmount==needed) {
                        newChunks.addAll(prepareChunksFromRange(newestRange))
                        finished = true
                    } else {
                        val usePartOfChunk = usePartOfChunk(needed, newestRange)
                        aapsLogger.debug(TAG, "Too big chunk found. Use Part of Chunk: $usePartOfChunk")
                        newChunks.addAll(prepareChunksFromRange(usePartOfChunk))
                        finished = true
                    }

                } else if (itemsCount==howManyEntriesDoWeNeed) {
                    finished = true
                }
            }
        }

        aapsLogger.error(TAG, "getNextChunks: New Chunks Found: $newChunks")

        return newChunks

    }


    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun usePartOfChunk(needed: Int, inputRange: HistoryRange): HistoryRange {
        val newMissedRange = HistoryRange(inputRange.start, inputRange.end-needed)
        this.historySummaryDto!!.missedRanges.add(newMissedRange)

        aapsLogger.debug(TAG, "New Missed Range: $newMissedRange")

        val newSelectedRange = HistoryRange(inputRange.end-needed+1, inputRange.end)

        aapsLogger.debug(TAG, "Returned Range: $newSelectedRange")

        return newSelectedRange
    }


    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun getMissedRangesFromActiveProcessing(): List<HistoryRange> {
        val historyRangeList = this.historySummaryDto!!.activeProcessing.stream()
            .map { item -> HistoryRange(item.startSequence, item.endSequence) }
            .collect(Collectors.toList())

        this.historySummaryDto!!.activeProcessing.clear()

        return historyRangeList
    }


    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun howManyItemsInNextChunks(list: MutableList<HistoryRequestInfo>) : Int {
        var countItems = 0
        list.stream().forEach{item -> countItems += item.numberOfLogs }
        aapsLogger.debug(TAG, "howManyItemsInNextChunks: $countItems")
        return countItems
    }

    private fun debugSummary() {
        aapsLogger.error(TAG, "Summary: active=${historySummaryDto!!.activeProcessing.size}, missed=${historySummaryDto!!.missedRanges.size}")
    }


    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun getNewestRange(): HistoryRange? {
        if (this.historySummaryDto!!.missedRanges.size==0) {
            aapsLogger.error(TAG, "getNewestChunk: No missed ranges found.")
            return null
        } else if (this.historySummaryDto!!.missedRanges.size==1) {
            aapsLogger.error(TAG, "getNewestChunk: One missed range found.")
            val selectedRange = this.historySummaryDto!!.missedRanges[0]
            this.historySummaryDto!!.missedRanges.removeAt(0)
            return selectedRange
        } else {
            var selectedRange : HistoryRange? = null
            for (missedRange in this.historySummaryDto!!.missedRanges) {
                if (selectedRange==null) {
                    selectedRange = missedRange
                } else {
                    if (missedRange.end > selectedRange.end) {
                        selectedRange = missedRange
                    }
                }
            }
            this.historySummaryDto!!.missedRanges.remove(selectedRange)
            aapsLogger.error(TAG, "getNewestChunk: One of many missed ranges found: $selectedRange")
            return selectedRange
        }
    }


    private fun prepareChunksFromRange(historyRange: HistoryRange): MutableList<HistoryRequestInfo> {
        return prepareChunks(historyRange.start, historyRange.end)
    }


    private fun prepareChunks(startRange: Long, endRange: Long): MutableList<HistoryRequestInfo> {
        aapsLogger.error(TAG, "HST: Start range: $startRange and End range : $endRange")

        var currentEnd = endRange
        val chunksList: MutableList<HistoryRequestInfo> = mutableListOf()

        while (currentEnd > startRange) {
            var currentStart = currentEnd - CHUNK_SIZE +1

            if (currentStart<startRange) {
                currentStart = startRange
            }

            chunksList.add(HistoryRequestInfo(startSequence=currentStart,
                                              endSequence = currentEnd))

            currentEnd -= CHUNK_SIZE
        }

        // aapsLogger.error(TAG, "HST: Chunks: ${pumpUtil.gsonRegular.toJson(chunksList)}")

        return chunksList
    }


    private fun saveSummary() {
        aapsLogger.error(TAG, "HST: Save Summary: $historySummaryDto")
        preferences.put(TandemStringNonPreferenceKey.HistorySummaryData, this.pumpUtil.gsonRegular.toJson(this.historySummaryDto))
        debugSummary()
    }


    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun executeNextLogGet(queue: ArrayDeque<HistoryRequestInfo>) {
        currentRequest = queue.removeFirst()
        aapsLogger.error(TAG, "HST: executeNextLogGet (start=${currentRequest!!.startSequence}, end=${currentRequest!!.endSequence}, count=${currentRequest!!.numberOfLogs})")
        this.communication.sendCommand(HistoryLogRequest(currentRequest!!.startSequence, currentRequest!!.numberOfLogs))
    }


    fun receivedLogStreamResponse(message: HistoryLogStreamResponse) {

        aapsLogger.warn(TAG, "Received from Stream: numberOfHistoryLogs=${message.numberOfHistoryLogs}, streamId=${message.streamId}, historyLogsSize=${message.historyLogs.size} ")

        this.currentRequest!!.historyLogMap.putAll(message.historyLogs.associateBy { it.sequenceNum })

        this.progressCurrentChunk = currentRequest!!.historyLogMap.size

        aapsLogger.error(TAG, "HST: Number of Logs: ${progressCurrentChunk}")

        updateRetrievalProgress()

        if (this.currentRequest!!.chunkComplete()) {
            processChunkComplete()
            return
        }

        enableLastMessageWatchdog()

    }


    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun processChunkComplete() {

        aapsLogger.error(TAG, "HST: processChunkComplete: ${currentRequest!!.historyLogMap.size}")

        disableLastMessageWatchdog()

        if (this.currentRequest!!.chunkHasAllItems()) {
            aapsLogger.warn(TAG, "HST: Chunk Has All Items: ${currentRequest!!.historyLogMap.size}")
        } else {
            aapsLogger.error(TAG, "HST: Chunk Has MISSING Items: ${currentRequest!!.historyLogMap.size}")
            addMissingItemsInChunk(this.currentRequest!!)
        }

        addToDatabase(this.currentRequest!!)
        removeFromActiveItems(this.currentRequest!!)
        saveSummary()

        this.progressPreviousChunksItems += currentRequest!!.historyLogMap.size
        this.progressCurrentChunk = 0

        if (haveWeReachedHistoryLimit()) {
            this.downloadRunning = false
            this.historySummaryDto!!.modifiedStartRecord = currentRequest!!.startSequence //we change modifiedStartRecord
            this.historySummaryDto!!.activeProcessing.clear()
            this.historySummaryDto!!.missedRanges.clear() // since we have all history needed, we clear missedRanges
            saveSummary()
            return;
        }

        if (listOfMissingItemsInChunk.size!=0) {
            aapsLogger.error(TAG, "HST: Missing items in the chunk: ${pumpUtil.gsonRegular.toJson(listOfMissingItemsInChunk)}")
            executeNextLogGet(listOfMissingItemsInChunk)
        } else {
            if (listOfRequests.size!=0) {
                aapsLogger.error(TAG, "HST: Next chunk retrieval (listOfRequests=${listOfRequests.size})")
                executeNextLogGet(listOfRequests)
            } else {
                aapsLogger.error(TAG, "HST: No more chunks to get... Exiting")
                this.downloadRunning = false
            }
        }

        // check if first and last received = yes chunk ready
        // the add to db
        // determine missing records (if number != 200)
        //    no:  next chuck
        //    yes: prepare missing records chunks - MRC
        //         repeat retrieval until all MRC here

    }


    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun addMissingItemsInChunk(currentRequest: HistoryRequestInfo) {
        // if items are missing in chunk, we add them back to activeList and to listOfMissingItemsInChunk
        val historyRangeList: MutableCollection<HistoryRequestInfo> = mutableListOf()
        var startSeq: Long = 0
        var endSeq: Long = 0

        var i = currentRequest.startSequence

        //aapsLogger.error(TAG, "Before While")

        while (i <= currentRequest.endSequence) {

            //aapsLogger.error(TAG, "While $i")

            if (!currentRequest.historyLogMap.containsKey(i)) {
                startSeq = i

                //aapsLogger.error(TAG, "Not found $i")

                for(j in startSeq!!+1..currentRequest.endSequence) {
                    aapsLogger.error(TAG, "For $j")
                    if (currentRequest.historyLogMap.containsKey(j)) {
                        //aapsLogger.error(TAG, "Key found $j")
                        endSeq = j-1
                        historyRangeList.add(HistoryRequestInfo(startSeq, endSeq))
                        aapsLogger.error(TAG, "Add History Range (start=$startSeq, end=$endSeq)")
                        //startSeq = null
                        //endSeq = null
                        i = j-1
                        break
                    }

                    if (j==currentRequest.endSequence) {
                        //aapsLogger.error(TAG, "End Reached ($startSeq-$endSeq)")
                        aapsLogger.error(TAG, "Add History Range On End (start=$startSeq, end=$endSeq)")
                        historyRangeList.add(HistoryRequestInfo(startSeq, currentRequest.endSequence))
                        i=currentRequest.endSequence
                    }
                }
            }
            i++
        }

        this.historySummaryDto!!.activeProcessing.addAll(historyRangeList)
        saveSummary()

        this.listOfMissingItemsInChunk.addAll(historyRangeList)
    }

    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        .withZone(ZoneId.systemDefault())

    private fun addToDatabase(historyRequestInfo: HistoryRequestInfo) {
        aapsLogger.debug(TAG, "Add History Logs to Database (count=${historyRequestInfo.historyLogMap.values.size}) N/A")

        val listOfRecords: MutableList<HistoryLog> = mutableListOf()

        for (entry in historyRequestInfo.historyLogMap.values) {
            if (entry.pumpTimeSec >= maxDateTimeInSec) {
                listOfRecords.add(entry)
                // TODO HistoryRetriever remove
                aapsLogger.error(TAG, "Entry:  ${formatter.format(entry.pumpTimeSecInstant)} - ${entry.javaClass.simpleName}")

                if (entry !is UnknownHistoryLog) {
                    knownLogItemsCount++;
                }
            }
        }

        aapsLogger.info(TAG, "Add History Logs to Database (filtered_count=${listOfRecords.size},retrieved_count=${historyRequestInfo.historyLogMap.values.size})")
        dbDataHandler.addHistoryLogs(listOfRecords);
    }


    private fun removeFromActiveItems(historyRequestInfo: HistoryRequestInfo) {
        //aapsLogger.error(TAG, "HST Before remove ${historySummaryDto!!.activeProcessing.size}")
        this.historySummaryDto!!.activeProcessing.remove(historyRequestInfo)
        //aapsLogger.error(TAG, "HST After Remove remove ${historySummaryDto!!.activeProcessing.size}")
    }


    private fun haveWeReachedHistoryLimit(): Boolean {

        for (entry in this.currentRequest!!.historyLogMap) {
            if (entry.value.pumpTimeSec < maxDateTimeInSec) {
                aapsLogger.info(TAG, "Found entry older than 1.5 months: $entry")
                return true
            }
        }

        aapsLogger.debug(TAG, "HST: haveWeReachedHistoryLimit: all entries are newer, returning false")

        return false
    }


    private fun disableLastMessageWatchdog() {
        // TODOX  disableLastMessageWatchdog - we will try without this for now, if needed it will be added in phase 4
        //aapsLogger.error(TAG, "HST: disableLastMessageWatchdog not implemented.")
    }

    private fun enableLastMessageWatchdog() {
        // TODOX enableLastMessageWatchdog -  - we will try without this for now, if needed it will be added in phase 4
        //    when message is received we note the time and wait in special thread if timeout is reached
        //    if watchdog already exists, we just extend time
        //aapsLogger.error(TAG, "HST: enableLastMessageWatchdog not implemented.")
    }

    fun receivedLogResponse(message: HistoryLogResponse) {
        aapsLogger.error(TAG, "Received LogResponse: $message")
    }


}

