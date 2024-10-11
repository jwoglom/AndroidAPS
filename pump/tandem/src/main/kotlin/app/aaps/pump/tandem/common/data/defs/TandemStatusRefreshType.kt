package app.aaps.pump.tandem.common.data.defs

import app.aaps.pump.common.driver.connector.defs.PumpCommandType

/**
 * Created by andy on 04.07.2022.
 */
enum class TandemStatusRefreshType(//    public PumpCommandType getCommandType(YpsoPumpFirmware medtronicDeviceType) {
    val refreshTime: Int, private val commandType: PumpCommandType
) {

    PumpHistory(5, PumpCommandType.GetHistory),  //

    //Configuration(0, null), //
    RemainingInsulin(-1, PumpCommandType.GetRemainingInsulin),  //
    BatteryStatus(55, PumpCommandType.GetBatteryStatus),  //
    PumpTime(60, PumpCommandType.GetTime) //

    ////        if (this == Configuration) {
    ////            return MedtronicCommandType.getSettings(medtronicDeviceType);
    ////        } else
    //            return commandType;
    //    }
}
