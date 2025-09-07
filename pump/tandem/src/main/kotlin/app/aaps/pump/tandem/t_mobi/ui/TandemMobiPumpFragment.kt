package app.aaps.pump.tandem.t_mobi.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.WarnColors
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventExtendedBolusChange
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.rx.events.EventRefreshButtonState
import app.aaps.core.interfaces.rx.events.EventTempBasalChange
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.pump.common.defs.PumpDriverMode
import app.aaps.pump.common.R as Rc
import app.aaps.pump.tandem.R
import dagger.android.support.DaggerFragment

import app.aaps.pump.common.defs.PumpDriverState
import app.aaps.pump.common.defs.PumpRunningState
import app.aaps.pump.common.defs.PumpUpdateFragmentType

import app.aaps.pump.common.driver.connector.defs.PumpCommandType
import app.aaps.pump.common.events.EventPumpDriverStateChanged
import app.aaps.pump.tandem.common.driver.TandemPumpStatus

import app.aaps.pump.tandem.common.util.TandemPumpUtil
import app.aaps.pump.tandem.databinding.TandemMobiFragmentBinding

import app.aaps.pump.common.events.EventPumpFragmentValuesChanged
import app.aaps.pump.tandem.common.driver.connector.def.TandemCustomCommand
import app.aaps.pump.tandem.common.keys.TandemBooleanPreferenceKey
import app.aaps.pump.tandem.common.keys.TandemStringPreferenceKey
import app.aaps.pump.tandem.t_mobi.TandemMobiPumpPlugin
import com.google.gson.Gson

