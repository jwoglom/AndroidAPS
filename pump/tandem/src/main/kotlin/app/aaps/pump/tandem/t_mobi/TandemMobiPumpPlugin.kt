package app.aaps.pump.tandem.t_mobi

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
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
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveListPreference
import app.aaps.core.validators.preferences.AdaptiveStringPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.pump.tandem.R
import app.aaps.pump.common.PumpPluginAbstract
import app.aaps.pump.common.data.PumpStatus
import app.aaps.pump.common.events.EventPumpConnectionParametersChanged
import app.aaps.pump.common.sync.PumpSyncStorage
import app.aaps.pump.common.utils.ProfileUtil
import app.aaps.pump.tandem.common.driver.connector.TandemPumpConnectionManager
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import app.aaps.pump.common.R as Rc

import app.aaps.pump.common.defs.*
import app.aaps.pump.common.defs.PumpDriverMode
import app.aaps.pump.common.defs.PumpDriverState
import app.aaps.pump.common.defs.PumpRunningState
import app.aaps.pump.common.defs.PumpUpdateFragmentType
import app.aaps.pump.common.driver.connector.commands.response.DataCommandResponse
import app.aaps.pump.common.driver.history.PumpHistoryPeriod
import app.aaps.pump.common.driver.refresh.PumpDataRefreshAction
import app.aaps.pump.common.driver.refresh.PumpDataRefreshCapable
import app.aaps.pump.common.driver.refresh.PumpDataRefreshType
import app.aaps.pump.common.events.EventPumpDataRefresh
import app.aaps.pump.common.events.EventPumpForceDisconnect
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.t_mobi.driver.TandemMobiPumpDriverConfiguration
import app.aaps.pump.common.events.EventPumpFragmentValuesChanged
import app.aaps.pump.tandem.common.comm.history.HistoryRetriever
import app.aaps.pump.tandem.common.comm.qe.QualifyingEventHandler
import app.aaps.pump.tandem.common.data.defs.QualifyingEventsFilter
import app.aaps.pump.tandem.common.data.defs.QualifyingEventsRange
import app.aaps.pump.tandem.common.data.defs.RefreshData
import app.aaps.pump.tandem.common.data.defs.TandemPumpSettingType
import app.aaps.pump.tandem.common.database.data.DbDataHandler
import app.aaps.pump.tandem.common.driver.connector.def.TandemCustomCommand
import app.aaps.pump.tandem.common.events.EventDatabaseAddQEData
import app.aaps.pump.tandem.common.events.EventHandleQualifyingEvent
import app.aaps.pump.tandem.common.events.EventRefreshPumpData
import app.aaps.pump.tandem.common.keys.TandemBooleanPreferenceKey
import app.aaps.pump.tandem.common.keys.TandemIntPreferenceKey
import app.aaps.pump.tandem.common.keys.TandemLongNonPreferenceKey
import app.aaps.pump.tandem.common.keys.TandemStringPreferenceKey
import app.aaps.pump.tandem.common.service.TandemService
import app.aaps.pump.tandem.t_mobi.ui.TandemMobiPumpFragment
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONObject

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by andy on 04.01.2025.
 *
 * @author Andy Rozman (andy.rozman@gmail.com)
 */
