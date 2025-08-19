package app.aaps.pump.tandem.common.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.aaps.pump.tandem.common.database.dao.TandemHistoryRecordDao
import app.aaps.pump.tandem.common.database.dao.TandemQualifyingEventsDao
import app.aaps.pump.tandem.common.database.data.entity.TandemHistoryRecordEntity
import app.aaps.pump.tandem.common.database.data.entity.TandemQualifyingEventEntity

@Database(
    entities = [TandemHistoryRecordEntity::class, TandemQualifyingEventEntity::class],
    exportSchema = true,
    version = TandemPumpDatabase.VERSION,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
abstract class TandemPumpDatabase : RoomDatabase() {

    abstract fun historyRecordDao(): TandemHistoryRecordDao

    abstract fun qualifyingEventsDao(): TandemQualifyingEventsDao

    companion object {

        const val VERSION = 3

        fun build(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                TandemPumpDatabase::class.java,
                "tandem_pump_database.db")
                .addMigrations(MIGRATION_2_3)
                .allowMainThreadQueries()
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()

        // 2 -> 3 added indexes for faster access and for easier cleanup
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_records_pumpTime ON history_records(pumpTime)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_qualifying_events_dateTime ON qualifying_events(dateTime)")
            }
        }

    }


}