package app.aaps.pump.tandem.t_mobi.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.pump.tandem.R
import app.aaps.pump.tandem.common.comm.TandemCommunicationManager
import app.aaps.pump.tandem.common.driver.connector.TandemPumpConnector
import app.aaps.pump.tandem.common.ui.DebugActionsActivity
import app.aaps.pump.tandem.common.util.TandemPumpUtil
import app.aaps.pump.tandem.databinding.TandemMobiSettingsBinding
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.User
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

@Deprecated("Replaced with Compose API - Actions Activity")
class TandemMobiSettingsActivity : TranslatedDaggerAppCompatActivity() {

    //@Inject lateinit var fabricPrivacy: FabricPrivacy
    //@Inject lateinit var commandQueue: CommandQueue
    //@Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var context: Context
    //@Inject lateinit var aapsSchedulers: AapsSchedulers

    //@Inject lateinit var tandemMobiManager: TandemMobiManager  // TODO remove
    //@Inject lateinit var tandemCommunicationManager: TandemCommunicationManager
    @Inject lateinit var tandemPumpConnector: TandemPumpConnector

    //@Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var tandemPumpUtil: TandemPumpUtil
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus

    private var disposables: CompositeDisposable = CompositeDisposable()

    private lateinit var binding: TandemMobiSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = TandemMobiSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = rh.gs(R.string.tandem_mobi_management)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)


        binding.buttonPumpInfo.setOnClickListener {
            // val type: PodActivationWizardActivity.Type =
            //     if (podStateManager.activationProgress.isAtLeast(ActivationProgress.PRIME_COMPLETED)) {
            //         PodActivationWizardActivity.Type.SHORT
            //     } else {
            //         PodActivationWizardActivity.Type.LONG
            //     }
            //
            // val intent = Intent(this, DashPodActivationWizardActivity::class.java)
            // intent.putExtra(PodActivationWizardActivity.KEY_TYPE, type)
            // startActivity(intent)
        }

        binding.buttonDeliveryLimit.setOnClickListener {
            //startActivity(Intent(this, DashPodDeactivationWizardActivity::class.java))
        }

        binding.buttonChangeCartridge.setOnClickListener {


            // val tandemChangeFillManager = TandemChangeFillManager(
            //     tandemCommunicationManager = tandemPumpConnector.getCommunicationManager(),
            //     aapsLogger = aapsLogger,
            //     pumpUtil = tandemPumpUtil
            // )
            //
            // val changeCartridgeStateMachine =
            //     ChangeCartridgeStateMachine(tandemChangeFillManager = tandemChangeFillManager,
            //                                 tandemPumpUtil = tandemPumpUtil,
            //                                 aapsLogger = aapsLogger)


            val intent = Intent(this, DebugActionsActivity::class.java)
            intent.putExtra("KEY_STATE_MACHINE", "ChangeCartridgeStateMachine")
            startActivity(intent)

            //startActivity(Intent(context, DebugActionsActivity::class.java))


            // val tandemChangeFillManager = TandemChangeFillManager(
            //     tandemCommunicationManager = tandemPumpConnector.getCommunicationManager(),
            //     aapsLogger = aapsLogger,
            //     pumpUtil = tandemPumpUtil
            // )
            //
            // val changeCartridgeStateMachine =
            //     ChangeCartridgeStateMachine(tandemChangeFillManager = tandemChangeFillManager,
            //                                 tandemPumpUtil = tandemPumpUtil,
            //                                 aapsLogger = aapsLogger)
            //
            // val fragment = DebugActionsFragment(changeCartridgeStateMachine)
            // //fragment.showsDialog
            //
            // fragment.show(getSupportFragmentManager(), "debug_action_dialog");

            //TandemWizardDialogFragment(context2 = context).show(supportFragmentManager, "tandem_wizard")

            // OKDialog.showConfirmation(
            //     this,
            //     rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_management_discard_pod_confirmation),
            //     Thread {
            //         podStateManager.reset()
            //     }
            // )
        }

        binding.buttonFillTubing.setOnClickListener {
            // binding.buttonPlayTestBeep.isEnabled = false
            // binding.buttonPlayTestBeep.setText(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_management_button_playing_test_beep)
            //
            // commandQueue.customCommand(
            //     CommandPlayTestBeep(),
            //     object : Callback() {
            //         override fun run() {
            //             if (!result.success) {
            //                 displayErrorDialog(
            //                     rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_warning),
            //                     rh.gs(
            //                         info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_two_strings_concatenated_by_colon,
            //                         rh.gs(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_error_failed_to_play_test_beep),
            //                         result.comment
            //                     ),
            //                     false
            //                 )
            //             }
            //         }
            //     }
            // )
        }

        binding.buttonFillCannula.setOnClickListener {
            //startActivity(Intent(this, DashPodHistoryActivity::class.java))
        }

        binding.buttonSiteReminder.setOnClickListener {
            //startActivity(Intent(this, DashPodHistoryActivity::class.java))
        }

        binding.buttonPumpStatus.setOnClickListener {
            tandemPumpConnector.getPumpStatus()
        }

        binding.buttonPumpSuspend.setOnClickListener {

        }

        binding.buttonPumpResume.setOnClickListener {

        }

    }

    override fun onResume() {
        super.onResume()
        // disposables += rxBus
        //     .toObservable(EventQueueChanged::class.java)
        //     .observeOn(aapsSchedulers.main)
        //     .subscribe({ refreshButtons() }, fabricPrivacy::logException)

        // refreshButtons()
    }

    override fun onPause() {
        super.onPause()
        disposables.clear()
    }

    private fun refreshButtons() {
        // Only show the discard button to reset a cached unique ID before the unique ID has actually been set
        // Otherwise, users should use the Deactivate Pod Wizard. In case proper deactivation fails,
        // they will get an option to discard the Pod there
        // val discardButtonEnabled =
        //     tandemMobiManager.uniqueId != null &&
        //         tandemMobiManager.activationProgress.isBefore(ActivationProgress.SET_UNIQUE_ID)
        // binding.buttonDiscardPod.visibility = discardButtonEnabled.toVisibility()
        //
        // binding.buttonActivatePod.isEnabled = tandemMobiManager.activationProgress.isBefore(ActivationProgress.COMPLETED)
        // binding.buttonDeactivatePod.isEnabled = tandemMobiManager.bluetoothAddress != null || tandemMobiManager.ltk != null
        //
        // if (tandemMobiManager.activationProgress.isAtLeast(ActivationProgress.PHASE_1_COMPLETED)) {
        //     if (commandQueue.isCustomCommandInQueue(CommandPlayTestBeep::class.java)) {
        //         binding.buttonPlayTestBeep.isEnabled = false
        //         binding.buttonPlayTestBeep.setText(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_management_button_playing_test_beep)
        //     } else {
        //         binding.buttonPlayTestBeep.isEnabled = true
        //         binding.buttonPlayTestBeep.setText(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_management_button_play_test_beep)
        //     }
        // } else {
        //     binding.buttonPlayTestBeep.isEnabled = false
        //     binding.buttonPlayTestBeep.setText(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_management_button_play_test_beep)
        // }
        //
        // if (discardButtonEnabled) {
        //     binding.buttonDiscardPod.isEnabled = true
        // }
    }

    // private fun displayErrorDialog(title: String, message: String, @Suppress("SameParameterValue") withSound: Boolean) {
    //     uiInteraction.runAlarm(message, title, if (withSound) app.aaps.core.ui.R.raw.boluserror else 0)
    // }
}
