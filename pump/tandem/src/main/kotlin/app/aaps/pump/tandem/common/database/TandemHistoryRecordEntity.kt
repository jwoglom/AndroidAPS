package app.aaps.pump.tandem.common.database

import androidx.room.Entity


@Entity(tableName = "history_records", primaryKeys = ["sequenceId", "pumpSerial"])
data class TandemHistoryRecordEntity(
    var sequenceId: Long,
    var pumpSerial: Int,
    var typeId: Int,
    var pumpTime: Long,   // EpochInMillis (pump stores time as EpochSeconds from Jan2008, we don't)
    var payload: ByteArray,
    var entitySubId: Long? = null, // some entities have special id (for example TBR has tempRateId)

    var createdAt: Long, // creation date of the record
    var updatedAt: Long  // update date of the record
)
