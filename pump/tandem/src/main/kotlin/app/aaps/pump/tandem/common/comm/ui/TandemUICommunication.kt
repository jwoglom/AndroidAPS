package app.aaps.pump.tandem.common.comm.ui

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.pump.common.defs.PumpRunningState
import app.aaps.pump.tandem.common.comm.TandemCommunicationManager
import app.aaps.pump.tandem.common.comm.data.CommunicationListener
import app.aaps.pump.tandem.common.comm.history.HistoryRetriever
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.driver.connector.response.HomeScreenMirrorDto
import app.aaps.pump.tandem.common.util.TandemPumpUtil

import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.NotificationBundle
import com.jwoglom.pumpx2.pump.messages.models.StatusMessage
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TempRateRequest
import com.jwoglom.pumpx2.pump.messages.response.control.DismissNotificationResponse
import com.jwoglom.pumpx2.pump.messages.response.control.EnterChangeCartridgeModeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.EnterFillTubingModeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.ExitChangeCartridgeModeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.ExitFillTubingModeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.ResumePumpingResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetTempRateResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.DetectingCartridgeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.EnterChangeCartridgeModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.ExitFillTubingModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillCannulaStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillTubingStateStreamResponse
// import com.jwoglom.pumpx2.pump.messages.response.controlStream.PumpingStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ApiVersionResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HomeScreenMirrorResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TempRateResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.UnknownMobiOpcode20Response
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogStreamResponse
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class TandemUICommunication @Inject constructor (
    var dataStore: TandemUIDataStore,
    var pumpStatus: TandemPumpStatus,
    var context: Context,
    var aapsLogger: AAPSLogger,
    var pumpUtil: TandemPumpUtil
): CommunicationListener {

    var TAG = LTag.PUMPCOMM

    var tandemCommunicationManager : TandemCommunicationManager? = null
        set(value) {
            if (value==null) {
                if (field!=null) {
                    field!!.communicationListener = null
                }
            }
            field = value
        }

    lateinit var historyRetriever: HistoryRetriever

    @Inject lateinit var uiInteraction: UiInteraction

    var messageCount = 0


    fun sendCommand(request: Message): Boolean {

        aapsLogger.warn(TAG, "Send command $request")

        if (tandemCommunicationManager==null) {
            aapsLogger.error(TAG, "Command ${request.javaClass.name} couldn't be executed because tandemCommunicationManager is null.")
            return false
        }

        if (!tandemCommunicationManager!!.isListenerEnabled()) {
            tandemCommunicationManager!!.communicationListener = this
        }

        if (!tandemCommunicationManager!!.isPumpFullyConnected()) {
            aapsLogger.warn(TAG, "Command ${request::javaClass.name} couldn't be executed because pump is not fully connected yet.")
            return false
        }

        tandemCommunicationManager!!.sendCommandWithListener(request)

        return true

    }


    override fun onReceiveMessage(message: Message) {

        //aapsLogger.error(TAG, "onReceiveMessageListener: $message")

        messageCount++

        if (message is ApiVersionResponse) {
            aapsLogger.debug(TAG, "Got ApiVersionRequest: $message")
            //checkPumpInitMessagesReceived(peripheral)
        } else if (message is TimeSinceResetResponse) {
            aapsLogger.debug(TAG,"Got TimeSinceResetResponse: $message")
            //checkPumpInitMessagesReceived(peripheral)
        }

        aapsLogger.debug(TAG , "TUC: Message received: ${message.javaClass.name}")
        dataStore.pumpLastMessageTimestamp.value = Instant.now()


        if (NotificationBundle.isNotificationResponse(message)) {
            // returns an instance of itself: ensures that watchers get the updated values
            if (dataStore.notificationBundle.value == null) {
                dataStore.notificationBundle.value = NotificationBundle()
            }

            if (pumpUtil.isNotificationNotFiltered(message)) {
                dataStore.notificationBundle.value = dataStore.notificationBundle.value?.add(message)
            }

            return
        }


        when (message) {

            is DismissNotificationResponse -> {
                aapsLogger.info(TAG, "DismissNotificationResponse received: success=${message.isStatusOK}")
            }
            is HomeScreenMirrorResponse -> {
                // dataStore.basalStatus.value = when (message.basalStatusIcon) {
                //     HomeScreenMirrorResponse.BasalStatusIcon.BASAL -> BasalStatus.ON
                //     HomeScreenMirrorResponse.BasalStatusIcon.ZERO_BASAL -> BasalStatus.ZERO
                //     HomeScreenMirrorResponse.BasalStatusIcon.TEMP_RATE -> BasalStatus.TEMP_RATE
                //     HomeScreenMirrorResponse.BasalStatusIcon.ZERO_TEMP_RATE -> BasalStatus.ZERO_TEMP_RATE
                //     HomeScreenMirrorResponse.BasalStatusIcon.SUSPEND -> BasalStatus.PUMP_SUSPENDED
                //     HomeScreenMirrorResponse.BasalStatusIcon.HYPO_SUSPEND_BASAL_IQ -> BasalStatus.BASALIQ_SUSPENDED
                //     HomeScreenMirrorResponse.BasalStatusIcon.INCREASE_BASAL -> BasalStatus.CONTROLIQ_INCREASED
                //     HomeScreenMirrorResponse.BasalStatusIcon.ATTENUATED_BASAL -> BasalStatus.CONTROLIQ_REDUCED
                //     else -> BasalStatus.UNKNOWN
                // }
                dataStore.pumpRunningState.value = if (message.basalStatusIcon == HomeScreenMirrorResponse.BasalStatusIcon.SUSPEND) PumpRunningState.Suspended else PumpRunningState.Running

                pumpStatus.pumpStatusMirror = HomeScreenMirrorDto()
                pumpStatus.pumpStatusMirror!!.parse(message.cargo)
            }
            // is CurrentBasalStatusResponse -> {
            //     dataStore.basalRate.value = "${twoDecimalPlaces1000Unit(message.currentBasalRate)} U"
            // }

            is TempRateResponse -> {
                dataStore.tempRateActive.value = message.active
                dataStore.tempRateDetails.value = message
            }

//             is BolusCalcDataSnapshotResponse -> {
// //                    if (!cached) {
// //                        dataStore.bolusCalcDataSnapshot.value = message
// //                    }
//             }

            // is BolusPermissionResponse -> {
            //     dataStore.bolusPermissionResponse.value = message
            // }
            // is RemoteCarbEntryResponse -> {
            //     dataStore.bolusCarbEntryResponse.value = message
            // }
            // is InitiateBolusResponse -> {
            //     dataStore.bolusInitiateResponse.value = message
            // }
            // is CancelBolusResponse -> {
            //     if (dataStore.bolusCancelResponse.value == null || message.wasCancelled()) {
            //         dataStore.bolusCancelResponse.value = message
            //     } else {
            //         Timber.w("skipping population of bolusCancelResponse: $message because a successful cancellation already existed in the state: ${dataStore.bolusCancelResponse.value}");
            //     }
            // }
            // is CurrentBolusStatusResponse -> {
            //     dataStore.bolusCurrentResponse.value = message
            // }
            is TimeSinceResetResponse -> {
                dataStore.timeSinceResetResponse.value = message
            }
            is EnterChangeCartridgeModeStateStreamResponse -> {
                dataStore.enterChangeCartridgeState.value = message
            }
            is DetectingCartridgeStateStreamResponse -> {
                dataStore.detectingCartridgeState.value = message
            }
            is FillTubingStateStreamResponse -> {
                dataStore.fillTubingState.value = message
            }
            is ExitFillTubingModeStateStreamResponse -> {
                dataStore.exitFillTubingState.value = message
            }
            is FillCannulaStateStreamResponse -> {
                dataStore.fillCannulaState.value = message
            }
            // is PumpingStateStreamResponse -> {
            //     dataStore.pumpingState.value = message
            // }
            is EnterChangeCartridgeModeResponse -> {
                if (!message.isStatusOK) {
                    unsuccessfulAlert(message.messageName())
                } else {
                    dataStore.inChangeCartridgeMode.value = true
                }
            }
            is ExitChangeCartridgeModeResponse -> {
                if (message.status != 0) {
                    unsuccessfulAlert(message.messageName())
                } else {
                    dataStore.inChangeCartridgeMode.value = false
                }
            }
            is EnterFillTubingModeResponse -> {
                if (!message.isStatusOK) {
                    unsuccessfulAlert(message.messageName())
                } else {
                    dataStore.inFillTubingMode.value = true
                }
            }
            is ExitFillTubingModeResponse -> {
                if (!message.isStatusOK) {
                    unsuccessfulAlert(message.messageName())
                } else {
                    dataStore.inFillTubingMode.value = false
                }
            }
            is UnknownMobiOpcode20Response -> {
                aapsLogger.error(TAG, "Received UnknownMobiOpcode20Response ignoring.")
            }
            is SetTempRateResponse -> {
                if (!message.isStatusOK)
                    unsuccessfulAlert(message.messageName())
                else {
                    aapsLogger.debug("Set Temp Rate Sent: ${message.tempRateId}")
                    sendCommand(TempRateRequest()) // for refreshing the screen
                }
            }
            is HistoryLogStatusResponse -> {
                dataStore.historyLogStatus.value = message

                if (::historyRetriever.isInitialized) {
                    historyRetriever.receivedStatus(message)
                }
            }
            is HistoryLogResponse -> {
                if (::historyRetriever.isInitialized) {
                    historyRetriever.receivedLogResponse(message)
                }
            }
            is HistoryLogStreamResponse -> {
                if (::historyRetriever.isInitialized) {
                    historyRetriever.receivedLogStreamResponse(message)
                }
            }
            is ResumePumpingResponse    -> {
                aapsLogger.error(TAG, "ResumePumpingResponse received with status=${message.isStatusOK}")
                if (message.isStatusOK) {
                    //dataStore.basalStatus.value = BasalStatus.ON
                    dataStore.pumpRunningState.value = PumpRunningState.Running
                } else {
                    unsuccessfulAlert(message.messageName())
                }
            }
            is StatusMessage            -> {
                aapsLogger.error(TAG, "Received StatusMessage which is not yet supported: ${message.javaClass.name}, success=${message.isStatusOK}")
                if (!message.isStatusOK) unsuccessfulAlert(message.messageName())
            }
            else -> {
                aapsLogger.error(TAG, "Command processing for ${message.javaClass.name} not yet supported.")
            }
        }

    }

    // TODO(jwoglom): if uncommented, sync with MalfunctionStatusResponse#hasMalfunction()
    // val malfunctionFilterSet: HashSet<String> = hashSetOf(
    //     "17-0x2032"  // LOW INSULIN ALERT2
    // )
    //
    // private fun isNotFiltered(message: Message): Boolean {
    //     if (message is MalfunctionStatusResponse) {
    //         val malfunction : MalfunctionStatusResponse  = message as MalfunctionStatusResponse
    //         return !(malfunctionFilterSet.contains(malfunction.errorString))
    //     } else
    //         return true;
    // }

    private fun unsuccessfulAlert(req: String) {

        aapsLogger.error(TAG,"$req was not successful. The pump returned an error fulfilling the request." )


        uiInteraction.addNotification(
            Notification.TANDEM_PUMP_MESSAGE_ERROR,
            text = "$req was not successful. The pump returned an error fulfilling the request." ,
            level = Notification.URGENT
        )

//        val show = AlertDialog.Builder(context)
//            .setTitle("Failed Pump Request")
//            .setMessage("$req was not successful. The pump returned an error fulfilling the request.")
//            .setPositiveButton("OK", null)
//            .show()
    }



//    }


}