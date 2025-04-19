package app.aaps.pump.tandem.common.driver.connector

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNewNotification
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.pump.common.data.BasalProfileDto
import app.aaps.pump.common.data.PumpTimeDifferenceDto
import app.aaps.pump.common.defs.PumpConfigurationTypeInterface
import app.aaps.pump.common.defs.PumpRunningState
import app.aaps.pump.common.defs.PumpUpdateFragmentType
import app.aaps.pump.common.defs.TempBasalPair
import app.aaps.pump.common.driver.connector.PumpDummyConnector
import app.aaps.pump.common.driver.connector.commands.data.AdditionalResponseDataInterface
import app.aaps.pump.common.driver.connector.commands.data.CustomCommandTypeInterface
import app.aaps.pump.common.driver.connector.commands.data.FirmwareVersionInterface
import app.aaps.pump.common.driver.connector.commands.parameters.PumpHistoryFilterInterface
import app.aaps.pump.common.driver.connector.commands.response.DataCommandResponse
import app.aaps.pump.common.driver.connector.defs.PumpCommandType
import app.aaps.pump.common.events.EventPumpFragmentValuesChanged
import app.aaps.pump.tandem.R
import app.aaps.pump.tandem.common.comm.TandemCommunicationManager
import app.aaps.pump.tandem.common.comm.TandemDataConverter
import app.aaps.pump.tandem.common.data.IDPSegmentDto
import app.aaps.pump.tandem.common.data.PumpProfileDto
import app.aaps.pump.tandem.common.data.defs.TandemCommandType
import app.aaps.pump.tandem.common.data.defs.TandemPumpApiVersion
import app.aaps.pump.tandem.common.data.defs.TandemPumpApiVersion.VERSION_2_1_to_2_4
import app.aaps.pump.tandem.common.data.defs.TandemPumpSettingType
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.driver.connector.def.TandemCustomCommand.*
import app.aaps.pump.tandem.common.driver.connector.def.ControlCommandResponse
import app.aaps.pump.tandem.common.driver.connector.def.TandemCustomCommand
import app.aaps.pump.tandem.common.driver.connector.response.AlarmStatusDto
import app.aaps.pump.tandem.common.driver.connector.response.AlertStatusDto
import app.aaps.pump.tandem.common.driver.connector.response.HomeScreenMirrorDto
import app.aaps.pump.tandem.common.driver.connector.response.PumpVersionDto
import app.aaps.pump.tandem.common.util.PumpX2L
import app.aaps.pump.tandem.common.util.TandemPumpConst
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.bluetooth.TandemConfig
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType
import com.jwoglom.pumpx2.pump.messages.models.StatusMessage
import com.jwoglom.pumpx2.pump.messages.request.control.CancelBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ChangeControlIQSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ChangeTimeDateRequest
import com.jwoglom.pumpx2.pump.messages.request.control.CreateIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetIDPSegmentRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetMaxBasalLimitRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetMaxBolusLimitRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetTempRateRequest
import com.jwoglom.pumpx2.pump.messages.request.control.StopTempRateRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SuspendPumpingRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.AlarmStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.AlertStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.BasalLimitSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ControlIQInfoV1Request
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ControlIQInfoV2Request
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentBatteryV1Request
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentBatteryV2Request
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.GlobalMaxBolusSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.IDPSegmentRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.IDPSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.InsulinStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ProfileStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.PumpFeaturesV1Request
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.PumpFeaturesV2Request
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.PumpVersionRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TempRateRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.response.ErrorResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CancelBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.control.ChangeControlIQSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CreateIDPResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetIDPSegmentResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetMaxBasalLimitResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetMaxBolusLimitResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetTempRateResponse
import com.jwoglom.pumpx2.pump.messages.response.control.StopTempRateResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SuspendPumpingResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlarmStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BasalIQStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BasalLimitSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQInfoV1Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQInfoV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.GlobalMaxBolusSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HomeScreenMirrorResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSegmentResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSegmentResponse.IDPSegmentStatus
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ProfileStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpFeaturesV1Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpFeaturesV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpVersionResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TempRateResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import org.joda.time.DateTime
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TODO
 * All commands that will be supported need to be implemented here (look at PumpConnectorInterface), and they also need
 * to be added to supportedCommandsList.
 *
 * Any command will be used from TandemPumpConnectionManager, if its not used there, then it doesn't need to be
 * implemented.
 *
 *
 */
