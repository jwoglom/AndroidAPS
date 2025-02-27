package app.aaps.pump.tandem.common.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.Profile.ProfileValue
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.utils.pump.ByteUtil
import app.aaps.pump.common.data.BasalProfileDto
import app.aaps.pump.common.defs.TempBasalPair
import app.aaps.pump.common.driver.connector.commands.response.DataCommandResponse
import app.aaps.pump.common.driver.connector.defs.PumpCommandType
import app.aaps.pump.common.driver.history.PumpDataConverter
import app.aaps.pump.tandem.common.data.IDPSegmentDto
import app.aaps.pump.tandem.common.data.defs.TandemPumpHistoryType
import app.aaps.pump.tandem.common.data.history.Bolus
import app.aaps.pump.tandem.common.data.history.DateTimeChanged
import app.aaps.pump.tandem.common.data.history.HistoryLogDto
import app.aaps.pump.tandem.common.data.history.HistoryLogObject
import app.aaps.pump.tandem.common.data.history.TemporaryBasal
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.helpers.Dates
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BasalLimitSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQInfoV1Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQInfoV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.GlobalMaxBolusSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSegmentResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TempRateResponse
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
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CGMHistoryLog
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
import com.jwoglom.pumpx2.pump.messages.response.historyLog.FactoryResetHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
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
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateCompletedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TimeChangedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TubingFilledHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.UnknownHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.UsbConnectedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.UsbDisconnectedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.UsbEnumeratedHistoryLog
import org.joda.time.DateTime
import javax.inject.Inject

