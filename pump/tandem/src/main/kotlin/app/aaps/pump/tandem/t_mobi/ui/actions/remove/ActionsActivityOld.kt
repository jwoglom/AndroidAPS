package app.aaps.pump.tandem.t_mobi.ui.actions.remove



// import app.aaps.pump.tandem.t_mobi.ui.actions.other.MobileApp
// import com.google.android.gms.common.ConnectionResult
// import com.google.android.gms.common.GoogleApiAvailability
// import com.google.android.gms.common.api.GoogleApiClient
// import com.google.android.gms.wearable.MessageApi
// import com.google.android.gms.wearable.MessageEvent
// import com.google.android.gms.wearable.Node
// import com.google.android.gms.wearable.Wearable
// import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
// import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
// import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
// import com.jwoglom.controlx2.db.historylog.HistoryLogViewModelFactory
// import com.jwoglom.controlx2.presentation.DataStore
// import com.jwoglom.controlx2.presentation.MobileApp
// import com.jwoglom.controlx2.presentation.navigation.Screen
// import com.jwoglom.controlx2.presentation.screens.PumpSetupStage
// import com.jwoglom.controlx2.presentation.screens.sections.messagePairToJson
// import com.jwoglom.controlx2.presentation.screens.sections.verbosePumpMessage
// import com.jwoglom.controlx2.presentation.util.ShouldLogToFile
// import com.jwoglom.controlx2.shared.PumpMessageSerializer
// import com.jwoglom.controlx2.shared.enums.BasalStatus
// import com.jwoglom.controlx2.shared.enums.CGMSessionState
// import com.jwoglom.controlx2.shared.enums.UserMode
// import com.jwoglom.controlx2.shared.util.SendType
// import com.jwoglom.controlx2.shared.util.pumpTimeToLocalTz
// import com.jwoglom.controlx2.shared.util.setupTimber
// import com.jwoglom.controlx2.shared.util.shortTime
// import com.jwoglom.controlx2.shared.util.shortTimeAgo
// import com.jwoglom.controlx2.shared.util.twoDecimalPlaces1000Unit
// import com.jwoglom.controlx2.util.extractPumpSid
// import com.jwoglom.pumpx2.pump.messages.response.control.StartG6SensorSessionResponse
// import com.jwoglom.pumpx2.pump.messages.response.control.StopG6SensorSessionResponse

