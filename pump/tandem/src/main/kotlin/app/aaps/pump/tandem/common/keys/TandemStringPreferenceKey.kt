package app.aaps.pump.tandem.common.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.pump.tandem.R
import app.aaps.pump.tandem.common.data.defs.QualifyingEventsFilter
import app.aaps.pump.tandem.common.data.defs.QualifyingEventsRange
import app.aaps.pump.tandem.common.data.defs.QuickBolusType

enum class TandemStringPreferenceKey(
    override val key: String,
    override val defaultValue: String,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val isPassword: Boolean = false,
    override val isPin: Boolean = false,
    override val exportable: Boolean = true
) : StringPreferenceKey {

    PumpSerial("pref_tandem_serial", ""),
    PumpAddress("pref_tandem_address", ""),
    PumpName("pref_tandem_name", ""),
    SharedConnectionData(key="pref_tandem_shared_connection_data", defaultValue = "", dependency = TandemBooleanPreferenceKey.UseSharedConnection, isPassword = true),
    PumpPairCode("pref_tandem_pair_code", ""),
    PumpApiVersion("pref_tandem_api_version", ""),
    PumpVersionResponse("pref_tandem_pump_version", ""),

    PumpHistorySummary("pref_tandem_history_summary", ""),
    QualifyingEventsFilterPref("pref_tandem_qe_filter", QualifyingEventsFilter.ALL.name),
    QualifyingEventsRangePref("pref_tandem_qe_range", QualifyingEventsRange.LAST_15_ITEMS.name),

    QuickBolusTypePref("pref_tandem_quick_bolus", QuickBolusType.DISABLED.name)

    // PumpFrequency("pref_medtronic_frequency", "medtronic_pump_frequency_us_ca"),




    //X @JvmField val PumpSerial = R.string.key_tandem_serial
    //X @JvmField val PumpAddress = R.string.key_tandem_address
    //X @JvmField val PumpName = R.string.key_tandem_name


    // X @JvmField val PumpPairCode = R.string.key_tandem_pair_code
    // X @JvmField val PumpApiVersion = R.string.key_tandem_api_version
    // X @JvmField val PumpVersionResponse = R.string.key_tandem_pump_version
    //

    //
    // X @JvmField val SharedConnectionData = R.string.key_tandem_shared_connection_data



}