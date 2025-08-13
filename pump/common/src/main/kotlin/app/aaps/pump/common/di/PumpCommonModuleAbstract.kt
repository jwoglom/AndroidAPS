package app.aaps.pump.common.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import app.aaps.pump.common.ble.BondStateReceiver
import app.aaps.pump.common.ui.PumpBLEConfigActivity
import app.aaps.pump.common.ui.PumpHistoryActivity

@Module
@Suppress("unused")
abstract class PumpCommonModuleAbstract {

    @ContributesAndroidInjector abstract fun contributesBondStateReceiver(): BondStateReceiver
    @ContributesAndroidInjector abstract fun contributesPumpBLEConfigActivity(): PumpBLEConfigActivity
    @ContributesAndroidInjector abstract fun contributesPumpHistoryActivity(): PumpHistoryActivity

}
