package app.aaps.pump.tandem.di

import app.aaps.core.interfaces.ui.compose.ComposeUiFactory
import app.aaps.implementation.ui.ComposeUiModule
import app.aaps.pump.tandem.common.comm.TandemDataConverter
import app.aaps.pump.tandem.common.comm.qe.QualifyingEventHandler
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
import app.aaps.pump.tandem.t_mobi.ui.ActionsActivity
import dagger.Binds
import dagger.multibindings.IntoMap

@Module(includes = [TandemDatabaseModule::class], subcomponents = [TandemComposeUiComponent::class])
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



    // T-Mobi Package - Activites and Fragments
    @ContributesAndroidInjector abstract fun contributeAAPSTimber(): PumpX2L
    @ContributesAndroidInjector abstract fun contributesTandemMobiPumpFragment(): TandemMobiPumpFragment
    @ContributesAndroidInjector abstract fun contributesTandemMobiPumpDriverConfiguration(): TandemMobiPumpDriverConfiguration
    //@ContributesAndroidInjector abstract fun contributesTandemMobiSettingsActivity(): TandemMobiSettingsActivity
    @ContributesAndroidInjector abstract fun contributesTandemMobiPumpPlugin(): TandemMobiPumpPlugin

    @ContributesAndroidInjector abstract fun contributesQualifyingEventHandler(): QualifyingEventHandler


    // Compose UI Activities
    //@ContributesAndroidInjector abstract fun contributesActionsActivity(): ActionsActivity




    //@ContributesAndroidInjector abstract fun contributesDebugActionsActivity(): DebugActionsActivity
    //@ContributesAndroidInjector abstract fun contributesDebugActionsFragment(): DebugActionsFragment
    //@ContributesAndroidInjector abstract fun contributesTandemChangeFillManager(): TandemChangeFillManager


    // @ContributesAndroidInjector abstract fun contributesTandemWizardDialogFragment(): TandemWizardDialogFragment


    // T-Slim Package - Activites and Fragments (disabled for now, TSlim not supported, it is not loopable)
    // @ContributesAndroidInjector abstract fun contributesTandemPumpFragment(): TandemSlimPumpFragment
    // @ContributesAndroidInjector abstract fun contributesTandemPumpDriverConfiguration(): TandemPumpDriverConfiguration


    @Binds
    @IntoMap
    @ComposeUiModule("tandem")
    abstract fun bindTandemComposeUiFactory(factory: TandemComposeUiComponent.FactoryCompose): ComposeUiFactory


}