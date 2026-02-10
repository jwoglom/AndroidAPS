package app.aaps.pump.tandem.mobi

import com.jwoglom.pumpx2.BuildConfig

class TandemMobiPluginVersion {

    val devVersion = "3.4.0.0-dev (04.02.2026)"

    val pumpX2Version = BuildConfig.PUMPX2_VERSION
    val tandemModuleVersion = "v0.7.10.0"

    companion object {
        val connectionFixerEnabled = false   // this is in testing for now

        @JvmStatic
        val downloadHistory = true // it seems we have some issues with history download on new versions

    }
}
