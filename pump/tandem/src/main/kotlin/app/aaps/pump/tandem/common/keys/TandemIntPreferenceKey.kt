package app.aaps.pump.tandem.common.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.pump.tandem.R

enum class TandemIntPreferenceKey(
    override val key: String,
    override val defaultValue: Int,
    override val min: Int = Int.MIN_VALUE,
    override val max: Int = Int.MAX_VALUE,
    override val calculatedDefaultValue: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = true
) : IntPreferenceKey {

    MaxBasal("pref_tandem_max_basal", 15, min = 1, max = 15),
    MaxBolus("pref_tandem_max_bolus", 25, min = 1, max = 25),
    PumpPairStatus("pref_tandem_pair_status", -1)

    //@JvmField val PumpPairStatus = R.string.key_tandem_pair_status

}