package app.aaps.pump.tandem.mobi.ui.overview

import android.content.Context
import android.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.TB
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.rx.events.EventRefreshButtonState
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.ActionCategory
import app.aaps.core.ui.compose.pump.PumpAction
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import app.aaps.core.ui.compose.pump.PumpInfoGroup
import app.aaps.core.ui.compose.pump.PumpInfoInterface
import app.aaps.core.ui.compose.pump.PumpInfoRow

import app.aaps.core.ui.compose.pump.PumpOverviewUiState
import app.aaps.core.ui.compose.pump.tickerFlow
import app.aaps.pump.common.data.PumpStatus
import app.aaps.pump.common.defs.PumpDriverMode
import app.aaps.pump.common.defs.PumpDriverState
import app.aaps.pump.common.defs.PumpRunningState
import app.aaps.pump.common.defs.PumpUpdateFragmentType
import app.aaps.pump.common.driver.connector.defs.PumpCommandType
import app.aaps.pump.common.events.EventPumpDriverStateChanged
import app.aaps.pump.common.events.EventPumpFragmentValuesChanged
import app.aaps.pump.tandem.R
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.driver.connector.def.TandemCustomCommand
import app.aaps.pump.tandem.common.keys.TandemBooleanPreferenceKey
import app.aaps.pump.tandem.common.keys.TandemStringPreferenceKey
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import app.aaps.pump.tandem.mobi.TandemMobiPumpPlugin
// import app.aaps.pump.dana.DanaPump
// import app.aaps.pump.dana.R
// import app.aaps.pump.dana.events.EventDanaRNewStatus
// import app.aaps.pump.dana.keys.DanaStringNonKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import app.aaps.core.ui.R as Rco
import app.aaps.pump.common.R as Rc
import app.aaps.core.interfaces.R as Rci

sealed class MobiOverviewEvent {
    data object StartData : MobiOverviewEvent()
    data object StartActions : MobiOverviewEvent()
    data object OpenNotification : MobiOverviewEvent()
    data object OpenEvents : MobiOverviewEvent()
    data object OpenHistory : MobiOverviewEvent()
}