// var dataStore = DataStore()
// val LocalDataStore = compositionLocalOf { dataStore }
//
// class ActionsActivity : ComponentActivity() {
// //    private lateinit var mApiClient: GoogleApiClient
//
//     private val applicationScope = CoroutineScope(SupervisorJob())
//
//     override fun onCreate(savedInstanceState: Bundle?) {
//         Timber.d("mobile UIActivity onCreate $savedInstanceState")
//         super.onCreate(savedInstanceState)
//         val startDestination = determineStartDestination()
//         Timber.d("startDestination=%s", startDestination)
//
//         setContent {
//             MobileApp(
//                 startDestination = startDestination,
//                 sendMessage = { path, message -> sendMessage(path, message) },
//                 sendPumpCommands = { type, messages -> sendPumpCommands(type, messages) },
//                 // sendServiceBolusRequest = { bolusId, bolusParameters, unitBreakdown, dataSnapshot, timeSinceReset ->
//                 //     sendServiceBolusRequest(
//                 //         bolusId,
//                 //         bolusParameters,
//                 //         unitBreakdown,
//                 //         dataSnapshot,
//                 //         timeSinceReset
//                 //     )
//                 // },
//                 // sendServiceBolusCancel = {
//                 //     sendMessage(
//                 //         "/to-phone/bolus-cancel",
//                 //         "".toByteArray()
//                 //     )
//                 // },
//                 // historyLogViewModel = historyLogViewModel
//             )
//         }
//
//         //reinitializeGoogleApiClient()
//         //checkNotificationPermissions()
//
//
//         startCommServiceWithPreconditions()
//     }
//
//
//
//
//     private val writeCharacteristicFailedCallback: (String) -> Unit = { uuid ->
//         sendMessage("/to-phone/write-characteristic-failed-callback", uuid.toByteArray())
//     }
//
//     override fun onResume() {
//         Timber.i("activity onResume")
//         // if (!mApiClient.isConnected && !mApiClient.isConnecting) {
//         //     mApiClient.connect()
//         // }
//
//         // if (!Prefs(applicationContext).tosAccepted()) {
//         //     Timber.i("BTPermissionsCheck not started because TOS not accepted")
//         // } else if (!Prefs(applicationContext).serviceEnabled()) {
//         //     Timber.i("BTPermissionsCheck not started because service not enabled")
//         // } else {
//         //     startBTPermissionsCheck()
//         // }
//         super.onResume()
//     }
//
//     override fun onStop() {
//         super.onStop()
//         // if (mApiClient.isConnected) {
//         //     mApiClient.disconnect()
//         // }
//     }
//
//     override fun onDestroy() {
//         //mApiClient.unregisterConnectionCallbacks(this)
//         super.onDestroy()
//     }
//
//     private fun startCommServiceWithPreconditions() {
//         // if (!Prefs(applicationContext).tosAccepted()) {
//         //     Timber.i("commService not started because first TOS not accepted")
//         // } else if (!Prefs(applicationContext).serviceEnabled()) {
//         //     Timber.i("commService not started because service not enabled")
//         // } else {
//             startCommService()
// //        }
//     }
//     private fun startCommService() {
//         // Timber.i("starting CommService")
//         // // Start CommService
//         // val intent = Intent(applicationContext, CommService::class.java)
//         //
//         // if (Build.VERSION.SDK_INT >= 26) {
//         //     applicationContext.startForegroundService(intent)
//         // } else {
//         //     applicationContext.startService(intent)
//         // }
//         // applicationContext.bindService(intent, commServiceConnection, BIND_AUTO_CREATE)
//     }
//
//     // private val commServiceConnection = object : ServiceConnection {
//     //     override fun onServiceConnected(name: ComponentName, service: IBinder) {
//     //         //retrieve an instance of the service here from the IBinder returned
//     //         //from the onBind method to communicate with
//     //         Timber.i("CommService onServiceConnected")
//     //     }
//     //
//     //     override fun onServiceDisconnected(name: ComponentName) {
//     //         Timber.i("CommService onServiceDisconnected")
//     //     }
//     // }
//
//     // override fun onConnected(bundle: Bundle?) {
//     //     connectionFailureCount = 0
//     //     Timber.i("mobile onConnected $bundle")
//     //     // sendMessage("/to-wear/connected", "phone_launched".toByteArray())
//     //     // Wearable.MessageApi.addListener(mApiClient, this)
//     // }
//     //
//     // override fun onConnectionSuspended(id: Int) {
//     //     Timber.i("mobile onConnectionSuspended: $id")
//     //     mApiClient.reconnect()
//     // }
//     //
//     // private var connectionFailureCount = 0
//     // override fun onConnectionFailed(result: ConnectionResult) {
//     //     Timber.i("mobile onConnectionFailed: $result connectionFailureCount=$connectionFailureCount")
//     //     if (!result.isSuccess) {
//     //         val apiAvail = GoogleApiAvailability.getInstance()
//     //         connectionFailureCount++
//     //         when (result.errorCode) {
//     //             ConnectionResult.API_UNAVAILABLE -> {
//     //                 AlertDialog.Builder(this)
//     //                     .setMessage(
//     //                         """The 'Wear OS' application is not installed on this device.\n
//     //                         This is required, even if not using a wearable, due to the current implementation of the app which uses these libraries.
//     //                         To resolve this issue, install the 'Wear OS' app from Google Play. This dependency will be removed in a later version.""".trimIndent()
//     //                     )
//     //                     .setPositiveButton("Install") { dialog, which ->
//     //                         openPlayStore("com.google.android.wearable.app")
//     //                     }
//     //                     .show()
//     //                 return
//     //             }
//     //             else -> {
//     //                 if (connectionFailureCount > 3) {
//     //                     if (apiAvail.isUserResolvableError(result.errorCode)) {
//     //                         apiAvail.getErrorDialog(this, result.errorCode, 1000) {
//     //                             exitProcess(0)
//     //                         }?.show();
//     //                     } else {
//     //                         AlertDialog.Builder(this)
//     //                             .setTitle("Error connecting to Google Play Services")
//     //                             .setMessage("$result")
//     //                             .setPositiveButton("OK") { dialog, which -> dialog.cancel() }
//     //                             .show()
//     //                     }
//     //                 } else {
//     //                     reinitializeGoogleApiClient()
//     //                     return
//     //                 }
//     //             }
//     //         }
//     //     }
//     //     mApiClient.reconnect()
//     // }
//
//     private fun sendMessage(path: String, message: ByteArray) {
//         // Timber.i("mobile sendMessage: $path ${String(message)}")
//         // fun inner(node: Node) {
//         //     Wearable.MessageApi.sendMessage(mApiClient, node.id, path, message)
//         //         .setResultCallback { result ->
//         //             if (result.status.isSuccess) {
//         //                 Timber.i("Message sent: ${path} ${String(message)}")
//         //             } else {
//         //                 Timber.e("mobile sendMessage callback: ${result}")
//         //             }
//         //         }
//         // }
//         // if (!path.startsWith("/to-wear")) {
//         //     Wearable.NodeApi.getLocalNode(mApiClient).setResultCallback { nodes ->
//         //         Timber.i("mobile sendMessage local: ${nodes.node}")
//         //         inner(nodes.node)
//         //     }
//         // }
//         // Wearable.NodeApi.getConnectedNodes(mApiClient).setResultCallback { nodes ->
//         //     Timber.i("mobile sendMessage nodes: $nodes")
//         //     nodes.nodes.forEach { node ->
//         //         inner(node)
//         //     }
//         // }
//     }
//
//     private fun sendPumpCommands(type: SendType, msgs: List<Message>) {
//         if (type == SendType.DEBUG_PROMPT) {
//             synchronized (dataStore.debugPromptAwaitingResponses) {
//                 val awaiting = dataStore.debugPromptAwaitingResponses.value ?: mutableSetOf()
//                 awaiting.addAll(msgs.map { it.responseClass.name })
//                 dataStore.debugPromptAwaitingResponses.value = awaiting
//                 Timber.d("added %s to debugPromptAwaitingResponses = %s", msgs, dataStore.debugPromptAwaitingResponses.value)
//             }
//         }
//         sendMessage("/to-pump/${type.slug}", PumpMessageSerializer.toBulkBytes(msgs))
//     }
//
//
//
//     // Message received from Wear or CommService
//     // override fun onMessageReceived(messageEvent: MessageEvent) {
//     //     Timber.i("phone messageReceived: ${messageEvent.path}: ${String(messageEvent.data)}")
//     //     when (messageEvent.path) {
//     //         "/to-phone/start-comm" -> {
//     //             when (String(messageEvent.data)) {
//     //                 // "skip_notif_permission" -> {
//     //                 //     startBTPermissionsCheck()
//     //                 //     startCommServiceWithPreconditions()
//     //                 //     dataStore.pumpSetupStage.value =
//     //                 //         dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.WAITING_PUMPX2_INIT)
//     //                 // }
//     //                 // else -> {
//     //                 //     requestNotificationCallback = { isGranted ->
//     //                 //         if (isGranted) {
//     //                 //             startBTPermissionsCheck()
//     //                 //             startCommServiceWithPreconditions()
//     //                 //             dataStore.pumpSetupStage.value =
//     //                 //                 dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.WAITING_PUMPX2_INIT)
//     //                 //         } else {
//     //                 //             dataStore.pumpSetupStage.value =
//     //                 //                 dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PERMISSIONS_NOT_GRANTED)
//     //                 //         }
//     //                 //     }
//     //                 //     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//     //                 //         requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
//     //                 //     }
//     //                 // }
//     //             }
//     //         }
//     //
//     //         "/to-phone/comm-started" -> {
//     //             dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_SEARCHING_FOR_PUMP)
//     //         }
//     //
//     //
//     //
//     //
//     //         // "/from-pump/pump-discovered" -> {
//     //         //     dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_DISCOVERED)
//     //         //     dataStore.setupDeviceName.value = String(messageEvent.data)
//     //         //     extractPumpSid(String(messageEvent.data))?.let {
//     //         //         dataStore.pumpSid.value = it
//     //         //     }
//     //         // }
//     //         //
//     //         // "/from-pump/pump-model" -> {
//     //         //     dataStore.setupDeviceModel.value = String(messageEvent.data)
//     //         //     dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_MODEL_METADATA)
//     //         // }
//     //         //
//     //         // "/from-pump/initial-pump-connection" -> {
//     //         //     dataStore.setupDeviceName.value = String(messageEvent.data)
//     //         //     extractPumpSid(String(messageEvent.data))?.let {
//     //         //         dataStore.pumpSid.value = it
//     //         //     }
//     //         //     dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_INITIAL_PUMP_CONNECTION)
//     //         // }
//     //         //
//     //         // "/from-pump/entered-pairing-code" -> {
//     //         //     dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_SENDING_PAIRING_CODE)
//     //         // }
//     //         //
//     //         // "/from-pump/missing-pairing-code" -> {
//     //         //     dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE)
//     //         // }
//     //         //
//     //         // "/from-pump/invalid-pairing-code" -> {
//     //         //     Timber.w("invalid-pairing-code with code: ${PumpState.getPairingCode(applicationContext)}")
//     //         //     PumpState.setPairingCode(applicationContext, "")
//     //         //     dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_INVALID_PAIRING_CODE)
//     //         //     sendMessage("/to-phone/stop-comm", "invalid_pairing_code".toByteArray())
//     //         // }
//     //         //
//     //         // "/from-pump/pump-critical-error" -> {
//     //         //     Timber.w("pump-critical-error: ${String(messageEvent.data)}")
//     //         //     dataStore.pumpCriticalError.value = Pair(String(messageEvent.data), Instant.now())
//     //         // }
//     //         //
//     //         // "/from-pump/pump-connected" -> {
//     //         //     dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_CONNECTED)
//     //         //     dataStore.setupDeviceName.value = String(messageEvent.data)
//     //         //     extractPumpSid(String(messageEvent.data))?.let {
//     //         //         dataStore.pumpSid.value = it
//     //         //     }
//     //         //     dataStore.pumpConnected.value = true
//     //         //     dataStore.pumpLastConnectionTimestamp.value = Instant.now()
//     //         // }
//     //         //
//     //         // // on explicit disconnection
//     //         // "/from-pump/pump-disconnected" -> {
//     //         //     dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_DISCONNECTED)
//     //         //     dataStore.setupDeviceModel.value = String(messageEvent.data)
//     //         //     dataStore.pumpConnected.value = false
//     //         // }
//     //         //
//     //         // // on implicit disconnection (i.e. we didn't get the explicit disconnect)
//     //         // "/from-pump/pump-not-connected" -> {
//     //         //     if (dataStore.pumpConnected.value == true) {
//     //         //         Timber.i("tracked implicit disconnection (before pump-disconnected)")
//     //         //         dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_DISCONNECTED)
//     //         //         dataStore.pumpConnected.value = false
//     //         //     }
//     //         // }
//     //
//     //         "/from-pump/receive-message" -> {
//     //             val pumpMessage = PumpMessageSerializer.fromBytes(messageEvent.data)
//     //             onPumpMessageReceived(pumpMessage, false)
//     //             dataStore.pumpLastMessageTimestamp.value = Instant.now()
//     //         }
//     //         "/from-pump/receive-cached-message" -> {
//     //             val pumpMessage = PumpMessageSerializer.fromBytes(messageEvent.data)
//     //             onPumpMessageReceived(pumpMessage, true)
//     //             dataStore.pumpLastMessageTimestamp.value = Instant.now()
//     //         }
//     //
//     //         "/from-pump/debug-message-cache" -> {
//     //             val processed = PumpMessageSerializer.fromDebugMessageCacheBytes(messageEvent.data)
//     //             dataStore.debugMessageCache.value = processed
//     //         }
//     //
//     //         // "/from-pump/debug-historylog-cache" -> {
//     //         //     val processed = PumpMessageSerializer.fromDebugHistoryLogCacheBytes(messageEvent.data)
//     //         //     var mp = dataStore.historyLogCache.value
//     //         //     if (mp == null) {
//     //         //          mp = mutableMapOf()
//     //         //     }
//     //         //     mp.putAll(processed)
//     //         //     dataStore.historyLogCache.value = mp
//     //         // }
//     //     }
//     // }
//
//     private fun onPumpMessageReceived(message: Message, cached: Boolean) {
//         if (dataStore.debugPromptAwaitingResponses.value?.contains(message.javaClass.name) == true) {
//             synchronized(dataStore.debugPromptAwaitingResponses) {
//                 val awaiting = dataStore.debugPromptAwaitingResponses.value ?: mutableSetOf()
//                 awaiting.remove(message.javaClass.name)
//                 dataStore.debugPromptAwaitingResponses.value = awaiting
//                 Timber.d("removed %s from debugPromptAwaitingResponses = %s", message.javaClass.name, dataStore.debugPromptAwaitingResponses.value)
//             }
//             fun setClipboard(str: String) {
//                 val clipboard: ClipboardManager =
//                     getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
//                 val clip = ClipData.newPlainText(str, str)
//                 clipboard.setPrimaryClip(clip)
//             }
//             //     val verboseStr = verbosePumpMessage(message)
//             //     AlertDialog.Builder(this)
//             //         .setMessage(verboseStr)
//             //         .setNeutralButton("Copy JSON") { dialog, which ->
//             //             setClipboard(messagePairToJson(Pair(message, Instant.now())))
//             //         }
//             //         .setNegativeButton("Copy") { dialog, which ->
//             //             setClipboard(verboseStr)
//             //         }
//             //         .setPositiveButton("OK") { dialog, which -> dialog.cancel() }
//             //         .show()
//         }
//         if (NotificationBundle.isNotificationResponse(message)) {
//             // returns an instance of itself: ensures that watchers get the updated values
//             if (dataStore.notificationBundle.value == null) {
//                 dataStore.notificationBundle.value = NotificationBundle()
//             }
//             dataStore.notificationBundle.value = dataStore.notificationBundle.value?.add(message);
//         }
//         when (message) {
//             is CurrentBatteryAbstractResponse              -> {
//                 dataStore.batteryPercent.value = message.batteryPercent
//             }
//
//             is ControlIQIOBResponse                        -> {
//                 dataStore.iobUnits.value = InsulinUnit.from1000To1(message.pumpDisplayedIOB)
//             }
//             // is ControlIQInfoAbstractResponse -> {
//             //     dataStore.controlIQMode.value = when (message.currentUserModeType) {
//             //         ControlIQInfoAbstractResponse.UserModeType.STANDARD -> UserMode.NONE
//             //         ControlIQInfoAbstractResponse.UserModeType.SLEEP -> UserMode.SLEEP
//             //         ControlIQInfoAbstractResponse.UserModeType.EXERCISE -> UserMode.EXERCISE
//             //         else -> UserMode.UNKNOWN
//             //     }
//             // }
//             // is InsulinStatusResponse -> {
//             //     dataStore.cartridgeRemainingUnits.value = message.currentInsulinAmount
//             // }
//             // is LastBolusStatusAbstractResponse -> {
//             //     dataStore.lastBolusStatus.value = "${twoDecimalPlaces1000Unit(message.deliveredVolume)}u at ${shortTime(pumpTimeToLocalTz(message.timestampInstant))}"
//             //     dataStore.lastBolusStatusResponse.value = message
//             // }
//             is HomeScreenMirrorResponse                    -> {
//                 // dataStore.controlIQStatus.value = when (message.apControlStateIcon) {
//                 //     HomeScreenMirrorResponse.ApControlStateIcon.STATE_GRAY -> "On"
//                 //     HomeScreenMirrorResponse.ApControlStateIcon.STATE_GRAY_RED_BIQ_CIQ_BASAL_SUSPENDED -> "Suspended"
//                 //     HomeScreenMirrorResponse.ApControlStateIcon.STATE_GRAY_BLUE_CIQ_INCREASE_BASAL -> "Increase"
//                 //     HomeScreenMirrorResponse.ApControlStateIcon.STATE_GRAY_ORANGE_CIQ_ATTENUATION_BASAL -> "Reduced"
//                 //     else -> "Off"
//                 // }
//                 // dataStore.cgmStatusText.value = when (message.cgmAlertIcon) {
//                 //     HomeScreenMirrorResponse.CGMAlertIcon.STARTUP_1, HomeScreenMirrorResponse.CGMAlertIcon.STARTUP_2, HomeScreenMirrorResponse.CGMAlertIcon.STARTUP_3, HomeScreenMirrorResponse.CGMAlertIcon.STARTUP_4 -> "Starting up"
//                 //     HomeScreenMirrorResponse.CGMAlertIcon.CALIBRATE, HomeScreenMirrorResponse.CGMAlertIcon.STARTUP_CALIBRATE, HomeScreenMirrorResponse.CGMAlertIcon.CHECKMARK_BLOOD_DROP -> "Calibration Needed"
//                 //     HomeScreenMirrorResponse.CGMAlertIcon.ERROR_HIGH_WEDGE, HomeScreenMirrorResponse.CGMAlertIcon.ERROR_LOW_WEDGE -> "Error"
//                 //     HomeScreenMirrorResponse.CGMAlertIcon.REPLACE_SENSOR -> "Replace Sensor"
//                 //     HomeScreenMirrorResponse.CGMAlertIcon.REPLACE_TRANSMITTER -> "Replace Transmitter"
//                 //     HomeScreenMirrorResponse.CGMAlertIcon.OUT_OF_RANGE -> "Out Of Range"
//                 //     HomeScreenMirrorResponse.CGMAlertIcon.FAILED_SENSOR -> "Sensor Failed"
//                 //     HomeScreenMirrorResponse.CGMAlertIcon.TRIPLE_DASHES -> "---"
//                 //     else -> ""
//                 // }
//                 // dataStore.cgmHighLowState.value = when (message.cgmAlertIcon) {
//                 //     HomeScreenMirrorResponse.CGMAlertIcon.LOW -> "LOW"
//                 //     HomeScreenMirrorResponse.CGMAlertIcon.HIGH -> "HIGH"
//                 //     else -> "IN_RANGE"
//                 // }
//                 // dataStore.cgmDeltaArrow.value = message.cgmTrendIcon.arrow()
//                 dataStore.basalStatus.value = when (message.basalStatusIcon) {
//                     HomeScreenMirrorResponse.BasalStatusIcon.BASAL                 -> BasalStatus.ON
//                     HomeScreenMirrorResponse.BasalStatusIcon.ZERO_BASAL            -> BasalStatus.ZERO
//                     HomeScreenMirrorResponse.BasalStatusIcon.TEMP_RATE             -> BasalStatus.TEMP_RATE
//                     HomeScreenMirrorResponse.BasalStatusIcon.ZERO_TEMP_RATE        -> BasalStatus.ZERO_TEMP_RATE
//                     HomeScreenMirrorResponse.BasalStatusIcon.SUSPEND               -> BasalStatus.PUMP_SUSPENDED
//                     HomeScreenMirrorResponse.BasalStatusIcon.HYPO_SUSPEND_BASAL_IQ -> BasalStatus.BASALIQ_SUSPENDED
//                     HomeScreenMirrorResponse.BasalStatusIcon.INCREASE_BASAL        -> BasalStatus.CONTROLIQ_INCREASED
//                     HomeScreenMirrorResponse.BasalStatusIcon.ATTENUATED_BASAL      -> BasalStatus.CONTROLIQ_REDUCED
//                     else                                                           -> BasalStatus.UNKNOWN
//                 }
//                 // dataStore.cartridgeRemainingEstimate.value = message.remainingInsulinPlusIcon
//             }
//             // is CurrentBasalStatusResponse -> {
//             //     dataStore.basalRate.value = "${twoDecimalPlaces1000Unit(message.currentBasalRate)}u"
//             // }
//             // is TempRateResponse -> {
//             //     dataStore.tempRateActive.value = message.active
//             //     dataStore.tempRateDetails.value = message
//             // }
//             // is CGMStatusResponse -> {
//             //     dataStore.cgmSessionState.value = when (message.sessionState) {
//             //         CGMStatusResponse.SessionState.SESSION_ACTIVE -> CGMSessionState.ACTIVE
//             //         CGMStatusResponse.SessionState.SESSION_STOPPED -> CGMSessionState.STOPPED
//             //         CGMStatusResponse.SessionState.SESSION_START_PENDING -> CGMSessionState.STARTING
//             //         CGMStatusResponse.SessionState.SESSION_STOP_PENDING -> CGMSessionState.STOPPING
//             //         else -> null
//             //     }
//             //     dataStore.cgmSessionExpireRelative.value = when (message.sessionState) {
//             //         CGMStatusResponse.SessionState.SESSION_ACTIVE -> shortTimeAgo(
//             //                 pumpTimeToLocalTz(message.sensorStartedTimestampInstant)
//             //                     .plus(10, ChronoUnit.DAYS),
//             //             suffix = "left")
//             //         else -> ""
//             //     }
//             //     dataStore.cgmSessionExpireExact.value = when (message.sessionState) {
//             //         CGMStatusResponse.SessionState.SESSION_ACTIVE -> shortTime(
//             //             pumpTimeToLocalTz(message.sensorStartedTimestampInstant)
//             //                 .plus(10, ChronoUnit.DAYS))
//             //         else -> ""
//             //     }
//             //     dataStore.cgmTransmitterStatus.value = when (message.transmitterBatteryStatus) {
//             //         CGMStatusResponse.TransmitterBatteryStatus.ERROR -> "Error"
//             //         CGMStatusResponse.TransmitterBatteryStatus.EXPIRED -> "Expired"
//             //         CGMStatusResponse.TransmitterBatteryStatus.OK -> "OK"
//             //         CGMStatusResponse.TransmitterBatteryStatus.OUT_OF_RANGE -> "OOR"
//             //         else -> "Unknown"
//             //     }
//             // }
//             // is CurrentEGVGuiDataResponse -> {
//             //     dataStore.cgmReading.value = message.cgmReading
//             //     dataStore.cgmDelta.value = message.trendRate
//             // }
//             // is GetSavedG7PairingCodeResponse -> {
//             //     dataStore.savedG7PairingCode.value = message.pairingCode
//             // }
//             // is BolusCalcDataSnapshotResponse -> {
//             //     if (!cached) {
//             //         dataStore.bolusCalcDataSnapshot.value = message
//             //     }
//             // }
//             // is LastBGResponse -> {
//             //     dataStore.bolusCalcLastBG.value = message
//             // }
//             // is GlobalMaxBolusSettingsResponse -> {
//             //     dataStore.maxBolusAmount.value = message.maxBolus
//             // }
//             // is HistoryLogStatusResponse -> {
//             //     dataStore.historyLogStatus.value = message
//             // }
//             // is BolusPermissionResponse -> {
//             //     dataStore.bolusPermissionResponse.value = message
//             // }
//             // is RemoteCarbEntryResponse -> {
//             //     dataStore.bolusCarbEntryResponse.value = message
//             // }
//             is InitiateBolusResponse                       -> {
//                 dataStore.bolusInitiateResponse.value = message
//             }
//
//             is CancelBolusResponse                         -> {
//                 if (dataStore.bolusCancelResponse.value == null || message.wasCancelled()) {
//                     dataStore.bolusCancelResponse.value = message
//                 } else {
//                     Timber.w("skipping population of bolusCancelResponse: $message because a successful cancellation already existed in the state: ${dataStore.bolusCancelResponse.value}");
//                 }
//             }
//
//             is CurrentBolusStatusResponse                  -> {
//                 dataStore.bolusCurrentResponse.value = message
//             }
//
//             is TimeSinceResetResponse                      -> {
//                 dataStore.timeSinceResetResponse.value = message
//             }
//
//             is EnterChangeCartridgeModeStateStreamResponse -> {
//                 dataStore.enterChangeCartridgeState.value = message
//             }
//
//             is DetectingCartridgeStateStreamResponse       -> {
//                 dataStore.detectingCartridgeState.value = message
//             }
//
//             is FillTubingStateStreamResponse               -> {
//                 dataStore.fillTubingState.value = message
//             }
//
//             is ExitFillTubingModeStateStreamResponse       -> {
//                 dataStore.exitFillTubingState.value = message
//             }
//
//             is FillCannulaStateStreamResponse              -> {
//                 dataStore.fillCannulaState.value = message
//             }
//
//             is PumpingStateStreamResponse                  -> {
//                 dataStore.pumpingState.value = message
//             }
//
//             is EnterChangeCartridgeModeResponse            -> {
//                 if (!message.isStatusOK) {
//                     unsuccessfulAlert(message.messageName())
//                 } else {
//                     dataStore.inChangeCartridgeMode.value = true
//                 }
//             }
//
//             is ExitChangeCartridgeModeResponse             -> {
//                 if (message.status != 0) {
//                     unsuccessfulAlert(message.messageName())
//                 } else {
//                     dataStore.inChangeCartridgeMode.value = false
//                 }
//             }
//
//             is EnterFillTubingModeResponse                 -> {
//                 if (!message.isStatusOK) {
//                     unsuccessfulAlert(message.messageName())
//                 } else {
//                     dataStore.inFillTubingMode.value = true
//                 }
//             }
//
//             is ExitFillTubingModeResponse                  -> {
//                 if (!message.isStatusOK) {
//                     unsuccessfulAlert(message.messageName())
//                 } else {
//                     dataStore.inFillTubingMode.value = false
//                 }
//             }
//             // error handlers
//             is StatusMessage                               -> {
//                 if (!message.isStatusOK) unsuccessfulAlert(message.messageName())
//             }
//         }
//     }
//
//
//     fun unsuccessfulAlert(req: String) {
//         AlertDialog.Builder(this@ActionsActivity)
//             .setTitle("Failed Pump Request")
//             .setMessage("$req was not successful. The pump returned an error fulfilling the request.")
//             .setPositiveButton("OK", null)
//             .show()
//     }
//
//     /**
//      * BT permissions
//      */
//
//
//
//     fun determineStartDestination(): String {
//         return when {
//             // !Prefs(applicationContext).tosAccepted() -> Screen.FirstLaunch.route
//             // !Prefs(applicationContext).pumpSetupComplete() -> Screen.PumpSetup.route
//             // !Prefs(applicationContext).appSetupComplete() -> Screen.AppSetup.route
//             else -> Screen.Landing.route
//         }
//     }
//
// }
