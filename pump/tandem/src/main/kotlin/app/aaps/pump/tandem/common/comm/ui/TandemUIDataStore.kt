package app.aaps.pump.tandem.common.comm.ui

import androidx.lifecycle.MutableLiveData
import app.aaps.pump.common.defs.PumpRunningState
import app.aaps.pump.tandem.common.database.data.dto.TandemHistoryRecordDto
import app.aaps.pump.tandem.common.database.data.dto.TandemQualifyingEventDto
import app.aaps.pump.tandem.t_mobi.ui.actions.other.BasalStatus
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcCondition
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalculatorBuilder
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType
import com.jwoglom.pumpx2.pump.messages.response.control.BolusPermissionResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CancelBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.control.RemoteCarbEntryResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBolusStatusAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
// import com.jwoglom.controlx2.presentation.screens.PumpSetupStage
// import com.jwoglom.controlx2.shared.enums.BasalStatus
// import com.jwoglom.controlx2.shared.enums.CGMSessionState
// import com.jwoglom.controlx2.shared.enums.UserMode
import com.jwoglom.pumpx2.pump.messages.models.NotificationBundle
import com.jwoglom.pumpx2.pump.messages.response.controlStream.DetectingCartridgeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.EnterChangeCartridgeModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.ExitFillTubingModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillCannulaStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillTubingStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.PumpingStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ApiVersionResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpVersionResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TempRateResponse
import timber.log.Timber
import java.time.Instant

// TODO TAF


class TandemUIDataStore {

    // Basic Stuff
    var apiVersionResponse = MutableLiveData<ApiVersionResponse>()
    var pumpVersionResponse = MutableLiveData<PumpVersionResponse>()
    val timeSinceResetResponse = MutableLiveData<TimeSinceResetResponse>()



    // TBR
    var tempRateActive = MutableLiveData<Boolean>(false)
    var tempRateDetails = MutableLiveData<TempRateResponse>()

    // Running State
    var pumpRunningState = MutableLiveData<PumpRunningState>()

    // Notifications
    val notificationBundle = MutableLiveData<NotificationBundle>(NotificationBundle())
    val notificationsPresent = MutableLiveData<Boolean>(false)

    // Cartridge/Canula Actions
    val inChangeCartridgeMode = MutableLiveData<Boolean>()
    val inFillTubingMode = MutableLiveData<Boolean>()
    val enterChangeCartridgeState = MutableLiveData<EnterChangeCartridgeModeStateStreamResponse>()
    val detectingCartridgeState = MutableLiveData<DetectingCartridgeStateStreamResponse>()
    val fillTubingState = MutableLiveData<FillTubingStateStreamResponse>()
    val exitFillTubingState = MutableLiveData<ExitFillTubingModeStateStreamResponse>()
    val fillCannulaState = MutableLiveData<FillCannulaStateStreamResponse>()
    val pumpingState = MutableLiveData<PumpingStateStreamResponse>()





    // Temporary Data for TesterApp
    //val pumpSetupStage = MutableLiveData<PumpSetupStage>(PumpSetupStage.PUMPX2_SEARCHING_FOR_PUMP)
    val pumpConnected = MutableLiveData<Boolean>(false)
    val pumpLastConnectionTimestamp = MutableLiveData<Instant>()
    val pumpLastMessageTimestamp = MutableLiveData<Instant>()
    //val pumpConnectionStatus = MutableLiveData<String>()

    val batteryPercent = MutableLiveData<Int>()
    val cartridgeRemainingUnits = MutableLiveData<Int>()
    val cartridgeRemainingEstimate = MutableLiveData<Boolean>()
    val lastBolusStatus = MutableLiveData<String>()
    val basalRate = MutableLiveData<String>()
    var basalStatus = MutableLiveData<BasalStatus>()

    // for setting TBR
    val tempRatePercentRawValue = MutableLiveData<String?>()
    val tempRateMinutesRawValue = MutableLiveData<String?>()
    val tempRateHoursRawValue = MutableLiveData<String?>()

    // OLD
    var pumpPairKey = MutableLiveData<String>()

