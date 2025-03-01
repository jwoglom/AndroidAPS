package app.aaps.pump.tandem.common.driver.connector

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
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
import app.aaps.pump.tandem.common.comm.TandemCommunicationManager
import app.aaps.pump.tandem.common.comm.TandemDataConverter
import app.aaps.pump.tandem.common.data.PumpProfileDto
import app.aaps.pump.tandem.common.data.defs.TandemCommandType
import app.aaps.pump.tandem.common.data.defs.TandemPumpApiVersion
import app.aaps.pump.tandem.common.data.defs.TandemPumpApiVersion.VERSION_2_1_to_2_4
import app.aaps.pump.tandem.common.data.defs.TandemPumpSettingType
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.driver.connector.TandemCustomCommand.SET_CONTROL_IQ
import app.aaps.pump.tandem.common.driver.connector.TandemCustomCommand.SET_MAX_BOLUS
import app.aaps.pump.tandem.common.driver.connector.def.ControlCommandResponse
import app.aaps.pump.tandem.common.driver.connector.response.HomeScreenMirrorDto
import app.aaps.pump.tandem.common.util.PumpX2L
import app.aaps.pump.tandem.common.util.TandemPumpConst
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.bluetooth.TandemConfig
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType
import com.jwoglom.pumpx2.pump.messages.models.StatusMessage
import com.jwoglom.pumpx2.pump.messages.request.control.CancelBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ChangeTimeDateRequest
import com.jwoglom.pumpx2.pump.messages.request.control.CreateIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.DeleteIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetIDPSegmentRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetTempRateRequest
import com.jwoglom.pumpx2.pump.messages.request.control.StopTempRateRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SuspendPumpingRequest
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
import com.jwoglom.pumpx2.pump.messages.response.ErrorResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CancelBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CreateIDPResponse
import com.jwoglom.pumpx2.pump.messages.response.control.DeleteIDPResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetIDPSegmentResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetTempRateResponse
import com.jwoglom.pumpx2.pump.messages.response.control.StopTempRateResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SuspendPumpingResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BasalIQStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BasalLimitSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQInfoV1Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQInfoV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.GlobalMaxBolusSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HomeScreenMirrorResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSegmentResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ProfileStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpFeaturesV1Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpFeaturesV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpVersionResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TempRateResponse
import dagger.android.HasAndroidInjector
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
                                              injector: HasAndroidInjector,
                                              var rxBus: RxBus,
                                              var resourceHelper: ResourceHelper,
                                              var sp: SP,
                                              aapsLogger: AAPSLogger,
                                              val pumpX2L: PumpX2L,
                                              private var tandemDataConverter: TandemDataConverter
): PumpDummyConnector(tandemPumpStatus, tandemPumpUtil, injector, aapsLogger) {

    private var tandemCommunicationManager: TandemCommunicationManager? = null
    var btAddressUsed: String? = null
    //var tandemPumpApiVersion: TandemPumpApiVersion = TandemPumpApiVersion.VERSION_2_1_to_2_4

    var TAG = LTag.PUMPCOMM

    // TODO Better Error response handling

    fun getCommunicationManager(): TandemCommunicationManager {
        return tandemCommunicationManager!!
    }


    override fun connectToPump(): Boolean {
        var newBtAddress = sp.getStringOrNull(TandemPumpConst.Prefs.PumpAddress, null)

        aapsLogger.info(TAG, "TANDEMDBG: connectToPump with $newBtAddress")

        if (!btAddressUsed.isNullOrEmpty()) {
            if (btAddressUsed.equals(newBtAddress)) {
                newBtAddress = null
            }
        } else {
            if (newBtAddress.isNullOrEmpty()) {
                return false;
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
        aapsLogger.info(TAG, "TANDEMDBG: disconnectFromPump")

        getCommunicationManager().disconnect()
        return true
    }


    override fun retrieveFirmwareVersion(): DataCommandResponse<FirmwareVersionInterface?> {
        val version = sp.getStringOrNull(TandemPumpConst.Prefs.PumpApiVersion, null)

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


            // PumpFeaturesV2Request
            //addToSettings(TandemPumpSettingType.BASAL_IQ_ENABLED, map, getCommunicationManager().sendCommand(BasalIQStatusRequest()))

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

        val responseData: DataCommandResponse<Int?> = sendAndReceivePumpData(
            PumpCommandType.GetBatteryStatus,
            getCorrectRequest(TandemCommandType.CurrentBattery))
        {   rawData -> tandemDataConverter.getBatteryResponse(rawData as CurrentBatteryAbstractResponse) }

        aapsLogger.info(TAG, "retrieveBatteryStatus result: ${responseData.value}")

        return responseData

    }


    override fun retrieveRemainingInsulin(): DataCommandResponse<Double?> {

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

        var responseMessage: Message? = getCommunicationManager().sendCommand(HistoryLogStatusRequest())

        var responseText = checkResponse(responseMessage, "HistoryLogStatusRequest")

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

        val responseData: DataCommandResponse<TempBasalPair?> = sendAndReceivePumpData(
            PumpCommandType.GetTemporaryBasal,
            TempRateRequest())
        {  rawContent -> tandemDataConverter.getTempBasalRate(rawContent as TempRateResponse) }

        if (responseData.isSuccess) {
            val tbr = responseData.value!!
            if (tbr.isActive) {
                pumpStatus.currentTempBasal = tbr
            }
        }

        return responseData
    }


    // Mobi Only
    override fun sendTemporaryBasal(value: Int, duration: Int): DataCommandResponse<AdditionalResponseDataInterface?> {

        if (!TandemPumpApiVersion.isMobi(this.tandemPumpStatus.tandemPumpFirmware)) {
            aapsLogger.warn(LTag.PUMPCOMM, "Running on TandemApiVersion that doesn't support TBR, running open loop.")
            return super.sendTemporaryBasal(value, duration)
        }

        val responseMessage: SetTempRateResponse? = getCommunicationManager().sendCommand(
            SetTempRateRequest(duration, value)) as SetTempRateResponse?

        if (responseMessage!=null) {
            return DataCommandResponse(
                PumpCommandType.SetTemporaryBasal, responseMessage.status == 1,
                if (responseMessage.status==1) null else "Error sending TBR: status=${responseMessage.status}",
                ControlCommandResponse(responseMessage.id, responseMessage.status)
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

        if (!TandemPumpApiVersion.isMobi(this.tandemPumpStatus.tandemPumpFirmware)) {
            aapsLogger.warn(LTag.PUMPCOMM, "Running on TandemApiVersion that doesn't support cancelTBR, running closed loop.")
            return super.cancelTemporaryBasal()
        }

        val responseMessage: StopTempRateResponse? = getCommunicationManager()
            .sendCommand(StopTempRateRequest()) as StopTempRateResponse?

        if (responseMessage!=null) {
            return DataCommandResponse(
                PumpCommandType.SetTemporaryBasal, responseMessage.status == 1,
                if (responseMessage.status==0) null else "Error sending cancelTBR: status=${responseMessage.status}",
                ControlCommandResponse(responseMessage.id, responseMessage.status)
            )
        } else {
            return DataCommandResponse(
                PumpCommandType.SetTemporaryBasal, false,
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

        if (responseMessage!=null) {
            return DataCommandResponse(
                pumpCommandType, responseMessage.isStatusOK,
                if (responseMessage.isStatusOK) null else "Error sending $operation: status=${responseMessage.status}",
                null
            )
        } else {
            return DataCommandResponse(
                PumpCommandType.SetTemporaryBasal, false,
                "Error getting response from sending $operation: null",
                null
            )
        }
    }



    override fun retrieveBasalProfile(): DataCommandResponse<BasalProfileDto?> {

        val pumpProfileDto = getInitialBasalProfileConfiguration()

        if (!pumpProfileDto.success) {
            return DataCommandResponse(PumpCommandType.GetBasalProfile, false, pumpProfileDto.errorDescription, null)
        }

        // var responseMessage: Message? = getCommunicationManager().sendCommand(ProfileStatusRequest())
        //
        // var responseText = checkResponse(responseMessage, "ProfileStatusRequest")
        //
        // if (responseText!=null) {
        //     return DataCommandResponse<BasalProfileDto?>(PumpCommandType.GetBasalProfile, false, responseText, null)
        // }
        //
        // var pumpProfileDto = PumpProfileDto()
        //
        // pumpProfileDto.profileStatusResponse = responseMessage as ProfileStatusResponse
        //
        // //var gsonText = pumpUtil.gson.toJson(profileStatusResponse);
        // //aapsLogger.info(LTag.PUMPCOMM, "DBG: Profile status response: $gsonText")
        //
        // val idpId = pumpProfileDto.profileStatusResponse!!.idpSlot0Id
        //
        // pumpProfileDto.activeIdpId = idpId
        //
        // aapsLogger.debug(LTag.PUMPCOMM, "IdpId of idpSlot0Id: $idpId")
        //
        // responseMessage = getCommunicationManager().sendCommand(IDPSettingsRequest(idpId))
        //
        // responseText = checkResponse(responseMessage, "IDPSettingsRequest (idpId=${idpId}")
        //
        // if (responseText!=null) {
        //     return DataCommandResponse<BasalProfileDto?>(PumpCommandType.GetBasalProfile, false, responseText, null)
        // }
        //
        // //val mapSegments = mutableMapOf<Int, IDPSegmentResponse>()
        //
        // pumpProfileDto.idpSettingsResponse = responseMessage as IDPSettingsResponse

        //gsonText = pumpUtil.gson.toJson(settings);
        //aapsLogger.info(LTag.PUMPCOMM, "DBG: Profile IDPSettingsResponse response: $gsonText")

        val numberOfSegments = pumpProfileDto.idpSettingsResponse!!.numberOfProfileSegments

        aapsLogger.debug(LTag.PUMPCOMM, "IDPSettings has $numberOfSegments segments.")

        var responseMessage : Message?
        var responseText : String?
        val idpId = pumpProfileDto.activeIdpId!!

        for (i in 0..numberOfSegments-1) {

            aapsLogger.debug(LTag.PUMPCOMM, "IDPSegmentRequest [idpId=$idpId, segmentIndex=$i]")

            responseMessage = getCommunicationManager().sendCommand(IDPSegmentRequest(idpId, i))

            responseText = checkResponse(responseMessage, "IDPSegmentRequest (idpId=${idpId}, segmentNumber=${i})")

            val rspMsg = responseMessage as IDPSegmentResponse

            //gsonText = pumpUtil.gson.toJson(rspMsg);

            //aapsLogger.info(LTag.PUMPCOMM, "DBG: Profile IDPSegmentResponse response: $gsonText")

            if (responseText!=null) {
                return DataCommandResponse(PumpCommandType.GetBasalProfile, false, responseText, null)
            } else {
                pumpProfileDto.mapSegments.put(i, rspMsg)
            }
        }

        val gsonText = pumpUtil.gson.toJson(pumpProfileDto);

        aapsLogger.error(LTag.PUMPCOMM, "PumpProfileDto: $gsonText")

        val basalProfileResponse = tandemDataConverter.getBasalProfileResponse(pumpProfileDto.idpSettingsResponse!!, pumpProfileDto.mapSegments)

        return basalProfileResponse
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

        aapsLogger.debug(LTag.PUMPCOMM, "IdpId of idpSlot0Id: $idpId")

        responseMessage = getCommunicationManager().sendCommand(IDPSettingsRequest(idpId))

        responseText = checkResponse(responseMessage, "IDPSettingsRequest (idpId=${idpId}")

        if (responseText!=null) {
            pumpProfileDto.success = false
            pumpProfileDto.errorDescription = responseText
            return pumpProfileDto
            //return DataCommandResponse<BasalProfileDto?>(PumpCommandType.GetBasalProfile, false, responseText, null)
        }

        //val mapSegments = mutableMapOf<Int, IDPSegmentResponse>()

        pumpProfileDto.idpSettingsResponse = responseMessage as IDPSettingsResponse

        return pumpProfileDto

    }

    // TODO at the moment we are storing only basal and target BG
    val idpStatusId = IDPSegmentResponse.IDPSegmentStatus.BASAL_RATE.id +
                            IDPSegmentResponse.IDPSegmentStatus.TARGET_BG.id

    override fun sendBasalProfile(profile: Profile): DataCommandResponse<Boolean?> {
        // TODO sendBasalProfile - not tested (not available in protocol)

        // if (!TandemPumpApiVersion.isMobi(this.tandemPumpStatus.tandemPumpFirmware)) {
        //     aapsLogger.warn(LTag.PUMPCOMM, "Running on TandemApiVersion that doesn't support sendBasalProfile, running open loop.")
        //     return super.sendBasalProfile(profile)
        // }

        aapsLogger.error(LTag.PUMPCOMM, "sendBasalProfile")

        val pumpProfileDto = getInitialBasalProfileConfiguration()

        if (!pumpProfileDto.success) {
            return DataCommandResponse(PumpCommandType.SetBasalProfile, false, pumpProfileDto.errorDescription, null)
        }


        // var responseMessage: Message? = getCommunicationManager().sendCommand(ProfileStatusRequest())
        //
        // var responseText = checkResponse(responseMessage, "ProfileStatusRequest")
        //
        // if (responseText!=null) {
        //     return DataCommandResponse(PumpCommandType.SetBasalProfile, false, responseText, false)
        // }
        //
        // val profileStatusResponse = responseMessage as ProfileStatusResponse
        //
        // var gsonText = pumpUtil.gson.toJson(profileStatusResponse);
        //
        // aapsLogger.info(LTag.PUMPCOMM, "DBG: Profile status response: $gsonText")
        //
        // val idpId = profileStatusResponse.idpSlot0Id
        //
        // aapsLogger.info(LTag.PUMPCOMM, "DBG: Profile idpId response: $idpId")
        //
        // responseMessage = getCommunicationManager().sendCommand(IDPSettingsRequest(idpId))
        //
        // responseText = checkResponse(responseMessage, "IDPSettingsRequest (idpId=${idpId}")
        //
        // if (responseText!=null) {
        //     return DataCommandResponse(PumpCommandType.SetBasalProfile, false, responseText, false)
        // }

        val idpSegments = tandemDataConverter.getIDPSegmentsFromProfile(profile)

        var gsonText = pumpUtil.gson.toJson(idpSegments)

        aapsLogger.error(LTag.PUMPCOMM, "Converted segments $gsonText")

        //val mapSegments = mutableMapOf<Int,IDPSegmentResponse>()

        // val settings = responseMessage as IDPSettingsResponse
        //
        // gsonText = pumpUtil.gson.toJson(settings);
        //
        // aapsLogger.info(LTag.PUMPCOMM, "DBG: Profile IDPSettingsResponse response: $gsonText")

        //var responseMessage : Message?
        var responseText : String?
        val idpId = pumpProfileDto.activeIdpId!!
        var success : Boolean = false

        var zeroByte : Byte = 0

        if (pumpProfileDto.idpSettingsResponse!!.numberOfProfileSegments==1) {

            aapsLogger.error(LTag.PUMPCOMM, "sendBasalProfile: count=1, SetIDPSegmentRequest")

            val request = SetIDPSegmentRequest(idpId, 0, SetIDPSegmentRequest.IDPSegmentOperation.MODIFY,
                                               0, idpSegments[0].profileBasalRate,
                                               idpSegments[0].profileCarbRatio, idpSegments[0].profileTargetBG, idpSegments[0].profileISF,
                                               idpStatusId)

            val responseMessage = getCommunicationManager().sendCommand(request) as SetIDPSegmentResponse

            responseText = checkResponse(responseMessage, "SetIDPSegmentRequest (idpId=${idpId},segmentIndex=${0})")

            if (responseText!=null) {
                return DataCommandResponse(PumpCommandType.SetBasalProfile, false, responseText, false)
            }

            success = (responseMessage.cargo[0]==zeroByte) as Boolean

            aapsLogger.error(LTag.PUMPCOMM, "SetIDPSegment Status: success=$success")

        } else {

            aapsLogger.error(LTag.PUMPCOMM, "sendBasalProfile: number=more, DeleteIDPRequest")


            val requestDelete = DeleteIDPRequest(idpId)

            var deleteIDPResponseMessage = getCommunicationManager().sendCommand(requestDelete) as DeleteIDPResponse

            responseText = checkResponse(deleteIDPResponseMessage, "DeleteIDPRequest (idpId=${idpId})")

            if (responseText!=null) {
                return DataCommandResponse(PumpCommandType.SetBasalProfile, false, responseText, false)
            }

            success = (deleteIDPResponseMessage.status==0) as Boolean

            aapsLogger.error(LTag.PUMPCOMM, "DeleteIDP Status: success=${success}")

            //if (deleteIDPResponseMessage.sta==0)

            aapsLogger.error(LTag.PUMPCOMM, "sendBasalProfile: number=more, CreateIDPRequest")

            val createRequest = CreateIDPRequest("AAPS Profile",
                             idpSegments[0].profileCarbRatio.toInt(), idpSegments[0].profileBasalRate,
                             idpSegments[0].profileTargetBG, idpSegments[0].profileISF,
                                                 profile.dia.toInt(), 0)

            val createResponseMessage = getCommunicationManager().sendCommand(createRequest) as CreateIDPResponse

            responseText = checkResponse(createResponseMessage, "CreateIDPRequest (idpId=${idpId},segmentIndex=0)")

            if (responseText!=null) {
                return DataCommandResponse(PumpCommandType.SetBasalProfile, false, responseText, false)
            }

            success = createResponseMessage.status==0

            aapsLogger.error(LTag.PUMPCOMM, "CreateIDPRequest Status: success=$success")

        }

        if (idpSegments.size>1) {

            for(index in 1..idpSegments.size-1) {

                aapsLogger.error(LTag.PUMPCOMM, "sendBasalProfile: number=more, SetIDPSegmentRequest $index")

                val setSegment = SetIDPSegmentRequest(idpId, index, SetIDPSegmentRequest.IDPSegmentOperation.CREATE_AFTER,
                                     idpSegments[index].profileStartTime, idpSegments[index].profileBasalRate,
                                     idpSegments[index].profileCarbRatio, idpSegments[index].profileTargetBG, idpSegments[index].profileISF,
                                                      idpStatusId)

                var responseMessage = getCommunicationManager().sendCommand(setSegment) as SetIDPSegmentResponse

                responseText = checkResponse(responseMessage, "SetIDPSegmentRequest (idpId=${idpId},segmentIndex=$index)")

                if (responseText!=null) {
                    return DataCommandResponse(PumpCommandType.SetBasalProfile, false, responseText, false)
                }



                success = (responseMessage.cargo[0]==zeroByte) as Boolean

                aapsLogger.error(LTag.PUMPCOMM, "SetIDPSegmentRequest Status: success=$success")

            }
        }

        return DataCommandResponse(PumpCommandType.SetBasalProfile, true, null, true)

    }


    override fun getTime(): DataCommandResponse<PumpTimeDifferenceDto?> {
        return DataCommandResponse(
            PumpCommandType.GetTime, true, null, pumpStatus.pumpTime)
    }

    override fun setTime(): DataCommandResponse<Boolean?> {

        aapsLogger.info(LTag.PUMPCOMM, "Firmware: ${this.tandemPumpStatus.tandemPumpFirmware}.")

        this.tandemPumpStatus.tandemPumpFirmware

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

        val responseMessage: HomeScreenMirrorResponse? = getCommunicationManager()
            .sendCommand(HomeScreenMirrorRequest()) as HomeScreenMirrorResponse?

        val gsonData = tandemPumpUtil.gson.toJson(responseMessage)
        aapsLogger.info(LTag.PUMPCOMM, "MirrorResponse: $gsonData")

        if (responseMessage!=null) {

            val homeScreenMirrorDto = HomeScreenMirrorDto()
            homeScreenMirrorDto.parse(responseMessage.cargo)

            tandemPumpStatus.pumpStatusMirror = homeScreenMirrorDto

            tandemPumpStatus.pumpRunningState = if (homeScreenMirrorDto.basalStatusIcon == HomeScreenMirrorResponse.BasalStatusIcon.SUSPEND) PumpRunningState.Suspended else PumpRunningState.Running

            //pumpStatus.pumpRunningState == PumpRunningState.Suspended

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
            SET_MAX_BOLUS  -> setMaxBolus(data as Int)
            SET_CONTROL_IQ -> setControlIQDisabled()
            else -> { }
        }


        // val responseMessage: SetTempRateResponse? = getCommunicationManager().sendCommand(
        //     SetTempRateRequest(duration, value)) as SetTempRateResponse?
        //
        // if (responseMessage!=null) {
        //     return DataCommandResponse(
        //         PumpCommandType.SetTemporaryBasal, responseMessage.status == 1,
        //         if (responseMessage.status==1) null else "Error sending TBR: status=${responseMessage.status}",
        //         ControlCommandResponse(responseMessage.id, responseMessage.status)
        //     )
        // } else {
        //     return DataCommandResponse(
        //         PumpCommandType.SetTemporaryBasal, false,
        //         "Error getting response from sending TBR: null",
        //         null
        //     )
        // }




        return DataCommandResponse(
            PumpCommandType.CustomCommand, false, "Command ${commandType.getKey()} not available.", null)
    }


    fun setMaxBolus(bolusAmount: Int): DataCommandResponse<AdditionalResponseDataInterface?>? {


        // val responseMessage: StopTempRateResponse? = getCommunicationManager()
        //     .sendCommand(StopTempRateRequest()) as StopTempRateResponse?
        //
        // if (responseMessage!=null) {
        //     return DataCommandResponse(
        //         PumpCommandType.SetTemporaryBasal, responseMessage.status == 1,
        //         if (responseMessage.status==0) null else "Error sending cancelTBR: status=${responseMessage.status}",
        //         ControlCommandResponse(responseMessage.id, responseMessage.status)
        //     )
        // } else {
        //     return DataCommandResponse(
        //         PumpCommandType.SetTemporaryBasal, false,
        //         "Error getting response from sending cancelTBR: null",
        //         null
        //     )
        // }

        return null
    }



    fun setControlIQDisabled(): DataCommandResponse<AdditionalResponseDataInterface?>? {

        //val controlIQSetting = ChangeControlIQSettingsRequest()

        // val responseMessage: ChangeControlIQSettingsResponse? = getCommunicationManager()
        //     .sendCommand(ChangeControlIQSettingsRequest(false, 0, 0)) as ChangeControlIQSettingsResponse?
        //
        // if (responseMessage!=null) {
        //     return DataCommandResponse(
        //         PumpCommandType.SetTemporaryBasal, responseMessage.status == 1,
        //         if (responseMessage.status==0) null else "Error sending setControlIQDisabled: status=${responseMessage.status}",
        //         ControlCommandResponse(responseMessage.id, responseMessage.status)
        //     )
        // } else {
        //     return DataCommandResponse(
        //         PumpCommandType.SetTemporaryBasal, false,
        //         "Error getting response from sending cancelTBR: null",
        //         null
        //     )
        // }

        return null
    }




    private inline fun <reified T>  sendAndReceivePumpData(commandType: PumpCommandType,
                                                           requestMessage: Message,
                                                           decode: (responseMessage: Message) -> T
    ): T {

        aapsLogger.info(TAG, "TANDEMDBG: sendAndReceivePumpData for ${commandType}  - Request message: ${requestMessage.javaClass.name}")

        val responseMessage: Message? = getCommunicationManager().sendCommand(requestMessage)

        aapsLogger.info(TAG, "TANDEMDBG: sendAndReceivePumpData: Response: ${responseMessage}")

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


    private fun getPumpVersionInfo() {
        // TODO getPumpVersionInfo M-7
        var responseMessage: Message? = getCommunicationManager().sendCommand(PumpVersionRequest()) as PumpVersionResponse
    }


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