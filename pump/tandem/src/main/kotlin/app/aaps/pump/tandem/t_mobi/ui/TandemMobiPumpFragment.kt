package app.aaps.pump.tandem.t_mobi.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.pump.common.defs.PumpDriverMode
import app.aaps.pump.tandem.R
import dagger.android.support.DaggerFragment

import app.aaps.pump.common.defs.PumpDriverState
import app.aaps.pump.common.defs.PumpUpdateFragmentType

import app.aaps.pump.common.driver.connector.defs.PumpCommandType
import app.aaps.pump.common.events.EventPumpDataRefresh
import app.aaps.pump.common.events.EventPumpDriverStateChanged
import app.aaps.pump.tandem.common.driver.TandemPumpStatus

import app.aaps.pump.tandem.common.util.TandemPumpUtil
import app.aaps.pump.tandem.databinding.TandemMobiFragmentBinding

import app.aaps.pump.common.events.EventPumpFragmentValuesChanged
import app.aaps.pump.common.ui.PumpHistoryActivity
import app.aaps.pump.tandem.common.util.TandemPumpConst
import app.aaps.pump.tandem.t_mobi.TandemMobiPumpPlugin
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HomeScreenMirrorResponse

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

    private var disposable: CompositeDisposable = CompositeDisposable()

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

        binding.pumpRefresh.setOnClickListener {
            binding.pumpRefresh.isEnabled = false
            tandemPumpPlugin.resetStatusState()
            commandQueue.readStatus("Clicked refresh", object : Callback() {
                override fun run() {
                    activity?.runOnUiThread { binding.pumpRefresh.isEnabled = true }
                    rxBus.send(EventPumpDataRefresh())
                }
            })
        }

        binding.pumpHistory.setOnClickListener {
            startActivity(Intent(context, PumpHistoryActivity::class.java))
        }

        binding.pumpConfig.setOnClickListener {
            startActivity(Intent(context, TandemMobiSettingsActivity::class.java))
        }

        setVisibilityOfDriverVersion()

    }

    @Synchronized
    override fun onResume() {
        super.onResume()

        loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        disposable += rxBus
            .toObservable(EventRefreshButtonState::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ binding.pumpRefresh.isEnabled = it.newState }, { fabricPrivacy.logException(it) })
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
            .subscribe({ updateGUI(PumpUpdateFragmentType.Queue) }, { fabricPrivacy.logException(it) })

        this.binding.pumpDriverVersion.text = tandemPumpPlugin.version

        updateGUI(PumpUpdateFragmentType.Full)
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        loopHandler.removeCallbacks(refreshLoop)
    }

    private fun displayNotConfiguredDialog() {
        context?.let {
            OKDialog.show(it, resourceHelper.gs(R.string.medtronic_warning),
                          resourceHelper.gs(R.string.medtronic_error_operation_not_possible_no_configuration), null)
        }
    }

    @Synchronized
    fun updateGUI(updateType: PumpUpdateFragmentType) {

        val pumpState = pumpSync.expectedPumpState()

        // last connection
        if (pumpStatus.lastConnection != 0L) {
            val minAgo = dateUtil.minAgo(resourceHelper, pumpStatus.lastConnection)
            val min = (System.currentTimeMillis() - pumpStatus.lastConnection) / 1000 / 60
            if (pumpStatus.lastConnection + 60 * 1000 > System.currentTimeMillis()) {
                binding.pumpLastConnection.setText(R.string.medtronic_pump_connected_now)
                binding.pumpLastConnection.setTextColor(Color.WHITE)
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
                binding.pumpLastConnection.text = minAgo
                binding.pumpLastConnection.setTextColor(Color.WHITE)
            }
        }

        if (updateType == PumpUpdateFragmentType.PumpStatus || updateType == PumpUpdateFragmentType.Full) {
            // Pump Status (Error)
            val pumpDriverState: PumpDriverState = pumpUtil.driverStatus

            updateCurrentActivity(pumpDriverState)
            updatePumpStatus()
        }

        if (updateType == PumpUpdateFragmentType.Queue || updateType == PumpUpdateFragmentType.Full) {
            // Queue
            val status = commandQueue.spannedStatus()
            if (status.toString() == "") {
                binding.pumpQueue.visibility = View.GONE
            } else {
                binding.pumpQueue.visibility = View.VISIBLE
                binding.pumpQueue.text = status
            }
        }

        if (updateType == PumpUpdateFragmentType.TreatmentValues || updateType == PumpUpdateFragmentType.Full) {
            // Last Bolus, TBR (Profile Change)

            //val bolusState: PumpSync.PumpState.Bolus? = pumpState.bolus
            
            // last bolus
           val bolus = pumpStatus.lastBolus
            if (bolus != null) {
                val agoMsc = System.currentTimeMillis() - bolus.bolusTimestamp!!
                val bolusMinAgo = agoMsc.toDouble() / 60.0 / 1000.0
                val unit = resourceHelper.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)
                val ago: String
                if (agoMsc < 60 * 1000) {
                    ago = resourceHelper.gs(R.string.medtronic_pump_connected_now)
                } else if (bolusMinAgo < 60) {
                    ago = dateUtil.minAgo(resourceHelper, pumpStatus.lastBolusTime!!.time)
                } else {
                    ago = dateUtil.hourAgo(pumpStatus.lastBolusTime!!.time, resourceHelper)
                }
                binding.pumpLastBolus.text = resourceHelper.gs(R.string.pump_last_bolus, bolus.insulin, unit, ago)
            } else {
                binding.pumpLastBolus.text = "-"
            }

            // base basal rate
            binding.pumpBaseBasalRate.text = resourceHelper.gs(app.aaps.pump.common.R.string.pump_base_basal_rate_with_profile,
                                                               pumpStatus.activeProfileName, tandemPumpPlugin.baseBasalRate)

            // tbr (always saved on pumpStatus)
            if (pumpStatus.currentTempBasal==null || System.currentTimeMillis() > pumpStatus.currentTempBasalEstimatedEnd!!) {
                pumpStatus.clearTbr()
                binding.pumpTempBasal.text = "-"
            } else {
                val msDiff = pumpStatus.currentTempBasalEstimatedEnd!! - System.currentTimeMillis()
                val min = msDiff / (60.0 * 1000.0)

                binding.pumpTempBasal.text = resourceHelper.gs(app.aaps.pump.common.R.string.pump_tbr_remaining_percent,
                                                              pumpStatus.currentTempBasal!!.insulinRate.toInt(), min.toInt())
            }

            //TBR TODO
            //binding.pumpTempBasal.text = activePlugin.getTempBasalFromHistory(System.currentTimeMillis())?.toStringFull()
            //     ?: ""
        }

        if (updateType == PumpUpdateFragmentType.Configuration || updateType == PumpUpdateFragmentType.Full) {
            // Firmware, Errors

            aapsLogger.info(LTag.PUMP,
                            "Firmware (driverMode=${pumpStatus.pumpDriverMode}, " +
                                "pumpFirmware=${pumpStatus.tandemPumpFirmware})")

            if (pumpStatus.pumpDriverMode == PumpDriverMode.Demo) {
                binding.pumpFirmware.text = resourceHelper.gs(R.string.pump_firmware_demo)
            } else {
                if (pumpStatus.tandemPumpFirmware.isClosedLoopPossible) {
                    binding.pumpFirmware.text = pumpStatus.tandemPumpFirmware.description
                } else {
                    binding.pumpFirmware.text = resourceHelper.gs(R.string.pump_firmware_open_loop_only, pumpStatus.tandemPumpFirmware.description)
                }
            }

            binding.pumpSerialNo.text = sp.getString(TandemPumpConst.Prefs.PumpSerial, "-")
            binding.pumpAddress.text = sp.getString(TandemPumpConst.Prefs.PumpAddress, "-")

        }

        if (updateType == PumpUpdateFragmentType.OtherValues || updateType == PumpUpdateFragmentType.Full) {
            // Battery, Reservoir

            // battery
            binding.pumpBattery.text = "{fa-battery-" + pumpStatus.batteryRemaining / 25 + "}  " + pumpStatus.batteryRemaining + "%"
            warnColors.setColorInverse(binding.pumpBattery, pumpStatus.batteryRemaining.toDouble(), 25, 10)

            // reservoir
            binding.pumpReservoir.text = resourceHelper.gs(app.aaps.core.ui.R.string.reservoir_value, pumpStatus.reservoirRemainingUnits, pumpStatus.reservoirFullUnits)
            warnColors.setColorInverse(binding.pumpReservoir, pumpStatus.reservoirRemainingUnits, 50, 20)
        }

        binding.pumpErrors.text = if (pumpStatus.errorDescription != null) pumpStatus.errorDescription else ""
        setVisibilityOfDriverVersion()
    }

    private fun setVisibilityOfDriverVersion() {
        val displayDriverVersion = sp.getBoolean(TandemPumpConst.Prefs.DisplayDriverVersion, true)
        binding.showDriverLayout.visibility = if (displayDriverVersion) View.VISIBLE else View.GONE
    }

    //var resourcesPumpCommon = app.aaps.pump.common.R

    private fun updateCurrentActivity(pumpDriverState: PumpDriverState?) {
        val resActivity = app.aaps.pump.common.R.string.pump_current_activity

        when (pumpDriverState) {
            null,
            PumpDriverState.Ready,
            PumpDriverState.Sleeping                   -> binding.currentActivity.text = resourceHelper.gs(resActivity, "{fa-bed}", "")
            PumpDriverState.Connecting,
            PumpDriverState.Disconnecting              -> binding.currentActivity.text = resourceHelper.gs(resActivity,"{fa-bluetooth-b spin}", resourceHelper.gs(pumpDriverState.resourceId))
            PumpDriverState.Connected,
            PumpDriverState.Disconnected               -> binding.currentActivity.text = resourceHelper.gs(resActivity,"{fa-bluetooth-b}", resourceHelper.gs(pumpDriverState.resourceId))

            PumpDriverState.ErrorCommunicatingWithPump -> {
                binding.currentActivity.text = resourceHelper.gs(resActivity,"{fa-bed}" , "Error ???")
                val errorType = pumpUtil.errorType

                binding.pumpErrors.text = if (errorType != null) errorType.name else ""
                //aapsLogger.warn(LTag.PUMP, "Errors are not supported.")
            }

            PumpDriverState.ExecutingCommand           -> {
                val commandType: PumpCommandType? = pumpUtil.currentCommand
                if (commandType == null) {
                    binding.currentActivity.text = resourceHelper.gs(resActivity,"{fa-bed}", "")
                } else {
                    binding.currentActivity.text = resourceHelper.gs(resActivity,"{fa-bluetooth-b}", resourceHelper.gs(commandType.resourceId))
                }
            }

            else                                       -> {
                binding.currentActivity.text = " " + resourceHelper.gs(pumpDriverState.resourceId)
            }
        }
    }

    private fun updatePumpStatus() {

        if (pumpStatus.pumpStatusMirror!=null) {
            if (pumpStatus.pumpStatusMirror!!.basalStatusIcon == HomeScreenMirrorResponse.BasalStatusIcon.SUSPEND) {
                binding.pumpStatus.text = resourceHelper.gs(app.aaps.pump.common.R.string.pump_status_suspended)
            } else {
                binding.pumpStatus.text = resourceHelper.gs(app.aaps.pump.common.R.string.pump_status_running)
            }
        } else {
            // TODO add driver initialization states
            binding.pumpStatus.text = resourceHelper.gs(app.aaps.pump.common.R.string.pump_status_unknown)
        }

    }

    // enum class UpdateGui {
    //     Status, // Pump Status (Error)
    //     Queue, // Queue
    //     TreatmentValues, // Last Bolus, TBR, Profile Change
    //     Full,
    //     Configuration,  // Firmware, Errors
    //     OtherValues // Battery, Reservoir
    // }

}
