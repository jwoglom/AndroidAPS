package app.aaps.pump.tandem.t_mobi

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.preference.Preference
import app.aaps.core.data.model.BS
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNewNotification
import app.aaps.core.interfaces.rx.events.EventRefreshButtonState
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.pump.tandem.R
import app.aaps.pump.common.PumpPluginAbstract
import app.aaps.pump.common.data.PumpStatus
import app.aaps.pump.common.events.EventPumpConnectionParametersChanged
import app.aaps.pump.common.sync.PumpSyncStorage
import app.aaps.pump.common.utils.ProfileUtil
import app.aaps.pump.tandem.common.driver.connector.TandemPumpConnectionManager
import app.aaps.pump.tandem.common.util.TandemPumpConst
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import app.aaps.pump.common.R as Rc

import app.aaps.pump.common.defs.*
import app.aaps.pump.common.defs.PumpDriverMode
import app.aaps.pump.common.defs.PumpDriverState
import app.aaps.pump.common.defs.PumpRunningState
import app.aaps.pump.common.defs.PumpUpdateFragmentType
import app.aaps.pump.common.driver.connector.commands.response.DataCommandResponse
import app.aaps.pump.common.driver.refresh.PumpDataRefreshAction
import app.aaps.pump.common.driver.refresh.PumpDataRefreshCapable
import app.aaps.pump.common.driver.refresh.PumpDataRefreshType
import app.aaps.pump.common.events.EventPumpDataRefresh
import app.aaps.pump.common.events.EventPumpForceDisconnect
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.t_mobi.driver.TandemMobiPumpDriverConfiguration
import app.aaps.pump.common.events.EventPumpFragmentValuesChanged
import app.aaps.pump.tandem.common.comm.qe.QualifyingEventHandler
import app.aaps.pump.tandem.common.data.defs.TandemPumpSettingType
import app.aaps.pump.tandem.common.driver.connector.def.TandemCustomCommand
import app.aaps.pump.tandem.common.events.EventHandleQualifyingEvent
import app.aaps.pump.tandem.common.service.TandemService
import app.aaps.pump.tandem.t_mobi.ui.TandemMobiPumpFragment
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONObject

import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by andy on 04.01.2025.
 *
 * @author Andy Rozman (andy.rozman@gmail.com)
 */
