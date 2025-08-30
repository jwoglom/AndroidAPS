package app.aaps.pump.tandem.common.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.Profile.ProfileValue
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.pump.common.data.BasalProfileDto
import app.aaps.pump.common.defs.BolusData
import app.aaps.pump.common.defs.BolusStatus
import app.aaps.pump.common.defs.BolusType
import app.aaps.pump.common.defs.TempBasalPair
import app.aaps.pump.common.driver.connector.commands.response.DataCommandResponse
import app.aaps.pump.common.driver.connector.defs.PumpCommandType
import app.aaps.pump.common.driver.history.PumpDataConverter
import app.aaps.pump.tandem.common.data.IDPSegmentDto
import app.aaps.pump.tandem.common.data.PumpProfileDto
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQInfoV1Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQInfoV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSegmentResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBolusStatusV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TempRateResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import javax.inject.Inject

class TandemDataConverter @Inject constructor(
    var aapsLogger: AAPSLogger,
    var sp: SP,
    var pumpStatus: TandemPumpStatus,
    var pumpUtil: TandemPumpUtil
) : PumpDataConverter {

    //var tbrMap = HashMap<Int, TemporaryBasal>()

    var TAG = LTag.PUMP

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



    // fun convertMessageToValue(message: Message) : Any? {
    //
    //     when(message) {
    //
    //         // Battery Level
    //         //is CurrentBatteryV1Response,
    //         //is CurrentBatteryV2Response         -> return getBatteryResponse(message as CurrentBatteryAbstractResponse)
    //
    //         // Configuration
    //         is ControlIQInfoV1Response,
    //         is ControlIQInfoV2Response          -> return getControlIQEnabled(message)
    //         is BasalLimitSettingsResponse       -> return message.basalLimit
    //         is GlobalMaxBolusSettingsResponse   -> return message.maxBolus
    //
    //         // Insulin Level
    //         //is InsulinStatusResponse            -> return getInsulinStatus(message)
    //
    //
    //
    //
    //         else                           -> {
    //             aapsLogger.warn(LTag.PUMPCOMM, "Can't convert Tandem response Message of type: ${message.opCode()} and class: ${message.javaClass.name}")
    //             return null
    //         }
    //     }
    //
    // }


    fun getInsulinStatus(message: InsulinStatusResponse): DataCommandResponse<Double?> {
        return DataCommandResponse(
            PumpCommandType.GetRemainingInsulin, true, null, message.currentInsulinAmount.toDouble())
    }


    fun getBolus(message: LastBolusStatusV2Response): DataCommandResponse<BolusData?> {

        var jsonVal = pumpUtil.gson.toJson(message)
        aapsLogger.info(TAG, "BOLUS: LastBolusStatusV2Response [${jsonVal}]")

        val additionalData = mutableMapOf<String,Any?>()
        additionalData["BolusSource"] = message.bolusSource
        additionalData["BolusSourceId"] = message.bolusSourceId

        val bolusStatus: BolusStatus = BolusStatus.DONE

        val bolusTypes = message.bolusType

        val bolusType: BolusType = if (bolusTypes.contains(element = BolusDeliveryHistoryLog.BolusType.EXTENDED)) {
            BolusType.EXTENDED
        } else {
            BolusType.NORMAL
        }

        val bolusData = BolusData(
            timestamp = message.timestampInstant.toEpochMilli(),
            amountImmediate = message.requestedVolume * 0.001,
            bolusType = bolusType,   // we can't determine if SMB at this point
            bolusId = message.bolusId.toLong(),
            bolusStatus = bolusStatus,
            additionalData = additionalData
        )

        jsonVal = pumpUtil.gson.toJson(message)
        aapsLogger.info(TAG, "BOLUS: BolusData [${jsonVal}]")

        return DataCommandResponse(
            PumpCommandType.GetBolus, true, null, bolusData)

    }


    fun getBolus(message: CurrentBolusStatusResponse?): DataCommandResponse<BolusData?> {

        var jsonVal = pumpUtil.gson.toJson(message)
        aapsLogger.info(TAG, "BOLUS: CurrentBolusStatusResponse [${jsonVal}]")

        if (message==null) {
            return DataCommandResponse(
                PumpCommandType.GetBolus, false, "Bolus data response from pump could not be read.", null)
        }

        val additionalData = mutableMapOf<String,Any?>()
        additionalData["BolusSource"] = message.bolusSource
        additionalData["BolusSourceId"] = message.bolusSourceId

        val bolusStatus: BolusStatus = when (message.status) {
            CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING -> BolusStatus.DELIVERING
            CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING -> BolusStatus.REQUESTED
            else                                                     -> BolusStatus.DONE
        }

        val bolusTypes = message.bolusTypes

        val bolusType: BolusType = if (bolusTypes.contains(element = BolusDeliveryHistoryLog.BolusType.EXTENDED)) {
            BolusType.EXTENDED
        } else {
            BolusType.NORMAL
        }

        val bolusData = BolusData(
            timestamp = message.timestampInstant.toEpochMilli(),
            amountImmediate = message.requestedVolume * 0.001,
            bolusType = bolusType,   // we can't determine if SMB at this point
            bolusId = message.bolusId.toLong(),
            bolusStatus = bolusStatus,
            additionalData = additionalData
        )

        jsonVal = pumpUtil.gson.toJson(message)
        aapsLogger.info(TAG, "BOLUS: BolusData [${jsonVal}]")

        return DataCommandResponse(
            PumpCommandType.GetBolus, true, null, bolusData)
    }


    fun getTempBasalRate(message: TempRateResponse): DataCommandResponse<TempBasalPair?> {

        val jsonVal = pumpUtil.gson.toJson(message)

        aapsLogger.info(TAG, "TBR: TempRateResponse [${jsonVal}]")

        val tempBasal = TempBasalPair( insulinRate = message.percentage.toDouble(),
                                       isPercent = true,
                                       durationMinutes = (message.duration/60.0).toInt(),
                                       start = message.startTimeInstant.toEpochMilli())

        tempBasal.isActive = message.active

        aapsLogger.info(TAG, "TBR: getTempBasalRate [${tempBasal}]")

        return DataCommandResponse(
            PumpCommandType.GetTemporaryBasal, true, null, if (message.active) tempBasal else null)
    }


    fun getBasalProfileResponse(pumpProfileDto: PumpProfileDto): DataCommandResponse<BasalProfileDto?> {

        val basalArray: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                                                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                                                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        val basalProfile: BasalProfileDto

        if (pumpProfileDto.isNewScenario) {
            basalProfile = BasalProfileDto(basalArray, "No Profile")
        } else {
            val settings: IDPSettingsResponse = pumpProfileDto.idpSettingsResponse!!
            val mapSegments: MutableMap<Int, IDPSegmentResponse> = pumpProfileDto.mapSegments
            for(time in 0..23) {
                basalArray[time] = findCorrectValueInSegments((time * 60), mapSegments.values)
            }

            basalProfile = BasalProfileDto(basalArray, settings.name)
        }

        aapsLogger.info(LTag.PUMPCOMM, "Received Basal Profile: $basalProfile")

        return DataCommandResponse(
            PumpCommandType.GetBasalProfile, true, null, basalProfile
        )
    }


    private fun findCorrectValueInSegments(targetTimeAsMinutes: Int, profileValueList : MutableCollection<IDPSegmentResponse>) : Double {
        var targetValue = 0.0
        for (profileValue in profileValueList) {
            if (profileValue.profileStartTime <= targetTimeAsMinutes) {
                targetValue = profileValue.profileBasalRate/1000.0
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
            idp.profileCarbRatio = (findCorrectValueInProfile(basVal.timeAsSeconds, profile.getIcsValues()) * 1000L).toLong()
            idp.profileISF = findCorrectValueInProfile(basVal.timeAsSeconds, profile.getIsfsMgdlValues()).toInt()

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


    // private fun getBasalLimit(message: BasalLimitSettingsResponse): Long {
    //     return message.basalLimit
    // }


    // private fun getControlIQEnabled(message: Message): Boolean {
    //     if (message is ControlIQInfoV1Response)
    //         return message.closedLoopEnabled
    //     else if (message is ControlIQInfoV2Response)
    //         return message.closedLoopEnabled
    //
    //     return false
    // }


    fun getBatteryResponse(message: CurrentBatteryAbstractResponse): DataCommandResponse<Int?> {
        return DataCommandResponse(
            PumpCommandType.GetBatteryStatus, true, null, message.currentBatteryIbc)
    }


}