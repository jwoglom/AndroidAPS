package app.aaps.pump.tandem.common.database.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "qualifying_events")
data class TandemQualifyingEventEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var pumpSerial: Int,
    var dateTime: Long,   // EpochInMillis
    var name: String, // coresponds to type
    var description: String? = null // additional info
)
