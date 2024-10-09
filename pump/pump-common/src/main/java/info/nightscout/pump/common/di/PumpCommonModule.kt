package info.nightscout.pump.common.di

import dagger.Module
import app.aaps.pump.common.di.PumpCommonModuleAbstract
import app.aaps.pump.common.di.PumpCommonModuleImpl

@Module(
    includes = [
        PumpCommonModuleAbstract::class,
        PumpCommonModuleImpl::class
    ]
)
open class PumpCommonModule
