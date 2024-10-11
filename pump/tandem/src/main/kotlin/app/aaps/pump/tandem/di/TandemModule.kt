package app.aaps.pump.tandem.di

import app.aaps.pump.tandem.common.comm.TandemDataConverter
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.driver.config.TandemBLESelector
import app.aaps.pump.tandem.common.driver.config.TandemHistoryDataProvider
import app.aaps.pump.tandem.common.driver.connector.TandemPumpConnectionManager
import app.aaps.pump.tandem.common.driver.connector.TandemPumpConnector
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import app.aaps.pump.tandem.t_mobi.ui.TandemMobiPumpFragment
import app.aaps.pump.tandem.t_mobi.driver.TandemMobiPumpDriverConfiguration
import dagger.Module
import dagger.android.ContributesAndroidInjector
import app.aaps.pump.tandem.common.ui.TandemPumpBLEConfigActivity
import app.aaps.pump.tandem.t_mobi.ui.TandemMobiSettingsActivity

@Module(includes = [TandemDatabaseModule::class])
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





    // T-Mobi Package - Activites and Fragments

    @ContributesAndroidInjector abstract fun contributesTandemMobiPumpFragment(): TandemMobiPumpFragment
    @ContributesAndroidInjector abstract fun contributesTandemMobiPumpDriverConfiguration(): TandemMobiPumpDriverConfiguration
    @ContributesAndroidInjector abstract fun contributesTandemMobiSettingsActivity(): TandemMobiSettingsActivity



    // T-Slim Package - Activites and Fragments (disabled for now, TSlim not supported, it is not loopable)
    // @ContributesAndroidInjector abstract fun contributesTandemPumpFragment(): TandemSlimPumpFragment
    // @ContributesAndroidInjector abstract fun contributesTandemPumpDriverConfiguration(): TandemPumpDriverConfiguration

}