@Singleton
class TandemMobiPumpPlugin @Inject constructor(
    //injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rxBus: RxBus,
    context: Context,
    rh: ResourceHelper,
    activePlugin: ActivePlugin,
    sp: SP,
    commandQueue: CommandQueue,
    fabricPrivacy: FabricPrivacy,
    val tandemUtil: TandemPumpUtil,
    val pumpStatus: TandemPumpStatus,
    val qualifyingEventHandler: QualifyingEventHandler,
    dateUtil: DateUtil,
    val pumpConnectionManager: TandemPumpConnectionManager,
    aapsSchedulers: AapsSchedulers,
    pumpSync: PumpSync,
    pumpSyncStorage: PumpSyncStorage,
    tandemPumpDriverConfiguration: TandemMobiPumpDriverConfiguration,
    decimalFormatter: DecimalFormatter,
    //val pumpX2L: PumpX2L,
    instantiator: Instantiator
) : PumpPluginAbstract(
    PluginDescription() //
        .mainType(PluginType.PUMP) //
        .fragmentClass(TandemMobiPumpFragment::class.java.name) //
        .pluginIcon(R.drawable.t_mobi)
        .pluginName(R.string.tandem_name_mobi) //
        .shortName(R.string.tandem_name_mobi_short) //
        .preferencesId(R.xml.pref_tandem_mobi)
        .description(R.string.description_pump_tandem_mobi),  //
    PumpType.TANDEM_T_MOBI_BT,
    rh, aapsLogger, commandQueue, rxBus, activePlugin, sp, context, fabricPrivacy, dateUtil,
    aapsSchedulers, pumpSync, pumpSyncStorage,
    tandemPumpDriverConfiguration, decimalFormatter, instantiator
), Pump, PluginConstraints, PumpDataRefreshCapable /*, PumpConstraints */ {

    // variables for handling statuses and history

    private var tandemService: TandemService? = null
    private var driverMode = PumpDriverMode.Automatic // TODO when implementation fully done, default should be automatic

    private val versionInternal = TandemMobiPluginVersion()

    private var wantedDriverMode = PumpDriverMode.Automatic  // TODO change this (demo mode means we are not communicating with pump)

    private val TAG = LTag.PUMP

    // Service
    //private var isServiceSet = false

    override fun onStart() {
        aapsLogger.debug(LTag.PUMP, model().model + " started (t:mobi) DUB - $version")
        super.onStart()
    }


    override fun updatePreferenceSummary(pref: Preference) {
        super.updatePreferenceSummary(pref)
        if (pref.key == rh.gs(R.string.key_tandem_address)) {
            val value: String? = sp.getStringOrNull(R.string.key_tandem_address, null)
            pref.summary = value ?: rh.gs(app.aaps.core.ui.R.string.not_set_short)
            aapsLogger.info(LTag.PUMP, "TANDEMDBG: Received event that pump number serial changed (this means bonding/unbonding is happening)")
        } else if (pref.key == rh.gs(R.string.key_tandem_serial)) {
            val value: String? = sp.getStringOrNull(R.string.key_tandem_serial, null)
            pref.summary = value ?: rh.gs(app.aaps.core.ui.R.string.not_set_short)
        } else if (pref.key == rh.gs(R.string.key_tandem_shared_connection_data)) {
            val value: String? = sp.getStringOrNull(R.string.key_tandem_shared_connection_data, null)
            if (value!=null && value.isNotEmpty()) {
                if (tandemService!!.validateParameters() && !tandemService!!.isConnected()) {
                    tandemService!!.connectToPump()
                }
            }
        }

        aapsLogger.info(LTag.PUMP, "Preference: $pref")
    }

    // PumpAbstract implementations
    override fun initPumpStatusData() {
        pumpStatus.lastConnection = sp.getLong(TandemPumpConst.Statistics.LastGoodPumpCommunicationTime, 0L)
        pumpStatus.lastDataTime = pumpStatus.lastConnection
        pumpStatus.previousConnection = pumpStatus.lastConnection
        aapsLogger.debug(TAG, "initPumpStatusData: " + pumpStatus)

        pumpType = PumpType.TANDEM_T_MOBI_BT

        // this is only thing that can change, by being configured
        // TODO pumpDescription.maxTempAbsolute = (pumpStatus.maxBasal != null) ? pumpStatus.maxBasal : 35.0d;
        aapsLogger.debug(LTag.PUMP, "pumpDescription: " + this.pumpDescription)

        //this.pumpDescription;

        pumpStatus.pumpDescription = this.pumpDescription

        // set first Tandem Pump Start
        if (!sp.contains(TandemPumpConst.Statistics.FirstPumpStart)) {
            sp.putLong(TandemPumpConst.Statistics.FirstPumpStart, System.currentTimeMillis())
        }

        pumpStatus.serialNumber = sp.getLong(TandemPumpConst.Prefs.PumpSerial, 0)

        pumpStatus.pumpType = PumpType.TANDEM_T_MOBI_BT
        pumpStatus.pumpDriverMode = this.wantedDriverMode
    }

    override fun onStartScheduledPumpActions() {

        // Enable logging in jwoglom's X2 library
        //Timber.plant(aapsTimberTree)

        // disposable.add(rxBus
        //                    .toObservable(EventPreferenceChange::class.java)
        //                    .observeOn(aapsSchedulers.io)
        //                    .subscribe({ event: EventPreferenceChange ->
        //                                   if (event.isChanged(TandemPumpConst.Prefs.PumpSerial, rh)) {
        //                                       resetStatusState()
        //                                       checkInitializationState() // TODO not sure about this
        //                                   }
        //                               }) { throwable: Throwable? -> fabricPrivacy.logException(throwable!!) })


        disposable += rxBus
           .toObservable(EventPumpConnectionParametersChanged::class.java)
           .observeOn(aapsSchedulers.io)
           .subscribe({ reconnectAfterDataChange() },
                      { fabricPrivacy.logException(it) })
        disposable += rxBus
           .toObservable(EventPumpForceDisconnect::class.java)
           .observeOn(aapsSchedulers.io)
           .subscribe({ tandemService!!.disconnectFromPump()},
                      { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventHandleQualifyingEvent::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ qualifyingEventHandler.handleEventReceivedFromPump(it) },
                       { fabricPrivacy.logException(it) })
        disposable += rxBus
           .toObservable(EventPumpDataRefresh::class.java)
           .observeOn(aapsSchedulers.io)
           .subscribe({ refreshDataFull() }, { fabricPrivacy.logException(it) })


        // TODO fix me repetable start with RxJava
//        Observable c = Observable.fromCallable(() -> {
//            //calls.getAndIncrement();
//            int o = 33;
//            return o;
//        })
//                .subscribeOn(aapsSchedulers.getIo())
//                .repeatWhen(z -> z.delay(1, TimeUnit.MINUTES))
//                .doOnError(it -> aapsLogger.error(it.getMessage(), it));

        //disposable += c;

//        disposable += rxBus
//                .toObservable(EventPumpStatusChanged::class.java)
//
//            .observeOn(AndroidSchedulers.mainThread())
//                .subscribe({ updateGUI(UpdateGui.Status) }, { fabricPrivacy.logException(it) })

        //this.disposable += c;

//        this.disposable = this.disposable + Completable.fromCallable({
//        if (this.isInitialized) {
//
//        }
//        }
//        .subscribeOn(schedulerProvider.io)
//                .repeatWhen {
//            it.delay(1, TimeUnit.MINUTES)
//        }
//.subscribeBy(
//                onComplete = {/* ignore, will not be called */},
//                onError = aapsLogger::e
//        )
//;;

        // check status every minute (if any status needs refresh we send readStatus command)
        startRefreshOfPumpCommands()

        TandemCustomCommand.translateKeywords(rh)

        //checkInitializationState()

    }

    private fun reconnectAfterDataChange() {
        aapsLogger.info(LTag.PUMP, "DUB Connection data changed... validating parameters and reconnecting if possible.")
        // new pump connected, we need to reset driver
        this.isDriverInitialized = false

        if (tandemService!!.validateParameters()) {
            if (tandemService!!.connectToPump()) {
                this.firstRun = true
            }
        }
    }


    private fun refreshDataFull() {
        initializePump(false)
    }

    // private fun checkInitializationState() {
    //     if (driverMode== PumpDriverMode.Demo) {
    //         this.driverInitialized = true
    //         return
    //     }
    //
    //     val pumpAddress = sp.getString(TandemPumpConst.Prefs.PumpAddress, "")
    //
    //     val pumpBondStatus = sp.getInt(TandemPumpConst.Prefs.PumpPairStatus, -1)
    //
    //     aapsLogger.debug(LTag.PUMP, "TANDEMDBG: Mobi [address=$pumpAddress,bondStatus=$pumpBondStatus]")
    //
    //     driverInitialized = (!pumpAddress.isEmpty() &&
    //         pumpBondStatus == 100 &&
    //         !tandemUtil.preventConnect)
    //
    //     aapsLogger.debug(LTag.PUMP, "TANDEMDBG: initialization status: $driverInitialized")
    //
    //     if (!driverInitialized) {
    //         pumpStatus.errorDescription = rh.gs(R.string.tandem_error_not_bonded)
    //         rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Full))
    //     } else {
    //         if (!pumpStatus.errorDescription.isNullOrEmpty()) {
    //             pumpStatus.errorDescription = null
    //             rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Configuration))
    //         }
    //     }
    //
    // }

    override val serviceClass: Class<*>?
        get() = TandemService::class.java

    val version: String = "${versionInternal.tandemModuleVersion} (${versionInternal.pumpX2Version})"

    override val pumpStatusData: PumpStatus
        get() = pumpStatus


    // Constraints interface
    // override fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
    //     // TODO
    //
    //
    //
    //
    //     if (value.value()) {
    //         value.set(
    //             pumpStatus.tandemPumpFirmware.isClosedLoopPossible,
    //             rh.gs(R.string.tandem_fol_closed_loop_not_allowed_x2), // TODO allowed Closed Loop on right firmware
    //             this
    //         )
    //     }
    //     return value
    // }


    override fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {

        value.set(false)
        return value

        //
        //
        // if (pumpStatus.pumpDriverMode==PumpDriverMode.Demo) {
        //     value.set(true)
        //     return value
        // }
        //
        // if (value.value()) {
        //     value.set(
        //         //aapsLogger,
        //         false,  //driverMode == PumpDriverMode.Automatic, // TODO allowed SMB on right firmware
        //         rh.gs(R.string.tandem_fol_smb_not_allowed),
        //         this
        //     )
        // }
        // return value
    }

    // override fun getPumpDriverConfiguration(): PumpDriverConfiguration {
    //     return pumpDriverConfiguration
    // }


    override fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        val maxBasalBySettings = sp.getLong(TandemPumpConst.Prefs.MaxBasal, 15)

        val allowedAmount = baseBasalRate * 2.5 // 250% is max allowed

        val maxBasalRate = Math.min(allowedAmount, maxBasalBySettings.toDouble())

        if (absoluteRate.value() > maxBasalRate) {
            if (maxBasalRate == allowedAmount) {
                absoluteRate.set(maxBasalRate,
                                 rh.gs(R.string.tandem_constraint_basal_rate_max_250, maxBasalRate, baseBasalRate),
                                 this)
            } else {
                absoluteRate.set(maxBasalRate,
                                 rh.gs(R.string.tandem_constraint_basal_rate_on_pump, maxBasalBySettings),
                                 this)
            }
        }

        return absoluteRate
    }


    override fun applyBasalPercentConstraints(percentRate: Constraint<Int>, profile: Profile): Constraint<Int> {

        val maxBasalBySettings = sp.getLong(TandemPumpConst.Prefs.MaxBasal, 15)

        val requestedAmount = baseBasalRate * (percentRate.value() / 100.0) // 250% is max allowed

        if (requestedAmount>maxBasalBySettings) {

            val percent = (maxBasalBySettings / baseBasalRate)

            if (percent > 250) {
                percentRate.set(
                    250,
                    rh.gs(R.string.tandem_constraint_basal_rate_percent, percentRate.value()),
                    this
                )
            } else {
                percentRate.set(
                    percent.toInt(),
                    rh.gs(R.string.tandem_constraint_basal_rate_percent_max_pump, maxBasalBySettings),
                    this
                )
            }
        } else {
            if (percentRate.value() > 250) {
                percentRate.set(
                    250,
                    rh.gs(R.string.tandem_constraint_basal_rate_percent, percentRate.value()),
                    this
                )
            }
        }

        return percentRate
    }



    override var serviceConnection: ServiceConnection? = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            aapsLogger.info(LTag.PUMP, "Tandem Service is disconnected")
            tandemService = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            aapsLogger.info(LTag.PUMP, "Tandem Service is connected")
            val mLocalBinder = service as TandemService.LocalBinder
            tandemService = mLocalBinder.serviceInstance
            //isServiceSet = true
            //tandemPump

            //tandemUtil.driverStatus = PumpDriverState.Connecting

            // var startup = Completable
            //     .timer(4, TimeUnit.SECONDS)
            //     .subscribeOn(aapsSchedulers.main) // where the work should be done
            //     .observeOn(aapsSchedulers.main) // where the data stream should be delivered
            //     .subscribe({
            //                    isDriverInitialized = tandemService!!.validateParameters()
            //                    aapsLogger.info(LTag.PUMP, "Connection parameters valid: ${isDriverInitialized}")
            //                    if (isDriverInitialized) {
            //                        aapsLogger.info(LTag.PUMP, "Trying to connect to: ${tandemService!!.pumpAddress}")
            //                        tandemService!!.connectToPump()
            //                    }
            //                }, { fabricPrivacy.logException(it)  })

            Thread {
                var driverInitialized = tandemService!!.validateParameters()
                aapsLogger.info(LTag.PUMP, "Connection parameters valid: ${driverInitialized}")
                if (driverInitialized) {
                    aapsLogger.info(LTag.PUMP, "Trying to connect to: ${tandemService!!.pumpAddress}")
                    tandemService!!.connectToPump()
                }
            }.start()

        }
    }