    val pumpFinderPumps = MutableLiveData<List<Pair<String, String>>>() // pump name, pump MAC
    val setupDeviceName = MutableLiveData<String>()
    val setupPairingCodeType = MutableLiveData<PairingCodeType>()
    val pumpSid = MutableLiveData<Int>()
    val setupDeviceModel = MutableLiveData<String>()
    val pumpCriticalError = MutableLiveData<Pair<String, Instant>>()


    // DATA ----------------------------------

    val dataQELoaded = MutableLiveData<Boolean>(false)
    val dataQE = MutableLiveData<MutableList<TandemQualifyingEventDto>>(mutableListOf())

    val dataHistoryLoaded = MutableLiveData<Boolean>(false)
    val dataHistory = MutableLiveData<MutableList<TandemHistoryRecordDto>>(mutableListOf())

    // DATA ----------------------------------




    //val iobUnits = MutableLiveData<Double>()
    //val controlIQStatus = MutableLiveData<String>()
//    val controlIQMode = MutableLiveData<UserMode>()



//    val cgmSessionState = MutableLiveData<CGMSessionState>()
//    val cgmSessionExpireRelative = MutableLiveData<String>()
//    val cgmSessionExpireExact = MutableLiveData<String>()
//    val cgmTransmitterStatus = MutableLiveData<String>()
//    val cgmReading = MutableLiveData<Int>()
//    val cgmDelta = MutableLiveData<Int>()
//    val cgmStatusText = MutableLiveData<String>()
//    val cgmHighLowState = MutableLiveData<String>()
//    val cgmDeltaArrow = MutableLiveData<String>()
//    val cgmSetupG6TxId = MutableLiveData<String>()
//    val cgmSetupG6SensorCode = MutableLiveData<String>()
//    val cgmSetupG7SensorCode = MutableLiveData<String>()
//    val savedG7PairingCode = MutableLiveData<Int>()
    val bolusCalcDataSnapshot = MutableLiveData<BolusCalcDataSnapshotResponse>()
    val bolusCalcLastBG = MutableLiveData<LastBGResponse>()
    //val maxBolusAmount = MutableLiveData<Int>()

    val landingBasalDisplayedText = MutableLiveData<String>()
    val landingControlIQDisplayedText = MutableLiveData<String>()

    val bolusCalculatorBuilder = MutableLiveData<BolusCalculatorBuilder>()
    val bolusCurrentParameters = MutableLiveData<BolusParameters>()
    val bolusCurrentConditions = MutableLiveData<List<BolusCalcCondition>>()
    val bolusConditionsPrompt = MutableLiveData<MutableList<BolusCalcCondition>>()
    val bolusConditionsPromptAcknowledged = MutableLiveData<MutableList<BolusCalcCondition>>()
    val bolusConditionsExcluded = MutableLiveData<MutableSet<BolusCalcCondition>>()
    val bolusFinalParameters = MutableLiveData<BolusParameters>()
    val bolusFinalCalcUnits = MutableLiveData<BolusCalcUnits>()
    val bolusFinalConditions = MutableLiveData<Set<BolusCalcCondition>>()

    val bolusUnitsRawValue = MutableLiveData<String?>()
    val bolusCarbsRawValue = MutableLiveData<String?>()
    val bolusGlucoseRawValue = MutableLiveData<String?>()




    val bolusPermissionResponse = MutableLiveData<BolusPermissionResponse>()
    val bolusCarbEntryResponse = MutableLiveData<RemoteCarbEntryResponse>()
    val bolusInitiateResponse = MutableLiveData<InitiateBolusResponse>()
    val bolusCancelResponse = MutableLiveData<CancelBolusResponse>()
    val lastBolusStatusResponse = MutableLiveData<LastBolusStatusAbstractResponse>()
    val bolusCurrentResponse = MutableLiveData<CurrentBolusStatusResponse>()


    val historyLogStatus = MutableLiveData<HistoryLogStatusResponse>()

    val historyLogCache = MutableLiveData<MutableMap<Long, HistoryLog>>(mutableMapOf())
    val debugMessageCache = MutableLiveData<List<Pair<Message, Instant>>>()
    val debugPromptAwaitingResponses = MutableLiveData<MutableSet<String>>()