class TandemDataConverter @Inject constructor(
    var aapsLogger: AAPSLogger,
    var sp: SP,
    var pumpStatus: TandemPumpStatus,
    var pumpUtil: TandemPumpUtil
) : PumpDataConverter {

    var tbrMap = HashMap<Int, TemporaryBasal>()


    // fun convertMessageToDataCommandResponse(message: Message): DataCommandResponse<Any?> {
    //
    //     when(message) {
    //
    //         // Battery Level
    //         is CurrentBatteryV1Response,
    //         is CurrentBatteryV2Response         -> return getBatteryResponse(message as CurrentBatteryAbstractResponse)
    //
    //         // Configuration
    //         // is ControlIQInfoV1Response,
    //         // is ControlIQInfoV2Response          -> return getControlIQEnabled(message)
    //         // is BasalLimitSettingsResponse       -> return message.basalLimit
    //         // is GlobalMaxBolusSettingsResponse   -> return message.maxBolus
    //
    //         // Insulin Level
    //         is InsulinStatusResponse            -> return getInsulinStatus(message)
    //
    //
    //
    //
    //         else                           -> {
    //             aapsLogger.warn(LTag.PUMPCOMM, "Can't convert Tandem response Message of type: ${message.opCode()} and class: ${messageClass.name}")
    //             return null
    //         }
    //     }
    //
    //     return null
    // }



    fun convertMessageToValue(message: Message) : Any? {

        when(message) {

            // Battery Level
            //is CurrentBatteryV1Response,
            //is CurrentBatteryV2Response         -> return getBatteryResponse(message as CurrentBatteryAbstractResponse)

            // Configuration
            is ControlIQInfoV1Response,
            is ControlIQInfoV2Response          -> return getControlIQEnabled(message)
            is BasalLimitSettingsResponse       -> return message.basalLimit
            is GlobalMaxBolusSettingsResponse   -> return message.maxBolus

            // Insulin Level
            //is InsulinStatusResponse            -> return getInsulinStatus(message)




            else                           -> {
                aapsLogger.warn(LTag.PUMPCOMM, "Can't convert Tandem response Message of type: ${message.opCode()} and class: ${message.javaClass.name}")
                return null
            }
        }

    }

    fun getInsulinStatus(message: InsulinStatusResponse): DataCommandResponse<Double?> {
        return DataCommandResponse(
            PumpCommandType.GetRemainingInsulin, true, null, message.currentInsulinAmount.toDouble())
    }

    fun getTempBasalRate(message: TempRateResponse): DataCommandResponse<TempBasalPair?> {

        val tempBasal = TempBasalPair()

        tempBasal.insulinRate = message.percentage.toDouble()
        tempBasal.isActive = message.active
        tempBasal.durationMinutes = message.duration.toInt()
        // TODO problem with 1.4.4
        //tempBasal.setStartTime(pumpUtil.getTimeFromPumpAsEpochMillis(message.startTime))

        return DataCommandResponse(
            PumpCommandType.GetTemporaryBasal, true, null, tempBasal)
    }


    fun getBasalProfileResponse(settings: IDPSettingsResponse, mapSegments: MutableMap<Int, IDPSegmentResponse>): DataCommandResponse<BasalProfileDto?> {

        val basalArray: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                                                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                                                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        for(time in 0..23) {
            basalArray[time] = findCorrectValueInSegments((time * 100), mapSegments.values)
        }

        val basalProfile = BasalProfileDto(basalArray, settings.name)

        aapsLogger.info(LTag.PUMPCOMM, "Received Basal Profile: $basalProfile")

        return DataCommandResponse(
            PumpCommandType.GetBasalProfile, true, null, basalProfile
        )
    }

    private fun findCorrectValueInSegments(targetTimeAsSeconds: Int, profileValueList : MutableCollection<IDPSegmentResponse>) : Double {
        var targetValue = 0.0
        for (profileValue in profileValueList) {
            if (profileValue.profileStartTime <= targetTimeAsSeconds) {
                targetValue = profileValue.profileBasalRate/1000.0;
            }
        }

        return targetValue
    }



    fun getIDPSegmentsFromProfile(profile: Profile) : List<IDPSegmentDto> {

        val segmentList: ArrayList<IDPSegmentDto> = arrayListOf()

        val basVals = profile.getBasalValues()
        var index = 0

        for (basVal in basVals) {
            val idp = IDPSegmentDto()

            idp.segmentIndex = index
            idp.profileStartTime = basVal.timeAsSeconds/60
            idp.profileBasalRate = (basVal.value * 1000).toInt()

            idp.profileTargetBG = findCorrectValueInProfile(basVal.timeAsSeconds, profile.getSingleTargetsMgdl()).toInt()

            // TODO carbratio  & isf (insulin sentivity factor = mgdl) at the moment ignored (not needed for AAPS)
            //idp.profileCarbRatio = findCorrectValueInProfile(basVal.timeAsSeconds, profile.getIcsValues()).toLong()
            //idp.profileISF = findCorrectValueInProfile(basVal.timeAsSeconds, profile.getIsfsMgdlValues()).toInt()

            idp.profileCarbRatio = 0
            idp.profileISF = 0

            segmentList.add(idp)

            index++
        }

        return segmentList
    }

    private fun findCorrectValueInProfile(targetTimeAsSeconds: Int, profileValueList : Array<ProfileValue>) : Double {
        var targetValue = 0.0
        for (profileValue in profileValueList) {
            if (profileValue.timeAsSeconds <= targetTimeAsSeconds) {
                targetValue = profileValue.value
            }
        }

        return targetValue
    }


    private fun getBasalLimit(message: BasalLimitSettingsResponse): Long {
        return message.basalLimit
    }

    private fun getControlIQEnabled(message: Message): Boolean {
        if (message is ControlIQInfoV1Response)
            return message.closedLoopEnabled
        else if (message is ControlIQInfoV2Response)
            return message.closedLoopEnabled

        return false
    }

    fun getBatteryResponse(message: CurrentBatteryAbstractResponse): DataCommandResponse<Int?> {
        return DataCommandResponse(
            PumpCommandType.GetBatteryStatus, true, null, message.currentBatteryIbc)
    }


    //CGMHistoryLog

    fun decodeHistoryLogs(historyLogList: List<HistoryLog>): MutableList<HistoryLogDto> {
        var historyListOut = mutableListOf<HistoryLogDto>()

        for (historyLog in historyLogList) {
            val decodeHistoryLog = decodeHistoryLog(historyLog)
            if (decodeHistoryLog!=null) {
                historyListOut.add(decodeHistoryLog)
            }
        }

        return historyListOut
    }



    fun decodeHistoryLog(historyLogPump: HistoryLog): HistoryLogDto? {

        // TODO this is not correctly implemented yet
        if (!isLogTypeSupported(historyLogPump)) {
            return null
        }

        val historyLog = createHistoryLogDto(historyLogPump)

        when (historyLogPump) {

            // Date Time - WIP
            is TimeChangedHistoryLog           -> historyLog.subObject = createTimeChangeRecord(historyLogPump)
            is DateChangeHistoryLog            -> historyLog.subObject = createDateChangeRecord(historyLogPump)

            // Bolus - WIP
            is BolusCompletedHistoryLog        -> historyLog.subObject = createBolusRecord(historyLogPump)
            is BolexCompletedHistoryLog        -> historyLog.subObject = createBolusRecord(historyLogPump)
            is BolexActivatedHistoryLog        -> historyLog.subObject = createBolusRecord(historyLogPump)
            is BolusActivatedHistoryLog        -> historyLog.subObject = createBolusRecord(historyLogPump)

            // Pump Status Changes - WIP
            // is PumpingResumedHistoryLog        -> historyLog.subObject = PumpStatusChanged(PumpStatusType.PumpRunning)
            // is PumpingSuspendedHistoryLog      -> historyLog.subObject = PumpStatusChanged(PumpStatusType.PumpSuspended, historyLogPump.reasonId) // TODO maybe different handling?

            // TBR
            is TempRateActivatedHistoryLog     -> historyLog.subObject = createTBRRecord(historyLogPump)
            is TempRateCompletedHistoryLog     -> historyLog.subObject = createTBRRecord(historyLogPump)

            // Alarm/Alert
            is AlarmActivatedHistoryLog        -> historyLog.subObject = createAlarmRecord(historyLogPump)
            is AlertActivatedHistoryLog        -> historyLog.subObject = createAlertRecord(historyLogPump)


            // not implemented yet

            // Bolus
            is BolusDeliveryHistoryLog,
            //is BolexActivatedHistoryLog,
            //is BolusActivatedHistoryLog,
            is CarbEnteredHistoryLog,

            // Bolus Delivery
            is BolusRequestedMsg1HistoryLog,
            is BolusRequestedMsg2HistoryLog,
            is BolusRequestedMsg3HistoryLog,

            // Basal
            is BasalDeliveryHistoryLog,
            is BasalRateChangeHistoryLog,



            // Configuration Changes
            is ParamChangeGlobalSettingsHistoryLog,
            is ParamChangePumpSettingsHistoryLog,

            // Pump Status Changes
            is CannulaFilledHistoryLog,
            is CartridgeFilledHistoryLog,
            is TubingFilledHistoryLog,




            is  CorrectionDeclinedHistoryLog,
            is  DailyBasalHistoryLog,
            is  DataLogCorruptionHistoryLog,
            is  FactoryResetHistoryLog,
            is  HypoMinimizerResumeHistoryLog,
            is  HypoMinimizerSuspendHistoryLog,


            is  LogErasedHistoryLog,
            is  NewDayHistoryLog,



            //is  ,
            is  UnknownHistoryLog,





            // not supported (and won't be supported)
                    -> historyLog.subObject = null

            else                               -> historyLog.subObject = null
        }



        return if (historyLog.subObject==null) null else historyLog

    }


    private fun isLogTypeSupported(historyLogPump: HistoryLog): Boolean {

        when (historyLogPump) {
            is IdpActionHistoryLog,
            is IdpActionMsg2HistoryLog,
            is IdpBolusHistoryLog,
            is IdpListHistoryLog,
            is IdpTimeDependentSegmentHistoryLog,
            is UsbConnectedHistoryLog,
            is UsbDisconnectedHistoryLog,
            is UsbEnumeratedHistoryLog,
            is CgmCalibrationGxHistoryLog,
            is CgmCalibrationHistoryLog,
            is CgmDataGxHistoryLog,
            is CgmDataSampleHistoryLog,
            is CGMHistoryLog,
            is ParamChangeReminderHistoryLog,
            is ParamChangeRemSettingsHistoryLog,
            is ControlIQPcmChangeHistoryLog,
            is ControlIQUserModeChangeHistoryLog,
            is BGHistoryLog                             -> return false

            else                                        -> return true
        }

    }



    private fun test() {

        // BasalDeliveryHistoryLog.java
        // BasalRateChangeHistoryLog.java
        // BGHistoryLog.java
        // BolexActivatedHistoryLog.java
        // BolexCompletedHistoryLog.java
        // BolusActivatedHistoryLog.java
        // BolusCompletedHistoryLog.java
        // BolusDeliveryHistoryLog.java
        // BolusRequestedMsg1HistoryLog.java
        // BolusRequestedMsg2HistoryLog.java
        // BolusRequestedMsg3HistoryLog.java
        // CannulaFilledHistoryLog.java
        // CarbEnteredHistoryLog.java
        // CartridgeFilledHistoryLog.java
        // CgmCalibrationGxHistoryLog.java
        // CgmCalibrationHistoryLog.java
        // CgmDataGxHistoryLog.java
        // CgmDataSampleHistoryLog.java
        // CGMHistoryLog.java
        // ControlIQPcmChangeHistoryLog.java
        // ControlIQUserModeChangeHistoryLog.java
        // CorrectionDeclinedHistoryLog.java
        // DailyBasalHistoryLog.java
        // DataLogCorruptionHistoryLog.java
        // DateChangeHistoryLog.java
        // FactoryResetHistoryLog.java
        //
        // HypoMinimizerResumeHistoryLog.java
        // HypoMinimizerSuspendHistoryLog.java
        //
        // LogErasedHistoryLog.java
        // NewDayHistoryLog.java
        // ParamChangeGlobalSettingsHistoryLog.java
        // ParamChangePumpSettingsHistoryLog.java
        // ParamChangeReminderHistoryLog.java
        // ParamChangeRemSettingsHistoryLog.java
        // PumpingResumedHistoryLog.java
        // PumpingSuspendedHistoryLog.java
        //
        // TempRateActivatedHistoryLog.java
        // TempRateCompletedHistoryLog.java
        // TimeChangedHistoryLog.java
        // TubingFilledHistoryLog.java
        // UnknownHistoryLog.java




    }







    private fun createBolusRecord(bolusLog: BolexCompletedHistoryLog): HistoryLogObject {

        val bolus = Bolus(bolusId = bolusLog.bolusId,
                          immediateAmount = bolusLog.insulinDelivered.toDouble(),
                          isCancelled = false,
                          isRunning = bolusLog.completionStatus == 0  // TODO createBolusRecord::completionStatus Bolus
        )

        return bolus

    }

    private fun createBolusRecord(historyLogPump: BolusActivatedHistoryLog): HistoryLogObject? {
        TODO("createBolusRecord(BolusActivatedHistoryLog) Not yet implemented")
    }

    private fun createBolusRecord(historyLogPump: BolexActivatedHistoryLog): HistoryLogObject? {
        TODO("createBolusRecord(BolexActivatedHistoryLog)  Not yet implemented")
    }


    private fun createTBRRecord(historyLogPump: TempRateActivatedHistoryLog): HistoryLogObject? {
        val temporaryBasal = TemporaryBasal(
                    percent = historyLogPump.percent.toInt(),
                    minutes = historyLogPump.duration.toInt(),
                    isRunning = true,
                    tempRateId = historyLogPump.tempRateId
                )
        tbrMap.put(historyLogPump.tempRateId, temporaryBasal)

        return temporaryBasal
    }


    private fun createTBRRecord(historyLogPump: TempRateCompletedHistoryLog): HistoryLogObject? {
        // TODO("createTBRRecord(TempRateCompletedHistoryLog)  Not yet implemented")
        //return TemporaryBasal(historyLogPump.)




        historyLogPump.tempRateId
        historyLogPump.timeLeft

        //TemporaryBasal tbr =

        return null
    }






    private fun createBolusRecord(bolusLog: BolusCompletedHistoryLog): HistoryLogObject {

        val bolus = Bolus(bolusId = bolusLog.bolusId,
                          immediateAmount = bolusLog.insulinDelivered.toDouble(),
                          isCancelled = false,
                          isRunning = bolusLog.completionStatusId == 0  // TODO completionStatus Bolus maybe different handling
        )

        return bolus

    }


    private fun createAlertRecord(historyLogPump: AlertActivatedHistoryLog): HistoryLogObject? {
        TODO("createAlertRecord Not yet implemented")
    }

    private fun createAlarmRecord(historyLogPump: AlarmActivatedHistoryLog): HistoryLogObject? {
        TODO("createAlarmRecord Not yet implemented")
    }



    private fun createDateChangeRecord(historyLogPump: DateChangeHistoryLog): HistoryLogObject {
        return createDateTimeChangeRecord(historyLogPump.getDateAfterInstant().toEpochMilli(), false)
    }

    // private fun createDateChangeRecord(historyLogPump: DateChangeResponse): HistoryLogObject {
    //     return createDateTimeChangeRecord(Dates.fromJan12008EpochDaysToDate(historyLogPump.dateAfter).toEpochMilli(), false)
    // }

    private fun createTimeChangeRecord(historyLogPump: TimeChangedHistoryLog): HistoryLogObject {
        return createDateTimeChangeRecord(Dates.fromJan12008EpochDaysToDate(historyLogPump.timeAfter).toEpochMilli(), true)
    }

    private fun createDateTimeChangeRecord(dateTime: Long, timeChanged: Boolean): DateTimeChanged {
        val dt = DateTime().withMillis(dateTime)
        return DateTimeChanged(year = dt.year, month = dt.monthOfYear, day = dt.dayOfMonth,
                               hour = dt.hourOfDay, minute = dt.minuteOfHour, second = dt.secondOfMinute,
                               timeChanged = timeChanged)
    }

    fun createHistoryLogDto(historyLogPump: HistoryLog) : HistoryLogDto {
        return HistoryLogDto(id= null,
                             serial = pumpStatus.serialNumber,
                             historyTypeIndex = historyLogPump.typeId(),
                             historyType = TandemPumpHistoryType.getByCode(historyLogPump.typeId()),
                             dateTimeMillis = historyLogPump.pumpTimeSecInstant.toEpochMilli(),
                             sequenceNum = historyLogPump.sequenceNum,
                             payload = ByteUtil.getCompactString(historyLogPump.cargo),
                             subObject = null)
    }



}