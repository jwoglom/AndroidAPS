package app.aaps.pump.tandem.di

import android.content.Context
import app.aaps.pump.tandem.common.database.TandemPumpDatabase
import app.aaps.pump.tandem.common.database.dao.TandemHistoryRecordDao
import app.aaps.pump.tandem.common.database.dao.TandemQualifyingEventsDao
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
@Suppress("unused")
class TandemDatabaseModule {

    @Provides
    @Singleton
    internal fun provideDatabase(context: Context): TandemPumpDatabase =
        TandemPumpDatabase.build(context)

    @Provides
    @Singleton
    internal fun provideHistoryRecordDao(historyDatabase: TandemPumpDatabase): TandemHistoryRecordDao =
        historyDatabase.historyRecordDao()

    @Provides
    @Singleton
    internal fun provideTandemQualifyingEventsDao(historyDatabase: TandemPumpDatabase): TandemQualifyingEventsDao =
        historyDatabase.qualifyingEventsDao()

}