@Singleton
class TandemPumpConnector @Inject constructor(var tandemPumpStatus: TandemPumpStatus,
                                              var context: Context,
                                              var tandemPumpUtil: TandemPumpUtil,
                                              var rxBus: RxBus,
                                              var resourceHelper: ResourceHelper,
                                              var sp: SP,
                                              aapsLogger: AAPSLogger,
                                              val pumpX2L: PumpX2L,
                                              private var tandemDataConverter: TandemDataConverter
): PumpDummyConnector(tandemPumpStatus, tandemPumpUtil, aapsLogger) {

    private var tandemCommunicationManager: TandemCommunicationManager? = null
    var btAddressUsed: String? = null

    private var TAG = LTag.PUMPCOMM

    // TODO Better Error response handling

    fun getCommunicationManager(): TandemCommunicationManager {
        return tandemCommunicationManager!!
    }


    override fun connectToPump(): Boolean {
        var newBtAddress = sp.getStringOrNull(TandemPumpConst.Prefs.PumpAddress, null)

        aapsLogger.info(TAG, "connectToPump with $newBtAddress")

        if (!btAddressUsed.isNullOrEmpty()) {
            if (btAddressUsed.equals(newBtAddress)) {
                newBtAddress = null
            }
        } else {
            if (newBtAddress.isNullOrEmpty()) {
                return false
            }
        }

        if (!newBtAddress.isNullOrEmpty()) {

            // TODO fix
            PumpState.enableActionsAffectingInsulinDelivery()

            val cfg = TandemConfig()
                .withFilterToBluetoothMac(newBtAddress)
                .withPairingCodeType(PairingCodeType.SHORT_6CHAR)

            this.tandemCommunicationManager = TandemCommunicationManager(
                context = context,
                aapsLogger = aapsLogger,
                sp = sp,
                pumpUtil = tandemPumpUtil,
                pumpStatus = tandemPumpStatus,
                pumpConfig = cfg,
                rxBus = rxBus,
                resourceHelper = resourceHelper,
                timberTree = pumpX2L
            )
            this.btAddressUsed = newBtAddress
        }

        return getCommunicationManager().connect()

    }


    override fun disconnectFromPump(): Boolean {
        aapsLogger.info(TAG, "disconnectFromPump")

        getCommunicationManager().disconnect()
        return true
    }


    override fun retrieveFirmwareVersion(): DataCommandResponse<FirmwareVersionInterface?> {
        val version = sp.getStringOrNull(TandemPumpConst.Prefs.PumpApiVersion, null)

        TODO()

        aapsLogger.info(TAG, "TANDEMDBG: retrieveFirmwareVersion ${version}")

        // if (version!=null) {
        //     this.tandemPumpStatus.tandemPumpFirmware.tandemPumpApiVersion = TandemPumpApiVersion.valueOf(version)
        // } else {
        //     this.tandemPumpApiVersion = TandemPumpApiVersion.VERSION_2_1_to_2_4
        // }

        aapsLogger.info(TAG, "retrieveFirmwareVersion result: ${version}")

        return DataCommandResponse(
            PumpCommandType.GetFirmwareVersion, true, null, this.tandemPumpStatus.tandemPumpFirmware)
    }


    override fun retrieveConfiguration(): DataCommandResponse<MutableMap<PumpConfigurationTypeInterface, Any>?> {

        aapsLogger.info(TAG, "retrieveConfiguration")

        val map:  MutableMap<PumpConfigurationTypeInterface, Any> = mutableMapOf()

        try {
            addToSettings(TandemPumpSettingType.CONTROL_IQ_ENABLED, map, getCommunicationManager().sendCommand(getCorrectRequest(TandemCommandType.ControlIQInfo)))
            addToSettings(TandemPumpSettingType.BASAL_LIMIT, map, getCommunicationManager().sendCommand(BasalLimitSettingsRequest()))
            addToSettings(TandemPumpSettingType.MAX_BOLUS, map, getCommunicationManager().sendCommand(GlobalMaxBolusSettingsRequest()))

            if (this.tandemPumpStatus.tandemPumpFirmware.isSameVersion(VERSION_2_1_to_2_4)) {
                if (tandemPumpStatus.featuresV1==null) {
                    addToSettings(TandemPumpSettingType.PUMP_FEATURES_1, map, getCommunicationManager().sendCommand(PumpFeaturesV1Request()))
                }
            } else {
                if (tandemPumpStatus.featuresV2==null) {
                    addToSettings(TandemPumpSettingType.PUMP_FEATURES_2, map, getCommunicationManager().sendCommand(PumpFeaturesV2Request()))
                }
            }

            val settings = tandemPumpUtil.gson.toJson(map)

            aapsLogger.info(TAG, "retrieveConfiguration result: ${settings}")

            return DataCommandResponse(
                PumpCommandType.GetSettings, true, null, map
            )
        } catch(ex: Exception) {
            return DataCommandResponse(
                PumpCommandType.GetSettings, false, "Problem reading settings. Ex: " + ex.toString(), map)
        }
    }


    private fun addToSettings(settingType: TandemPumpSettingType, settingsMap: MutableMap<PumpConfigurationTypeInterface, Any>, message: Message?) {

        when(message) {
            is ControlIQInfoV1Response -> {  settingsMap.put(settingType, message.closedLoopEnabled) }
            is ControlIQInfoV2Response -> {  settingsMap.put(settingType, message.closedLoopEnabled) }
            is BasalLimitSettingsResponse -> {  settingsMap.put(settingType, message.basalLimit)  }
            is GlobalMaxBolusSettingsResponse -> {  settingsMap.put(settingType, message.maxBolus) }
            is BasalIQStatusResponse  -> {  settingsMap.put(settingType, message.basalIQStatusState)  }
            is PumpFeaturesV2Response -> {
                settingsMap.put(settingType, pumpUtil.gson.toJson(message))
                tandemPumpStatus.featuresV2 = message
            }
            is PumpFeaturesV1Response -> {
                settingsMap.put(settingType, pumpUtil.gson.toJson(message))
                tandemPumpStatus.featuresV1 = message
            }
            is ErrorResponse          -> {
                aapsLogger.error(LTag.PUMPBTCOMM, "Problem with packets from Tandem: requestedCodeId=${message.requestCodeId}, errorCode: ${message.errorCode}")
                throw Exception("Problem with packets from Tandem: requestedCodeId=${message.requestCodeId}, errorCode: ${message.errorCode}")
            }
            null -> {
                aapsLogger.error(LTag.PUMPBTCOMM, "Received null response from Tandem for ${settingType.name}")
                throw Exception("Received null response from Tandem for ${settingType.name}")
            }
        }
    }


    override fun retrieveBatteryStatus(): DataCommandResponse<Int?> {

        aapsLogger.info(LTag.PUMPCOMM, "retrieveBatteryStatus")

        val responseData: DataCommandResponse<Int?> = sendAndReceivePumpData(
            PumpCommandType.GetBatteryStatus,
            getCorrectRequest(TandemCommandType.CurrentBattery))
        {   rawData -> tandemDataConverter.getBatteryResponse(rawData as CurrentBatteryAbstractResponse) }

        aapsLogger.info(TAG, "retrieveBatteryStatus result: ${responseData.value}")

        return responseData

    }


    override fun retrieveRemainingInsulin(): DataCommandResponse<Double?> {

        aapsLogger.info(LTag.PUMPCOMM, "retrieveRemainingInsulin")

        val responseData: DataCommandResponse<Double?> = sendAndReceivePumpData(
            PumpCommandType.GetRemainingInsulin,
            InsulinStatusRequest())
        {  rawContent -> tandemDataConverter.getInsulinStatus(rawContent as InsulinStatusResponse) }

        aapsLogger.info(TAG, "retrieveRemainingInsulin result: ${responseData.value}")

        return responseData
    }


    override fun getPumpHistory(): DataCommandResponse<List<Any>?> {
        // TODO Connector: getPumpHistory


        if (true)
            return super.getPumpHistory()

        HistoryLogStatusRequest()

        val responseMessage: Message? = getCommunicationManager().sendCommand(HistoryLogStatusRequest())

        val responseText = checkResponse(responseMessage, "HistoryLogStatusRequest")

        if (responseText!=null) {
            return DataCommandResponse(PumpCommandType.GetHistory, false, responseText, null)
        }

        var historyLogStatus = responseMessage as HistoryLogStatusResponse






        // val profileStatusResponse = responseMessage as ProfileStatusResponse
        //
        // val idpId = profileStatusResponse.idpSlot0Id
        //
        // responseMessage = getCommunicationManager().sendCommand(IDPSettingsRequest(idpId))
        //
        // responseText = checkResponse(responseMessage, "IDPSettingsRequest (idpId=${idpId}")
        //
        // if (responseText!=null) {
        //     return DataCommandResponse<BasalProfileDto?>(PumpCommandType.GetBasalProfile, false, responseText, null)
        // }






        return DataCommandResponse<List<Any>?>(
            PumpCommandType.GetHistory, false, "Command not implemented.", null)
    }


    override fun sendBolus(detailedBolusInfo: DetailedBolusInfo): DataCommandResponse<AdditionalResponseDataInterface?> {
        // TODO V1 Connector: sendBolus
        return super.sendBolus(detailedBolusInfo)
    }


    override fun cancelBolus(): DataCommandResponse<AdditionalResponseDataInterface?> {
        // TODO V1 Connector: cancelBolus N-7
        var responseMessage: Message? = getCommunicationManager().sendCommand(CancelBolusRequest()) as CancelBolusResponse

        return super.cancelBolus()
    }


    override fun retrieveTemporaryBasal(): DataCommandResponse<TempBasalPair?> {

        aapsLogger.info(LTag.PUMPCOMM, "retrieveTemporaryBasal")

        val responseData: DataCommandResponse<TempBasalPair?> = sendAndReceivePumpData(
            PumpCommandType.GetTemporaryBasal,
            TempRateRequest())
        {  rawContent -> tandemDataConverter.getTempBasalRate(rawContent as TempRateResponse) }

        return responseData

    }


    // Mobi Only
    override fun sendTemporaryBasal(value: Int, duration: Int): DataCommandResponse<TempBasalPair?> {

        aapsLogger.info(LTag.PUMPCOMM, "sendTemporaryBasal [amount=$value,duration=$duration]")

        if (!TandemPumpApiVersion.isMobi(this.tandemPumpStatus.tandemPumpFirmware)) {
            aapsLogger.warn(LTag.PUMPCOMM, "Running on TandemApiVersion that doesn't support TBR, running open loop.")
            return super.sendTemporaryBasal(value, duration)
        }

        val responseMessage: SetTempRateResponse? = getCommunicationManager().sendCommand(
            SetTempRateRequest(duration, value)) as SetTempRateResponse?

        if (responseMessage!=null) {

            aapsLogger.info(TAG, "TBR: setTempBasalRate [duration=${duration},amount=${value},id=${responseMessage.tempRateId}]: isSuccess=${responseMessage.isStatusOK}")

            val tbr = TempBasalPair(insulinRate = value.toDouble(),
                                    isPercent = true,
                                    durationMinutes = duration,
                                    start = System.currentTimeMillis())

            tbr.id = responseMessage.tempRateId.toLong()

            return DataCommandResponse(
                PumpCommandType.SetTemporaryBasal, responseMessage.isStatusOK,
                if (responseMessage.isStatusOK) null else "Error sending TBR: status=${responseMessage.status}",
                tbr
            )
        } else {
            return DataCommandResponse(
                PumpCommandType.SetTemporaryBasal, false,
                "Error getting response from sending TBR: null",
                null
            )
        }
    }


    override fun cancelTemporaryBasal(): DataCommandResponse<AdditionalResponseDataInterface?> {

        aapsLogger.info(LTag.PUMPCOMM, "cancelTemporaryBasal")

        if (!TandemPumpApiVersion.isMobi(this.tandemPumpStatus.tandemPumpFirmware)) {
            aapsLogger.warn(LTag.PUMPCOMM, "Running on TandemApiVersion that doesn't support cancelTBR, running closed loop.")
            return super.cancelTemporaryBasal()
        }

        val responseMessage: StopTempRateResponse? = getCommunicationManager()
            .sendCommand(StopTempRateRequest()) as StopTempRateResponse?

        return if (responseMessage!=null) {
            DataCommandResponse(
                PumpCommandType.CancelTemporaryBasal, responseMessage.isStatusOK,
                if (responseMessage.isStatusOK) null else "Error sending cancelTBR: status=${responseMessage.status}",
                ControlCommandResponse(responseMessage.tempRateId, responseMessage.status)
            )
        } else {
            DataCommandResponse(
                PumpCommandType.CancelTemporaryBasal, false,
                "Error getting response from sending cancelTBR: null",
                null
            )
        }
    }


    fun executeSimpleSetCommand(requestMessage: Message,
                                pumpCommandType: PumpCommandType,
                                operation: String): DataCommandResponse<AdditionalResponseDataInterface?> {

        val responseMessage: StatusMessage? = getCommunicationManager()
            .sendCommand(requestMessage) as StatusMessage?

        return if (responseMessage!=null) {
            DataCommandResponse(
                pumpCommandType, responseMessage.isStatusOK,
                if (responseMessage.isStatusOK) null else "Error sending $operation: status=${responseMessage.status}",
                null
            )
        } else {
            DataCommandResponse(
                PumpCommandType.SetTemporaryBasal, false,
                "Error getting response from sending $operation: null",
                null
            )
        }
    }


    override fun retrieveBasalProfile(): DataCommandResponse<BasalProfileDto?> {

        val pumpProfileDto = getBasalProfileInternal()

        if (pumpProfileDto.success) {
            val basalProfileResponse = tandemDataConverter.getBasalProfileResponse(pumpProfileDto)
            return basalProfileResponse
        } else {
            return DataCommandResponse(PumpCommandType.GetBasalProfile, false, pumpProfileDto.errorDescription, null)
        }

    }


    fun getBasalProfileInternal(): PumpProfileDto {

        aapsLogger.info(LTag.PUMPCOMM, "getBasalProfileInternal - getBasal Profile from Pump")

        val pumpProfileDto: PumpProfileDto = getInitialBasalProfileConfiguration()

        if (!pumpProfileDto.success || pumpProfileDto.isNewScenario) {
            return pumpProfileDto
        }

        val numberOfSegments = pumpProfileDto.idpSettingsResponse!!.numberOfProfileSegments

        aapsLogger.debug(LTag.PUMPCOMM, "IDPSettings has $numberOfSegments segments.")

        var responseMessage : Message?
        var responseText : String?
        val idpId = pumpProfileDto.activeIdpId!!

        for (i in 0..numberOfSegments-1) {

            aapsLogger.debug(LTag.PUMPCOMM, "IDPSegmentRequest [idpId=$idpId, segmentIndex=$i]")

            responseMessage = getCommunicationManager().sendCommand(IDPSegmentRequest(idpId, i))

            responseText = checkResponse(responseMessage, "IDPSegmentRequest [idpId=${idpId}, segmentNumber=${i}]")

            val rspMsg = responseMessage as IDPSegmentResponse

            if (responseText!=null) {
                pumpProfileDto.success = false
                pumpProfileDto.errorDescription = responseText
                return pumpProfileDto
            } else {
                pumpProfileDto.mapSegments.put(i, rspMsg)
            }
        }

        val gsonText = pumpUtil.gson.toJson(pumpProfileDto);

        aapsLogger.debug(LTag.PUMPCOMM, "PumpProfileDto: $gsonText")

        return pumpProfileDto
    }



    private fun checkResponse(responseMessage: Message?, description: String): String? {
        return if (responseMessage==null || responseMessage is ErrorResponse) {
            val responseText = if (responseMessage==null) {
                "Response for $description was null (timeout or some other problem)."
            } else {
                val errorResponse: ErrorResponse = responseMessage as ErrorResponse
                "Received Error Response: ${errorResponse.errorCode.name} on ${description}."
            }

            aapsLogger.error(TAG, responseText)

            responseText
        } else {
            null
        }
    }

    var zeroByte : Byte = 0

    // TODO remove SDP tags as soon as this is fully tested and implemented
    fun getInitialBasalProfileConfiguration(): PumpProfileDto {

        var responseMessage: Message? = getCommunicationManager().sendCommand(ProfileStatusRequest())

        var responseText = checkResponse(responseMessage, "ProfileStatusRequest")

        val pumpProfileDto = PumpProfileDto()

        if (responseText!=null) {
            pumpProfileDto.success = false
            pumpProfileDto.errorDescription = responseText
            return pumpProfileDto
        }

        pumpProfileDto.profileStatusResponse = responseMessage as ProfileStatusResponse

        //var gsonText = pumpUtil.gson.toJson(profileStatusResponse);
        //aapsLogger.info(LTag.PUMPCOMM, "DBG: Profile status response: $gsonText")

        val idpId = pumpProfileDto.profileStatusResponse!!.idpSlot0Id

        pumpProfileDto.activeIdpId = idpId

        aapsLogger.debug(TAG, "IdpId of idpSlot0Id: $idpId")

        if (idpId==-1) {
            aapsLogger.info(TAG, "No profiles exists on pump, NEW scenario needs to run")
            pumpProfileDto.isNewScenario = true
            return pumpProfileDto
        }

        responseMessage = getCommunicationManager().sendCommand(IDPSettingsRequest(idpId))

        responseText = checkResponse(responseMessage, "IDPSettingsRequest (idpId=${idpId}")

        if (responseText!=null) {
            pumpProfileDto.success = false
            pumpProfileDto.errorDescription = responseText
            return pumpProfileDto
        }

        pumpProfileDto.idpSettingsResponse = responseMessage as IDPSettingsResponse

        return pumpProfileDto

    }


    val idpStatusId = IDPSegmentStatus.toBitmask(IDPSegmentStatus.BASAL_RATE,
                                                 IDPSegmentStatus.START_TIME,
                                                 IDPSegmentStatus.CARB_RATIO,
                                                 IDPSegmentStatus.TARGET_BG,
                                                 IDPSegmentStatus.CORRECTION_FACTOR)

    override fun sendBasalProfile(profile: Profile): DataCommandResponse<Boolean?> {

        aapsLogger.info(LTag.PUMPCOMM, "sendBasalProfile")

        if (!TandemPumpApiVersion.isMobi(this.tandemPumpStatus.tandemPumpFirmware)) {
            aapsLogger.warn(LTag.PUMPCOMM, "Running on TandemApiVersion that doesn't support sendBasalProfile, running open loop.")
            return super.sendBasalProfile(profile)
        }

        aapsLogger.info(LTag.PUMPCOMM, "sendBasalProfile - getBasal Profile from Pump")

        val pumpProfileDto = getBasalProfileInternal()

        if (!pumpProfileDto.success) {
            aapsLogger.error(LTag.PUMPCOMM, "sendBasalProfile: ERROR:  NOT SUCCESS - ${pumpProfileDto.errorDescription}")

            return DataCommandResponse(PumpCommandType.SetBasalProfile, false, pumpProfileDto.errorDescription, null)
        }

        val idpSegments = tandemDataConverter.getIDPSegmentsFromProfile(profile)

        if (idpSegments.size>16) {
            aapsLogger.error(LTag.PUMPCOMM, "sendBasalProfile - You have more than 16 basal segments. Profile must be adjusted to have only 16 segments. Last valid segment (16) will be extended over remaining time.")

            val notification = Notification(Notification.TANDEM_BASAL_PROFILE_ERROR, resourceHelper.gs(R.string.tandem_error_profile_only_16_segments), Notification.URGENT, 24*60)
            rxBus.send(EventNewNotification(notification))
        }

        val gsonText = pumpUtil.gson.toJson(idpSegments)
        aapsLogger.debug(LTag.PUMPCOMM, "sendBasalProfile - Converted segments: $gsonText")

        var responseText : String?
        var success = false


        if (pumpProfileDto.isNewScenario) {

            aapsLogger.info(LTag.PUMPCOMM, "sendBasalProfile - NEW Scenario - Create Initial IDP")

            val createIDPRequest = CreateIDPRequest("AAPS Profile",
                                                  idpSegments[0].profileCarbRatio.toInt(),
                                                  idpSegments[0].profileBasalRate,
                                                  idpSegments[0].profileTargetBG,
                                                  idpSegments[0].profileISF,
                                                  profile.dia.toInt() * 60,
                                                  0) // for AAPS profile we are setting this to 0

            val responseMessage = getCommunicationManager().sendCommand(createIDPRequest) as CreateIDPResponse

            responseText = checkResponse(responseMessage, "sendBasalProfile - NEW Scenario - Create Initial IDP")

            if (responseText!=null) {
                aapsLogger.error(LTag.PUMPCOMM, "sendBasalProfile: ERROR: $responseText  - NEW Scenario - Create Initial IDP")

                return DataCommandResponse(PumpCommandType.SetBasalProfile, false, responseText, false)
            }

            pumpProfileDto.activeIdpId = responseMessage.newIdpId

            success = responseMessage.isStatusOK

            aapsLogger.info(LTag.PUMPCOMM, "sendBasalProfile - Create Initial IDP - Status: success=$success")

        } else {
            // delete all segments excepts first one
            if (pumpProfileDto.mapSegments.size>1) {
                for (index in (pumpProfileDto.mapSegments.size-1) downTo 1) {
                    deleteIDPSegment(pumpProfileDto.mapSegments[index]!!, index)
                }
            }
        }

        val idpId = pumpProfileDto.activeIdpId!!

        var segmentCount = idpSegments.size-1

        if (segmentCount>15) {
            segmentCount = 15
        }


        for(index in 0..segmentCount) {

            val operation = if (pumpProfileDto.isNewScenario || index>0)
                SetIDPSegmentRequest.IDPSegmentOperation.CREATE_SEGMENT
            else
                SetIDPSegmentRequest.IDPSegmentOperation.MODIFY_SEGMENT_ID

            if (pumpProfileDto.isNewScenario && index==0) {
                continue; // if we have new scenario we already created 1st segment when we created profile
            }

            aapsLogger.info(LTag.PUMPCOMM, "sendBasalProfile - SetIDPSegmentRequest [index=${index},operation=${operation}]")

            val setSegment = SetIDPSegmentRequest(idpId, 0, 0, operation,
                                                  idpSegments[index].profileStartTime,
                                                  idpSegments[index].profileBasalRate,
                                                  idpSegments[index].profileCarbRatio,
                                                  idpSegments[index].profileTargetBG,
                                                  idpSegments[index].profileISF,
                                                  idpStatusId)

            val responseMessage = getCommunicationManager().sendCommand(setSegment) as SetIDPSegmentResponse

            responseText = checkResponse(responseMessage, "sendBasalProfile - SetIDPSegmentRequest (idpId=${idpId},segmentIndex=$index)")

            if (responseText!=null) {
                aapsLogger.error(LTag.PUMPCOMM, "sendBasalProfile: ERROR:  $responseText    SetIDPSegmentRequest [index=${index},operation=$operation]")

                return DataCommandResponse(PumpCommandType.SetBasalProfile, false, responseText, false)
            }

            success = responseMessage.isStatusOK

            aapsLogger.info(LTag.PUMPCOMM, "sendBasalProfile - SetIDPSegmentRequest[index=${index},operation=$operation] Status: success=$success")

        }

        return DataCommandResponse(PumpCommandType.SetBasalProfile, success, if (success) null else "Problem setting basal profile.", success)
    }


    fun sendBasalProfileNew(pumpProfileDto: PumpProfileDto, idpSegments: List<IDPSegmentDto>): DataCommandResponse<Boolean?> {

        aapsLogger.info(LTag.PUMPCOMM, "sendBasalProfileNew")

        // if (!TandemPumpApiVersion.isMobi(this.tandemPumpStatus.tandemPumpFirmware)) {
        //     aapsLogger.warn(LTag.PUMPCOMM, "Running on TandemApiVersion that doesn't support sendBasalProfile, running open loop.")
        //     return super.sendBasalProfile(profile)
        // }
        //
        // aapsLogger.info(LTag.PUMPCOMM, "sendBasalProfile - getBasal Profile from Pump")
        //
        // val pumpProfileDto = getBasalProfileInternal()
        //
        // if (!pumpProfileDto.success) {
        //     aapsLogger.error(LTag.PUMPCOMM, "sendBasalProfile: ERROR:  NOT SUCCESS - ${pumpProfileDto.errorDescription}")
        //
        //     return DataCommandResponse(PumpCommandType.SetBasalProfile, false, pumpProfileDto.errorDescription, null)
        // }
        //
        // val idpSegments = tandemDataConverter.getIDPSegmentsFromProfile(profile)
        //
        // if (idpSegments.size>16) {
        //     aapsLogger.error(LTag.PUMPCOMM, "sendBasalProfile - You have more than 16 basal segments. Profile must be adjusted to have only 16 segments. Last valid segment (16) will be extended over remaining time.")
        //
        //     val notification = Notification(Notification.TANDEM_BASAL_PROFILE_ERROR, resourceHelper.gs(R.string.tandem_error_profile_only_16_segments), Notification.URGENT, 24*60)
        //     rxBus.send(EventNewNotification(notification))
        // }
        //
        // val gsonText = pumpUtil.gson.toJson(idpSegments)
        // aapsLogger.debug(LTag.PUMPCOMM, "sendBasalProfile - Converted segments: $gsonText")
        //
        var responseText : String?
        val idpId = pumpProfileDto.activeIdpId!!
        var success = false


        // create



        var segmentCount = idpSegments.size-1

        if (segmentCount>15) {
            segmentCount = 15
        }


        for(index in 0..segmentCount) {

            val operation = SetIDPSegmentRequest.IDPSegmentOperation.CREATE_SEGMENT

            aapsLogger.info(LTag.PUMPCOMM, "sendBasalProfile - SetIDPSegmentRequest [index=${index},operation=${operation}]")

            val setSegment = SetIDPSegmentRequest(idpId, 0, 0, operation,
                                                  idpSegments[index].profileStartTime,
                                                  idpSegments[index].profileBasalRate,
                                                  idpSegments[index].profileCarbRatio,
                                                  idpSegments[index].profileTargetBG,
                                                  idpSegments[index].profileISF,
                                                  idpStatusId)

            val responseMessage = getCommunicationManager().sendCommand(setSegment) as SetIDPSegmentResponse

            responseText = checkResponse(responseMessage, "sendBasalProfile - SetIDPSegmentRequest (idpId=${idpId},segmentIndex=$index)")

            if (responseText!=null) {
                aapsLogger.error(LTag.PUMPCOMM, "sendBasalProfile: ERROR:  $responseText    SetIDPSegmentRequest [index=${index},operation=$operation]")

                return DataCommandResponse(PumpCommandType.SetBasalProfile, false, responseText, false)
            }

            success = responseMessage.isStatusOK

            aapsLogger.info(LTag.PUMPCOMM, "sendBasalProfile - SetIDPSegmentRequest[index=${index},operation=$operation] Status: success=$success")

        }

        return DataCommandResponse(PumpCommandType.SetBasalProfile, success, if (success) null else "Problem setting basal profile.", success)
    }




    private fun deleteIDPSegment(idpSegment: IDPSegmentResponse, index: Int): Boolean {

        val setSegment = SetIDPSegmentRequest(idpSegment.idpId, 0, index, SetIDPSegmentRequest.IDPSegmentOperation.DELETE_SEGMENT_ID,
                                              idpSegment.profileStartTime, idpSegment.profileBasalRate,
                                              idpSegment.profileCarbRatio, idpSegment.profileTargetBG, idpSegment.profileISF,
                                              idpStatusId)

        val responseMessage = getCommunicationManager().sendCommand(setSegment) as SetIDPSegmentResponse

        val responseText = checkResponse(responseMessage, "SetIDPSegmentRequest [idpId=${idpSegment.idpId},segmentIndex=$index]")

        if (responseText!=null) {
            aapsLogger.error(LTag.PUMPCOMM, "sendBasalProfile - SetIDPSegmentRequest: ERROR: ${responseText} - SetIDPSegmentRequest [index=${index},operation=DELETE_SEGMENT_ID]")
            return false
        }

        val success = responseMessage.isStatusOK

        aapsLogger.info(LTag.PUMPCOMM, "sendBasalProfile - SetIDPSegmentRequest[segmentIndex=$index,op=DELETE_SEGMENT_ID] Status: success=$success")

        return success
    }


    override fun getTime(): DataCommandResponse<PumpTimeDifferenceDto?> {
        // TimeSinceResetRequest
        aapsLogger.info(LTag.PUMPCOMM, "getTime")

        val responseMessage = getCommunicationManager().sendCommand(TimeSinceResetRequest())
            as TimeSinceResetResponse

        val responseText = checkResponse(responseMessage, "TimeSinceResetRequest")

        if (responseText!=null) {
            return DataCommandResponse(PumpCommandType.GetTime, false, responseText, null)
        }

        val dtPump = DateTime(responseMessage.currentTimeInstant.toEpochMilli())

        val pumpTimeDifference = PumpTimeDifferenceDto(DateTime.now(), dtPump)

        return DataCommandResponse(PumpCommandType.SetTime, true,
                                   null,
                                   pumpTimeDifference)
    }

    override fun setTime(): DataCommandResponse<Boolean?> {

        aapsLogger.info(LTag.PUMPCOMM, "setTime")

        if (!TandemPumpApiVersion.isMobi(this.tandemPumpStatus.tandemPumpFirmware)) {
            aapsLogger.warn(LTag.PUMPCOMM, "Running on TandemApiVersion that doesn't support setTime, running open loop.")
            return super.setTime()
        }

        val responseMessage = getCommunicationManager().sendCommand(ChangeTimeDateRequest(Instant.now())) as StatusMessage

        val responseText = checkResponse(responseMessage, "ChangeTimeDateRequest")

        if (responseText!=null) {
            return DataCommandResponse(PumpCommandType.SetTime, false, responseText, false)
        }

        return DataCommandResponse(PumpCommandType.SetTime, responseMessage.status==0,
                                   if (responseMessage.status==0) null else "Error sending ChangeTimeDate: status=${responseMessage.status}",
                                   responseMessage.status==0)

    }

    override fun getFilteredPumpHistory(filter: PumpHistoryFilterInterface): DataCommandResponse<List<Any>?> {
        // TODO Connector: getFilteredPumpHistory
        return DataCommandResponse(
            PumpCommandType.GetHistory, true, null, listOf())
    }

    override fun getPumpStatus(): DataCommandResponse<AdditionalResponseDataInterface?> {

        aapsLogger.info(LTag.PUMPCOMM, "getPumpStatus")

        val responseMessage: HomeScreenMirrorResponse? = getCommunicationManager()
            .sendCommand(HomeScreenMirrorRequest()) as HomeScreenMirrorResponse?

        val gsonData = tandemPumpUtil.gson.toJson(responseMessage)
        aapsLogger.info(LTag.PUMPCOMM, " MirrorResponse: $gsonData") // TODO change to DEBUG

        if (responseMessage!=null) {

            val homeScreenMirrorDto = HomeScreenMirrorDto()
            homeScreenMirrorDto.parse(responseMessage.cargo)

            tandemPumpStatus.pumpStatusMirror = homeScreenMirrorDto
            tandemPumpStatus.pumpRunningState = if (homeScreenMirrorDto.basalStatusIcon ==
                HomeScreenMirrorResponse.BasalStatusIcon.SUSPEND) PumpRunningState.Suspended
            else PumpRunningState.Running
            rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.PumpStatus))

            return DataCommandResponse(
                PumpCommandType.GetPumpStatus, true,
                null,
                homeScreenMirrorDto
            )
        } else {
            return DataCommandResponse(
                PumpCommandType.GetPumpStatus, false,
                "Error getting status from Pump: null",
                null
            )
        }

    }

    override fun executeCustomCommand(commandType: CustomCommandTypeInterface, data: Any?): DataCommandResponse<AdditionalResponseDataInterface?> {
        val commandTypeInternal = commandType as TandemCustomCommand

        when(commandTypeInternal) {
            SET_MAX_BOLUS      -> return setMaxBolus(data as Int)
            SET_MAX_BASAL      -> return setMaxBasal(data as Int)
            SET_CONTROL_IQ     -> return setControlIQDisabled()
            GET_PUMP_INFO      -> return getPumpInfo()
            GET_ALERTS         -> return getAlerts()
            GET_ALARMS         -> return getAlarms()
            else                              -> {
                aapsLogger.error(TAG, "Unhandled Custom Command: ${commandType.name}")
            }
        }

        return DataCommandResponse(
            PumpCommandType.CustomCommand, false, "Command ${commandType.getKey()} not available.", null)
    }


    private fun getPumpInfo(): DataCommandResponse<AdditionalResponseDataInterface?> {

        aapsLogger.info(TAG, "getPumpInfo")

        val responseMessage: PumpVersionResponse? = getCommunicationManager()
            .sendCommand(PumpVersionRequest()) as PumpVersionResponse?

        if (responseMessage!=null) {

            val pumpInfo = PumpVersionDto()
            pumpInfo.parse(responseMessage.cargo)

            return DataCommandResponse(
                PumpCommandType.CustomCommand, success = true,
                null,
                pumpInfo
            )
        } else {
            return DataCommandResponse(
                PumpCommandType.CustomCommand, false,
                "Error getting response from sending PumpVersionRequest: null",
                null
            )
        }
    }


    private fun setMaxBolus(bolusAmount: Int): DataCommandResponse<AdditionalResponseDataInterface?> {

        aapsLogger.info(LTag.PUMPCOMM, "setMaxBolus [bolusAmount=$bolusAmount]")

        val responseMessage: SetMaxBolusLimitResponse? = getCommunicationManager()
            .sendCommand(SetMaxBolusLimitRequest(bolusAmount*1000)) as SetMaxBolusLimitResponse?

        if (responseMessage!=null) {
            return DataCommandResponse(
                PumpCommandType.CustomCommand, responseMessage.isStatusOK,
                if (responseMessage.isStatusOK) null else "Error sending SetMaxBolusLimit(maxBolus=${bolusAmount}): status=${responseMessage.status}",
                null
            )
        } else {
            return DataCommandResponse(
                PumpCommandType.CustomCommand, false,
                "Error getting response from sending SetMaxBolusLimit: null",
                null
            )
        }
    }

    private fun getAlerts(): DataCommandResponse<AdditionalResponseDataInterface?> {

        aapsLogger.info(LTag.PUMPCOMM, "getAlerts")

        val responseMessage: AlertStatusResponse? = getCommunicationManager()
            .sendCommand(AlertStatusRequest()) as AlertStatusResponse?

        if (responseMessage!=null) {

            val alertStatusDto = AlertStatusDto()
            alertStatusDto.parse(responseMessage.cargo)

            return DataCommandResponse(
                PumpCommandType.CustomCommand, true,
                null,
                alertStatusDto
            )
        } else {
            return DataCommandResponse(
                PumpCommandType.CustomCommand, false,
                "Error getting response from sending AlertStatusRequest: null",
                null
            )
        }
    }


    private fun getAlarms(): DataCommandResponse<AdditionalResponseDataInterface?> {

        aapsLogger.info(LTag.PUMPCOMM, "getAlerts")

        val responseMessage: AlarmStatusResponse? = getCommunicationManager()
            .sendCommand(AlarmStatusRequest()) as AlarmStatusResponse?

        if (responseMessage!=null) {

            val alarmStatusDto = AlarmStatusDto()
            alarmStatusDto.parse(responseMessage.cargo)

            return DataCommandResponse(
                PumpCommandType.CustomCommand, true,
                null,
                alarmStatusDto
            )
        } else {
            return DataCommandResponse(
                PumpCommandType.CustomCommand, false,
                "Error getting response from sending AlarmStatusRequest: null",
                null
            )
        }
    }


    private fun setMaxBasal(basalAmount: Int): DataCommandResponse<AdditionalResponseDataInterface?> {

        aapsLogger.info(LTag.PUMPCOMM, "setMaxBasal [basalAmount=$basalAmount]")

        val responseMessage: SetMaxBasalLimitResponse? = getCommunicationManager()
            .sendCommand(SetMaxBasalLimitRequest(basalAmount*1000)) as SetMaxBasalLimitResponse?

        if (responseMessage!=null) {
            return DataCommandResponse(
                PumpCommandType.CustomCommand, responseMessage.status==0,
                if (responseMessage.status==0) null else "Error sending SetMaxBasalLimit(maxBasal=${basalAmount}): status=${responseMessage.status}",
                null
            )
        } else {
            return DataCommandResponse(
                PumpCommandType.CustomCommand, false,
                "Error getting response from sending SetMaxBasalLimit: null",
                null
            )
        }
    }


    private fun setControlIQDisabled(): DataCommandResponse<AdditionalResponseDataInterface?> {

        aapsLogger.info(LTag.PUMPCOMM, "setControlIQDisabled")

        val responseMessage: ChangeControlIQSettingsResponse? = getCommunicationManager()
            .sendCommand(ChangeControlIQSettingsRequest(false, 0, 0)) as ChangeControlIQSettingsResponse?

        if (responseMessage!=null) {
            return DataCommandResponse(
                PumpCommandType.CustomCommand, responseMessage.isStatusOK,
                if (responseMessage.isStatusOK) null else "Error sending ChangeControlIQSettings(enabled=false): status=${responseMessage.status}",
                null
            )
        } else {
            return DataCommandResponse(
                PumpCommandType.CustomCommand, false,
                "Error getting response from sending ChangeControlIQSettings: null",
                null
            )
        }
    }




    private inline fun <reified T>  sendAndReceivePumpData(commandType: PumpCommandType,
                                                           requestMessage: Message,
                                                           decode: (responseMessage: Message) -> T
    ): T {

        aapsLogger.info(TAG, "TANDEMDBG: sendAndReceivePumpData for $commandType  - Request message: ${requestMessage.javaClass.name}")

        val responseMessage: Message? = getCommunicationManager().sendCommand(requestMessage)

        aapsLogger.info(TAG, "TANDEMDBG: sendAndReceivePumpData: Response: $responseMessage")

        return if (responseMessage==null) {
            DataCommandResponse(
                commandType, false, "Error communicating with pump (timeout).", null) as T
        } else if (responseMessage is ErrorResponse) {
            DataCommandResponse(
                commandType, false, "Error communicating with pump. Error: ${responseMessage.errorCode.name}", null) as T
        } else {
            val response = decode(responseMessage)

            aapsLogger.info(TAG, "TANDEMDBG: sendAndReceivePumpData: decoded Response: ${response}")

            response
        }
    }


    private fun suspendPump() {
        // TODO suspendPump M-7
        var responseMessage: Message? = getCommunicationManager().sendCommand(SuspendPumpingRequest()) as SuspendPumpingResponse
    }


    private fun changeCartridge() {
        // TODO changeCartridge M-7
        //var responseMessage: Message? = getCommunicationManager().sendCommand(ChangeCartridgeRequest()) as ChangeCartridgeResponse
    }

    // private fun fillCanula_???() {
    //     // TODO changeCartridge M-7
    //     var responseMessage: Message? = getCommunicationManager().sendCommand(ChangeCartridgeRequest()) as ChangeCartridgeResponse
    // }


    // private fun getPumpVersionInfo() {
    //     // TODO getPumpVersionInfo M-7
    //     var responseMessage: Message? = getCommunicationManager().sendCommand(PumpVersionRequest()) as PumpVersionResponse
    // }


    private fun getCorrectRequest(command: TandemCommandType): Message {
        return when(this.tandemPumpStatus.tandemPumpFirmware) {
            TandemPumpApiVersion.VERSION_2_1_to_2_4,
            -> {
                when(command) {
                    TandemCommandType.ControlIQInfo  -> ControlIQInfoV1Request()
                    TandemCommandType.CurrentBattery -> CurrentBatteryV1Request()
                }
            }

            TandemPumpApiVersion.VERSION_2_5_OR_HIGHER,
            TandemPumpApiVersion.VERSION_3_0,
            TandemPumpApiVersion.VERSION_3_2,
            TandemPumpApiVersion.VERSION_3_4,
            TandemPumpApiVersion.VERSION_3_5_MOBI,
            TandemPumpApiVersion.VERSION_3_6_MOBI,
            TandemPumpApiVersion.VERSION_4_x -> {
                when(command) {
                    TandemCommandType.ControlIQInfo  -> ControlIQInfoV2Request()
                    TandemCommandType.CurrentBattery -> CurrentBatteryV2Request()
                }
            }
            else                                                                                                                                                                                    -> throw Exception()
        }
    }

    init {
        supportedCommandsList = setOf(
            PumpCommandType.GetFirmwareVersion,
            PumpCommandType.GetRemainingInsulin,
            PumpCommandType.GetTime,
            PumpCommandType.SetTime,
            PumpCommandType.GetSettings,
            PumpCommandType.GetBatteryStatus,
            PumpCommandType.GetTemporaryBasal,
            PumpCommandType.SetTemporaryBasal,
            PumpCommandType.CancelTemporaryBasal
        )
    }

    override fun getSupportedCommands(): Set<PumpCommandType> {
        return supportedCommandsList
    }

    fun isConnected(): Boolean {
        return if (tandemCommunicationManager==null) {
            false
        } else {
            getCommunicationManager().connected
        }
    }

}