    init {
        // pumpConnected.observeForever { t -> Timber.i("DataStore.pumpConnected=$t") }
        // pumpLastConnectionTimestamp.observeForever { t -> Timber.i("DataStore.pumpLastConnectionTimestamp=$t") }
        // //pumpLastMessageTimestamp.observeForever { t -> Timber.i("DataStore.pumpLastMessageTimestamp=$t") }
        // //watchConnected.observeForever { t -> Timber.i("DataStore.watchConnected=$t") }
        //
        // //pumpSetupStage.observeForever { t -> Timber.i("DataStore.setupStage=$t") }
        // pumpFinderPumps.observeForever { t -> Timber.i("DataStore.pumpFinderPumps=$t") }
        // setupDeviceName.observeForever { t -> Timber.i("DataStore.setupDeviceName=$t") }
        // setupPairingCodeType.observeForever { t -> Timber.i("DataStore.setupPairingCodeType=$t") }
        // pumpSid.observeForever { t -> Timber.i("DataStore.pumpSid=$t") }
        // setupDeviceModel.observeForever { t -> Timber.i("DataStore.setupDeviceModel=$t") }
        // pumpCriticalError.observeForever { t -> Timber.i("DataStore.pumpCriticalError=$t") }
        //
        // notificationBundle.observeForever { t -> Timber.i("DataStore.notificationBundle=$t") }
        // batteryPercent.observeForever { t -> Timber.i("DataStore.batteryPercent=$t") }
        // //iobUnits.observeForever { t -> Timber.i("DataStore.iobUnits=$t") }
        // cartridgeRemainingUnits.observeForever { t -> Timber.i("DataStore.cartridgeRemainingUnits=$t") }
        // cartridgeRemainingEstimate.observeForever { t -> Timber.i("DataStore.cartridgeRemainingEstimate=$t") }
        // lastBolusStatus.observeForever { t -> Timber.i("DataStore.lastBolusStatus=$t") }
        // //controlIQStatus.observeForever { t -> Timber.i("DataStore.controlIQStatus=$t") }
        // //controlIQMode.observeForever { t -> Timber.i("DataStore.controlIQMode=$t") }
        // basalRate.observeForever { t -> Timber.i("DataStore.basalRate=$t") }
        // basalStatus.observeForever { t -> Timber.i("DataStore.basalStatus=$t") }
        // tempRateActive.observeForever { t -> Timber.i("DataStore.tempRateActive=$t") }
        //tempRateDetails.observeForever { t -> Timber.i("DataStore.tempRateDetails=$t") }
        //cgmSessionState.observeForever { t -> Timber.i("DataStore.cgmSessionState=$t") }
//        cgmSessionExpireRelative.observeForever { t -> Timber.i("DataStore.cgmSessionExpireRelative=$t") }
//        cgmSessionExpireExact.observeForever { t -> Timber.i("DataStore.cgmSessionExpireExact=$t") }
//        cgmTransmitterStatus.observeForever { t -> Timber.i("DataStore.cgmTransmitterStatus=$t") }
//        cgmReading.observeForever { t -> Timber.i("DataStore.cgmReading=$t") }
//        cgmDelta.observeForever { t -> Timber.i("DataStore.cgmDelta=$t") }
//        cgmStatusText.observeForever { t -> Timber.i("DataStore.cgmStatusText=$t") }
//        cgmHighLowState.observeForever { t -> Timber.i("DataStore.cgmHighLowState=$t") }
//        cgmDeltaArrow.observeForever { t -> Timber.i("DataStore.cgmDeltaArrow=$t") }
//        cgmSetupG6TxId.observeForever { t -> Timber.i("DataStore.cgmSetupG6TxId=$t") }
//        cgmSetupG6SensorCode.observeForever { t -> Timber.i("DataStore.cgmSetupG6SensorId=$t") }
//        cgmSetupG7SensorCode.observeForever { t -> Timber.i("DataStore.cgmSetupG7SensorId=$t") }
//        savedG7PairingCode.observeForever { t -> Timber.i("DataStore.savedG7PairingCode=$t") }
//         bolusCalcDataSnapshot.observeForever { t -> Timber.i("DataStore.bolusCalcDataSnapshot=$t") }
//         bolusCalcLastBG.observeForever { t -> Timber.i("DataStore.bolusCalcLastBG=$t") }
//         //maxBolusAmount.observeForever { t -> Timber.i("DataStore.maxBolusAmount=$t") }
//
//         landingBasalDisplayedText.observeForever { t -> Timber.i("DataStore.landingBasalDisplayedText=$t") }
//         landingControlIQDisplayedText.observeForever { t -> Timber.i("DataStore.landingControlIQDisplayedText=$t") }
//
//         bolusCalculatorBuilder.observeForever { t -> Timber.i("DataStore.bolusCalculatorBuilder=$t") }
//         bolusCurrentParameters.observeForever { t -> Timber.i("DataStore.bolusCurrentParameters=$t") }
//         bolusCurrentConditions.observeForever { t -> Timber.i("DataStore.bolusCurrentConditions=$t") }
//         bolusConditionsPrompt.observeForever { t -> Timber.i("DataStore.bolusConditionsPrompt=$t") }
//         bolusConditionsPromptAcknowledged.observeForever { t -> Timber.i("DataStore.bolusConditionsPromptAcknowledged=$t") }
//         bolusConditionsExcluded.observeForever { t -> Timber.i("DataStore.bolusConditionsExcluded=$t") }
//         bolusFinalParameters.observeForever { t -> Timber.i("DataStore.bolusFinalParameters=$t") }
//         bolusFinalCalcUnits.observeForever { t -> Timber.i("DataStore.bolusFinalCalcUnits=$t") }
//         bolusFinalConditions.observeForever { t -> Timber.i("DataStore.bolusFinalConditions=$t") }
//
//         bolusUnitsRawValue.observeForever { t -> Timber.i("DataStore.bolusUnitsRawValue=$t") }
//         bolusCarbsRawValue.observeForever { t -> Timber.i("DataStore.bolusCarbsRawValue=$t") }
//         bolusGlucoseRawValue.observeForever { t -> Timber.i("DataStore.bolusGlucoseRawValue=$t") }
//
//         tempRatePercentRawValue.observeForever { t -> Timber.i("DataStore.tempRatePercentRawValue=$t") }
//         tempRateMinutesRawValue.observeForever { t -> Timber.i("DataStore.tempRateMinutesRawValue=$t") }
//         tempRateHoursRawValue.observeForever { t -> Timber.i("DataStore.tempRateHoursRawValue=$t") }
//
//         timeSinceResetResponse.observeForever { t -> Timber.i("DataStore.timeSinceResetResponse=$t") }
//         bolusPermissionResponse.observeForever { t -> Timber.i("DataStore.bolusPermissionResponse=$t") }
//         bolusCarbEntryResponse.observeForever { t -> Timber.i("DataStore.bolusCarbEntryResponse=$t") }
//         bolusInitiateResponse.observeForever { t -> Timber.i("DataStore.bolusInitiateResponse=$t") }
//         bolusCancelResponse.observeForever { t -> Timber.i("DataStore.bolusCancelResponse=$t") }
//         bolusCurrentResponse.observeForever { t -> Timber.i("DataStore.bolusCurrentResponse=$t") }
//
//         inChangeCartridgeMode.observeForever { t -> Timber.i("DataStore.inChangeCartridgeMode=$t") }
//         inFillTubingMode.observeForever { t -> Timber.i("DataStore.inFillTubingMode=$t") }
//         enterChangeCartridgeState.observeForever { t -> Timber.i("DataStore.enterChangeCartridgeState=$t") }
//         detectingCartridgeState.observeForever { t -> Timber.i("DataStore.detectingCartridgeState=$t") }
//         fillTubingState.observeForever { t -> Timber.i("DataStore.fillTubingState=$t") }
//         exitFillTubingState.observeForever { t -> Timber.i("DataStore.exitFillTubingState=$t") }
//         fillCannulaState.observeForever { t -> Timber.i("DataStore.fillCannulaState=$t") }
//         pumpingState.observeForever { t -> Timber.i("DataStore.pumpingState=$t") }
//
//         historyLogStatus.observeForever { t -> Timber.i("DataStore.historyLogStatus=$t") }
//
//         historyLogCache.observeForever { t -> Timber.i("DataStore.historyLogCache=$t") }
//         debugMessageCache.observeForever { t -> Timber.i("DataStore.debugMessageCache=$t") }
//         debugPromptAwaitingResponses.observeForever { t -> Timber.i("DataStore.debugPromptAwaitingResponses=$t") }
    }


}