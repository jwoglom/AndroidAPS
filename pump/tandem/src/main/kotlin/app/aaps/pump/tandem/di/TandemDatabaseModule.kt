package app.aaps.pump.tandem.di

//import android.content.Context
import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.tandem.common.comm.TandemDataConverter
import app.aaps.pump.tandem.common.database.HistoryMapper
import app.aaps.pump.tandem.common.database.TandemHistoryRecordDao
import app.aaps.pump.tandem.common.database.TandemPumpDatabase
import app.aaps.pump.tandem.common.database.TandemPumpHistory
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.driver.config.TandemBLESelector
import app.aaps.pump.tandem.common.driver.config.TandemHistoryDataProvider
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import app.aaps.pump.tandem.t_mobi.driver.TandemMobiPumpDriverConfiguration
import dagger.Module
import dagger.Provides
import dagger.Reusable
import javax.inject.Singleton

// import dagger.Provides
// import dagger.Reusable
// import info.nightscout.androidaps.interfaces.PumpSync
// import HistoryMapper
// import HistoryRecordDao
// import info.nightscout.androidaps.plugins.pump.tandem.database.YpsoPumpDatabase
// import info.nightscout.androidaps.plugins.pump.tandem.database.YpsoPumpHistory
// import TandemPumpStatus
// import TandemPumpUtil
// import app.aaps.core.interfaces.logging.AAPSLogger
// import javax.inject.Singleton

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
    @Reusable // no state, let system decide when to reuse or create new.
    internal fun provideHistoryMapper(
        tandemPumpUtil: TandemPumpUtil,
        aapsLogger: AAPSLogger
    ) = HistoryMapper(tandemPumpUtil, aapsLogger)


    @Provides
    @Singleton
    internal fun provideTandemPumpHistory(
        dao: TandemHistoryRecordDao,
        pumpHistoryDatabase: TandemPumpDatabase,
        historyMapper: HistoryMapper,
        pumpSync: PumpSync,
        tandemPumpUtil: TandemPumpUtil,
        pumpStatus: TandemPumpStatus,
        aapsLogger: AAPSLogger
    ) = TandemPumpHistory(dao, pumpHistoryDatabase, historyMapper, pumpSync, tandemPumpUtil, pumpStatus, aapsLogger)

    // @Provides
    // @Singleton
    // internal fun provideTandemHistoryDataProvider(
    //     resourceHelper: ResourceHelper,
    //     aapsLogger: AAPSLogger,
    //     tandemPumpUtil: TandemPumpUtil,
    //     tandemDataConverter: TandemDataConverter,
    //     tandemPumpHistory: TandemPumpHistory
    // ) = TandemHistoryDataProvider(resourceHelper, aapsLogger, tandemPumpUtil, tandemDataConverter, tandemPumpHistory)

    // @Provides
    // @Singleton
    // internal fun provideTandemMobiPumpDriverConfiguration(
    //     pumpBLESelector: TandemBLESelector,
    //     pumpHistoryDataProvider: TandemHistoryDataProvider
    // ) = TandemMobiPumpDriverConfiguration(pumpBLESelector, pumpHistoryDataProvider)


}