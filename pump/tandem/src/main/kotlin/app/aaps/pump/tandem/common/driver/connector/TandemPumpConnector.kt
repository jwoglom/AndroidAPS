package app.aaps.pump.tandem.common.driver.connector

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.sharedPreferences.SP
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.*
import com.jwoglom.pumpx2.pump.messages.response.ErrorResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.*
import dagger.android.HasAndroidInjector
import app.aaps.pump.common.data.BasalProfileDto
import app.aaps.pump.common.defs.PumpConfigurationTypeInterface
import app.aaps.pump.common.driver.connector.commands.data.AdditionalResponseDataInterface
import app.aaps.pump.common.driver.connector.commands.data.CustomCommandTypeInterface
import app.aaps.pump.common.driver.connector.commands.data.FirmwareVersionInterface
import app.aaps.pump.common.driver.connector.commands.parameters.PumpHistoryFilterInterface
import app.aaps.pump.common.driver.connector.commands.response.DataCommandResponse
import app.aaps.pump.common.data.PumpTimeDifferenceDto
import info.nightscout.pump.common.defs.TempBasalPair
import app.aaps.pump.common.driver.connector.PumpDummyConnector

import app.aaps.pump.common.driver.connector.defs.PumpCommandType
import app.aaps.pump.tandem.common.comm.TandemCommunicationManager
import app.aaps.pump.tandem.common.comm.TandemDataConverter
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.data.defs.TandemCommandType
import app.aaps.pump.tandem.common.data.defs.TandemPumpApiVersion
import app.aaps.pump.tandem.common.data.defs.TandemPumpSettingType
import app.aaps.pump.tandem.common.util.TandemPumpConst
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import com.jwoglom.pumpx2.pump.messages.request.control.CancelBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ChangeCartridgeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetTempRateRequest
import com.jwoglom.pumpx2.pump.messages.request.control.StopTempRateRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SuspendPumpingRequest
import com.jwoglom.pumpx2.pump.messages.response.control.CancelBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.control.ChangeCartridgeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetTempRateResponse
import com.jwoglom.pumpx2.pump.messages.response.control.StopTempRateResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SuspendPumpingResponse

