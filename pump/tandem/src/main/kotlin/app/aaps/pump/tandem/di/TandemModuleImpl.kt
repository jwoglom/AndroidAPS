package app.aaps.pump.tandem.di

import app.aaps.pump.tandem.common.comm.ui.TandemUIDataStore
import app.aaps.pump.tandem.common.database.dao.TandemQualifyingEventsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
open class TandemModuleImpl {

    // @Provides
    // @Singleton
    // fun provideTandemUIDataStore(): TandemUIDataStore = TandemUIDataStore()

}