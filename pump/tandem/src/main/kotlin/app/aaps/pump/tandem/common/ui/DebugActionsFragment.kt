package app.aaps.pump.tandem.common.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
// import android.widget.Button
// import androidx.annotation.IdRes
// import androidx.annotation.LayoutRes
// //import androidx.navigation.fragment.findNavController
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.pump.tandem.common.process.ActionStateMachine
import app.aaps.pump.tandem.common.process.ProcessState
import app.aaps.pump.tandem.common.process.UIActionListener
import app.aaps.pump.tandem.databinding.TandemSimpleActionsDialogBinding
import dagger.android.support.DaggerDialogFragment

import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject


class DebugActionsFragment constructor(
                                       val actionStateMachine: ActionStateMachine
                                       ) : DialogFragment(), UIActionListener {

    //@Inject lateinit var aapsLogger: AAPSLogger


    private var _binding: TandemSimpleActionsDialogBinding? = null
    //private var _progressIndicationBinding: EquilPairProgressBinding? = null
    val binding get() = _binding!!
    //private val progressIndicationBinding get() = _progressIndicationBinding!!





    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = TandemSimpleActionsDialogBinding.inflate(inflater, container, false)
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = false
        dialog?.setCanceledOnTouchOutside(false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        // runnable = Runnable {
        //     for (i in 0..19) {
        //         if (pairingEnded) {
        //             activity?.runOnUiThread {
        //                 _binding?.danarsPairingProgressProgressbar?.progress = 100
        //                 _binding?.danarsPairingProgressStatus?.setText(app.aaps.pump.dana.R.string.danars_pairingok)
        //                 handler.postDelayed({ dismiss() }, 1000)
        //             }
        //             return@Runnable
        //         }
        //         _binding?.danarsPairingProgressProgressbar?.progress = i * 5
        //         SystemClock.sleep(1000)
        //     }
        //     activity?.runOnUiThread {
        //         _binding?.danarsPairingProgressProgressbar?.progress = 100
        //         _binding?.danarsPairingProgressStatus?.setText(app.aaps.pump.dana.R.string.danars_pairingtimedout)
        //         _binding?.ok?.visibility = View.VISIBLE
        //     }
        // }
        //setViews()

        this.actionStateMachine.startStateMachine()

    }





    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // binding.fragmentTitle.setText(getTitleId())

        //val nextPage = getNextPageActionId()

        binding.tndBtnLeft.setOnClickListener {
            var buttonText :String? = null
            runOnUiThread{
                buttonText = binding.tndBtnLeft.text.toString()
            }

            this.actionStateMachine.receiveButtonClickEventFromUI(buttonText!!)
        }

        binding.tndBtnRight.setOnClickListener {
            var buttonText :String? = null
            runOnUiThread{
                buttonText = binding.tndBtnRight.text.toString()
            }

            this.actionStateMachine.receiveButtonClickEventFromUI(buttonText!!)
        }


        // if (nextPage == null) {
        //     binding.navButtonsLayout.buttonNext.text = getString(R.string.equil_common_wizard_button_finish)
        //     binding.navButtonsLayout.buttonNext.backgroundTintList =
        //         ColorStateList.valueOf(rh.gc(R.color.equilWizardFinishButtonColor))
        // }

        //updateProgressIndication()

        // binding.navButtonsLayout.buttonNext.setOnClickListener {
        //     if (nextPage == null) {
        //         activity?.finish()
        //     } else {
        //         if (isAdded)
        //             findNavController().navigate(nextPage)
        //     }
        // }


    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun setSectionName(title: String) {
        runOnUiThread {
            this.binding.tndActionTitle.text = title
        }
    }

    override fun setTitle(title: String) {
        runOnUiThread {
            this.binding.tndStepTitle.text = title
        }
    }

    override fun setInstructions(text: String) {
        runOnUiThread {
            this.binding.tndDetails.text = text
        }
    }

    override fun showShortTextStatus(text: String) {
        runOnUiThread {
            this.binding.tndStatus.text = text
        }
    }

    override fun setImage(name: String) {
        // runOnUiThread {
        //     //this.binding.tndDetails.text = text
        // }
    }

    override fun disableButtonsAndReset() {
        runOnUiThread {
            this.binding.tndBtnLeft.text = "Cancel"
            this.binding.tndBtnRight.text = "Continue"
            this.binding.tndBtnLeft.visibility = View.GONE
            this.binding.tndBtnRight.visibility = View.GONE
        }
    }

    override fun enableButton(@StringRes buttonText: Int, rightButton: Boolean) {
        runOnUiThread {
            if (rightButton) {
                this.binding.tndBtnRight.setText(buttonText)
                this.binding.tndBtnRight.isEnabled = true
                this.binding.tndBtnRight.visibility = View.VISIBLE

            } else {
                this.binding.tndBtnLeft.setText(buttonText)
                this.binding.tndBtnLeft.isEnabled = true
                this.binding.tndBtnLeft.visibility = View.VISIBLE

            }
        }
    }

    override fun enableBothButtons(leftButton: Int?, righButton: Int?) {
        runOnUiThread {
            if (righButton!=null) {
                this.binding.tndBtnRight.setText(righButton)
                this.binding.tndBtnRight.visibility = View.VISIBLE

            } else {
                this.binding.tndBtnRight.visibility = View.GONE
            }

            if (leftButton!=null) {
                this.binding.tndBtnLeft.setText(leftButton)
                this.binding.tndBtnLeft.visibility = View.VISIBLE
            } else {
                this.binding.tndBtnLeft.visibility = View.GONE
            }
        }
    }

    override fun closeDialog() {
        runOnUiThread {
            this.dismiss()
            //this.binding.tndDetails.text = text
        }
        TODO("Not yet implemented")
    }

    var stringBuilder = StringBuilder()

    override fun displayLongStatus(text: String) {
        stringBuilder.append(text)
        stringBuilder.append("\n")
        runOnUiThread {
            this.binding.tndDebugArea.text = stringBuilder.toString()
        }
    }

    @JvmField var currentState: ProcessState? = null

    override fun setCurrentState(processState: ProcessState) {
        this.currentState = processState
    }

    // private fun updateProgressIndication() {
    //     (activity as? EquilPairActivity)?.let {
    //         val numberOfSteps = it.getActualNumberOfSteps()
    //
    //         val currentFragment = getIndex() - (it.getTotalDefinedNumberOfSteps() - numberOfSteps)
    //         val progressPercentage = (currentFragment / numberOfSteps.toDouble() * 100).roundToInt()
    //
    //         progressIndicationBinding.progressIndication.progress = progressPercentage
    //     }
    // }

    // @LayoutRes
    // protected abstract fun getLayoutId(): Int
    //
    // @IdRes
    // protected abstract fun getNextPageActionId(): Int?

    // @StringRes
    // protected fun getTitleId(): Int = viewModel.getTitleId()
    //
    // @StringRes protected fun getTextId(): Int = viewModel.getTextId()

    // protected abstract fun getIndex(): Int
    //
    // protected fun showLoading() {
    //     if (activity == null || !isAdded) return
    //     LoadingDlg().show(childFragmentManager, "loading")
    // }
    //
    // protected fun dismissLoading() {
    //     if (activity == null || !isAdded) return
    //     val fragment = childFragmentManager.findFragmentByTag("loading")
    //     if (fragment is LoadingDlg) {
    //         try {
    //             fragment.dismiss()
    //         } catch (e: IllegalStateException) {
    //             // dialog not running yet
    //             aapsLogger.error("Unhandled exception", e)
    //         }
    //     }
    // }



    // override fun getLayoutId(): Int {
    //     return R.layout.equil_pair_air_fragment
    // }
    //
    // override fun getNextPageActionId(): Int {
    //     return R.id.action_startEquilActivationFragment_to_startEquilPairConfirmFragment
    // }
    //
    // override fun getIndex(): Int {
    //     if ((activity as? EquilPairActivity)?.pair == false) {
    //         return 4
    //     }
    //     return 5
    // }


    // override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    //     super.onViewCreated(view, savedInstanceState)
    //     buttonNext = view.findViewById(R.id.button_next)
    //     lytAction = view.findViewById(R.id.lyt_action)
    //     buttonNext.alpha = 0.3f
    //     buttonNext.isClickable = false
    //     view.findViewById<Button>(R.id.button_air).setOnClickListener {
    //         context?.let {
    //             showLoading()
    //             setStep()
    //         }
    //     }
    //     view.findViewById<Button>(R.id.button_finish).setOnClickListener {
    //         context?.let {
    //             showLoading()
    //             if ((activity as? EquilPairActivity)?.pair == true) {
    //                 setAlarmMode()
    //             } else {
    //                 if (equilManager.equilState?.basalSchedule == null) {
    //                     setProfile()
    //                 } else {
    //                     setTime()
    //                 }
    //             }
    //         }
    //     }
    // }

    // private fun setStep() {
    //     commandQueue.customCommand(CmdStepSet(false, EquilConst.EQUIL_STEP_AIR, aapsLogger, sp, equilManager), object : Callback() {
    //         override fun run() {
    //             if (activity == null) return
    //             aapsLogger.debug(LTag.PUMPCOMM, "result====" + result.success)
    //             if (result.success) {
    //                 dismissLoading()
    //             } else {
    //                 dismissLoading()
    //                 equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
    //             }
    //         }
    //     })
    // }
    //
    // private fun setTime() {
    //     showLoading()
    //     commandQueue.customCommand(CmdTimeSet(aapsLogger, sp, equilManager), object : Callback() {
    //         override fun run() {
    //             if (activity == null) return
    //             if (result.success) {
    //                 SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
    //                 dismissLoading()
    //                 readFM()
    //             } else {
    //                 dismissLoading()
    //                 equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
    //             }
    //         }
    //     })
    // }
    //
    // private fun setAlarmMode() {
    //     showLoading()
    //     val mode = preferences.get(EquilIntKey.EquilTone)
    //     commandQueue.customCommand(CmdAlarmSet(mode, aapsLogger, sp, equilManager), object : Callback() {
    //         override fun run() {
    //             if (activity == null) return
    //             if (result.success) {
    //                 SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
    //                 dismissLoading()
    //                 setProfile()
    //             } else {
    //                 dismissLoading()
    //                 equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
    //             }
    //         }
    //     })
    // }
    //
    // private fun readFM() {
    //     commandQueue.customCommand(CmdDevicesGet(aapsLogger, sp, equilManager), object : Callback() {
    //         override fun run() {
    //             if (activity == null) return
    //             aapsLogger.debug(LTag.PUMPCOMM, "CmdGetDevices result====" + result.success)
    //             if (result.success) {
    //                 dismissLoading()
    //                 runOnUiThread {
    //                     // binding.navButtonsLayout.buttonNext.performClick()
    //                     val nextPage = getNextPageActionId()
    //                     findNavController().navigate(nextPage)
    //                 }
    //             } else {
    //                 dismissLoading()
    //                 equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
    //             }
    //         }
    //     })
    // }
    //
}