import javax.inject.Inject

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
class TandemPumpConnector @Inject constructor(var tandemPumpStatus: TandemPumpStatus,
                                              var context: Context,
                                              var tandemPumpUtil: TandemPumpUtil,
                                              injector: HasAndroidInjector,
                                              var sp: SP,
                                              aapsLogger: AAPSLogger,
                                              var tandemDataConverter: TandemDataConverter
): PumpDummyConnector(tandemPumpStatus, tandemPumpUtil, injector, aapsLogger) {

    var tandemCommunicationManager: TandemCommunicationManager? = null
    var btAddressUsed: String? = null
    var tandemPumpApiVersion: TandemPumpApiVersion = TandemPumpApiVersion.VERSION_2_1_to_2_4

    var TAG = LTag.PUMPCOMM

    // TODO Better Error response handling

    fun getCommunicationManager(): TandemCommunicationManager {
        return tandemCommunicationManager!!
    }


    override fun connectToPump(): Boolean {
        var newBtAddress = sp.getStringOrNull(TandemPumpConst.Prefs.PumpAddress, null)

        aapsLogger.info(TAG, "TANDEMDBG: connectToPump with ${newBtAddress}")

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
            tandemCommunicationManager = TandemCommunicationManager(
                context = context,
                aapsLogger = aapsLogger,
                sp = sp,
                pumpUtil = tandemPumpUtil,
                pumpStatus = tandemPumpStatus,
                btAddress = newBtAddress
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

        if (version!=null) {
            this.tandemPumpApiVersion = TandemPumpApiVersion.valueOf(version)
        } else {
            this.tandemPumpApiVersion = TandemPumpApiVersion.VERSION_2_1_to_2_4
        }

        return DataCommandResponse(
            PumpCommandType.GetFirmwareVersion, true, null, this.tandemPumpApiVersion)
    }


    override fun retrieveConfiguration(): DataCommandResponse<MutableMap<PumpConfigurationTypeInterface, Any>?> {

        val map:  MutableMap<PumpConfigurationTypeInterface, Any> = mutableMapOf()

        try {
            addToSettings(TandemPumpSettingType.CONTROL_IQ_ENABLED, map, getCommunicationManager().sendCommand(getCorrectRequest(TandemCommandType.ControlIQInfo)))
            addToSettings(TandemPumpSettingType.BASAL_LIMIT, map, getCommunicationManager().sendCommand(BasalLimitSettingsRequest()))
            addToSettings(TandemPumpSettingType.MAX_BOLUS, map, getCommunicationManager().sendCommand(GlobalMaxBolusSettingsRequest()))

            return DataCommandResponse(
                PumpCommandType.GetSettings, true, null, map
            )
        } catch(ex: Exception) {
            return DataCommandResponse(
                PumpCommandType.GetSettings, false, "Problem with reading some data: Ex: " + ex.toString(), map)
        }
    }


    private fun addToSettings(settingType: TandemPumpSettingType, settingsMap: MutableMap<PumpConfigurationTypeInterface, Any>, message: Message?) {

        when(message) {
            is ControlIQInfoV1Response -> {  settingsMap.put(settingType, message.closedLoopEnabled) }
            is ControlIQInfoV2Response -> {  settingsMap.put(settingType, message.closedLoopEnabled) }
            is BasalLimitSettingsResponse -> {  settingsMap.put(settingType, message.basalLimit)  }
            is GlobalMaxBolusSettingsResponse -> {  settingsMap.put(settingType, message.maxBolus) }
            is ErrorResponse -> {
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

        return responseData

    }


    override fun retrieveRemainingInsulin(): DataCommandResponse<Double?> {

        val responseData: DataCommandResponse<Double?> = sendAndReceivePumpData(
            PumpCommandType.GetRemainingInsulin,
            InsulinStatusRequest())
        {  rawContent -> tandemDataConverter.getInsulinStatus(rawContent as InsulinStatusResponse) }

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
            return DataCommandResponse<List<Any>?>(PumpCommandType.GetHistory, false, responseText, null)
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
        //     return DataCommandResponse<BasalProfileDto?>(PumpCommandType.SetBasalProfile, false, responseText, null)
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
        // TODO V2 Connector: sendTemporaryBasal

        var responseMessage: Message? = getCommunicationManager().sendCommand(SetTempRateRequest()) as SetTempRateResponse



        return super.sendTemporaryBasal(value, duration)
    }


    override fun cancelTemporaryBasal(): DataCommandResponse<AdditionalResponseDataInterface?> {

        var responseMessage: Message? = getCommunicationManager().sendCommand(StopTempRateRequest()) as StopTempRateResponse


        // TODO V2 Connector: cancelTemporaryBasal N-8
        return super.cancelTemporaryBasal()
    }


    override fun retrieveBasalProfile(): DataCommandResponse<BasalProfileDto?> {

        var responseMessage: Message? = getCommunicationManager().sendCommand(ProfileStatusRequest())

        var responseText = checkResponse(responseMessage, "ProfileStatusRequest")

        if (responseText!=null) {
            return DataCommandResponse<BasalProfileDto?>(PumpCommandType.SetBasalProfile, false, responseText, null)
        }

        val profileStatusResponse = responseMessage as ProfileStatusResponse

        val idpId = profileStatusResponse.idpSlot0Id

        responseMessage = getCommunicationManager().sendCommand(IDPSettingsRequest(idpId))

        responseText = checkResponse(responseMessage, "IDPSettingsRequest (idpId=${idpId}")

        if (responseText!=null) {
            return DataCommandResponse<BasalProfileDto?>(PumpCommandType.SetBasalProfile, false, responseText, null)
        }

        val mapSegments = mutableMapOf<Int,IDPSegmentResponse>()

        val settings = responseMessage as IDPSettingsResponse
        for (i in 1..settings.numberOfProfileSegments) {
            responseMessage = getCommunicationManager().sendCommand(IDPSegmentRequest(settings.idpId, i))

            responseText = checkResponse(responseMessage, "IDPSegmentRequest (idpId=${settings.idpId}, segmentNumber=${i})")

            if (responseText!=null) {
                return DataCommandResponse<BasalProfileDto?>(PumpCommandType.SetBasalProfile, false, responseText, null)
            } else {
                mapSegments.put(i, responseMessage as IDPSegmentResponse)
            }
        }

        return tandemDataConverter.getBasalProfileResponse(settings, mapSegments)

    }


    private fun checkResponse(responseMessage: Message?, description: String): String? {
        return if (responseMessage==null || responseMessage is ErrorResponse) {
            val ressponseText = if (responseMessage==null) {
                "Response was null (timeout or some other problem), when getting ${description}"
            } else {
                val errorResponse: ErrorResponse = responseMessage as ErrorResponse
                "Received Error Response: ${errorResponse.errorCode.name} on ${description}."
            }
            aapsLogger.info(TAG, "TANDEMDBG: ${ressponseText}")

            ressponseText
        } else {
            null
        }
    }



    override fun sendBasalProfile(profile: Profile): DataCommandResponse<AdditionalResponseDataInterface?> {
        // TODO V2 Connector: sendBasalProfile  (not available in protocol)
        return super.sendBasalProfile(profile)
    }

    override fun getTime(): DataCommandResponse<PumpTimeDifferenceDto?> {
        return DataCommandResponse(
            PumpCommandType.GetTime, true, null, pumpStatus.pumpTime)
    }

    override fun setTime(): DataCommandResponse<AdditionalResponseDataInterface?> {
        // TODO V2 Connector: setTime (not available in protocol)
        return super.setTime()
    }

    override fun getFilteredPumpHistory(filter: PumpHistoryFilterInterface): DataCommandResponse<List<Any>?> {
        // TODO Connector: getFilteredPumpHistory
        return DataCommandResponse(
            PumpCommandType.GetHistory, true, null, listOf())
    }

    override fun executeCustomCommand(commandType: CustomCommandTypeInterface): DataCommandResponse<AdditionalResponseDataInterface?> {



        return DataCommandResponse(
            PumpCommandType.CustomCommand, false, "Command ${commandType.getKey()} not available.", null)
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
        var responseMessage: Message? = getCommunicationManager().sendCommand(ChangeCartridgeRequest()) as ChangeCartridgeResponse
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
        return when(tandemPumpApiVersion) {
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
            PumpCommandType.GetSettings,
            PumpCommandType.GetBatteryStatus,
            PumpCommandType.GetTemporaryBasal,
        )
    }

    override fun getSupportedCommands(): Set<PumpCommandType> {
        return supportedCommandsList
    }


}