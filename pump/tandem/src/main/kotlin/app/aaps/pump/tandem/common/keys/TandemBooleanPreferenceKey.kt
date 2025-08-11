package app.aaps.pump.tandem.common.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.pump.tandem.R

enum class TandemBooleanPreferenceKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val calculatedDefaultValue: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val exportable: Boolean = true,
    override val hideParentScreenIfHidden: Boolean = false,
) : BooleanPreferenceKey {

    DisplayDriverVersion(key = "pref_tandem_display_driver_version", defaultValue = true),
    UseSharedConnection(key = "pref_tandem_use_shared_connection", defaultValue = false),

    ShowUnknownEntriesInHistory(key ="pref_tandem_show_unknowns_in_history", defaultValue = false),
    ShowCargoOfUnknownEntries(key ="", defaultValue = false, dependency = ShowUnknownEntriesInHistory),

    AutoConfirmLowBasalDelivery(key ="", defaultValue = false)



}