@HiltViewModel
@Stable
open class MobiOverviewViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    protected val rh: ResourceHelper,
    rxBus: RxBus,
    aapsSchedulers: AapsSchedulers,
    private val commandQueue: CommandQueue,
    private val dateUtil: DateUtil,
    private val tandemPump: TandemMobiPumpPlugin,
    private val tandemPumpStatus: TandemPumpStatus,
    private val activePlugin: ActivePlugin,
    private val ch: ConcentrationHelper,
    private val persistenceLayer: PersistenceLayer,
    protected val uel: UserEntryLogger,
    private val tandemUtil: TandemPumpUtil,
    protected val preferences: Preferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val disposable = CompositeDisposable()

    private val _events = MutableSharedFlow<MobiOverviewEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<MobiOverviewEvent> = _events

    private val communicationStatus = PumpCommunicationStatus(rxBus, commandQueue, context, viewModelScope)

    // RxBus events converted to a flow trigger for recomposition
    protected val rxTrigger = MutableStateFlow(0L)

    companion object {
        private const val PLACEHOLDER = "-"
    }

    var displayDriver = true

    var currentActivity = mutableStateOf<String>("")
    var buttonsEnabled = mutableStateOf<Boolean>(true)
    var pumpRunningState = mutableStateOf(rh.gs(Rc.string.pump_status_running))
    var pumpErrors = mutableStateOf("")
    var pumpTempBasal = mutableStateOf<String>("-")
    var semaphoreTexts = mutableStateOf("")

    var mapSemaphore = mapOf(
        Pair("NOTIFICATIONS", rh.gs(R.string.pump_data_status_notification)),
        Pair("EVENTS", rh.gs(R.string.pump_data_status_events)),
        Pair("HISTORY", rh.gs(R.string.pump_data_status_history))
    )

    //val iconBack = Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Rco.string.back))

    init {

        // TODO refresh driver display

        displayDriver = preferences.get(TandemBooleanPreferenceKey.DisplayDriverVersion)

        disposable += rxBus
            .toObservable(EventRefreshButtonState::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                buttonsEnabled.value = it.newState
                rxTrigger.value = System.currentTimeMillis()
                       },
                       { aapsLogger.error(LTag.PUMP, "Error: ${it.message}", it) })
        disposable += rxBus
            .toObservable(EventPumpDriverStateChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                            updateCurrentActivity(it.driverStatus)
                            rxTrigger.value = System.currentTimeMillis()
                       },
                       { aapsLogger.error(LTag.PUMP, "Error: ${it.message}", it) })
        disposable += rxBus
            .toObservable(EventPumpFragmentValuesChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                            updateGUI(it.updateType)
                            rxTrigger.value = System.currentTimeMillis()
                       },
                       { aapsLogger.error(LTag.PUMP, "Error: ${it.message}", it) })


        updateCurrentActivity(tandemUtil.driverStatus)


        // disposable += rxBus
        //     .toObservable(EventDanaRNewStatus::class.java)
        //     .observeOn(aapsSchedulers.io)
        //     .subscribe({ rxTrigger.value = System.currentTimeMillis() }, { aapsLogger.error(LTag.PUMP, "Error", it) })
        // disposable += rxBus
        //     .toObservable(EventInitializationChanged::class.java)
        //     .observeOn(aapsSchedulers.io)
        //     .subscribe({ rxTrigger.value = System.currentTimeMillis() }, { aapsLogger.error(LTag.PUMP, "Error", it) })
        //
        // // Observe EB/TB database changes for immediate UI updates
        // persistenceLayer.observeChanges(EB::class.java)
        //     .onEach { rxTrigger.value = System.currentTimeMillis() }
        //     .launchIn(viewModelScope)
        // persistenceLayer.observeChanges(TB::class.java)
        //     .onEach { rxTrigger.value = System.currentTimeMillis() }
        //     .launchIn(viewModelScope)
    }


    private fun updateGUI(updateType: PumpUpdateFragmentType) {
        updateCurrentActivity(tandemUtil.driverStatus)

        // TODO need to reimplement the old logic

        //val pumpState = pumpSync.expectedPumpState()

        // NOTE: Custom_2 is used for updating 3 indicators;
        //       Custom_1 is used for updating Last Pump Event (used for debug purposes only, labels are disabled programaticaly)

        // currentTextColor = binding.pumpBaseBasalRate.currentTextColor // we need color from item, in case we are not running in dark mode
        //
        // // last connection
        // if (pumpStatus.lastConnection != 0L) {
        //
        //     val min = (System.currentTimeMillis() - pumpStatus.lastConnection) / 1000 / 60
        //     if (pumpStatus.lastConnection + 60 * 1000 > System.currentTimeMillis()) {
        //         binding.pumpLastConnection.setText(app.aaps.core.interfaces.R.string.now)
        //         binding.pumpLastConnection.setTextColor(currentTextColor)
        //     } else if (pumpStatus.lastConnection + 30 * 60 * 1000 < System.currentTimeMillis()) {
        //
        //         if (min < 60) {
        //             binding.pumpLastConnection.text = resourceHelper.gs(app.aaps.core.interfaces.R.string.minago, min)
        //         } else if (min < 1440) {
        //             val h = min / 60.0f
        //             binding.pumpLastConnection.text = resourceHelper.gs(app.aaps.core.interfaces.R.string.hoursago, h)
        //         } else {
        //             val h = min / 60.0f
        //             val d = h / 24.0f
        //             // h = h - (d * 24);
        //             binding.pumpLastConnection.text = resourceHelper.gs(app.aaps.core.interfaces.R.string.days_ago, d)
        //         }
        //         binding.pumpLastConnection.setTextColor(Color.RED)
        //     } else {
        //         val minAgo = dateUtil.minAgo(resourceHelper, pumpStatus.lastConnection)
        //         binding.pumpLastConnection.text = minAgo
        //         binding.pumpLastConnection.setTextColor(currentTextColor)
        //     }
        // }
        //
        // if (updateType == PumpUpdateFragmentType.PumpStatus || updateType == PumpUpdateFragmentType.Full) {
        //     // Pump Status (Error)
        //     val pumpDriverState: PumpDriverState = pumpUtil.driverStatus
        //
        //     // updateCurrentActivity(pumpDriverState)
        //     //updatePumpStatus()
        //     updateCurrentActivity(pumpDriverState)
        // }
        //
        //
        // this.updateQueue()
        //
        //
        // if (updateType == PumpUpdateFragmentType.Bolus ||
        //     updateType == PumpUpdateFragmentType.TreatmentValues ||
        //     updateType == PumpUpdateFragmentType.Full) {
        //
        //     // Last Bolus, TBR (Profile Change)
        //
        //     // last bolus
        //     val bolus = pumpStatus.tandemLastBolus
        //
        //     if (bolus != null) {
        //         val agoMsc = System.currentTimeMillis() - bolus.timestamp
        //         val bolusMinAgo = agoMsc.toDouble() / 60.0 / 1000.0
        //         val unit = resourceHelper.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)
        //         val ago: String
        //         if (agoMsc < 60 * 1000) {
        //             ago = resourceHelper.gs(Rc.string.time_now)
        //         } else if (bolusMinAgo < 60) {
        //             ago = dateUtil.minAgo(resourceHelper, bolus.timestamp)
        //         } else if (bolusMinAgo < (60*24)) {
        //             ago = dateUtil.hourAgo(bolus.timestamp, resourceHelper)
        //         } else if (bolusMinAgo < (60*24*30)) {
        //             ago = dateUtil.dayAgo(bolus.timestamp, resourceHelper)
        //         } else {
        //             ago = resourceHelper.gs(Rc.string.time_over_month_ago)
        //         }
        //         binding.pumpLastBolus.text = resourceHelper.gs(R.string.pump_last_bolus, bolus.amountImmediateDelivered, unit, ago)
        //     } else {
        //         binding.pumpLastBolus.text = "-"
        //     }
        //
        // }
        //
        // if (updateType == PumpUpdateFragmentType.TBR ||
        //     updateType == PumpUpdateFragmentType.TreatmentValues ||
        //     updateType == PumpUpdateFragmentType.Full) {
        //
        //     // base basal rate
        //     binding.pumpBaseBasalRate.text = resourceHelper.gs(Rc.string.pump_base_basal_rate_with_profile,
        //                                                        pumpStatus.activeProfileName,
        //                                                        tandemPumpPlugin.baseBasalRate.cU)
        //
        //     // tbr (always saved on pumpStatus)
        //     if (pumpStatus.currentTempBasal==null || System.currentTimeMillis() > pumpStatus.currentTempBasalEstimatedEnd!!) {
        //         pumpStatus.clearTbr()
        //         binding.pumpTempBasal.text = "-"
        //     } else {
        //         val msDiff = pumpStatus.currentTempBasalEstimatedEnd!! - System.currentTimeMillis()
        //         val min = msDiff / (60.0 * 1000.0)
        //         binding.pumpTempBasal.text = resourceHelper.gs(Rc.string.pump_tbr_remaining_percent,
        //                                                        pumpStatus.currentTempBasal!!.insulinRate.toInt(), min.toInt())
        //     }
        // }
        //
        // if (updateType == PumpUpdateFragmentType.Configuration ||
        //     updateType == PumpUpdateFragmentType.Full) {
        //     // Firmware, Errors
        //
        //     if (pumpStatus.pumpDriverMode == PumpDriverMode.Demo) {
        //         binding.pumpFirmware.text = resourceHelper.gs(R.string.pump_firmware_demo)
        //     } else {
        //         if (pumpStatus.tandemPumpFirmware.isClosedLoopPossible) {
        //             binding.pumpFirmware.text = pumpStatus.tandemPumpFirmware.description
        //         } else {
        //             binding.pumpFirmware.text = resourceHelper.gs(R.string.pump_firmware_open_loop_only, pumpStatus.tandemPumpFirmware.description)
        //         }
        //     }
        //
        //     binding.pumpSerialNo.text = pumpUtil.getStringPreferenceOrDefault(TandemStringPreferenceKey.PumpSerial, "-")
        //     binding.pumpAddress.text = pumpUtil.getStringPreferenceOrDefault(TandemStringPreferenceKey.PumpAddress, "-")
        // }
        //
        // if (updateType == PumpUpdateFragmentType.Battery ||
        //     updateType == PumpUpdateFragmentType.OtherValues ||
        //     updateType == PumpUpdateFragmentType.Full) {
        //     updateBattery()
        // }
        //
        // if (updateType == PumpUpdateFragmentType.Reservoir ||
        //     updateType == PumpUpdateFragmentType.OtherValues ||
        //     updateType == PumpUpdateFragmentType.Full) {
        //     updateReservoir()
        // }
        //
        // if (updateType == PumpUpdateFragmentType.Custom_1 ||
        //     updateType == PumpUpdateFragmentType.Full) {
        //     // qualifying events
        //     val sb = StringBuilder()
        //
        //     //aapsLogger.info(TAG, "XA: QE: ${pumpStatus.lastQualifyingEventsInfo}")
        //
        //     if (pumpStatus.lastQualifyingEventsInfo!=null) {
        //         sb.append("QE: ")
        //         sb.append(pumpStatus.lastQualifyingEventsInfo)
        //     }
        //
        //     //aapsLogger.info(TAG, "XA: Alarms: ${pumpStatus.tandemAlarms}")
        //
        //     if (pumpStatus.tandemAlarms!=null && !pumpStatus.tandemAlarms!!.isEmpty()) {
        //         if (!sb.isEmpty()){
        //             sb.append(", ")
        //         }
        //         sb.append("Alarms: ")
        //         for (alarm in pumpStatus.tandemAlarms!!) {
        //             sb.append(alarm.name + ", ")
        //         }
        //     }
        //
        //     //aapsLogger.info(TAG, "XA: Alerts: ${pumpStatus.tandemAlerts}")
        //
        //     if (pumpStatus.tandemAlerts!=null && !pumpStatus.tandemAlerts!!.isEmpty()) {
        //         if (!sb.isEmpty()){
        //             sb.append(", ")
        //         }
        //         sb.append("Alerts: ")
        //         for (alarm in pumpStatus.tandemAlerts!!) {
        //             sb.append(alarm.name + ", ")
        //         }
        //     }
        //
        //     var endText = sb.toString()
        //
        //     if (endText.endsWith(", ")) {
        //         endText = endText.substring(0, endText.length-2)
        //     }
        //
        //     binding.pumpQeInfo.text = endText
        // }
        //
        // if (updateType == PumpUpdateFragmentType.Custom_2 ||
        //     updateType == PumpUpdateFragmentType.Full) {
        //     updateDataSemaphore()
        // }
        //
        // showPumpErrors()
        // setVisibilityOfDriverVersion()




    }


    private fun updateDataSemaphore() {
        val sb = StringBuilder()

        if (tandemPumpStatus.semaphoreNotifications) {
            sb.append("   ")
            sb.append(mapSemaphore.get("NOTIFICATIONS"))
        }

        if (tandemPumpStatus.semaphoreEvents) {
            sb.append("   ")
            sb.append(mapSemaphore.get("EVENTS"))
        }

        if (tandemPumpStatus.semaphoreHistory) {
            sb.append("   ")
            sb.append(mapSemaphore.get("HISTORY"))
        }

        semaphoreTexts.value = sb.toString().trim()

        // updateLabelColor(binding.pumpDataStatusNotification, pumpStatus.semaphoreNotifications, Color.RED)
        // updateLabelColor(binding.pumpDataStatusEvents, pumpStatus.semaphoreEvents, Color.GREEN)
        // updateLabelColor(binding.pumpDataStatusHistory, pumpStatus.semaphoreHistory, Color.BLUE)
    }




    // private val pumpDataFlow = combine(
    //     tandemPumpStatus.lastConnectionFlow,
    //     tandemPumpStatus.reservoirRemainingUnitsFlow,
    //     tandemPumpStatus.batteryRemainingFlow,
    //     tandemPumpStatus.lastBolusTimeFlow,
    //     tandemPumpStatus.lastBolusAmountFlow,
    //     tandemPumpStatus.pumpRunningStateFlow
    // ) { lastConnection, reservoir, battery, lastBolusTime, lastBolusAmount, pumpRunningState ->
    //     PumpSnapshot(lastConnectionTime = lastConnection,
    //                  reservoir = reservoir,
    //                  battery = battery,
    //                  lastBolusTime = lastBolusTime,
    //                  lastBolusAmount = lastBolusAmount,
    //                  pumpRunningState = pumpRunningState)
    // }


    private val refreshFlow = combine(
        rxTrigger,
        communicationStatus.refreshTrigger,
        tickerFlow(60_000L)
    ) { _, _, _ -> }


    val uiState: StateFlow<PumpOverviewUiState> = combine(
        communicationStatus.refreshTrigger,
        tickerFlow(60_000L)
    ) { _, _ -> buildUiState() }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = buildUiState()
    )


    private fun buildUiState(): PumpOverviewUiState {
        return PumpOverviewUiState(
            statusBanner = communicationStatus.statusBanner(),
            infoRows = buildInfoRows(),
            primaryActions = buildPrimaryActions(),
            //managementActions = buildManagementActions(),
            queueStatus = communicationStatus.queueStatus()
        )
    }


    override fun onCleared() {
        super.onCleared()
        disposable.clear()
    }


    fun onRefreshClick() {
        aapsLogger.debug(LTag.PUMP, "Clicked connect to pump")

        setButtonState(false)
        tandemPump.resetStatusState()
        commandQueue.readStatus("Clicked refresh", object : Callback() {
            override fun run() {
                setButtonState(true)
            }
        })
    }

    private fun setButtonState(enabled: Boolean) {
        this.buttonsEnabled.value = enabled
    }

    fun onDataClick() {
        _events.tryEmit(MobiOverviewEvent.StartData)
    }

    fun onActionClick() {
        _events.tryEmit(MobiOverviewEvent.StartActions)
    }

    fun onOpenEventsClick() {
        _events.tryEmit(MobiOverviewEvent.OpenEvents)
    }

    fun onOpenHistoryClick() {
        _events.tryEmit(MobiOverviewEvent.OpenHistory)
    }

    fun onOpenNotificationClick() {
        _events.tryEmit(MobiOverviewEvent.OpenNotification)
    }


    private fun buildPrimaryActions(): List<PumpAction> {
        if (primaryActions==null || primaryActions.isEmpty()) {
            primaryActions = listOf(
                PumpAction(
                    label = rh.gs(app.aaps.core.ui.R.string.refresh),
                    iconRes = app.aaps.core.ui.R.drawable.ic_refresh,
                    category = ActionCategory.PRIMARY,
                    visible = buttonsEnabled.value,
                    onClick = { onRefreshClick() }
                ),
                PumpAction(
                    label = rh.gs(R.string.pump_data),
                    icon = Icons.AutoMirrored.Filled.List,
                    category = ActionCategory.PRIMARY,
                    visible = buttonsEnabled.value,
                    onClick = { onDataClick() }
                ),
                PumpAction(
                    label = rh.gs(R.string.pump_actions),
                    icon = Icons.AutoMirrored.Filled.List,
                    category = ActionCategory.PRIMARY,
                    visible = buttonsEnabled.value,
                    onClick = { onActionClick() }
                )
            )

        }

        return primaryActions;
    }


    var primaryActions = listOf<PumpAction>()



    @Synchronized
    private fun updateCurrentActivity(pumpDriverState: PumpDriverState?) {
        val resActivity = Rc.string.pump_current_activity

        //aapsLogger.info(LTag.PUMP, "DUB Update Current activity: ${pumpDriverState!!.name}")

        when (pumpDriverState) {
            //null,
            PumpDriverState.Ready,
            PumpDriverState.Sleeping                   -> {
                currentActivity.value = rh.gs(pumpDriverState.resourceId)
                // icon {fa-bed}
            }
            PumpDriverState.Connecting,
            PumpDriverState.Handshaking,
            PumpDriverState.Disconnecting              ->  {
                currentActivity.value = rh.gs(pumpDriverState.resourceId)
                // {fa-bluetooth spin}
            }
            PumpDriverState.Connected,
            PumpDriverState.Disconnected               -> {
                currentActivity.value = rh.gs(pumpDriverState.resourceId)
                // {fa-bluetooth}
            }

            PumpDriverState.ErrorCommunicatingWithPump -> {
                currentActivity.value = "Error ???"
                // fa-bed
                val errorType = tandemUtil.errorType

                pumpErrors.value = if (errorType != null) errorType.name else ""
                //aapsLogger.warn(LTag.PUMP, "Errors are not supported.")
            }

            PumpDriverState.ExecutingCommand           -> {
                val commandType: PumpCommandType? = tandemUtil.currentCommand
                val customCommandTypeInterface : TandemCustomCommand? = tandemUtil.customCommandType as TandemCustomCommand?
                // {fa-bluetooth}
                if (commandType == null) {
                    currentActivity.value = rh.gs(pumpDriverState.resourceId)
                } else {
                    if (commandType == PumpCommandType.CustomCommand) {
                        if (customCommandTypeInterface==null) {
                            currentActivity.value = rh.gs(commandType.resourceId)
                        } else {
                            currentActivity.value = customCommandTypeInterface.getDescription()
                        }
                    } else {
                        if (commandType == PumpCommandType.GetHistoryWithParameters) {
                            val progress: String = tandemUtil.historyProgress.orEmpty()
                            currentActivity.value = rh.gs(commandType.resourceId, progress)
                        } else {
                            currentActivity.value = rh.gs(commandType.resourceId)
                        }
                    }
                }
            }

            else                                       -> {
                currentActivity.value = rh.gs(pumpDriverState!!.resourceId)
            }
        }

        updatePumpStatus()
    }

    private fun updatePumpStatus() {
        when(tandemPumpStatus.pumpRunningState) {
            PumpRunningState.Unknown   -> pumpRunningState.value = rh.gs(Rc.string.pump_status_unknown)
            PumpRunningState.Running   -> pumpRunningState.value = rh.gs(Rc.string.pump_status_running)
            PumpRunningState.Suspended -> pumpRunningState.value = rh.gs(Rc.string.pump_status_suspended)
        }
    }



    // val primaryActions = listOf(
    //     PumpAction(
    //         label = rh.gs(app.aaps.core.ui.R.string.refresh),
    //         iconRes = app.aaps.core.ui.R.drawable.ic_refresh,
    //         category = ActionCategory.PRIMARY,
    //         visible = true, // TODO
    //         onClick = { onRefreshClick() }
    //     ),
    //     PumpAction(
    //         label = rh.gs(R.string.pump_data),
    //         icon = Icons.AutoMirrored.Filled.List,
    //         category = ActionCategory.PRIMARY,
    //         visible = true, // TODO
    //         onClick = { onDataClick() }
    //     ),
    //     PumpAction(
    //         label = rh.gs(R.string.pump_actions),
    //         icon = Icons.AutoMirrored.Filled.List,
    //         category = ActionCategory.PRIMARY,
    //         visible = true, // TODO
    //         onClick = { onActionClick() }
    //     )
    // )

    // Battery warn level





    /**
     * Override in subclasses for variant-specific unpair logic (e.g., BLE bond removal for DanaRS).
     * Default: clears stored device name, resets pump state, logs user entry.
     */
    // open fun performUnpair() {
    //     uel.log(Action.CLEAR_PAIRING_KEYS, Sources.Dana)
    //     preferences.remove(DanaStringNonKey.RName)
    //     danaPump.reset()
    //     rxTrigger.value = System.currentTimeMillis()
    // }

    /**
     * Override in subclasses to add variant-specific management actions (e.g., BLE pair/unpair for DanaRS).
     */
    // protected open fun buildManagementActions(pump: TandemPumpStatus, isInitialized: Boolean, isConfigured: Boolean): List<PumpAction> = buildList {
    //     // User Settings (only for non-Korean, non-legacy-DanaR firmware)
    //     // if (pump.hwModel != 1 && pump.protocol != 0x00) {
    //     //     add(
    //     //         PumpAction(
    //     //             label = rh.gs(R.string.danar_user_options),
    //     //             icon = Icons.Filled.Settings,
    //     //             category = ActionCategory.MANAGEMENT,
    //     //             visible = isInitialized,
    //     //             onClick = { onUserSettingsClick() }
    //     //         )
    //     //     )
    //     // }
    //
    //     add(
    //         PumpAction(
    //             label = rh.gs(Rco.string.refresh),
    //             icon = Icons.Filled.Bluetooth,
    //             category = ActionCategory.MANAGEMENT,
    //             onClick = { onRefreshClick() }
    //         )
    //     )
    //
    //     add(
    //         PumpAction(
    //             label = rh.gs(Rco.string.refresh),
    //             icon = Icons.Filled.Bluetooth,
    //             category = ActionCategory.MANAGEMENT,
    //             onClick = { onRefreshClick() }
    //         )
    //     )
    //
    //     add(
    //         PumpAction(
    //             label = rh.gs(Rco.string.refresh),
    //             icon = Icons.Filled.Bluetooth,
    //             category = ActionCategory.MANAGEMENT,
    //             onClick = { onRefreshClick() }
    //         )
    //     )
    //
    //
    //     // Pair / Unpair
    //     // if (isConfigured) {
    //     //     add(
    //     //         PumpAction(
    //     //             label = rh.gs(app.aaps.core.ui.R.string.pump_unpair),
    //     //             icon = Icons.Filled.Bluetooth,
    //     //             category = ActionCategory.MANAGEMENT,
    //     //             onClick = { onUnpairClick() }
    //     //         )
    //     //     )
    //     // } else {
    //     //     add(
    //     //         PumpAction(
    //     //             label = rh.gs(app.aaps.core.ui.R.string.pairing),
    //     //             icon = Icons.Filled.Bluetooth,
    //     //             category = ActionCategory.MANAGEMENT,
    //     //             onClick = { onPairClick() }
    //     //         )
    //     //     )
    //     // }
    // }


    private fun buildInfoRows(): List<PumpInfoInterface> = buildList {

        // TODO split data creation (in updateGUI)

        var infoGroup = PumpInfoGroup()

        //  1. Pump Firmware
        infoGroup.list.add(PumpInfoRow(label = rh.gs(R.string.pump_firmware_label), value = buildFirmware()))

        //  2. Serial Nr
        tandemPump.serialNumber().takeIf { it.isNotEmpty() }?.let {
            infoGroup.list.add(PumpInfoRow(label = rh.gs(Rco.string.serial_number), value = it))
        }
        //  3. BT Address
        val address = tandemUtil.getStringPreferenceOrDefault(TandemStringPreferenceKey.PumpAddress, "-")
        infoGroup.list.add(PumpInfoRow(label = rh.gs(R.string.pump_address_label), value = address))

        // Driver version
        if (displayDriver) {
            infoGroup.list.add(PumpInfoRow(label = rh.gs(R.string.driver_version), value = tandemPump.version))
        }

        add(infoGroup)

        infoGroup = PumpInfoGroup()

        //  4. BT State
        infoGroup.list.add(PumpInfoRow(label = rh.gs(R.string.pump_bt_state_label), value = currentActivity.value))

        // X 5. Queue

        //  6. Pump Status
        val (pumpRunningState, pumpRunningStateLevel) = buildPumpRunningState()
        infoGroup.list.add(PumpInfoRow(label = rh.gs(R.string.pump_status_label), value = pumpRunningState, level = pumpRunningStateLevel))

        add(infoGroup)

        infoGroup = PumpInfoGroup()

        //  7. Battery
        val (batteryText, batteryLevel) = buildBattery()
        infoGroup.list.add(PumpInfoRow(label = rh.gs(Rco.string.battery_label), value = batteryText, level = batteryLevel))

        //  8. Reservoir
        val (reservoirText, reservoirLevel) = buildReservoir()
        infoGroup.list.add(PumpInfoRow(label = rh.gs(Rco.string.reservoir_label), value = reservoirText, level = reservoirLevel))

        //  9. Last connect
        val (lastConnText, lastConnLevel) = buildLastConnection()
        infoGroup.list.add(PumpInfoRow(label = rh.gs(Rco.string.last_connection_label), value = lastConnText, level = lastConnLevel))

        //  10. Last Bolus
        val lastBolus = buildLastBolus();
        infoGroup.list.add(PumpInfoRow(label = rh.gs(Rco.string.last_bolus_label), value = lastBolus))

        //  11. Base basal rate
        val baseBasalRate = "(" + tandemPumpStatus.activeProfileName + ")  " +
            rh.gs(Rco.string.pump_base_basal_rate, tandemPump.baseBasalRate.cU)
        infoGroup.list.add(PumpInfoRow(label = rh.gs(Rco.string.base_basal_rate_label), value = baseBasalRate))

        //  12. Temp Basal
        buildTBR()
        infoGroup.list.add(PumpInfoRow(label = rh.gs(Rco.string.tempbasal_label), value = pumpTempBasal.value))

        add(infoGroup)

        // 13. Error

        if (!pumpErrors.value.isEmpty()) {
            add(PumpInfoRow(label = rh.gs(R.string.pump_driver_errors), value = pumpErrors.value))
        }

        updateDataSemaphore()
        add(PumpInfoRow(label = "     ", value = semaphoreTexts.value))

        // TODO   Notification   Events    History

        // TODO   Pictures

        // Buttons:
        // TODO     1 - Refresh
        // TODO     2 - Data
        // TODO     3 - Actions



        // // RileyLink battery (conditional)
        // if (rileyLinkServiceData.showBatteryLevel) {
        //     val batteryText = rileyLinkServiceData.batteryLevel?.let { "$it%" } ?: "?"
        //     add(PumpInfoRow(label = rh.gs(app.aaps.pump.medtronic.R.string.rl_battery_label), value = batteryText))
        // }
        //
        // // Pump status
        // val pumpStatusText = buildPumpStatusText()
        // add(PumpInfoRow(label = rh.gs(RileyLinkR.string.medtronic_pump_status), value = pumpStatusText))
        //
        // // Last connection
        // val (lastConnText, lastConnLevel) = buildLastConnection()
        // add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.last_connection_label), value = lastConnText, level = lastConnLevel))
        //
        // // Last bolus
        // add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.last_bolus_label), value = buildLastBolus()))
        //
        // // Base basal rate
        // val basalText = "(" + medtronicPumpStatus.activeProfileName + ")  " +
        //     rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, medtronicPumpPlugin.baseBasalRate.cU)
        // add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.base_basal_rate_label), value = basalText))
        //
        // // Temp basal
        // val tbrText = buildTempBasal()
        // add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.tempbasal_label), value = tbrText, visible = tbrText.isNotEmpty()))
        //
        // // Battery
        // val (batteryText, batteryLevel) = buildBattery()
        // add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.battery_label), value = batteryText, level = batteryLevel))
        //
        // // Reservoir
        // val (reservoirText, reservoirLevel) = buildReservoir()
        // add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.reservoir_label), value = reservoirText, level = reservoirLevel))
        //
        // // Errors
        // val errorsText = medtronicPumpStatus.errorInfo
        // val errorsLevel = if (errorsText != PLACEHOLDER) StatusLevel.CRITICAL else StatusLevel.NORMAL
        // add(PumpInfoRow(label = rh.gs(app.aaps.core.ui.R.string.errors), value = errorsText, level = errorsLevel))
    }


    private fun buildFirmware(): String {
        return if (tandemPumpStatus.pumpDriverMode == PumpDriverMode.Demo) {
            rh.gs(R.string.pump_firmware_demo)
        } else {
            if (tandemPumpStatus.tandemPumpFirmware.isClosedLoopPossible) {
                tandemPumpStatus.tandemPumpFirmware.description
            } else {
                rh.gs(R.string.pump_firmware_open_loop_only, tandemPumpStatus.tandemPumpFirmware.description)
            }
        }
    }

    private fun buildReservoir(): Pair<String, StatusLevel> {
        val remaining = tandemPumpStatus.reservoirRemainingUnits
        val full = tandemPumpStatus.reservoirFullUnits
        val text = rh.gs(Rco.string.reservoir_value, remaining, full)
        val level = when {
            remaining <= 20.0 -> StatusLevel.CRITICAL
            remaining <= 50.0 -> StatusLevel.WARNING
            else              -> StatusLevel.NORMAL
        }
        return text to level
    }

    private fun buildLastConnection(): Pair<String, StatusLevel> {
        val lastConnection = tandemPumpStatus.lastConnection

        if (lastConnection == 0L) return PLACEHOLDER to StatusLevel.NORMAL

        val min = (System.currentTimeMillis() - lastConnection) / 1000 / 60
        if (lastConnection + 60 * 1000 > System.currentTimeMillis()) {
            return rh.gs(Rci.string.now) to StatusLevel.NORMAL
        } else if (lastConnection + 30 * 60 * 1000 < System.currentTimeMillis()) {
            return (if (min < 60) {
                rh.gs(Rci.string.minago, min)
            } else if (min < 1440) {
                val h = min / 60.0f
                rh.gs(Rci.string.hoursago, h)
            } else {
                val h = min / 60.0f
                val d = h / 24.0f
                rh.gs(Rci.string.days_ago, d)
            }) to StatusLevel.CRITICAL

        } else {
            val minAgo = dateUtil.minAgo(rh, lastConnection)
            return minAgo to StatusLevel.NORMAL
        }
    }

    private fun buildBattery(): Pair<String, StatusLevel> {
        val remaining = tandemPumpStatus.batteryRemaining
        val text = "${remaining}%"
        val level = when {
            remaining == null -> StatusLevel.NORMAL
            remaining <= 20   -> StatusLevel.CRITICAL
            remaining <= 30   -> StatusLevel.WARNING
            else              -> StatusLevel.NORMAL
        }
        return text to level
    }

    private fun buildLastBolus(): String {
        val bolus = tandemPumpStatus.tandemLastBolus

        return if (bolus != null) {
            val agoMsc = System.currentTimeMillis() - bolus.timestamp
            val bolusMinAgo = agoMsc.toDouble() / 60.0 / 1000.0
            val unit = rh.gs(Rco.string.insulin_unit_shortname)
            val ago: String
            if (agoMsc < 60 * 1000) {
                ago = rh.gs(Rc.string.time_now)
            } else if (bolusMinAgo < 60) {
                ago = dateUtil.minAgo(rh, bolus.timestamp)
            } else if (bolusMinAgo < (60*24)) {
                ago = dateUtil.hourAgo(bolus.timestamp, rh)
            } else if (bolusMinAgo < (60*24*30)) {
                ago = dateUtil.dayAgo(bolus.timestamp, rh)
            } else {
                ago = rh.gs(Rc.string.time_over_month_ago)
            }
            rh.gs(R.string.pump_last_bolus, bolus.amountImmediateDelivered, unit, ago)
        } else {
            PLACEHOLDER
        }
    }

    private fun buildTBR() {
        if (tandemPumpStatus.currentTempBasal==null || System.currentTimeMillis() > tandemPumpStatus.currentTempBasalEstimatedEnd!!) {
            tandemPumpStatus.clearTbr()
            pumpTempBasal.value = PLACEHOLDER
        } else {
            val msDiff = tandemPumpStatus.currentTempBasalEstimatedEnd!! - System.currentTimeMillis()
            val min = msDiff / (60.0 * 1000.0)
            pumpTempBasal.value = rh.gs(Rc.string.pump_tbr_remaining_percent,
                                                           tandemPumpStatus.currentTempBasal!!.insulinRate.toInt(), min.toInt())
        }
    }

    private fun buildPumpRunningState():  Pair<String, StatusLevel> {
        return (when(tandemPumpStatus.pumpRunningState) {
            PumpRunningState.Unknown   -> rh.gs(Rc.string.pump_status_unknown)
            PumpRunningState.Running   -> rh.gs(Rc.string.pump_status_running)
            PumpRunningState.Suspended -> rh.gs(Rc.string.pump_status_suspended)
        }) to
            if (tandemPumpStatus.pumpRunningState == PumpRunningState.Suspended) StatusLevel.CRITICAL else StatusLevel.NORMAL

    }


}