import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class TandemMobiPumpFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var warnColors: WarnColors
    @Inject lateinit var pumpUtil: TandemPumpUtil
    @Inject lateinit var pumpStatus: TandemPumpStatus
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var tandemPumpPlugin: TandemMobiPumpPlugin
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var sp: SP
    lateinit var gson: Gson
    var currentTextColor: Int = 0

    private var disposable: CompositeDisposable = CompositeDisposable()
    var TAG = LTag.PUMP

    private val loopHandler = Handler(Looper.getMainLooper())
    private lateinit var refreshLoop: Runnable

    private var _binding: TandemMobiFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateGUI(PumpUpdateFragmentType.Full) }
            loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = TandemMobiFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.pumpRefreshMobi.setOnClickListener {
            setButtonState(false)
            tandemPumpPlugin.resetStatusState()
            commandQueue.readStatus("Clicked refresh", object : Callback() {
                override fun run() {
                    activity?.runOnUiThread {
                        setButtonState(true)
                    }
                }
            })
        }

        binding.pumpHistory.setOnClickListener {
            startActivity(Intent(context, DataActivity::class.java))
        }

        binding.pumpConfig.setOnClickListener {
            startActivity(Intent(context, ActionsActivity::class.java))
        }

        setVisibilityOfDriverVersion()

        disableLastPumpEvent()

    }

    private fun disableLastPumpEvent() {
        binding.showLastPumpEvent.visibility = View.GONE
        binding.showLastPumpEventLine.visibility = View.GONE
    }

    private fun setButtonState(enabled: Boolean) {
        binding.pumpRefreshMobi.isEnabled = enabled
        binding.pumpHistory.isEnabled = enabled
        binding.pumpConfig.isEnabled = enabled
    }



    @Synchronized
    override fun onResume() {
        super.onResume()

        loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        disposable += rxBus
            .toObservable(EventRefreshButtonState::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ binding.pumpRefreshMobi.isEnabled = it.newState }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventPumpDriverStateChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateCurrentActivity(it.driverStatus) }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI(PumpUpdateFragmentType.TreatmentValues) }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI(PumpUpdateFragmentType.TreatmentValues) }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventPumpFragmentValuesChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI(it.updateType) }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventQueueChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateQueue() }, { fabricPrivacy.logException(it) })

        this.binding.pumpDriverVersion.text = tandemPumpPlugin.version

        //this.gson = TandemPumpUtil

        updateGUI(PumpUpdateFragmentType.Full)
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        loopHandler.removeCallbacks(refreshLoop)
    }

    // private fun displayNotConfiguredDialog() {
    //     context?.let {
    //         OKDialog.show(it, resourceHelper.gs(R.string.medtronic_warning),
    //                       resourceHelper.gs(R.string.medtronic_error_operation_not_possible_no_configuration), null)
    //     }
    // }



    @Synchronized
    fun updateGUI(updateType: PumpUpdateFragmentType) {

        //val pumpState = pumpSync.expectedPumpState()

        currentTextColor = binding.pumpBaseBasalRate.currentTextColor // we need color from item, in case we are not running in dark mode

        // last connection
        if (pumpStatus.lastConnection != 0L) {

            val min = (System.currentTimeMillis() - pumpStatus.lastConnection) / 1000 / 60
            if (pumpStatus.lastConnection + 60 * 1000 > System.currentTimeMillis()) {
                binding.pumpLastConnection.setText(app.aaps.core.interfaces.R.string.now)
                binding.pumpLastConnection.setTextColor(currentTextColor)
            } else if (pumpStatus.lastConnection + 30 * 60 * 1000 < System.currentTimeMillis()) {

                if (min < 60) {
                    binding.pumpLastConnection.text = resourceHelper.gs(app.aaps.core.interfaces.R.string.minago, min)
                } else if (min < 1440) {
                    val h = min / 60.0f
                    binding.pumpLastConnection.text = resourceHelper.gs(app.aaps.core.interfaces.R.string.hoursago, h)
                } else {
                    val h = min / 60.0f
                    val d = h / 24.0f
                    // h = h - (d * 24);
                    binding.pumpLastConnection.text = resourceHelper.gs(app.aaps.core.interfaces.R.string.days_ago, d)
                }
                binding.pumpLastConnection.setTextColor(Color.RED)
            } else {
                val minAgo = dateUtil.minAgo(resourceHelper, pumpStatus.lastConnection)
                binding.pumpLastConnection.text = minAgo
                binding.pumpLastConnection.setTextColor(currentTextColor)
            }
        }

        if (updateType == PumpUpdateFragmentType.PumpStatus || updateType == PumpUpdateFragmentType.Full) {
            // Pump Status (Error)
            val pumpDriverState: PumpDriverState = pumpUtil.driverStatus

            // updateCurrentActivity(pumpDriverState)
            //updatePumpStatus()
            updateCurrentActivity(pumpDriverState)
        }


        this.updateQueue()


        if (updateType == PumpUpdateFragmentType.Bolus ||
            updateType == PumpUpdateFragmentType.TreatmentValues ||
            updateType == PumpUpdateFragmentType.Full) {

            // Last Bolus, TBR (Profile Change)

            // last bolus
            val bolus = pumpStatus.tandemLastBolus

            if (bolus != null) {
                val agoMsc = System.currentTimeMillis() - bolus.timestamp
                val bolusMinAgo = agoMsc.toDouble() / 60.0 / 1000.0
                val unit = resourceHelper.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)
                val ago: String
                if (agoMsc < 60 * 1000) {
                    ago = resourceHelper.gs(Rc.string.time_now)
                } else if (bolusMinAgo < 60) {
                    ago = dateUtil.minAgo(resourceHelper, bolus.timestamp)
                } else if (bolusMinAgo < (60*24)) {
                    ago = dateUtil.hourAgo(bolus.timestamp, resourceHelper)
                } else {
                    ago = dateUtil.dayAgo(bolus.timestamp, resourceHelper)
                }
                binding.pumpLastBolus.text = resourceHelper.gs(R.string.pump_last_bolus, bolus.amountImmediateDelivered, unit, ago)
            } else {
                binding.pumpLastBolus.text = "-"
            }

        }

        if (updateType == PumpUpdateFragmentType.TBR ||
            updateType == PumpUpdateFragmentType.TreatmentValues ||
            updateType == PumpUpdateFragmentType.Full) {

            // base basal rate
            binding.pumpBaseBasalRate.text = resourceHelper.gs(Rc.string.pump_base_basal_rate_with_profile,
                                                               pumpStatus.activeProfileName,
                                                               tandemPumpPlugin.baseBasalRate)

            // tbr (always saved on pumpStatus)
            if (pumpStatus.currentTempBasal==null || System.currentTimeMillis() > pumpStatus.currentTempBasalEstimatedEnd!!) {
                pumpStatus.clearTbr()
                binding.pumpTempBasal.text = "-"
            } else {
                val msDiff = pumpStatus.currentTempBasalEstimatedEnd!! - System.currentTimeMillis()
                val min = msDiff / (60.0 * 1000.0)
                binding.pumpTempBasal.text = resourceHelper.gs(Rc.string.pump_tbr_remaining_percent,
                                                              pumpStatus.currentTempBasal!!.insulinRate.toInt(), min.toInt())
            }
        }

        if (updateType == PumpUpdateFragmentType.Configuration ||
            updateType == PumpUpdateFragmentType.Full) {
            // Firmware, Errors

            if (pumpStatus.pumpDriverMode == PumpDriverMode.Demo) {
                binding.pumpFirmware.text = resourceHelper.gs(R.string.pump_firmware_demo)
            } else {
                if (pumpStatus.tandemPumpFirmware.isClosedLoopPossible) {
                    binding.pumpFirmware.text = pumpStatus.tandemPumpFirmware.description
                } else {
                    binding.pumpFirmware.text = resourceHelper.gs(R.string.pump_firmware_open_loop_only, pumpStatus.tandemPumpFirmware.description)
                }
            }

            binding.pumpSerialNo.text = pumpUtil.getStringPreferenceOrDefault(TandemStringPreferenceKey.PumpSerial, "-")
            binding.pumpAddress.text = pumpUtil.getStringPreferenceOrDefault(TandemStringPreferenceKey.PumpAddress, "-")
        }

        if (updateType == PumpUpdateFragmentType.Battery ||
            updateType == PumpUpdateFragmentType.OtherValues ||
            updateType == PumpUpdateFragmentType.Full) {
            updateBattery()
        }

        if (updateType == PumpUpdateFragmentType.Reservoir ||
            updateType == PumpUpdateFragmentType.OtherValues ||
            updateType == PumpUpdateFragmentType.Full) {
            updateReservoir()
        }

        if (updateType == PumpUpdateFragmentType.Custom_1 ||
            updateType == PumpUpdateFragmentType.Full) {
            // qualifying events
            val sb = StringBuilder()

            //aapsLogger.info(TAG, "XA: QE: ${pumpStatus.lastQualifyingEventsInfo}")

            if (pumpStatus.lastQualifyingEventsInfo!=null) {
                sb.append("QE: ")
                sb.append(pumpStatus.lastQualifyingEventsInfo)
            }

            //aapsLogger.info(TAG, "XA: Alarms: ${pumpStatus.tandemAlarms}")

            if (pumpStatus.tandemAlarms!=null && !pumpStatus.tandemAlarms!!.isEmpty()) {
                if (!sb.isEmpty()){
                    sb.append(", ")
                }
                sb.append("Alarms: ")
                for (alarm in pumpStatus.tandemAlarms!!) {
                    sb.append(alarm.name + ", ")
                }
            }

            //aapsLogger.info(TAG, "XA: Alerts: ${pumpStatus.tandemAlerts}")

            if (pumpStatus.tandemAlerts!=null && !pumpStatus.tandemAlerts!!.isEmpty()) {
                if (!sb.isEmpty()){
                    sb.append(", ")
                }
                sb.append("Alerts: ")
                for (alarm in pumpStatus.tandemAlerts!!) {
                    sb.append(alarm.name + ", ")
                }
            }

            var endText = sb.toString()

            if (endText.endsWith(", ")) {
                endText = endText.substring(0, endText.length-2)
            }

            binding.pumpQeInfo.text = endText
        }

        if (updateType == PumpUpdateFragmentType.Custom_2 ||
            updateType == PumpUpdateFragmentType.Full) {
            updateDataSemaphore()
        }

        showPumpErrors()
        setVisibilityOfDriverVersion()
    }

    private fun updateDataSemaphore() {
        updateLabelColor(binding.pumpDataStatusNotification, pumpStatus.semaphoreNotifications, Color.RED)
        updateLabelColor(binding.pumpDataStatusEvents, pumpStatus.semaphoreEvents, Color.GREEN)
        updateLabelColor(binding.pumpDataStatusHistory, pumpStatus.semaphoreHistory, Color.BLUE)
    }

    private fun updateLabelColor(pumpDataStatusLabel: TextView, semaphoreFlag: Boolean, color: Int) {
        if (semaphoreFlag) {
            pumpDataStatusLabel.setTextColor(color)
        } else {
            pumpDataStatusLabel.setTextColor(currentTextColor)
        }
    }

    private fun showPumpErrors() {
        if (pumpStatus.errorDescription != null) {
            binding.pumpErrors.text = pumpStatus.errorDescription
            binding.pumpErrorsView.visibility = View.VISIBLE
            binding.pumpErrorsDelimiter.visibility = View.VISIBLE
        } else {
            binding.pumpErrorsView.visibility = View.GONE
            binding.pumpErrorsDelimiter.visibility = View.GONE
        }
    }

    private fun updateReservoir() {
        // reservoir
        binding.pumpReservoir.text = resourceHelper.gs(app.aaps.core.ui.R.string.reservoir_value, pumpStatus.reservoirRemainingUnits, pumpStatus.reservoirFullUnits)
        warnColors.setColorInverse(binding.pumpReservoir, pumpStatus.reservoirRemainingUnits, 50, 20)
    }

    private fun updateBattery() {
        // battery
        binding.pumpBattery.text = "{fa-battery-" + pumpStatus.batteryRemaining / 25 + "}  " + pumpStatus.batteryRemaining + "%"
        warnColors.setColorInverse(binding.pumpBattery, pumpStatus.batteryRemaining.toDouble(), 30, 20)
    }

    private fun setVisibilityOfDriverVersion() {
        val displayDriverVersion = pumpUtil.getBooleanPreferenceOrDefault(TandemBooleanPreferenceKey.DisplayDriverVersion, true)
        binding.showDriverLayout.visibility = if (displayDriverVersion) View.VISIBLE else View.GONE
    }


    fun updateQueue() {
        // Queue
        val status = commandQueue.spannedStatus()
        if (status.toString() == "") {
            binding.pumpQueue.visibility = View.GONE
        } else {
            binding.pumpQueue.visibility = View.VISIBLE
            binding.pumpQueue.text = status
        }
    }


    @Synchronized
    private fun updateCurrentActivity(pumpDriverState: PumpDriverState?) {
        val resActivity = Rc.string.pump_current_activity

        //aapsLogger.info(LTag.PUMP, "DUB Update Current activity: ${pumpDriverState!!.name}")

        when (pumpDriverState) {
            //null,
            PumpDriverState.Ready,
            PumpDriverState.Sleeping                   -> binding.currentActivity.text = resourceHelper.gs(resActivity, " {fa-bed} ", "")
            PumpDriverState.Connecting,
            PumpDriverState.Handshaking,
            PumpDriverState.Disconnecting              -> binding.currentActivity.text = resourceHelper.gs(resActivity," {fa-bluetooth spin} ", resourceHelper.gs(pumpDriverState.resourceId))
            PumpDriverState.Connected,
            PumpDriverState.Disconnected               -> binding.currentActivity.text = resourceHelper.gs(resActivity," {fa-bluetooth} ", resourceHelper.gs(pumpDriverState.resourceId))

            PumpDriverState.ErrorCommunicatingWithPump -> {
                binding.currentActivity.text = resourceHelper.gs(resActivity," {fa-bed} " , "Error ???")
                val errorType = pumpUtil.errorType

                binding.pumpErrors.text = if (errorType != null) errorType.name else ""
                //aapsLogger.warn(LTag.PUMP, "Errors are not supported.")
            }

            PumpDriverState.ExecutingCommand           -> {
                val commandType: PumpCommandType? = pumpUtil.currentCommand
                val customCommandTypeInterface : TandemCustomCommand? = pumpUtil.customCommandType as TandemCustomCommand?
                if (commandType == null) {
                    binding.currentActivity.text = resourceHelper.gs(resActivity," {fa-bluetooth} ", resourceHelper.gs(pumpDriverState.resourceId))
                } else {
                    if (commandType == PumpCommandType.CustomCommand) {
                        if (customCommandTypeInterface==null) {
                            binding.currentActivity.text = resourceHelper.gs(resActivity, " {fa-bluetooth} ", resourceHelper.gs(commandType.resourceId))
                        } else {
                            binding.currentActivity.text = resourceHelper.gs(resActivity, " {fa-bluetooth} ", customCommandTypeInterface.getDescription())
                        }
                    } else {
                        if (commandType == PumpCommandType.GetHistoryWithParameters) {
                            val progress: String = pumpUtil.historyProgress.orEmpty()
                            binding.currentActivity.text = resourceHelper.gs(resActivity, " {fa-bluetooth} ", resourceHelper.gs(commandType.resourceId, progress))
                        } else {
                            binding.currentActivity.text = resourceHelper.gs(resActivity, " {fa-bluetooth} ", resourceHelper.gs(commandType.resourceId))
                        }
                    }
                }
            }

            else                                       -> {
                binding.currentActivity.text = " " + resourceHelper.gs(pumpDriverState!!.resourceId)
            }
        }

        updatePumpStatus()
    }

    private fun updatePumpStatus() {
        when(pumpStatus.pumpRunningState) {
            PumpRunningState.Unknown   -> binding.pumpStatus.text = resourceHelper.gs(Rc.string.pump_status_unknown)
            PumpRunningState.Running   -> binding.pumpStatus.text = resourceHelper.gs(Rc.string.pump_status_running)
            PumpRunningState.Suspended -> binding.pumpStatus.text = resourceHelper.gs(Rc.string.pump_status_suspended)
        }
    }

}
