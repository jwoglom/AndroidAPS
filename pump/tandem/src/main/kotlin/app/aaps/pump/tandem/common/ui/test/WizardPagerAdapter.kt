package app.aaps.pump.tandem.common.ui.test

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import app.aaps.pump.tandem.R

class WizardPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    private val pages = listOf(PageOneFragment(), PageTwoFragment(), PageThreeFragment())

    override fun getItemCount(): Int = pages.size
    override fun createFragment(position: Int): Fragment = pages[position]
}

class PageOneFragment : Fragment(R.layout.tandem_change_cartridge_1_dialog)
{

}
class PageTwoFragment : Fragment(R.layout.tandem_change_cartridge_1_dialog)
class PageThreeFragment : Fragment(R.layout.tandem_change_cartridge_1_dialog)