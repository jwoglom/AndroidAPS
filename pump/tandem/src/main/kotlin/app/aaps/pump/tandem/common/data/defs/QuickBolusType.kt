package app.aaps.pump.tandem.common.data.defs

import androidx.annotation.StringRes
import app.aaps.pump.tandem.R

// TODO(jwoglom): replace with https://github.com/jwoglom/pumpX2/blob/main/messages/src/main/java/com/jwoglom/pumpx2/pump/messages/request/control/SetQuickBolusSettingsRequest.java#L84
enum class QuickBolusType(@StringRes val friendlyName: Int)  {

    DISABLED(R.string.pump_quick_bolus_disabled),
    UNITS_0_5(R.string.pump_quick_bolus_units_0_5),
    UNITS_1_0(R.string.pump_quick_bolus_units_1_0),
    UNITS_2_O(R.string.pump_quick_bolus_units_2_0),
    UNITS_5_0(R.string.pump_quick_bolus_units_5_0),
    CARBS_2G(R.string.pump_quick_bolus_carbs_2g),
    CARBS_5G(R.string.pump_quick_bolus_carbs_5g),
    CARBS_10G(R.string.pump_quick_bolus_carbs_10g),
    CARBS_15G(R.string.pump_quick_bolus_carbs_15g),

}