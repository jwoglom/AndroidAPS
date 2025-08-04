package app.aaps.pump.tandem.common.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class TandemLongNonPreferenceKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongNonPreferenceKey {

    TbrsSet("tandem_tbrs_set", 0),
    SmbBoluses("tandem_smb_boluses_delivered", 0),
    StandardBoluses("tandem_std_boluses_delivered", 0),
    FirstPumpUse("tandem_first_pump_use", 0),
    LastGoodPumpCommunicationTime("tandem_lastGoodPumpCommunicationTime", 0L),
    // LastPumpHistoryEntry("medtronic_pump_history_entry", 0L),
    // LastPrime("medtronic_last_sent_prime", 0L),
    // LastRewind("medtronic_last_sent_rewind", 0L),
    // LastBatteryChange("medtronic_last_sent_battery_change", 0L),

}