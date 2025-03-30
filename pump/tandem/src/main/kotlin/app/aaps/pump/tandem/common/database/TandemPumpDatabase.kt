package app.aaps.pump.tandem.common.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters


@Database(
    entities = [TandemHistoryRecordEntity::class],
    exportSchema = true,
    version = TandemPumpDatabase.VERSION
)
@TypeConverters(DatabaseConverters::class)
abstract class TandemPumpDatabase : RoomDatabase() {

    abstract fun historyRecordDao(): TandemHistoryRecordDao

    companion object {

        const val VERSION = 1

        fun build(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                TandemPumpDatabase::class.java,
                "tandem_pump_database.db"
            )
                .allowMainThreadQueries()
                .fallbackToDestructiveMigration()
                .build()

    }

}