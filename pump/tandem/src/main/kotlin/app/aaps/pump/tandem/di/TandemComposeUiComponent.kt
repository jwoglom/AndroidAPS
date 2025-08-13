package app.aaps.pump.tandem.di

import app.aaps.core.interfaces.ui.compose.ComposeUiFactory
import app.aaps.core.interfaces.ui.compose.ComposeUi
import app.aaps.pump.tandem.t_mobi.ui.ActionsActivity
import app.aaps.pump.tandem.t_mobi.ui.DataActivity
import dagger.Subcomponent

@Subcomponent
interface TandemComposeUiComponent : ComposeUi {
    fun inject(activity: ActionsActivity)
    fun inject(activity: DataActivity)
    // fun inject(activity: PairingActivity)


    @Subcomponent.Factory
    interface FactoryCompose : ComposeUiFactory {
        override fun create(): TandemComposeUiComponent
    }
}