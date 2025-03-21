package app.aaps.pump.tandem.t_mobi.ui.actions.other

sealed class Screen(
    val route: String
) {
    object FirstLaunch : Screen("FirstLaunch")
    object PumpSetup : Screen("PumpSetup")
    object AppSetup : Screen("AppSetup")
    object Landing : Screen("Landing")
}
