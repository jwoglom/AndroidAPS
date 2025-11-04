package app.aaps.pump.tandem.t_mobi

import com.jwoglom.pumpx2.BuildConfig

class TandemMobiPluginVersion {

    val devVersion = "3.3.3.0-dev-c (10.10.2025)"

    val pumpX2Version = BuildConfig.PUMPX2_VERSION
    val tandemModuleVersion = "v0.7.1.2"

    companion object {
        @JvmStatic
        val connectionFixerEnabled = true   // this is in testing for npw
    }
}
