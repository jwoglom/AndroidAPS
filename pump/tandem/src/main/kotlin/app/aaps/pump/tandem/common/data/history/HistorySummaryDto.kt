package app.aaps.pump.tandem.common.data.history

import app.aaps.pump.tandem.common.comm.history.HistoryRequestInfo

data class HistorySummaryDto(
    var serialNumber: Int,  // serial
    var startRecord: Long, // first history record on pump
    var modifiedStartRecord: Long, // first history record in database (we keep only 1 month of history, so we would never retrieve anything over 1.5 month
    var lastRecord: Long, // current last record in database
    var missedRanges: ArrayList<HistoryRange> = arrayListOf(),
    var activeProcessing: ArrayList<HistoryRequestInfo> = arrayListOf()
)


data class HistoryRange(var start: Long, var end: Long)