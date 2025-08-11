package app.aaps.pump.tandem.common.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.aaps.pump.tandem.common.database.dao.TandemHistoryRecordDao
import app.aaps.pump.tandem.common.database.dao.TandemQualifyingEventsDao
import app.aaps.pump.tandem.common.database.data.entity.TandemHistoryRecordEntity
import app.aaps.pump.tandem.common.database.data.entity.TandemQualifyingEventEntity

@Database(
    entities = [TandemHistoryRecordEntity::class, TandemQualifyingEventEntity::class],
    exportSchema = true,
    version = TandemPumpDatabase.VERSION
)
//@TypeConverters(DatabaseConverters::class)
abstract class TandemPumpDatabase : RoomDatabase() {

    abstract fun historyRecordDao(): TandemHistoryRecordDao

    abstract fun qualifyingEventsDao(): TandemQualifyingEventsDao

    companion object {

        const val VERSION = 2

        fun build(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                TandemPumpDatabase::class.java,
                "tandem_pump_database.db")
                .allowMainThreadQueries()
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()

    }

}