//     override fun onStart() {
//         //super.onStart()
//         initPumpStatusData()
//
// //            serviceConnection?.let { serviceConnection ->
//             val intent = Intent(context, TandemService::class.java)
//             context.bindService(intent, serviceConnectionTandem, Context.BIND_AUTO_CREATE)
//             disposable.add(
//                 rxBus
//                     .toObservable(EventAppExit::class.java)
//                     .observeOn(aapsSchedulers.io)
//                     .subscribe({ context.unbindService(serviceConnectionTandem) }, fabricPrivacy::logException)
//             )
//
//             //          }
//
//         this.serviceRunning = true
//         onStartScheduledPumpActions()
//     }


    // private val dddserviceConnection: ServiceConnection = object : ServiceConnection {
    //     override fun onServiceDisconnected(name: ComponentName) {
    //         aapsLogger.debug(LTag.PUMP, "Tandem Service is disconnected")
    //         tandemService = null
    //     }
    //
    //     override fun onServiceConnected(name: ComponentName, service: IBinder) {
    //         aapsLogger.debug(LTag.PUMP, "Tandem Service is connected")
    //         val mLocalBinder = service as TandemService.LocalBinder
    //         tandemService = mLocalBinder.serviceInstance
    //     }
    // }


    // Pump Interface
    override fun isInitialized(): Boolean {
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "isInitialized ${isDriverInitialized}")
        return isDriverInitialized
    }

    override fun isSuspended(): Boolean {
        val suspended = (pumpStatus.pumpRunningState != PumpRunningState.Running)
        aapsLogger.debug(LTag.PUMP, "DUB isSuspended - $suspended")
        return suspended
    }

    override fun isConnected(): Boolean {
        val status = tandemUtil.driverStatus
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, logPrefix +"DUB isConnected [status=$status]")
        return status == PumpDriverState.Connected || status == PumpDriverState.Handshaking || status == PumpDriverState.ExecutingCommand
        // return isServiceSet && tandemService?.isInitialized == true


        // if (!driverInitialized)
        //     return false
        //
        // // if (driverMode== PumpDriverMode.Demo) {
        // //     return true
        // // }
        //
        // val driverStatus = tandemUtil.driverStatus
        // if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "isConnected - " + driverStatus.name)
        // return driverStatus == PumpDriverState.Ready || driverStatus == PumpDriverState.ExecutingCommand
    }

    override fun isConnecting(): Boolean {
        val status = tandemUtil.driverStatus
        val error = tandemUtil.errorType

        aapsLogger.debug(LTag.PUMP, logPrefix + "DUB isConnecting [status=$status]")

        val unreachable = (error!=null && error==PumpErrorType.PumpUnreachable)

        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, logPrefix + "DUB isConnecting [status=$status]")
        return status == PumpDriverState.Connecting || unreachable

        // return !isServiceSet || tandemService?.isInitialized != true

        // if (!driverInitialized)
        //     return false
        //
        // val driverStatus = tandemUtil.driverStatus
        // if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "isConnecting - " + driverStatus.name)
        //
        // return driverStatus == PumpDriverState.Connecting
    }

    // override fun connect(reason: String) {
    //     if (!driverInitialized) {
    //         return
    //     }
    //     if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "connect (reason=$reason).")
    //
    //     if (tandemUtil.preventConnect) {
    //         aapsLogger.info(LTag.PUMP, "in prevent connect mode (probably pairing in process)")
    //     } else {
    //         pumpConnectionManager.connectToPump() //deviceMac = pumpAddress, deviceBonded = pumpBonded)
    //     }
    // }

    // override fun disconnect(reason: String) {
    //     if (!driverInitialized)
    //         return
    //
    //     if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "disconnect (reason=$reason).")
    //
    //     val driverStatus = tandemUtil.driverStatus
    //
    //     if (driverStatus==PumpDriverState.Connected) {
    //         pumpConnectionManager.disconnectFromPump()
    //     } else {
    //         aapsLogger.debug(LTag.PUMP, "pump was not connected, so disconnect not required.")
    //     }
    // }

    // override fun stopConnecting() {
    //     if (!driverInitialized)
    //         return
    //     if (displayConnectionMessages)
    //         aapsLogger.debug(LTag.PUMP, "stopConnecting [PumpPluginAbstract] - default (empty) implementation.")
    // }
    //
    override fun isHandshakeInProgress(): Boolean {
        val status = tandemUtil.driverStatus
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "DUB isHandshakeInProgress - ${status==PumpDriverState.Handshaking}")

        return status == PumpDriverState.Handshaking


        // if (!driverInitialized)
        //     return false
        //
        // if (displayConnectionMessages)
        //     aapsLogger.debug(LTag.PUMP, "isHandshakeInProgress - " + tandemUtil.driverStatus.name)
        // return tandemUtil.driverStatus === PumpDriverState.Connecting
    }
    //
    // override fun finishHandshaking() {
    //     if (displayConnectionMessages)
    //         aapsLogger.debug(LTag.PUMP, "finishHandshaking [PumpPluginAbstract] - default (empty) implementation.")
    // }

    override val isFakingTempsByExtendedBoluses: Boolean
        get() = false

    override fun canHandleDST(): Boolean {
        return false
    }

    override fun isBusy(): Boolean {
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, logPrefix + "isBusy")
        if (busy || tandemUtil.preventConnect) {
            if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, logPrefix + "isBusy")
            return true
        } else
            return false
    }

    var busy = false
    //var secondRun = false

    override fun getPumpStatus(reason: String) {
        var needRefresh = false

        aapsLogger.info(LTag.PUMP, "getPumpStatus [first_run=$firstRun]")

        if (firstRun) {
            needRefresh = initializePump(!isRefresh)
            firstRun = false
        } else {
            refreshAnyStatusThatNeedsToBeRefreshed()
        }

        if (needRefresh) {
            rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Full))
        }
    }

    fun resetStatusState() {
        firstRun = true
        isRefresh = true
    }

    private fun refreshAnyStatusThatNeedsToBeRefreshed(): Boolean {
        val statusRefresh = workWithStatusRefresh(
            PumpDataRefreshAction.GetData, null,
            null)!!

        if (!doWeHaveAnyStatusNeededRefereshing(statusRefresh)) {
            return false
        }

        val refreshTypesNeededToReschedule: MutableSet<PumpDataRefreshType> = HashSet()

        var resetTime = false
        var resetDisplay = false

        if (hasTimeDateOrTimeZoneChanged) {
            aapsLogger.info(TAG, "Refresh_PumpTime: hasTimeDateOrTimeZoneChanged")

            if (!statusRefresh.contains(PumpDataRefreshType.PumpTime)) {
                statusRefresh.put(PumpDataRefreshType.PumpTime, System.currentTimeMillis()-2000)
                aapsLogger.info(TAG, "Refresh_PumpTime: added PumpTime to change list")
            }
            //
            // checkTimeAndOptionallySetTime(readTime = true)
            //
            // // read time if changed, set new time
            // hasTimeDateOrTimeZoneChanged = false
            //
            // if (statusRefresh!!.contains(PumpDataRefreshType.PumpTime)) {
            //     statusRefresh.remove(PumpDataRefreshType.PumpTime)
            //     refreshTypesNeededToReschedule.add(PumpDataRefreshType.PumpTime)
            //     resetTime = true
            // }
        }

        // execute

        for ((key, value) in statusRefresh!!) {
            if (value!! > 0 && System.currentTimeMillis() > value) {
                when (key) {
                    PumpDataRefreshType.PumpHistory      -> {
                        aapsLogger.info(LTag.PUMP, "Refresh_PumpHistory")
                        readPumpHistory()
                    }

                    PumpDataRefreshType.PumpTime         -> {
                        aapsLogger.info(LTag.PUMP, "Refresh_PumpTime")
                        pumpConnectionManager.getTime()
                        if (checkTimeAndOptionallySetTime(readTime = true)) {
                            resetDisplay = true
                        }
                        refreshTypesNeededToReschedule.add(key)
                        resetTime = true
                    }

                    PumpDataRefreshType.BatteryStatus,
                    PumpDataRefreshType.PumpStatus,
                    PumpDataRefreshType.RemainingInsulin -> {
                        if (key == PumpDataRefreshType.RemainingInsulin) {
                            aapsLogger.info(LTag.PUMP, "Refresh_RemainingInsulin")
                            pumpConnectionManager.getRemainingInsulin()
                        } else if (key == PumpDataRefreshType.PumpStatus) {
                            aapsLogger.info(LTag.PUMP, "Refresh_PumpStatus")
                            pumpConnectionManager.getPumpStatus()   // TODO
                        } else {
                            aapsLogger.info(LTag.PUMP, "Refresh_BatteryStatus");
                            pumpConnectionManager.getBatteryLevel()
                        }
                        refreshTypesNeededToReschedule.add(key)
                        resetDisplay = true
                        resetTime = true
                    }

                    else -> {

                    }
                    //null                                                                                -> TODO()
                }
            }

            // reschedule
            for (refreshType2 in refreshTypesNeededToReschedule) {
                scheduleNextRefresh(refreshType2)
            }
        }

        if (resetTime)
            pumpStatus.setLastCommunicationToNow()

        return resetDisplay
    }





    private fun setRefreshButtonEnabled(enabled: Boolean) {
        rxBus.send(EventRefreshButtonState(enabled))
    }

    // TODO not implemented fully
    private fun initializePump(realInit: Boolean): Boolean {
        //if (isDriverInitialized) return false
        aapsLogger.info(LTag.PUMP, logPrefix + "initializePump - start")

        setRefreshButtonEnabled(false)
        //pumpState = PumpDriverState.Connected

        // time (6h) - setting time command not available
        aapsLogger.info(LTag.PUMP, "Refresh_PumpTime: onInitializePump")
        checkTimeAndOptionallySetTime(!realInit)  // we read time only if its refresh and not first init

        // read status of pump from Db
        pumpConnectionManager.getPumpStatus()
        scheduleNextRefresh(PumpDataRefreshType.PumpStatus, 0)

        // TODO readPumpHistory
        // readPumpHistory()

        // remaining insulin (>50 = 4h; 50-20 = 1h; 15m) -
        pumpConnectionManager.getRemainingInsulin() // (command not available)
        scheduleNextRefresh(PumpDataRefreshType.RemainingInsulin, 10)

        // remaining power (1h) -
        pumpConnectionManager.getBatteryLevel()
        scheduleNextRefresh(PumpDataRefreshType.BatteryStatus, 20)

        //testModeCode();

        // configuration (once and then if history shows config changes)
        pumpConnectionManager.getConfiguration()
        checkThatSettingsAreEnforced()

        // pump info
        pumpConnectionManager.executeCustomCommand(TandemCustomCommand.GET_PUMP_INFO)

        // get basal profile
        pumpConnectionManager.getBasalProfile()

        // get TBR (if needed)
        if (pumpStatus.pumpStatusMirror==null || pumpStatus.pumpStatusMirror!!.isTemporaryBasalRunning()) {
            pumpConnectionManager.getTemporaryBasal()
        }

        // TODO remove
        if (pumpStatus.pumpStatusMirror==null) {
            aapsLogger.info(TAG, "TBR: Pump Status Mirror: is null")
        } else {
            aapsLogger.info(TAG, "TBR: Pump Status Mirror: ${pumpStatus.pumpStatusMirror!!.basalStatusIcon}")
        }


        pumpStatus.setLastCommunicationToNow()
        setRefreshButtonEnabled(true)

        if (!isRefresh) {
            pumpState = PumpDriverState.Initialized
        }

        isDriverInitialized = true  // means that first data was read
        firstRun = false


        return true
    }

    private fun testModeCode() {

        //pumpConnectionManager.sendBasalProfile()

        var okProfile = "{\"dia\":\"5\"," +
            "\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}]," +
            "\"sens\":[{\"time\":\"00:00\",\"value\":\"6\"}," +
            "          {\"time\":\"08:00\",\"value\":\"6.5\"}," +
            "          {\"time\":\"12:00\",\"value\":\"7\"}," +
            "          {\"time\":\"15:00\",\"value\":\"7.1\"}," +
            "          {\"time\":\"19:00\",\"value\":\"8\"}" +
            "]," +
            "\"timezone\":\"GMT\"," +
            "\"basal\":[{\"time\":\"00:00\",\"value\":\"0.8\"}," +
            "           {\"time\":\"05:00\",\"value\":\"0.9\"}," +
            "           {\"time\":\"07:00\",\"value\":\"1.2\"}," +
            "           {\"time\":\"13:00\",\"value\":\"1.4\"}," +
            "           {\"time\":\"18:00\",\"value\":\"2.2\"}," +
            "           {\"time\":\"20:00\",\"value\":\"3\"}" +
            "]," +
            "\"target_low\":[{\"time\":\"00:00\",\"value\":\"8\"}," +
            "                 {\"time\":\"06:00\",\"value\":\"6\"}," +
            "                 {\"time\":\"12:00\",\"value\":\"5\"}," +
            "                 {\"time\":\"19:00\",\"value\":\"6\"}]," +
            "\"target_high\":[{\"time\":\"00:00\",\"value\":\"8\"}," +
            "                 {\"time\":\"06:00\",\"value\":\"6\"}," +
            "                 {\"time\":\"12:00\",\"value\":\"5\"}," +
            "                 {\"time\":\"19:00\",\"value\":\"6\"}]," +
            "\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"

        val profile = ProfileSealed.Pure(pureProfileFromJson(JSONObject(okProfile), dateUtil)!!,
                                         activePlugin)

        aapsLogger.error(LTag.PUMP, "SBP - Setting Profile")
        val result = pumpConnectionManager.setBasalProfile(profile)

        if (result.isSuccess) {
            aapsLogger.error(LTag.PUMP, "SBP - Setting Profile was SUCCESS")

            pumpConnectionManager.getBasalProfile()



        } else {
            aapsLogger.error(LTag.PUMP, "SBP - Problem setting Profile: ${result.errorDescription}")
        }




        System.exit(44)
    }



    override fun isThisProfileSet(profile: Profile): Boolean {

        aapsLogger.debug(TAG, "isThisProfileSet")

        if (!isDriverInitialized) {
            aapsLogger.warn(TAG, "Driver not initialized. Returning isThisProfileSet=true" )
            return true
        }

        if (pumpStatus.basalsByHour == null) {
            aapsLogger.debug(TAG, "  Pump Profile:     null, returning false")
            return false
        } else {
            val profileAsString = ProfileUtil.getBasalProfilesDisplayableAsStringOfArrayV2(profile, PumpType.TANDEM_T_MOBI_BT)
            val profileDriver = ProfileUtil.getProfilesByHourToString(pumpStatus.basalsByHour)

            // TODO isThisProfile Set display profile - set logging to debug
            aapsLogger.info(TAG, "AAPS Profile:     $profileAsString")
            aapsLogger.info(TAG, "Pump Profile:     $profileDriver")

            val areTheySame = profileAsString.equals(profileDriver)

            aapsLogger.info(TAG, "Pump Profile is the same: $areTheySame")  // TODO set to debug

            return areTheySame
        }
    }

    override fun lastDataTime(): Long {
        return if (pumpStatus.lastConnection != 0L) {
            pumpStatus.lastConnection
        } else System.currentTimeMillis()
    }


    override val baseBasalRate: Double
        get() = if (pumpStatus.basalsByHour == null) {
                aapsLogger.debug(LTag.PUMP, "Profile is not set !")
                pumpStatus.baseBasalRate = 0.0
                pumpStatus.baseBasalRate
            } else {
                val basal = pumpStatus.basalProfileForHour
                aapsLogger.debug(LTag.PUMP, "Basal for this hour is: $basal")
                pumpStatus.baseBasalRate = basal
                basal
            }


    override val reservoirLevel: Double
        get() = pumpStatus.reservoirRemainingUnits


    override val batteryLevel: Int
        get() = pumpStatus.batteryRemaining


    override fun triggerUIChange() {
        rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.TreatmentValues))
    }


    // override fun hasService(): Boolean {
    //     return true
    // }

    private var bolusDeliveryType = BolusDeliveryType.Idle

    private enum class BolusDeliveryType {
        Idle,  //
        DeliveryPrepared,  //
        Delivering,  //
        CancelDelivery
    }


    private fun checkThatSettingsAreEnforced() {

        if (pumpStatus.settings!=null) {

            var maxBolus = (pumpStatus.settings!![TandemPumpSettingType.MAX_BOLUS]) as Int
            val maxBolusRequired = sp.getInt(TandemPumpConst.Prefs.MaxBolus, 25)

            maxBolus /= 1000

            aapsLogger.debug(TAG, "Current Max Bolus: ${maxBolus}, Required: $maxBolusRequired")

            if (maxBolus != maxBolusRequired) {
                pumpConnectionManager.executeCustomCommand(TandemCustomCommand.SET_MAX_BOLUS, maxBolusRequired)
            }

            var maxBasal = (pumpStatus.settings!![TandemPumpSettingType.BASAL_LIMIT]) as Long
            val maxBasalRequired = sp.getInt(TandemPumpConst.Prefs.MaxBasal, 15)

            val maxBasalInt = (maxBasal / 1000).toInt()

            aapsLogger.debug(TAG, "Current Max Basal: ${maxBasalInt}, Required: $maxBasalRequired")

            if (maxBasalInt != maxBasalRequired) {
                pumpConnectionManager.executeCustomCommand(TandemCustomCommand.SET_MAX_BASAL, maxBasalRequired)
            }


            val controlIQEnabled = (pumpStatus.settings!![TandemPumpSettingType.CONTROL_IQ_ENABLED]) as Boolean

            if (controlIQEnabled) {
                pumpConnectionManager.executeCustomCommand(TandemCustomCommand.SET_CONTROL_IQ, false)
            }
        }

    }



    private fun checkTimeAndOptionallySetTime(readTime: Boolean): Boolean {
        aapsLogger.info(LTag.PUMP, logPrefix + "checkTimeAndOptionallySetTime - Start")

        if (readTime) {
            pumpConnectionManager.getTime()
        }

        try {

            if (pumpStatus.pumpTime != null) {

                val diff = Math.abs(pumpStatus.pumpTime!!.timeDifference)

                pumpStatus.pumpTime!!.displayTime(gson = gson, aapsLogger = aapsLogger)

                if (diff > 60) {
                    aapsLogger.error(TAG, "Time difference between phone and pump is more than 60s ($diff)")

                    if (!pumpStatus.tandemPumpFirmware.isMobi()) {
                        val notification = Notification(Notification.OMNIPOD_TIME_OUT_OF_SYNC,
                                                        rh.gs(Rc.string.pump_time_difference_too_big, 60, diff), Notification.INFO, 60)
                        rxBus.send(EventNewNotification(notification))
                    } else {

                        val time = pumpConnectionManager.setTime()

                        if (time.isSuccess) {

                                val notification = Notification(
                                    Notification.INSIGHT_DATE_TIME_UPDATED,
                                    rh.gs(Rc.string.pump_time_updated),
                                    Notification.INFO, 60
                                )
                                rxBus.send(EventNewNotification(notification))

                        } else {
                            aapsLogger.error(LTag.PUMP, "Setting time on pump failed.")
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            aapsLogger.error(TAG, "Setting time on pump failed.")
        } finally {
            setRefreshButtonEnabled(false)
            scheduleNextRefresh(PumpDataRefreshType.PumpTime, 0)
        }

        return true
    }

    // TODO progress bar
    override fun deliverBolus(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, logPrefix + "deliverBolus - " + BolusDeliveryType.DeliveryPrepared)
        return if (detailedBolusInfo.insulin > pumpStatus.reservoirRemainingUnits) {
            PumpEnactResultObject(rh) //
                .success(false) //
                .enacted(false) //
                .comment(
                    rh.gs(
                        R.string.ypsopump_cmd_bolus_could_not_be_delivered_no_insulin,
                        pumpStatus.reservoirRemainingUnits,
                        detailedBolusInfo.insulin
                    )
                )
        } else try {
            setRefreshButtonEnabled(false)

            val commandResponse = pumpConnectionManager.deliverBolus(detailedBolusInfo)

            if (commandResponse.isSuccess) {
                val now = System.currentTimeMillis()

                detailedBolusInfo.bolusTimestamp = now

                this.pumpStatus.lastBolus = detailedBolusInfo

                // we subtract insulin, exact amount will be visible with next remainingInsulin update.
                //pumpStatus.reservoirRemainingUnits -= detailedBolusInfo.insulin

                incrementStatistics(if (detailedBolusInfo.bolusType == BS.Type.SMB)
                    TandemPumpConst.Statistics.SMBBoluses
                else
                    TandemPumpConst.Statistics.StandardBoluses)

                if (detailedBolusInfo.carbs > 0.0) {
                    pumpSync.syncCarbsWithTimestamp(
                        timestamp = now,
                        amount = detailedBolusInfo.carbs,
                        pumpId = null,
                        pumpType = pumpType,
                        pumpSerial = serialNumber()
                    )
                }

                readPumpHistoryAfterAction(bolusInfo = detailedBolusInfo)

                PumpEnactResultObject(rh).success(true) //
                    .enacted(true) //
                    .bolusDelivered(detailedBolusInfo.insulin) //
                    //.carbsDelivered(detailedBolusInfo.carbs)   // TODO
            } else {
                PumpEnactResultObject(rh) //
                    .success(false) //
                    .enacted(false) //
                    .comment(rh.gs(R.string.ypsopump_cmd_bolus_could_not_be_delivered))
            }
        } finally {
            finishAction("Bolus")
        }
    }

    override fun stopBolusDelivering() {
        bolusDeliveryType = BolusDeliveryType.CancelDelivery
        // TODO if there is command
        if (isLoggingEnabled) aapsLogger.warn(LTag.PUMP, "TandemPumpPlugin::deliverBolus - Stop Bolus Delivery.")
    }

    private val isLoggingEnabled: Boolean
        get() = true


    // if enforceNew===true current temp basal is canceled and new TBR set (duration is prolonged),
    // if false and the same rate is requested enacted=false and success=true is returned and TBR is not changed
    @Synchronized
    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile,
                                     enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType
    ): PumpEnactResult {
        setRefreshButtonEnabled(false)

        return try {
            aapsLogger.info(LTag.PUMP, "TBR setTempBasalPercent: rate: ${percent} %, duration=$durationInMinutes [enforce=$enforceNew,tbrType=${tbrType.name}]"  )

            // read current TBR
            val tbrCurrent = readTBR()  // if null is returned no TBR running
            if (tbrCurrent == null) {
                // aapsLogger.warn(LTag.PUMP, logPrefix + " TBR setTempBasalPercent - Could not read current TBR, canceling operation.")
                // return instantiator.providePumpEnactResult().success(false).enacted(false)
                //     .comment(rh.gs(Rc.string.pump_cmd_err_cant_read_tbr))
            } else {
                //pumpStatus.currentTempBasal = tbrCurrent

                aapsLogger.info(LTag.PUMP, "TBR Current Basal: ${tbrCurrent}")
            }
            if (!enforceNew && tbrCurrent!=null) {
                aapsLogger.info(LTag.PUMP, "enforceNEW = false")
                if (tandemUtil.isSame(tbrCurrent.insulinRate, percent)) {
                    aapsLogger.info(LTag.PUMP, "enforceNEW = false, same = true")
                    var sameRate = true
                    // if (tandemUtil.isSame(0.0, percent) && durationInMinutes > 0) {
                    //     // if rate is 0.0 and duration>0 then the rate is not the same
                    //     aapsLogger.info(LTag.PUMP, "enforceNEW = false, same = true, value=0%/0min NOT THE SAME")
                    //     sameRate = false
                    // }
                    if (sameRate) {
                        aapsLogger.info(LTag.PUMP, logPrefix + "TBR setTempBasalPercent - No enforceNew and same rate. Exiting.")
                        return instantiator.providePumpEnactResult().success(true).enacted(false)
                    }
                }
                // if not the same rate, we cancel and start new
            }

            // if TBR is running we will cancel it.
            if (tbrCurrent!=null) {
                aapsLogger.info(LTag.PUMP, "setTempBasalPercent - TBR running - so canceling it.")

                // CANCEL
                val commandResponseCancel = pumpConnectionManager.cancelTemporaryBasal()
                if (commandResponseCancel.isSuccess) {
                    aapsLogger.info(LTag.PUMP, " TBR setTempBasalPercent - Current TBR cancelled.")
                } else {
                    aapsLogger.error(logPrefix + "setTempBasalPercent - Cancel TBR failed.")
                    return instantiator.providePumpEnactResult().success(false).enacted(false)
                        .comment(rh.gs(Rc.string.pump_cmd_err_cant_cancel_tbr_stop_op))
                }
            }
            // else {
            //     aapsLogger.info(LTag.PUMP, "TBR cancel was skipped: $tbrCurrent")
            // }

            // now start new TBR
            val commandResponse  = pumpConnectionManager.setTemporaryBasal(percent, durationInMinutes)

            val controlCommandResponse: TempBasalPair = commandResponse.value as TempBasalPair

            aapsLogger.info(LTag.PUMP, logPrefix + "setTempBasalPercent - setTBR. Response: " + commandResponse)
            if (commandResponse.isSuccess) {

                // val tbr = TempBasalPair(
                //     insulinRate = percent.toDouble(),
                //     isPercent = true,
                //     start = System.currentTimeMillis(),
                //     durationMinutes = durationInMinutes)

                pumpStatus.currentTempBasal = controlCommandResponse

                readPumpHistoryAfterAction(tempBasalInfo = controlCommandResponse)

                //val tempData = PumpDbEntryTBR(percent.toDouble(), false, durationInMinutes * 60, tbrType)
                //medtronicPumpStatus.runningTBRWithTemp = tempData
                //pumpSyncStorage.addTemporaryBasalRateWithTempId(tempData, true, this)

                //commandResponse.value as

                pumpSync.addTemporaryBasalWithTempId(timestamp = controlCommandResponse.start!!,
                                                     isAbsolute = false,
                                                     pumpSerial = serialNumber(),
                                                     pumpType = PumpType.TANDEM_T_MOBI_BT,
                                                     type = tbrType,
                                                     duration = (controlCommandResponse.durationMinutes*60*1000).toLong(),
                                                     rate = percent.toDouble(),
                                                     tempId = (100000000 + controlCommandResponse.id!!)
                                                     )

                incrementStatistics(TandemPumpConst.Statistics.TBRsSet)

                instantiator.providePumpEnactResult().success(true).enacted(true) //
                    .percent(percent).duration(durationInMinutes)
            } else {
                instantiator.providePumpEnactResult().success(false).enacted(false) //
                    .comment(rh.gs(Rc.string.pump_cmd_err_tbr_could_not_be_delivered))
            }
        } finally {
            finishAction("TBR")
        }
    }

    // TODO setTempBasalAbsolute
    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile,
                                      enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType
    ): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, "TBR setTempBasalAbsolute called with a rate of $absoluteRate for $durationInMinutes min [enforce=$enforceNew,tbrType=${tbrType.name}].")

        //val profileString = ProfileUtil.getBasalProfilesDisplayableAsStringOfArrayV2(profile, PumpType.TANDEM_T_MOBI_BT)

        //aapsLogger.info(TAG, "TBR Profile: $profileString")

        //val newAbsoluteValue = pumpDescription.pumpType.determineCorrectBasalSize(absoluteRate)

        //aapsLogger.info(TAG, "TBR newAbsoluteValue: $newAbsoluteValue")

        val unroundedPercentage = ((absoluteRate / baseBasalRate) * 100).toInt()

        //val newPrecentValue = pumpDescription.pumpType.determineCorrectBasalSize(unroundedPercentage.toDouble())

        //aapsLogger.info(TAG, "TBR newPercentValue: $newPrecentValue")

        aapsLogger.info(LTag.PUMP, "TBR abs=$absoluteRate,base=${pumpStatus.basalProfileForHour},percent: $unroundedPercentage")

        return this.setTempBasalPercent(unroundedPercentage, durationInMinutes, profile, enforceNew, tbrType)
    }


    private fun finishAction(overviewKey: String?) {
        if (overviewKey != null) rxBus.send(EventRefreshOverview(overviewKey, false))
        triggerUIChange()
        setRefreshButtonEnabled(true)
    }

    // TODO we might want to return data
    private fun readPumpHistory() {
        aapsLogger.warn(LTag.PUMP, logPrefix + "readPumpHistory N/A WIP.")

        //ypsoPumpHistoryHandler.getFullPumpHistory()

        //        pumpConnectionManager.getPumpHistory()

        scheduleNextRefresh(PumpDataRefreshType.PumpHistory)

    }


    private fun readPumpHistoryAfterAction(bolusInfo: DetailedBolusInfo? = null,
                                           tempBasalInfo: TempBasalPair? = null,
                                           profile: Profile? = null) {
        // TODO readPumpHistoryAfterAction
        // if (true)
        //     return
        aapsLogger.warn(LTag.PUMP, logPrefix + "readPumpHistoryAfterAction N/A.")
        // ypsoPumpHistoryHandler.getLastEventAndSendItToPumpSync(
        //     bolusInfo = bolusInfo,
        //     tempBasalInfo = tempBasalInfo,
        //     profile = profile
        // )
    }

    // private fun scheduleNextRefresh(refreshType: TandemStatusRefreshType, additionalTimeInMinutes: Int = 0) {
    //     when (refreshType) {
    //         TandemStatusRefreshType.RemainingInsulin -> {
    //             val remaining = pumpStatus.reservoirRemainingUnits
    //             val min: Int
    //             min = if (remaining > 50) 4 * 60 else if (remaining > 20) 60 else 15
    //             workWithStatusRefresh(StatusRefreshAction.Add, refreshType, getTimeInFutureFromMinutes(min))
    //         }
    //
    //         //  TODO TandemPumpStatusRefreshType.Configuration
    //
    //         TandemStatusRefreshType.PumpTime,
    //         TandemStatusRefreshType.BatteryStatus    -> {
    //             workWithStatusRefresh(
    //                 StatusRefreshAction.Add, refreshType,
    //                 getTimeInFutureFromMinutes(refreshType.refreshTime + additionalTimeInMinutes)
    //             )
    //         }
    //         TandemStatusRefreshType.PumpHistory      -> {
    //             workWithStatusRefresh(
    //                 StatusRefreshAction.Add, refreshType,
    //                 getTimeInFutureFromMinutes(getHistoryRefreshTime() + additionalTimeInMinutes)
    //             )
    //         }
    //
    //     }
    // }

    private fun getHistoryRefreshTime(): Int {
        // TODO history Refresh Time
        if (this.driverMode != PumpDriverMode.Automatic) {
            return 15 // TODO use settings
        } else {
            return 5
        }
    }
    //
    // private enum class StatusRefreshAction {
    //     Add,  //
    //     GetData
    // }

    // @Synchronized
    // private fun workWithStatusRefresh(
    //     action: StatusRefreshAction,  //
    //     statusRefreshType: TandemStatusRefreshType?,  //
    //     time: Long?
    // ): Map<TandemStatusRefreshType?, Long?>? {
    //     return when (action) {
    //         StatusRefreshAction.Add     -> {
    //             statusRefreshMap[statusRefreshType] = time
    //             null
    //         }
    //
    //         StatusRefreshAction.GetData -> {
    //             HashMap(statusRefreshMap)
    //         }
    //
    //         //else                        -> null
    //     }
    // }


    private fun readTBR(): TempBasalPair? {
        val temporaryBasalResponse = pumpConnectionManager.getTemporaryBasal()

        aapsLogger.info(LTag.PUMP, "TBR readTBR ${temporaryBasalResponse}")

        return if (temporaryBasalResponse.value!=null) {
            val tbr = temporaryBasalResponse.value!!

            // // we sometimes get rate returned even if TBR is no longer running
            // if (tbr.durationMinutes == 0) {
            //     tbr.insulinRate = 0.0
            // }
            tbr
        } else {
            null
        }
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        return try {
            aapsLogger.info(TAG, "TBR cancelTempBasal - started")
            setRefreshButtonEnabled(false)
            val tbrCurrent = readTBR()

            if (tbrCurrent==null) {
                aapsLogger.info(TAG, "cancelTempBasal - TBR is not running exiting.")
                pumpStatus.clearTbr()
                //finishAction("TBR")
                return instantiator.providePumpEnactResult().success(true).enacted(false)
            }

            // if (tbrCurrent != null) {
            //     if (tbrCurrent.insulinRate == 0.0 && tbrCurrent.durationMinutes == 0) {
            //         aapsLogger.info(LTag.PUMP, logPrefix + "cancelTempBasal - TBR already canceled.")
            //         finishAction("TBR")
            //         pumpStatus.clearTbr()
            //         return PumpEnactResultObject(rh).success(true).enacted(false)
            //     }
            // } else {
            //     aapsLogger.warn(LTag.PUMP, logPrefix + "cancelTempBasal - Could not read currect TBR, canceling operation.")
            //     finishAction("TBR")
            //     return PumpEnactResultObject(rh).success(false).enacted(false)
            //         .comment(rh.gs(R.string.ypsopump_cmd_cant_read_tbr))
            // }

            val commandResponse = pumpConnectionManager.cancelTemporaryBasal()

            if (commandResponse.isSuccess) {
                aapsLogger.info(TAG, "cancelTempBasal - Cancel TBR successful.")

                readPumpHistoryAfterAction(tempBasalInfo = TempBasalPair(insulinRate = 0.0, isPercent = true, durationMinutes = 0))
                pumpStatus.clearTbr()

                instantiator.providePumpEnactResult().success(true).enacted(true) //
                    .isTempCancel(true)
            } else {
                aapsLogger.info(TAG, "cancelTempBasal - Cancel TBR failed.")
                instantiator.providePumpEnactResult().success(false).enacted(false) //
                    .comment(rh.gs(Rc.string.pump_cmd_err_cant_cancel_tbr))
            }
        } finally {
            finishAction("TBR")
        }
    }

    override fun serialNumber(): String {
        return pumpStatus.serialNumber.toString()
    }

    override fun generateTempId(objectA: Any): Long {
        return 0L
    }

    //    @NotNull @Override
    //    public Constraint<Boolean> isClosedLoopAllowed(@NotNull Constraint<Boolean> value) {
    ////        if(pumpStatus.ypsopumpFirmware==null) {
    ////            return value.set(aapsLogger,false, rh.gs(R.string.some_reason), this);
    ////        } else {
    ////            return value;
    ////        }
    //
    //        return new Constraint<Boolean>(true);
    //    }



    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, "setNewBasalProfile - start")
        return try {
            setRefreshButtonEnabled(false)
            val resultCommandResponse: DataCommandResponse<Boolean?>
            val driverModeCurrent = driverMode

            resultCommandResponse = pumpConnectionManager.setBasalProfile(profile)

            //readPumpHistoryAfterAction(profile = profile)

            aapsLogger.info(LTag.PUMP, logPrefix + "Basal Profile was set: " + resultCommandResponse)

            if (resultCommandResponse.isSuccess) {
                pumpStatus.basalsByHour = ProfileUtil.getArrayOfHourlyBasals(profile)
                instantiator.providePumpEnactResult()
                    .success(true).enacted(true)
            } else {
                instantiator.providePumpEnactResult()
                    .success(false).enacted(false)
                    .comment(rh.gs(Rc.string.pump_cmd_err_basal_profile_could_not_be_set))
            }
        } finally {
            finishAction("Set Basal Profile")
        }
    }

    // override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {
    //     aapsLogger.warn(LTag.PUMP, logPrefix + "Time or TimeZone changed. ")
    //     hasTimeDateOrTimeZoneChanged = true
    // }


    // OPERATIONS not supported by Pump or Plugin


    init {
        displayConnectionMessages = true
    }


    override fun getRefreshTime(pumpDataRefreshType: PumpDataRefreshType): Int {
        return (
            when(pumpDataRefreshType) {
                PumpDataRefreshType.PumpHistory      -> getHistoryRefreshTime()
                PumpDataRefreshType.RemainingInsulin -> {
                    val remaining = pumpStatus.reservoirRemainingUnits

                    //if (remaining > 50) 60 else if (remaining > 20) 30 else 15
                    15   // TODO use upper formula, this is just for testing
                }
                PumpDataRefreshType.BatteryStatus    -> 55
                PumpDataRefreshType.PumpTime         -> 300
                PumpDataRefreshType.PumpStatus       -> 5
                else                                 -> -1
        })
    }


    override fun isInPreventConnectMode(): Boolean {
        return tandemUtil.preventConnect
    }


    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {
        aapsLogger.warn(LTag.PUMP, logPrefix + "Time or TimeZone changed (type=$timeChangeType). ")
        this.timeChangeType = timeChangeType
        this.hasTimeDateOrTimeZoneChanged = true
        val timeRefresh = System.currentTimeMillis() + (20*1000)  // 20s in future
        scheduleNextRefreshWithSpecifiedTime(PumpDataRefreshType.PumpTime, timeRefresh)
    }


}