package app.aaps.pump.tandem.common.database.data

import app.aaps.pump.common.defs.PumpHistoryEntryGroup
import app.aaps.pump.tandem.common.database.data.dto.TandemHistoryRecordDto
import app.aaps.pump.tandem.common.database.data.entity.TandemHistoryRecordEntity
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.jwoglom.pumpx2.pump.messages.response.historyLog.AlarmActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.AlertActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BGHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalRateChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolexActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolexCompletedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusCompletedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusRequestedMsg1HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusRequestedMsg2HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusRequestedMsg3HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CannulaFilledHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CarbEnteredHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CartridgeFilledHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmCalibrationGxHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmCalibrationHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataGxHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataSampleHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.ControlIQPcmChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.ControlIQUserModeChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CorrectionDeclinedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DailyBasalHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DataLogCorruptionHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DateChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG7CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.FactoryResetHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HypoMinimizerResumeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HypoMinimizerSuspendHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.IdpActionHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.IdpActionMsg2HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.IdpBolusHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.IdpListHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.IdpTimeDependentSegmentHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.LogErasedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.NewDayHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.ParamChangeGlobalSettingsHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.ParamChangePumpSettingsHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.ParamChangeRemSettingsHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.ParamChangeReminderHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.PumpingResumedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.PumpingSuspendedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateCompletedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TimeChangedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TubingFilledHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.UnknownHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.UsbConnectedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.UsbDisconnectedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.UsbEnumeratedHistoryLog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DbDataConverter @Inject constructor(
    val tandemPumpStatus: TandemPumpStatus,
    val tandemPumpUtil: TandemPumpUtil) {

    val historyLogParser = HistoryLogParser()
    //val pumpStatus = MainAppData.tandemPumpStatus


    /**
     * Get TandemHistoryRecordEntity from historyLog
     */
    fun getTandemHistoryRecordEntity(historyLog: HistoryLog): TandemHistoryRecordEntity {

        val now = System.currentTimeMillis()

        return TandemHistoryRecordEntity(
            sequenceId = historyLog.sequenceNum,
            pumpSerial = tandemPumpStatus.serialNumber.toInt(),
            typeId = historyLog.typeId(),
            pumpTime = historyLog.pumpTimeSecInstant.toEpochMilli(),
            payload = historyLog.cargo,
            entitySubId = getEntitySubId(historyLog = historyLog),
            entitySubIdName = getEntitySubIdName(historyLog = historyLog),
            createdAt = now,
            updatedAt = now
        )

    }

    /**
     * Get TandemHistoryRecordDto from Entity object
     */
    fun getHistoryRecordDto(entity: TandemHistoryRecordEntity) : TandemHistoryRecordDto {

        val historyLog = HistoryLogParser.parse(entity.payload)

        val dto = TandemHistoryRecordDto(
            pumpTime = historyLog.pumpTimeSecInstant.toEpochMilli(),
            historyLog = historyLog,
            group = determineGroup(historyLog = historyLog),
            sequenceId = entity.sequenceId,
            entitySubId = entity.entitySubId,
            name = parseName(historyLog = historyLog),
            description = getDescription(historyLog = historyLog)
        )

        return dto
    }


    private fun getEntitySubId(historyLog: HistoryLog?): Int? {
        // TODO implement get getEntitySubId for TandemHistoryRecordDto
        return when(historyLog) {
            is BolusActivatedHistoryLog -> {
                historyLog.bolusId
            }
            is BolusCompletedHistoryLog -> {
                historyLog.bolusId
            }
            is CorrectionDeclinedHistoryLog -> {
                historyLog.bolusId
            }
            is BolexActivatedHistoryLog -> {
                historyLog.bolusId
            }
            is BolexCompletedHistoryLog -> {
                historyLog.bolusId
            }
            is BolusDeliveryHistoryLog -> {
                historyLog.bolusID
            }
            is BolusRequestedMsg1HistoryLog -> {
                historyLog.bolusId
            }
            is BolusRequestedMsg2HistoryLog -> {
                historyLog.bolusId
            }
            is BolusRequestedMsg3HistoryLog -> {
                historyLog.bolusId
            }
            is TempRateActivatedHistoryLog -> {
                historyLog.tempRateId
            }
            is TempRateCompletedHistoryLog -> {
                historyLog.tempRateId
            }

            is IdpActionHistoryLog,
            is IdpActionMsg2HistoryLog,
            is IdpBolusHistoryLog,
            is IdpListHistoryLog,
            is IdpTimeDependentSegmentHistoryLog,
            is BasalRateChangeHistoryLog,
            is BasalDeliveryHistoryLog, // -> PumpHistoryEntryGroup.Basal

            is BGHistoryLog,
            is DexcomG6CGMHistoryLog,
            is DexcomG7CGMHistoryLog,
            is CgmCalibrationGxHistoryLog,
            is CgmCalibrationHistoryLog,
            is CgmDataGxHistoryLog,
            is CgmDataSampleHistoryLog, // -> PumpHistoryEntryGroup.Glucose

            is CarbEnteredHistoryLog,
            // is BolexActivatedHistoryLog,
            // is BolexCompletedHistoryLog,
            // is BolusDeliveryHistoryLog,
            // is BolusRequestedMsg1HistoryLog,
            // is BolusRequestedMsg2HistoryLog,
            // is BolusRequestedMsg3HistoryLog, // -> PumpHistoryEntryGroup.Bolus

            is NewDayHistoryLog,
            is PumpingResumedHistoryLog,
            is PumpingSuspendedHistoryLog, // -> PumpHistoryEntryGroup.Base

            is DataLogCorruptionHistoryLog,
            is DateChangeHistoryLog,
            is FactoryResetHistoryLog,
            is LogErasedHistoryLog,
            is TimeChangedHistoryLog,
            is ParamChangeGlobalSettingsHistoryLog,
            is ParamChangePumpSettingsHistoryLog,
            is ParamChangeReminderHistoryLog,
            is ParamChangeRemSettingsHistoryLog, // -> PumpHistoryEntryGroup.Configuration

            is TubingFilledHistoryLog,
            is CartridgeFilledHistoryLog,
            is CannulaFilledHistoryLog, // -> PumpHistoryEntryGroup.Prime


                // no subId


            is UsbConnectedHistoryLog,
            is UsbDisconnectedHistoryLog,
            is UsbEnumeratedHistoryLog,
            is ControlIQPcmChangeHistoryLog,
            is ControlIQUserModeChangeHistoryLog,
            is HypoMinimizerResumeHistoryLog,
            is HypoMinimizerSuspendHistoryLog,
            is DailyBasalHistoryLog,
            is AlarmActivatedHistoryLog,
            is AlertActivatedHistoryLog -> null


            else -> null
        }

    }


    private fun getEntitySubIdName(historyLog: HistoryLog?): String? {

        return when(historyLog) {
            is BolusActivatedHistoryLog,
            is CorrectionDeclinedHistoryLog,
            is BolexActivatedHistoryLog,
            is BolexCompletedHistoryLog,
            is BolusDeliveryHistoryLog,
            is BolusRequestedMsg1HistoryLog,
            is BolusRequestedMsg2HistoryLog,
            is BolusRequestedMsg3HistoryLog,
            is BolusCompletedHistoryLog -> {
                "bolusId"
            }

            is TempRateActivatedHistoryLog,
            is TempRateCompletedHistoryLog -> {
                "tempRateId"
            }

            else -> null
        }

    }


    private fun getDescription(historyLog: HistoryLog?): String? {
        // TODO implement get Description for TandemHistoryRecordDto
        return when(historyLog) {
            is BolusActivatedHistoryLog -> {
                "Amount: ${historyLog.bolusSize} U, BolusId=${historyLog.bolusId}"
            }
            is BolusCompletedHistoryLog -> {
                "Requested: ${historyLog.insulinRequested} U, Delivered: ${historyLog.insulinDelivered} U, Status: ${historyLog.completionStatus.name}, BolusId=${historyLog.bolusId}"
            }
            is DailyBasalHistoryLog -> {
                "Daily Total: ${historyLog.dailyTotalBasal}"
            }
            is CorrectionDeclinedHistoryLog -> {
                ", BolusId=${historyLog.bolusId}"

            }
//            public class CorrectionDeclinedHistoryLog extends HistoryLog {
//                private int bg;
//                private int bolusId;
//                private float iob;
//                private int targetBg;
//                private int isf;

            is AlarmActivatedHistoryLog -> {
                "Alarm: ${historyLog.alarmResponseType.name}"
            }
            is AlertActivatedHistoryLog-> {
                "Alert: ${historyLog.alertResponseType.name}"
            }



            is TempRateActivatedHistoryLog,
            is TempRateCompletedHistoryLog,
            is IdpActionHistoryLog,
            is IdpActionMsg2HistoryLog,
            is IdpBolusHistoryLog,
            is IdpListHistoryLog,
            is IdpTimeDependentSegmentHistoryLog,
            is BasalRateChangeHistoryLog,
            is BasalDeliveryHistoryLog,

            is BGHistoryLog,
            is DexcomG6CGMHistoryLog,
            is DexcomG7CGMHistoryLog,
            is CgmCalibrationGxHistoryLog,
            is CgmCalibrationHistoryLog,
            is CgmDataGxHistoryLog,
            is CgmDataSampleHistoryLog,

            is CarbEnteredHistoryLog, //-> { historyLog.},
            is BolexActivatedHistoryLog,
            is BolexCompletedHistoryLog,
            is BolusDeliveryHistoryLog,
            is BolusRequestedMsg1HistoryLog,
            is BolusRequestedMsg2HistoryLog,
            is BolusRequestedMsg3HistoryLog, // -> PumpHistoryEntryGroup.Bolus

            is NewDayHistoryLog,
            is PumpingResumedHistoryLog,
            is PumpingSuspendedHistoryLog, // -> PumpHistoryEntryGroup.Base

            is DataLogCorruptionHistoryLog,
            is DateChangeHistoryLog,
            is FactoryResetHistoryLog,
            is LogErasedHistoryLog,
            is TimeChangedHistoryLog,
            is ParamChangeGlobalSettingsHistoryLog,
            is ParamChangePumpSettingsHistoryLog,
            is ParamChangeReminderHistoryLog,
            is ParamChangeRemSettingsHistoryLog, // -> PumpHistoryEntryGroup.Configuration

            is TubingFilledHistoryLog,
            is CartridgeFilledHistoryLog,
            is CannulaFilledHistoryLog, // -> PumpHistoryEntryGroup.Prime



            is ControlIQPcmChangeHistoryLog, //-> { historyLog.}
            is ControlIQUserModeChangeHistoryLog, //




            is HypoMinimizerResumeHistoryLog,
            is HypoMinimizerSuspendHistoryLog, // -> PumpHistoryEntryGroup.IntegratedLoop

                // checked

            is UsbConnectedHistoryLog,
            is UsbDisconnectedHistoryLog,
            is UsbEnumeratedHistoryLog -> null

            else -> null
        }

    }

    fun parseName(historyLog: HistoryLog) : String {
        if (historyLog is UnknownHistoryLog) {
            return "Unknown ${historyLog.typeId()}"
        } else {
            var className = historyLog.javaClass.simpleName
            className = className.replace("HistoryLog", "")

            // TODO split name by uppercase characters for TandemHistoryRecordDto

            return className
        }
    }


    fun determineGroup(historyLog: HistoryLog): PumpHistoryEntryGroup {
        return when(historyLog) {
            is TempRateActivatedHistoryLog,
            is TempRateCompletedHistoryLog,
            is IdpActionHistoryLog,
            is IdpActionMsg2HistoryLog,
            is IdpBolusHistoryLog,
            is IdpListHistoryLog,
            is IdpTimeDependentSegmentHistoryLog,
            is BasalRateChangeHistoryLog,
            is BasalDeliveryHistoryLog -> PumpHistoryEntryGroup.Basal

            is BGHistoryLog,
            is DexcomG6CGMHistoryLog,
            is DexcomG7CGMHistoryLog,
            is CgmCalibrationGxHistoryLog,
            is CgmCalibrationHistoryLog,
            is CgmDataGxHistoryLog,
            is CgmDataSampleHistoryLog -> PumpHistoryEntryGroup.Glucose

            is CarbEnteredHistoryLog,
            is BolexActivatedHistoryLog,
            is BolexCompletedHistoryLog,
            is BolusActivatedHistoryLog,
            is BolusCompletedHistoryLog,
            is BolusDeliveryHistoryLog,
            is BolusRequestedMsg1HistoryLog,
            is BolusRequestedMsg2HistoryLog,
            is BolusRequestedMsg3HistoryLog -> PumpHistoryEntryGroup.Bolus

            is NewDayHistoryLog,
            is PumpingResumedHistoryLog,
            is PumpingSuspendedHistoryLog -> PumpHistoryEntryGroup.Base

            is DataLogCorruptionHistoryLog,
            is DateChangeHistoryLog,
            is FactoryResetHistoryLog,
            is LogErasedHistoryLog,
            is TimeChangedHistoryLog,
            is ParamChangeGlobalSettingsHistoryLog,
            is ParamChangePumpSettingsHistoryLog,
            is ParamChangeReminderHistoryLog,
            is ParamChangeRemSettingsHistoryLog -> PumpHistoryEntryGroup.Configuration

            is TubingFilledHistoryLog,
            is CartridgeFilledHistoryLog,
            is CannulaFilledHistoryLog -> PumpHistoryEntryGroup.Prime

            is AlarmActivatedHistoryLog,
            is AlertActivatedHistoryLog -> PumpHistoryEntryGroup.Alarm

            is UsbConnectedHistoryLog,
            is UsbDisconnectedHistoryLog,
            is UsbEnumeratedHistoryLog  -> PumpHistoryEntryGroup.Other

            is ControlIQPcmChangeHistoryLog,
            is ControlIQUserModeChangeHistoryLog,
            is CorrectionDeclinedHistoryLog,
            is HypoMinimizerResumeHistoryLog,
            is HypoMinimizerSuspendHistoryLog -> PumpHistoryEntryGroup.IntegratedLoop

            is DailyBasalHistoryLog -> PumpHistoryEntryGroup.Statistic

            else -> PumpHistoryEntryGroup.Unknown
        }
    }

    fun getRawData(historyLog: HistoryLog) : String {
        var historyLogData = tandemPumpUtil.gsonRegular.toJson(historyLog)

        val elementRoot: JsonElement = JsonParser.parseString(historyLogData)

        var elementMap : MutableMap<String,String> = mutableMapOf()
        printElement(elementRoot, "", elementMap)

        return ""
    }

    private fun printElement(element: JsonElement, indent: String, elementMap : MutableMap<String,String>) {
        if (element.isJsonObject()) {
            element.getAsJsonObject().entrySet().forEach { entry ->
                println(indent + entry.key + ":")
                printElement(entry.value, "$indent  ", elementMap)

                elementMap.put(entry.key, entry.value.asString)
            }
        } else if (element.isJsonArray()) {
            //element.getAsJsonArray().forEach { e -> printElement(e, "$indent  ", elementMap) }
        } else {
            ///if (element)
            //println(indent + element.getAsString())
            //return element.asString
        }
    }




}