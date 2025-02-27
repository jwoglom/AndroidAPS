package app.aaps.pump.tandem.common.ui.test

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.viewpager2.widget.ViewPager2
import app.aaps.pump.tandem.R
import dagger.android.support.DaggerDialogFragment
import javax.inject.Inject


class TandemWizardDialogFragment @Inject constructor(val context2: Context): DaggerDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(context2)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.tandem_dialog_wizard)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewPager: ViewPager2 = view.findViewById(R.id.viewPager)
        viewPager.adapter = WizardPagerAdapter(this)
    }
}