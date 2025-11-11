package app.aaps.pump.tandem.t_mobi

import com.jwoglom.pumpx2.BuildConfig

class TandemMobiPluginVersion {

    val devVersion = "3.3.3.0-dev-d (04.11.2025)"

    val pumpX2Version = BuildConfig.PUMPX2_VERSION
    val tandemModuleVersion = "v0.7.3.4"

    companion object {
        @JvmStatic
        val connectionFixerEnabled = false   // this is in testing for now

        @JvmStatic
        val downloadHistory = true // it seems we have some issues with history download on new versions

    }
}
