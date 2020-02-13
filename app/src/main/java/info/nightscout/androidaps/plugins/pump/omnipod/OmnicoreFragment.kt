package info.nightscout.androidaps.plugins.pump.omnipod

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodUpdateGui
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.omnicore_fragment.*
import org.slf4j.LoggerFactory
import androidx.viewpager.widget.ViewPager
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.fragment.app.FragmentStatePagerAdapter


class OmnicoreFragment : Fragment() {
    private val disposable = CompositeDisposable()
    private val log = LoggerFactory.getLogger(L.PUMP)


    private val loopHandler = Handler()
    private lateinit var refreshLoop: Runnable

    private var tabLayout: TabLayout? = null
    private var viewPager: ViewPager? = null

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateGui() }
            loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        val view = inflater.inflate(R.layout.omnicore_fragment, container, false)
        tabLayout = view.findViewById(R.id.omnicore_tabs) as TabLayout
        viewPager = view.findViewById(R.id.omnicore_viewpager) as ViewPager
        viewPager!!.setAdapter(TabViewAdapter(childFragmentManager))
        tabLayout!!.post(Runnable {tabLayout!!.setupWithViewPager(viewPager)})
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        omnicore_launchomnicorebutton.setOnClickListener {if (L.isEnabled(L.PUMP))
            log.debug("Omnicore Launch Button clicked")
            OmnipodPlugin.getPlugin().openOmnicore(context,MainApp.gs(R.string.omnicore_package_name));
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable.add(RxBus
                .toObservable(EventOmnipodUpdateGui::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateGui() }, { FabricPrivacy.logException(it) })
        )
        disposable.add(RxBus
                .toObservable(EventTempBasalChange::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateGui() }, { FabricPrivacy.logException(it) })
        )

        loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        loopHandler.removeCallbacks(refreshLoop)
    }

    @Synchronized
    private fun updateGui() {
        if (L.isEnabled(L.PUMP))
            log.debug("Omnicore Fragment GUI Update")
 //Nothing to do
    }

    private inner class TabViewAdapter(fm: FragmentManager?) : FragmentStatePagerAdapter(fm!!, androidx.fragment.app.FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        private val tabs = 3
        private val log = LoggerFactory.getLogger(L.PUMP)


        override fun getItem(position: Int): Fragment {
            if (L.isEnabled(L.PUMP))
                log.debug("Omnicore Fragment tab get Item " + position)


            when (position) {
                0 ->  {
                    if (L.isEnabled(L.PUMP))
                        log.debug("Omnicore Fragment tab Status Tab selected")
                    return OmnicoreStatusFragment()

                }
                1 -> {
                    if (L.isEnabled(L.PUMP))
                        log.debug("Omnicore Fragment tab Command Tab selected")
                    return OmnicoreCommandFragment()


                }
                2 -> {
                    if (L.isEnabled(L.PUMP))
                        log.debug("Omnicore Fragment tab Stats Tab selected")
                    return OmnicoreStatsFragment()

                }
                else -> {
                    if (L.isEnabled(L.PUMP))
                        log.debug("Omnicore Fragment tab Invalid tab selected. Defaulting to first tab")
                    return getItem(0)

                }


            }

        }

        override fun getCount(): Int {
            if (L.isEnabled(L.PUMP))
                log.debug("Omnicore Fragment tab get count")
            return tabs
        }

        override fun getPageTitle(position: Int): CharSequence? {
            when (position) {
                0 -> return MainApp.gs(R.string.omnicore_tabtitle_status)
                1 -> return MainApp.gs(R.string.omnicore_tabtitle_history)
                2 -> return MainApp.gs(R.string.omnicore_tabtitle_stats)
            }
            return null
        }
    }



}