@Singleton
class TandemMobiPumpPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rxBus: RxBus,
    context: Context,
    preferences: Preferences,
    rh: ResourceHelper,
    activePlugin: ActivePlugin,
    commandQueue: CommandQueue,
    fabricPrivacy: FabricPrivacy,
    val tandemPumpUtil: TandemPumpUtil,
    val pumpStatus: TandemPumpStatus,
    val qualifyingEventHandler: QualifyingEventHandler,
    dateUtil: DateUtil,
    val pumpConnectionManager: TandemPumpConnectionManager,
    aapsSchedulers: AapsSchedulers,
    pumpSync: PumpSync,
    pumpSyncStorage: PumpSyncStorage,
    tandemPumpDriverConfiguration: TandemMobiPumpDriverConfiguration,
    decimalFormatter: DecimalFormatter,
    val dbDataHandler: DbDataHandler,
    val historyRetriever: HistoryRetriever,
    instantiator: Instantiator
) : PumpPluginAbstract(
    pluginDescription = PluginDescription() //
        .mainType(PluginType.PUMP) //
        .fragmentClass(TandemMobiPumpFragment::class.java.name) //
        .pluginIcon(R.drawable.t_mobi)
        .pluginName(R.string.tandem_name_mobi) //
        .shortName(R.string.tandem_name_mobi_short) //
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        //.preferencesId(R.xml.pref_tandem_mobi)
        .description(R.string.description_pump_tandem_mobi),  //
    pumpType = PumpType.TANDEM_T_MOBI_BT,
    rh = rh,
    aapsLogger = aapsLogger,
    commandQueue = commandQueue,
    rxBus = rxBus,
    activePlugin = activePlugin,
    preferences = preferences,
    context = context,
    fabricPrivacy = fabricPrivacy,
    dateUtil = dateUtil,
    aapsSchedulers = aapsSchedulers,
    pumpSync = pumpSync,
    pumpSyncStorage = pumpSyncStorage,
    pumpDriverConfigurationInternal = tandemPumpDriverConfiguration,
    decimalFormatter = decimalFormatter,
    instantiator = instantiator,
    ownPreferences = listOf(
        TandemLongNonPreferenceKey::class.java,
        TandemStringPreferenceKey::class.java,
        TandemIntPreferenceKey::class.java,
        TandemBooleanPreferenceKey::class.java
    )
), Pump, PluginConstraints, PumpDataRefreshCapable /*, PumpConstraints */ {

    // variables for handling statuses and history

    private var tandemService: TandemService? = null
    private var driverMode = PumpDriverMode.Automatic // TODO when implementation fully done, default should be automatic

    private val versionInternal = TandemMobiPluginVersion()

    private var wantedDriverMode = PumpDriverMode.Automatic  // TODO change this (demo mode means we are not communicating with pump)

    @Suppress("PropertyName")
    private val TAG = LTag.PUMP

    override fun onStart() {
        aapsLogger.debug(LTag.PUMP, model().model + " started (t:mobi) - $version")
        dbDataHandler.databaseStatistics() // TODO remove in future

        PumpHistoryEntryGroup.doTranslation(rh)
        PumpHistoryPeriod.doTranslation(rh)

        super.onStart()
    }


    override fun updatePreferenceSummary(pref: Preference) {
        super.updatePreferenceSummary(pref)
        if (pref.key == TandemStringPreferenceKey.PumpAddress.key) {
            val value: String? = tandemPumpUtil.getStringPreferenceOrDefaultOrNull(TandemStringPreferenceKey.PumpAddress, null)
                //sp.getStringOrNull(R.string.key_tandem_address, null)
            pref.summary = value ?: rh.gs(app.aaps.core.ui.R.string.not_set_short)
            aapsLogger.info(LTag.PUMP, "TANDEMDBG: Received event that pump address changed (this means bonding/unbonding is happening)")
        } else if (pref.key == TandemStringPreferenceKey.PumpSerial.key) {
            val value: String? = tandemPumpUtil.getStringPreferenceOrDefaultOrNull(TandemStringPreferenceKey.PumpSerial, null)
                //sp.getStringOrNull(R.string.key_tandem_serial, null)
            pref.summary = value ?: rh.gs(app.aaps.core.ui.R.string.not_set_short)
            aapsLogger.info(LTag.PUMP, "TANDEMDBG: Received event that pump serial changed (this means bonding/unbonding is happening)")
        } else if (pref.key == TandemStringPreferenceKey.SharedConnectionData.key) {
            val value: String? = tandemPumpUtil.getStringPreferenceOrDefaultOrNull(TandemStringPreferenceKey.SharedConnectionData, null)
                //sp.getStringOrNull(R.string.key_tandem_shared_connection_data, null)
            aapsLogger.info(LTag.PUMP, "TANDEMDBG: Received event that connection data changed. Reconnecting to pump")
            if (value!=null && value.isNotEmpty()) {
                if (tandemService!!.validateParameters() && !tandemService!!.isConnected()) {
                    tandemService!!.connectToPump()
                }
            }
        }
    }

    // PumpAbstract implementations
    override fun initPumpStatusData() {
        //pumpStatus.lastConnection = sp.getLong(TandemPumpConst.Statistics.LastGoodPumpCommunicationTime, 0L)
        if (preferences.getIfExists(TandemLongNonPreferenceKey.LastGoodPumpCommunicationTime) != null) {
            pumpStatus.lastConnection = preferences.get(TandemLongNonPreferenceKey.LastGoodPumpCommunicationTime)
            pumpStatus.lastDataTime = pumpStatus.lastConnection
            pumpStatus.previousConnection = pumpStatus.lastConnection
        }
        aapsLogger.debug(TAG, "initPumpStatusData: " + pumpStatus)

        pumpType = PumpType.TANDEM_T_MOBI_BT

        // this is only thing that can change, by being configured
        // TODO pumpDescription.maxTempAbsolute = (pumpStatus.maxBasal != null) ? pumpStatus.maxBasal : 35.0d;
        aapsLogger.debug(LTag.PUMP, "pumpDescription: " + this.pumpDescription)

        //this.pumpDescription;

        pumpStatus.pumpDescription = this.pumpDescription

        // set first Tandem Pump Start
        if (preferences.getIfExists(TandemLongNonPreferenceKey.FirstPumpUse) == null)
            preferences.put(TandemLongNonPreferenceKey.FirstPumpUse, System.currentTimeMillis())

        if (preferences.getIfExists(TandemStringPreferenceKey.PumpSerial) != null) {
            val preference = preferences.get(TandemStringPreferenceKey.PumpSerial);
            if (preference.isNotEmpty()) {
                pumpStatus.serialNumber = preferences.get(TandemStringPreferenceKey.PumpSerial).toLong()
            }
        }

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

        disposable += rxBus
            .toObservable(EventRefreshPumpData::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ refreshAfterUI(it) }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventDatabaseAddQEData::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ dbDataHandler.addQualifyingEventRecords(it.eventEntities) } ,
                       { fabricPrivacy.logException(it) })



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


    private fun refreshAfterUI(refreshEvent: EventRefreshPumpData) {
        if (refreshEvent.refreshEvents.contains(RefreshData.SEMAPHORE_EVENTS)) {
            if (pumpStatus.semaphoreNeedsRefresh) {
                rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Custom_2))
            }
        }

        if (refreshEvent.refreshEvents.contains(RefreshData.PUMP_INSULIN_LEVEL)) {
            scheduleNextRefreshWithSpecifiedTime(PumpDataRefreshType.RemainingInsulin, System.currentTimeMillis())
            scheduleNextRefreshWithSpecifiedTime(PumpDataRefreshType.Custom_1, System.currentTimeMillis())
            scheduleNextRefreshWithSpecifiedTime(PumpDataRefreshType.GetTemporaryBasal, System.currentTimeMillis())
            if (!commandQueue.statusInQueue()) {
                commandQueue.readStatus("Status Refresh (UI)", null)
            }
        } else if (refreshEvent.refreshEvents.contains(RefreshData.PUMP_STATUS)) {
            scheduleNextRefreshWithSpecifiedTime(PumpDataRefreshType.PumpStatus, System.currentTimeMillis())
            if (!commandQueue.statusInQueue()) {
                commandQueue.readStatus("Status Refresh (UI)", null)
            }
        }
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
        aapsLogger.info(LTag.PUMP, logPrefix + "refreshDataFull")
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
    //     // TODOX
    //
    //
    //
    //
    //     if (value.value()) {
    //         value.set(
    //             pumpStatus.tandemPumpFirmware.isClosedLoopPossible,
    //             rh.gs(R.string.tandem_fol_closed_loop_not_allowed_x2), // TODOX allowed Closed Loop on right firmware
    //             this
    //         )
    //     }
    //     return value
    // }


    // override fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
    //     // TODOX enable SMB
    //     // value.set(false)
    //     // return value
    //
    //     if (pumpStatus.pumpDriverMode==PumpDriverMode.Demo) {
    //         value.set(true)
    //         return value
    //     }
    //
    //     if (value.value()) {
    //         value.set(
    //             //aapsLogger,
    //             true,  //driverMode == PumpDriverMode.Automatic, // TODOX allowed SMB on right firmware
    //             rh.gs(R.string.tandem_constraint_SMB),
    //             this
    //         )
    //     }
    //     return value
    // }


    override fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        val maxBasalBySettings = tandemPumpUtil.getIntPreferenceOrDefault(TandemIntPreferenceKey.MaxBasal)

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

        val maxBasalBySettings = tandemPumpUtil.getIntPreferenceOrDefault(TandemIntPreferenceKey.MaxBasal)

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
        //aapsLogger.debug(LTag.PUMP, "DUB isSuspended - $suspended")
        return suspended
    }

    override fun isConnected(): Boolean {
        val status = tandemPumpUtil.driverStatus
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
        val status = tandemPumpUtil.driverStatus
        val error = tandemPumpUtil.errorType

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
        val status = tandemPumpUtil.driverStatus
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
        if (displayConnectionMessages) aapsLogger.error(LTag.PUMP, logPrefix + "isBusy")
        if (busy || tandemPumpUtil.preventConnect) {
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

        //val refreshTypesNeededToReschedule: MutableSet<PumpDataRefreshType> = HashSet()

        //var resetTime = false
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
        val currentTimeMillis = System.currentTimeMillis()

        aapsLogger.error("RefreshCheck: $statusRefresh  (currentTime=$currentTimeMillis)")

        for ((key, value) in statusRefresh) {
            //aapsLogger.error("RefreshCheck: $key = $value  (currentTime=$currentTimeMillis)")
            if (value!! > 0 && currentTimeMillis > value) {
                var resetTime = false
                when (key) {
                    // PumpDataRefreshType.PumpHistory      -> {
                    //     aapsLogger.info(LTag.PUMP, "Refresh_PumpHistory")
                    //     readPumpHistory()
                    // }

                    PumpDataRefreshType.PumpTime         -> {
                        aapsLogger.info(LTag.PUMP, "Refresh_PumpTime")
                        pumpConnectionManager.getTime()
                        if (checkTimeAndOptionallySetTime(readTime = true)) {
                            resetDisplay = true
                        }
                        resetTime = true
                        //refreshTypesNeededToReschedule.add(key)
                        //resetTime = true
                    }

                    PumpDataRefreshType.GetTemporaryBasal -> {
                        // TODO GetTemporaryBasal refresh after stop/start of pump
                        aapsLogger.error(LTag.PUMP, "Refresh_GetTemporaryBasal: NOT IMPLEMENTED.")

                    }

                    PumpDataRefreshType.BatteryStatus -> {
                        aapsLogger.error(LTag.PUMP, "Refresh_BatteryStatus");
                        pumpConnectionManager.getBatteryLevel()
                        rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Battery))
                        //refreshTypesNeededToReschedule.add(key)
                        resetTime = true
                    }

                    PumpDataRefreshType.PumpStatus -> {
                        getFullPumpStatus(readHistory = true)
                        resetTime = true
                    }

                    // this is for simple status refresh (only PumpStatus without Alerts/Alarms or history
                    PumpDataRefreshType.Custom_1 -> {
                        aapsLogger.info(LTag.PUMP, "Refresh_Custom_1 (simple pump status after UI)")
                        pumpConnectionManager.getPumpStatus()
                        rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.PumpStatus))
                    }

                    PumpDataRefreshType.RemainingInsulin -> {
                        aapsLogger.info(LTag.PUMP, "Refresh_RemainingInsulin")
                        pumpConnectionManager.getRemainingInsulin()
                        rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Reservoir))

                        //resetDisplay = true
                        resetTime = true
                    }

                    else -> {
                        aapsLogger.error(LTag.PUMP, "Refresh Unsupported action: ${key}");
                    }



                } // when

                //scheduleNextRefresh(key!!)

                if (resetTime) {
                    scheduleNextRefresh(key!!)
                }


            } // if

            // we always schedule refresh

        }

        // we always reset time
        pumpStatus.setLastCommunicationToNow()

        return resetDisplay
    }

    private fun getFullPumpStatus(readHistory: Boolean) {
        aapsLogger.info(LTag.PUMP, "Refresh_PumpStatus")
        pumpConnectionManager.getPumpStatus()

        // TODO do all relevant notifications
        pumpConnectionManager.executeCustomCommand(TandemCustomCommand.GET_ALARMS)
        pumpConnectionManager.executeCustomCommand(TandemCustomCommand.GET_ALERTS)
        if (pumpStatus.semaphoreNeedsRefresh) {
            rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Custom_2))
        }

        if (readHistory) {
            historyRetriever.downloadHistory()
        }
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
        getFullPumpStatus(readHistory = true)  // TODO change this
        scheduleNextRefresh(PumpDataRefreshType.PumpStatus, 0)
        rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Configuration))

        // remaining insulin (>50 = 4h; 50-20 = 1h; 15m) -
        pumpConnectionManager.getRemainingInsulin()
        scheduleNextRefresh(PumpDataRefreshType.RemainingInsulin, 1)
        rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Reservoir))

        // remaining power (1h) -
        pumpConnectionManager.getBatteryLevel()
        scheduleNextRefresh(PumpDataRefreshType.BatteryStatus, 2)
        rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Battery))

        //testModeCode();

        // configuration (once and then if history shows config changes)
        pumpConnectionManager.getConfiguration()
        checkThatSettingsAreEnforced()

        // pump info
        pumpConnectionManager.executeCustomCommand(TandemCustomCommand.GET_PUMP_INFO)

        // get TBR (if needed)
        if (pumpStatus.pumpStatusMirror==null || pumpStatus.pumpStatusMirror!!.isTemporaryBasalRunning()) {
            pumpConnectionManager.getTemporaryBasal()
            rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.TBR))
        }

        // get last bolus
        pumpConnectionManager.getBolus()
        rxBus.send(EventPumpFragmentValuesChanged(PumpUpdateFragmentType.Bolus))

        // get basal profile
        pumpConnectionManager.getBasalProfile()

        pumpStatus.setLastCommunicationToNow()
        setRefreshButtonEnabled(true)

        if (!isRefresh) {
            pumpState = PumpDriverState.Initialized
        }

        isDriverInitialized = true  // means that first data was read
        firstRun = false

        return true
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

            aapsLogger.debug(TAG, "AAPS Profile:     $profileAsString")
            aapsLogger.debug(TAG, "Pump Profile:     $profileDriver")

            val areTheySame = profileAsString.equals(profileDriver)

            aapsLogger.debug(TAG, "Pump Profile is the same: $areTheySame")

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
            val maxBolusRequired = tandemPumpUtil.getIntPreferenceOrDefault(TandemIntPreferenceKey.MaxBolus)

            maxBolus /= 1000

            aapsLogger.debug(TAG, "Current Max Bolus: ${maxBolus}, Required: $maxBolusRequired")

            if (maxBolus != maxBolusRequired) {
                pumpConnectionManager.executeCustomCommand(TandemCustomCommand.SET_MAX_BOLUS, maxBolusRequired)
            }

            val maxBasal = (pumpStatus.settings!![TandemPumpSettingType.BASAL_LIMIT]) as Long

            val maxBasalRequired = tandemPumpUtil.getIntPreferenceOrDefault(TandemIntPreferenceKey.MaxBasal)

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
                        Rc.string.pump_cmd_err_bolus_could_not_be_delivered_no_insulin,
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
                    TandemLongNonPreferenceKey.SmbBoluses
                else
                    TandemLongNonPreferenceKey.StandardBoluses)

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
                    .comment(rh.gs(Rc.string.pump_cmd_err_bolus_could_not_be_delivered))
            }
        } finally {
            finishAction("Bolus")
        }
    }

    override fun stopBolusDelivering() {
        //bolusDeliveryType = BolusDeliveryType.CancelDelivery

        if (bolusDeliveryType==BolusDeliveryType.Delivering ||
            bolusDeliveryType==BolusDeliveryType.DeliveryPrepared) {

            bolusDeliveryType = BolusDeliveryType.CancelDelivery // we don't want to come here twice

            // FIXME cancel needs to be done n driver side

            val bolusResponse = pumpConnectionManager.getBolus()

            if (bolusResponse.isSuccess) {



                val bolusData = bolusResponse.value

                if (bolusData==null || bolusData.bolusStatus==BolusStatus.DONE) {
                    aapsLogger.warn(TAG, "Bolus data is either null or BolusStatus is DONE. Bolus is no longer running, so cancel is not possible.")
                    bolusDeliveryType = BolusDeliveryType.Idle
                } else {
                    aapsLogger.info(TAG, "Cancelling Bolus: ")
                    val cancelBolusResponse = pumpConnectionManager.cancelBolus(bolusData)

                    if (cancelBolusResponse.isSuccess) {
                        bolusDeliveryType = BolusDeliveryType.Idle
                    } else {
                        aapsLogger.warn(TAG, "Bolus couldn't be cancelled")
                    }
                }
            } else {
                aapsLogger.warn(TAG, "Bolus response was not received, cancelBolus will be skipped.")
            }

        } else {
            aapsLogger.warn(TAG, "Bolus delivery Type is $bolusDeliveryType, cancelBolus will be skipped.")
        }

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
                if (tandemPumpUtil.isSame(tbrCurrent.insulinRate, percent)) {
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

                incrementStatistics(TandemLongNonPreferenceKey.TbrsSet)

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


    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile,
                                      enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType
    ): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, "TBR setTempBasalAbsolute called with a rate of $absoluteRate for $durationInMinutes min [enforce=$enforceNew,tbrType=${tbrType.name}].")

        val unroundedPercentage = ((absoluteRate / baseBasalRate) * 100).toInt()

        aapsLogger.info(LTag.PUMP, "TBR abs=$absoluteRate,base=${pumpStatus.basalProfileForHour},percent: $unroundedPercentage")

        return this.setTempBasalPercent(unroundedPercentage, durationInMinutes, profile, enforceNew, tbrType)
    }


    private fun finishAction(overviewKey: String?) {
        if (overviewKey != null) rxBus.send(EventRefreshOverview(overviewKey, false))
        triggerUIChange()
        setRefreshButtonEnabled(true)
    }


    private fun readPumpHistoryAfterAction(bolusInfo: DetailedBolusInfo? = null,
                                           tempBasalInfo: TempBasalPair? = null,
                                           profile: Profile? = null) {
        // TODO readPumpHistoryAfterAction
        // if (true)
        //     return
        aapsLogger.warn(LTag.PUMP, logPrefix + "readPumpHistoryAfterAction N/A.")
        // pumpHistoryHandler.getLastEventAndSendItToPumpSync(
        //     bolusInfo = bolusInfo,
        //     tempBasalInfo = tempBasalInfo,
        //     profile = profile
        // )
    }


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
                return instantiator.providePumpEnactResult().success(true).enacted(false)
            }

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
        displayConnectionMessages = false // TODO can be removed in future
    }


    override fun getRefreshTime(pumpDataRefreshType: PumpDataRefreshType): Int {
        return (
            when(pumpDataRefreshType) {
                //PumpDataRefreshType.PumpHistory      -> getHistoryRefreshTime()
                PumpDataRefreshType.RemainingInsulin -> {
                    val remaining = pumpStatus.reservoirRemainingUnits

                    if (remaining > 50) 60 else if (remaining > 20) 30 else 15
                }
                PumpDataRefreshType.BatteryStatus    -> {
                    val power = pumpStatus.batteryRemaining
                    if (power > 30) 55 else if (power > 20) 30 else 15
                }
                PumpDataRefreshType.PumpTime         -> 300
                PumpDataRefreshType.PumpStatus       -> 5
                else                                 -> -1
        })
    }


    override fun isInPreventConnectMode(): Boolean {
        return tandemPumpUtil.preventConnect
    }


    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {
        aapsLogger.warn(LTag.PUMP, logPrefix + "Time or TimeZone changed (type=$timeChangeType). ")
        this.timeChangeType = timeChangeType
        this.hasTimeDateOrTimeZoneChanged = true
        val timeRefresh = System.currentTimeMillis() + (20*1000)  // 20s in future
        scheduleNextRefreshWithSpecifiedTime(PumpDataRefreshType.PumpTime, timeRefresh)
        // TODO timezoneOrDSTChanged: this might not work as expected, needs to be tested again...
    }


    companion object {
        val qeFilterValues = arrayOf<CharSequence>(QualifyingEventsFilter.ALL.name, QualifyingEventsFilter.AAPS_RELEVANT.name)
        val qeRangeValues = arrayOf<CharSequence>(QualifyingEventsRange.LAST_15_ITEMS.name,
                                                  QualifyingEventsRange.LAST_3_HOURS.name,
                                                  QualifyingEventsRange.LAST_6_HOURS.name,
                                                  QualifyingEventsRange.LAST_12_HOURS.name,
                                                  QualifyingEventsRange.LAST_24_HOURS.name)


        // val pumpFreqValues = arrayOf<CharSequence>(RileyLinkTargetFrequency.MedtronicUS.key!!, RileyLinkTargetFrequency.MedtronicWorldWide.key!!)
        // val encodingValues = arrayOf<CharSequence>(RileyLinkEncodingType.FourByteSixByteLocal.key!!, RileyLinkEncodingType.FourByteSixByteRileyLink.key!!)
        // val batteryValues = mutableListOf<CharSequence>().also { list -> BatteryType.entries.forEach { list.add(it.key) } }.toTypedArray()
    }


    // TODO Preferences:
    //    - add MIN_BASAL_ALERT2 confirmation (hardcoded ATM)
    //    - add MIN_RESERVOIR2 confirmation not implemented yet, but might be needed

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        aapsLogger.info(TAG, "addPreferenceScreen: preferenceManager=$preferenceManager, parent=$parent, requiredKey=$requiredKey")
        if (requiredKey != null) return

        // val batteryEntries = mutableListOf<CharSequence>().also { list -> BatteryType.entries.forEach { list.add(rh.gs(it.friendlyName)) } }.toTypedArray()
        // val encodingEntries = arrayOf<CharSequence>(rh.gs(RileyLinkEncodingType.FourByteSixByteLocal.friendlyName!!), rh.gs(RileyLinkEncodingType.FourByteSixByteLocal.friendlyName!!))
        //
        // val pumpTypeEntries = arrayOf<CharSequence>(
        //     "Other (unsupported)",
        //     "512",
        //     "712",
        //     "515",
        //     "715",
        //     "522",
        //     "722",
        //     "523 (Fw 2.4A or lower)",
        //     "723 (Fw 2.4A or lower)",
        //     "554 (EU Fw. <= 2.6A)",
        //     "754 (EU Fw. <= 2.6A)",
        //     "554 (CA Fw. <= 2.7A)",
        //     "754 (CA Fw. <= 2.7A)"
        // )
        //
        // val bolusDelayEntries = arrayOf<CharSequence>("5", "10", "15")

        val qeFilterEntries = arrayOf<CharSequence>(rh.gs(QualifyingEventsFilter.ALL.friendlyName),
                                             rh.gs(QualifyingEventsFilter.AAPS_RELEVANT.friendlyName))
        val qeRangeEntries = arrayOf<CharSequence>(rh.gs(QualifyingEventsRange.LAST_15_ITEMS.friendlyName),
                                                   rh.gs(QualifyingEventsRange.LAST_3_HOURS.friendlyName),
                                                   rh.gs(QualifyingEventsRange.LAST_6_HOURS.friendlyName),
                                                   rh.gs(QualifyingEventsRange.LAST_12_HOURS.friendlyName),
                                                   rh.gs(QualifyingEventsRange.LAST_24_HOURS.friendlyName))


        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "tandem_tmobi_settings"
            title = rh.gs(R.string.tandem_name_mobi)
            initialExpandedChildrenCount = 0

            //
            // <EditTextPreference
            // android:defaultValue="00000000"
            // android:key="@string/key_tandem_serial"
            // android:selectAllOnFocus="true"
            // android:singleLine="false"
            // android:enabled="false"
            // android:shouldDisableView="false"
            // android:title="@string/pump_serial_number"
            // />
            // <!--            validate:customRegexp="@string/eightdigitnumber"-->
            // <!--            validate:testErrorString="@string/error_mustbe8digitnumber"-->
            // <!--            validate:testType="regexp" -->
            //

            // TODO enabled false doesn't work/exist
            addPreference(
                AdaptiveStringPreference(
                    ctx = context,
                    stringKey = TandemStringPreferenceKey.PumpSerial,
                    title = R.string.pump_serial_number
                )
                // singleLine="false", selectAllOnFocus="true"
            )

            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = TandemBooleanPreferenceKey.UseSharedConnection,
                    title = R.string.tandem_cfg_use_shared_connection,
                    summary = R.string.tandem_cfg_use_shared_connection_summary
                )
            )

            addPreference(
                AdaptiveStringPreference(
                    ctx = context,
                    stringKey = TandemStringPreferenceKey.SharedConnectionData,
                    title = R.string.tandem_cfg_shared_connection_data,
                    summary = R.string.tandem_cfg_shared_connection_data_summary
                )
                // singleLine="false", selectAllOnFocus="true"
            )

            // TODO Intent selector doesn't work
            // addPreference(
            //     AdaptiveIntentPreference(
            //         ctx = context,
            //         intentKey = TandemIntentPreferenceKey.PumpAddressSelector,
            //         title = R.string.tandem_pump_configuration,
            //         intent = Intent(context, TandemPumpBLEConfigActivity::class.java)
            //     )
            // )

            //
            // <Preference
            // android:enabled="false"
            // android:key="@string/key_tandem_address"
            // android:summary=""
            // android:title="Tandem Configuration">
            // <intent android:action="app.aaps.pump.tandem.common.ui.TandemPumpBLEConfigActivity" />
            // </Preference>
            //

            addPreference(
                AdaptiveIntPreference(
                    ctx = context,
                    intKey = TandemIntPreferenceKey.MaxBolus,
                    title = R.string.tandem_pump_max_bolus

                )
            )

            addPreference(
                AdaptiveIntPreference(
                    ctx = context,
                    intKey = TandemIntPreferenceKey.MaxBasal,
                    title = R.string.tandem_pump_max_basal
                )
            )

            addPreference(
                AdaptiveListPreference(
                    ctx = context,
                    stringKey = TandemStringPreferenceKey.QualifyingEventsFilterPref,
                    title = R.string.data_qe_filter_description,
                    entries = qeFilterEntries,
                    entryValues = qeFilterValues
                )
            )

            addPreference(
                AdaptiveListPreference(
                    ctx = context,
                    stringKey = TandemStringPreferenceKey.QualifyingEventsRangePref,
                    title = R.string.data_qe_range_description,
                    entries = qeRangeEntries,
                    entryValues = qeRangeValues
                )
            )

            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = TandemBooleanPreferenceKey.DisplayDriverVersion,
                    title = R.string.tandem_cfg_display_driver_version,
                    summary = R.string.tandem_cfg_display_driver_version_summary
                )
            )

            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = TandemBooleanPreferenceKey.ShowCargoOfUnknownEntries,
                    title = R.string.tandem_cfg_show_cargo_of_unknown_logs,
                    summary = R.string.tandem_cfg_show_cargo_of_unknown_logs_summary
                )
            )
        }




        // val batteryEntries = mutableListOf<CharSequence>().also { list -> BatteryType.entries.forEach { list.add(rh.gs(it.friendlyName)) } }.toTypedArray()
        // val encodingEntries = arrayOf<CharSequence>(rh.gs(RileyLinkEncodingType.FourByteSixByteLocal.friendlyName!!), rh.gs(RileyLinkEncodingType.FourByteSixByteLocal.friendlyName!!))
        //
        //
        //
        // val bolusDelayEntries = arrayOf<CharSequence>("5", "10", "15")

            // addPreference(
            //     AdaptiveStringPreference(
            //         ctx = context, stringKey = MedtronicStringPreferenceKey.Serial, title = R.string.medtronic_serial_number,
            //         validatorParams = DefaultEditTextValidator.Parameters(
            //             testType = EditTextValidator.TEST_REGEXP,
            //             customRegexp = rh.gs(R.string.sixdigitnumber),
            //             testErrorString = rh.gs(app.aaps.core.validators.R.string.error_mustbe6digitnumber)
            //         )
            //     )
            // )
            // addPreference(
            //     AdaptiveListPreference(
            //         ctx = context,
            //         stringKey = MedtronicStringPreferenceKey.PumpType,
            //         title = R.string.medtronic_pump_type,
            //         entries = pumpTypeEntries,
            //         entryValues = pumpTypeEntries
            //     )
            // )
            // addPreference(
            //     AdaptiveListPreference(
            //         ctx = context,
            //         stringKey = MedtronicStringPreferenceKey.PumpFrequency,
            //         title = R.string.medtronic_pump_frequency,
            //         entries = pumpFreqEntries,
            //         entryValues = pumpFreqValues
            //     )
            // )
            // addPreference(AdaptiveIntPreference(ctx = context, intKey = MedtronicIntPreferenceKey.MaxBasal, title = R.string.medtronic_pump_max_basal))
            // addPreference(AdaptiveIntPreference(ctx = context, intKey = MedtronicIntPreferenceKey.MaxBolus, title = R.string.medtronic_pump_max_bolus))
            // addPreference(
            //     AdaptiveListIntPreference(
            //         ctx = context,
            //         intKey = MedtronicIntPreferenceKey.BolusDelay,
            //         title = R.string.medtronic_pump_bolus_delay,
            //         entries = bolusDelayEntries,
            //         entryValues = bolusDelayEntries
            //     )
            // )
            // addPreference(
            //     AdaptiveListPreference(
            //         ctx = context,
            //         stringKey = RileyLinkStringPreferenceKey.Encoding,
            //         title = R.string.medtronic_pump_encoding,
            //         entries = encodingEntries,
            //         entryValues = encodingValues
            //     )
            // )
            // addPreference(
            //     AdaptiveListPreference(
            //         ctx = context,
            //         stringKey = MedtronicStringPreferenceKey.BatteryType,
            //         title = R.string.medtronic_pump_battery_select,
            //         entries = batteryEntries,
            //         entryValues = batteryValues
            //     )
            // )
            // addPreference(
            //     AdaptiveIntentPreference(
            //         ctx = context, intentKey = RileyLinkIntentPreferenceKey.MacAddressSelector, title = app.aaps.pump.common.hw.rileylink.R.string.rileylink_configuration,
            //         intent = Intent(context, RileyLinkBLEConfigActivity::class.java)
            //     )
            // )
            // addPreference(
            //     AdaptiveSwitchPreference(
            //         ctx = context,
            //         booleanKey = RileylinkBooleanPreferenceKey.OrangeUseScanning,
            //         title = app.aaps.pump.common.hw.rileylink.R.string.orange_use_scanning_level,
            //         summary = app.aaps.pump.common.hw.rileylink.R.string.orange_use_scanning_level_summary
            //     )
            // )
            // addPreference(
            //     AdaptiveSwitchPreference(
            //         ctx = context,
            //         booleanKey = RileylinkBooleanPreferenceKey.ShowReportedBatteryLevel,
            //         title = app.aaps.pump.common.hw.rileylink.R.string.riley_link_show_battery_level,
            //         summary = app.aaps.pump.common.hw.rileylink.R.string.riley_link_show_battery_level_summary
            //     )
            // )
            // addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = MedtronicBooleanPreferenceKey.SetNeutralTemp, title = R.string.set_neutral_temps_title, summary = R.string.set_neutral_temps_summary))

    }



}