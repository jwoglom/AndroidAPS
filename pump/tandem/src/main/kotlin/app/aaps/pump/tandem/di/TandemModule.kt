package app.aaps.pump.tandem.di

import app.aaps.core.interfaces.ui.compose.ComposeUiFactory
import app.aaps.implementation.ui.ComposeUiModule
import app.aaps.pump.tandem.common.comm.TandemDataConverter
import app.aaps.pump.tandem.common.comm.history.HistoryRetriever
import app.aaps.pump.tandem.common.comm.qe.QualifyingEventHandler
import app.aaps.pump.tandem.common.comm.ui.TandemUICommunication
import app.aaps.pump.tandem.common.comm.ui.TandemUIDataStore
import app.aaps.pump.tandem.common.database.data.DbDataConverter
import app.aaps.pump.tandem.common.database.data.DbDataHandler
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.driver.config.TandemBLESelector
import app.aaps.pump.tandem.common.driver.config.TandemHistoryDataProvider
import app.aaps.pump.tandem.common.driver.connector.TandemPumpConnectionManager
import app.aaps.pump.tandem.common.driver.connector.TandemPumpConnector
import app.aaps.pump.tandem.common.service.TandemService
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import app.aaps.pump.tandem.t_mobi.ui.TandemMobiPumpFragment
import app.aaps.pump.tandem.t_mobi.driver.TandemMobiPumpDriverConfiguration
import dagger.Module
import dagger.android.ContributesAndroidInjector
import app.aaps.pump.tandem.common.ui.TandemPumpBLEConfigActivity
import app.aaps.pump.tandem.common.util.PumpX2L
import app.aaps.pump.tandem.t_mobi.TandemMobiPumpPlugin
import dagger.Binds
import dagger.Provides
import dagger.multibindings.IntoMap
import javax.inject.Singleton

@Module(includes = [TandemDatabaseModule::class, TandemModuleImpl::class],
        subcomponents = [TandemComposeUiComponent::class])
@Suppress("unused")
abstract class TandemModule {

    // Driver basics
    @ContributesAndroidInjector abstract fun contributeTandemPumpUtil(): TandemPumpUtil
    @ContributesAndroidInjector abstract fun contributeTandemPumpStatus(): TandemPumpStatus

    // Communication Layer
    @ContributesAndroidInjector abstract fun contributeTandemConnectionManager(): TandemPumpConnectionManager
    @ContributesAndroidInjector abstract fun contributeTandemPumpConnector(): TandemPumpConnector
    @ContributesAndroidInjector abstract fun contributeTandemDataConverter(): TandemDataConverter

    // Configuration
    @ContributesAndroidInjector abstract fun contributesTandemBLESelector(): TandemBLESelector
    @ContributesAndroidInjector abstract fun contributesTandemPumpBLEConfigActivity(): TandemPumpBLEConfigActivity

    // Data
    @ContributesAndroidInjector abstract fun contributesTandemHistoryDataProvider(): TandemHistoryDataProvider

    // Service
    @ContributesAndroidInjector abstract fun contributesTandemService(): TandemService

    // Database
    @ContributesAndroidInjector abstract fun contributesDbDataHandler(): DbDataHandler
    @ContributesAndroidInjector abstract fun contributesDbDataConverter(): DbDataConverter

    // Database Related (QE and History)
    @ContributesAndroidInjector abstract fun contributesQualifyingEventHandler(): QualifyingEventHandler
    @ContributesAndroidInjector abstract fun contributesHistoryRetriever(): HistoryRetriever


    // T-Mobi Package - Activites and Fragments
    @ContributesAndroidInjector abstract fun contributeAAPSTimber(): PumpX2L
    @ContributesAndroidInjector abstract fun contributesTandemMobiPumpFragment(): TandemMobiPumpFragment
    @ContributesAndroidInjector abstract fun contributesTandemMobiPumpDriverConfiguration(): TandemMobiPumpDriverConfiguration
    @ContributesAndroidInjector abstract fun contributesTandemMobiPumpPlugin(): TandemMobiPumpPlugin



    // Compose UI Activities
    //@ContributesAndroidInjector abstract fun contributesActionsActivity(): ActionsActivity
    // @ContributesAndroidInjector abstract fun contributesTandemUICommunication(): TandemUICommunication

    // @Provides
    // @Singleton
    // @ContributesAndroidInjector abstract fun contributesTandemUIDataStore(): TandemUIDataStore

    // T-Slim Package - Activites and Fragments (disabled for now, TSlim not supported, it is not loopable)
    // @ContributesAndroidInjector abstract fun contributesTandemPumpFragment(): TandemSlimPumpFragment
    // @ContributesAndroidInjector abstract fun contributesTandemPumpDriverConfiguration(): TandemPumpDriverConfiguration


    @Binds
    @IntoMap
    @ComposeUiModule("tandem")
    abstract fun bindTandemComposeUiFactory(factory: TandemComposeUiComponent.FactoryCompose): ComposeUiFactory


}