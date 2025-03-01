package app.aaps.pump.tandem.common.ui.change_cartridge

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.pump.tandem.databinding.TandemChangeCartridge1DialogBinding

import dagger.android.support.DaggerDialogFragment
import javax.inject.Inject

class ChangeCartridge1Dialog : DaggerDialogFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var ctx: Context
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var rxBus: RxBus

    private var _binding: TandemChangeCartridge1DialogBinding? = null

    val binding get() = _binding!!

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)

        _binding = TandemChangeCartridge1DialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
        binding.btnOk.setOnClickListener {
            dismiss()
        }
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // tnd_ app:srcCompat

    override fun show(manager: FragmentManager, tag: String?) {
        try {
            manager.beginTransaction().let {
                it.add(this, tag)
                it.commitAllowingStateLoss()
            }
        } catch (e: IllegalStateException) {
            aapsLogger.debug(e.localizedMessage ?: e.toString())
        }
    }
}
