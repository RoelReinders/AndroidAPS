package info.nightscout.androidaps.plugins.pump.omnipod

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodUpdateGui
import info.nightscout.androidaps.plugins.pump.omnipod.utils.OmniCoreStats
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import kotlinx.android.synthetic.main.omnicore_stats_fragment.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import org.slf4j.LoggerFactory
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog


class OmnicoreStatsFragment: Fragment() {
    private val disposable = CompositeDisposable()

    private val log = LoggerFactory.getLogger(L.PUMP)



    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreate(savedInstanceState)
        if (L.isEnabled(L.PUMP))
            log.debug("Omnicore Stats Fragment Create View")
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.omnicore_stats_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        omnicore_resetstatsbutton?.setOnClickListener {
            if (L.isEnabled(L.PUMP))
                log.debug("Omnicore reset")
            resetStats()
        }
    }

    @Synchronized
    override fun onResume() {
        if (L.isEnabled(L.PUMP))
            log.debug("Omnicore Stats Fragment Resume")
        super.onResume()
        disposable.add(RxBus
                .toObservable(EventOmnipodUpdateGui::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateGui() }, { FabricPrivacy.logException(it) })
        )
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        if (L.isEnabled(L.PUMP))
            log.debug("Omnicore Stats Fragment pause")
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    private fun resetStats() {
        val context = this.context as Context
        val builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.omnicore_tab_reset_stats_button)
        builder.setMessage(R.string.omnicore_tab_reset_stats_confirmation)
        builder.setPositiveButton(R.string.yes, DialogInterface.OnClickListener { dialog, id ->
            dialog.dismiss()
            //TODO:Null Check
            OmnipodPlugin.getPlugin().pdm.pdmStats.resetStats()
        })
        builder.setNegativeButton(R.string.no, DialogInterface.OnClickListener { dialog, id -> dialog.dismiss() })
        val alert = builder.create()
        alert.show()
        updateGui()
    }

    @Synchronized
    private fun updateGui() {
        if (L.isEnabled(L.PUMP))
            log.debug("Omnicore Stats Fragment updateGUI")
        //TODO: null check
        val omnicorePump = OmnipodPlugin.getPlugin()
        val stats =   omnicorePump.pdm.pdmStats

        var tmpdata = stats.getStat(OmniCoreStats.OmnicoreStatType.STARTDATE)

        if (tmpdata > 0) {
            omnicore_stats_start?.visibility = View.VISIBLE
            omnicore_stats_start_label?.text = OmniCoreStats.OmnicoreStatType.STARTDATE.description
            omnicore_stats_start_data?.text = DateUtil.dateAndTimeString(tmpdata)
        }
        else {
            omnicore_stats_start?.visibility = View.GONE
        }

        tmpdata = stats.getStat(OmniCoreStats.OmnicoreStatType.ENDDATE)
        if (tmpdata > 0) {
            omnicore_stats_end?.visibility = View.VISIBLE
            omnicore_stats_end_label?.text = OmniCoreStats.OmnicoreStatType.ENDDATE.description
            omnicore_stats_end_data?.text = DateUtil.dateAndTimeString(tmpdata)
        }
        else {
            omnicore_stats_end?.visibility = View.GONE
        }

        tmpdata = stats.getStat(OmniCoreStats.OmnicoreStatType.POD)
        if (tmpdata > 0) {
            omnicore_stats_pods?.visibility = View.VISIBLE
            omnicore_stats_pods_label?.text = OmniCoreStats.OmnicoreStatType.POD.description
            omnicore_stats_pods_data?.text = tmpdata.toString()
        }
        else {
            omnicore_stats_pods?.visibility = View.GONE
        }

        tmpdata = stats.getStat(OmniCoreStats.OmnicoreStatType.TOTALCOMMANDS)
        if (tmpdata > 0) {
            omnicore_stats_commands?.visibility = View.VISIBLE
            omnicore_stats_commands_label?.text = OmniCoreStats.OmnicoreStatType.TOTALCOMMANDS.description
            omnicore_stats_commands_data?.text = tmpdata.toString()
        }
        else {
            omnicore_stats_commands?.visibility = View.GONE
        }

        tmpdata = stats.getStat(OmniCoreStats.OmnicoreStatType.COMMANDFAIL)
        if (tmpdata > 0) {
            omnicore_stats_failures?.visibility = View.VISIBLE
            omnicore_stats_failures_label?.text = OmniCoreStats.OmnicoreStatType.COMMANDFAIL.description
            omnicore_stats_failures_data?.text = tmpdata.toString()
        }
        else {
            omnicore_stats_failures?.visibility = View.GONE
        }

        tmpdata = stats.getStat(OmniCoreStats.OmnicoreStatType.TOTALTIME)
        if (tmpdata > 0) {
            omnicore_stats_totaltime?.visibility = View.VISIBLE
            omnicore_stats_totaltime_label?.text = OmniCoreStats.OmnicoreStatType.TOTALTIME.description
            omnicore_stats_totaltime_data?.text = stats.getDurationAsString(OmniCoreStats.OmnicoreStatType.TOTALTIME)
        }
        else {
            omnicore_stats_totaltime?.visibility = View.GONE
        }

        tmpdata = stats.getStat(OmniCoreStats.OmnicoreStatType.TBRTOTAL)
        if (tmpdata > 0) {
            omnicore_stats_tbr?.visibility = View.VISIBLE
            omnicore_stats_tbr_label?.text = OmniCoreStats.OmnicoreStatType.TBRTOTAL.description
            omnicore_stats_tbr_data?.text = tmpdata.toString()
        }
        else {
            omnicore_stats_tbr?.visibility = View.GONE
        }

        tmpdata = stats.getStat(OmniCoreStats.OmnicoreStatType.TBRFAIL)
        if (tmpdata > 0) {
            omnicore_stats_tbrfail?.visibility = View.VISIBLE
            omnicore_stats_tbrfail_label?.text = OmniCoreStats.OmnicoreStatType.TBRFAIL.description
            omnicore_stats_tbrfail_data?.text = tmpdata.toString()
        }
        else {
            omnicore_stats_tbrfail?.visibility = View.GONE
        }

        tmpdata = stats.getStat(OmniCoreStats.OmnicoreStatType.TBRTIME)
        if (tmpdata > 0) {
            omnicore_stats_tbrtime?.visibility = View.VISIBLE
            omnicore_stats_tbrtime_label?.text = OmniCoreStats.OmnicoreStatType.TBRTIME.description
            omnicore_stats_tbrtime_data?.text = stats.getDurationAsString(OmniCoreStats.OmnicoreStatType.TBRTIME)
        }
        else {
            omnicore_stats_tbrtime?.visibility = View.GONE
        }

        tmpdata = stats.getStat(OmniCoreStats.OmnicoreStatType.BOLUS)
        if (tmpdata > 0) {
            omnicore_stats_bolus?.visibility = View.VISIBLE
            omnicore_stats_bolus_label?.text = OmniCoreStats.OmnicoreStatType.BOLUS.description
            omnicore_stats_bolus_data?.text = tmpdata.toString()
        }
        else {
            omnicore_stats_bolus?.visibility = View.GONE
        }

        tmpdata = stats.getStat(OmniCoreStats.OmnicoreStatType.BOLUSSMB)
        if (tmpdata > 0) {
            omnicore_stats_bolussmb?.visibility = View.VISIBLE
            omnicore_stats_bolussmb_label?.text = OmniCoreStats.OmnicoreStatType.BOLUSSMB.description
            omnicore_stats_bolussmb_data?.text = tmpdata.toString()
        }
        else {
            omnicore_stats_bolussmb.visibility = View.GONE
        }

        tmpdata = stats.getStat(OmniCoreStats.OmnicoreStatType.BOLUSFAIL)
        if (tmpdata > 0) {
            omnicore_stats_bolusfail?.visibility = View.VISIBLE
            omnicore_stats_bolusfail_label?.text = OmniCoreStats.OmnicoreStatType.BOLUSFAIL.description
            omnicore_stats_bolusfail_data?.text = tmpdata.toString()
        }
        else {
            omnicore_stats_bolusfail?.visibility = View.GONE
        }

        tmpdata = stats.getStat(OmniCoreStats.OmnicoreStatType.BOLUSTIME)
        if (tmpdata > 0) {
            omnicore_stats_bolustime?.visibility = View.VISIBLE
            omnicore_stats_bolustime_label?.text = OmniCoreStats.OmnicoreStatType.BOLUSTIME.description
            omnicore_stats_bolustime_data?.text = stats.getDurationAsString(OmniCoreStats.OmnicoreStatType.BOLUSTIME)
        }
        else {
            omnicore_stats_bolustime?.visibility = View.GONE
        }

        tmpdata = stats.getStat(OmniCoreStats.OmnicoreStatType.PROFILESET)
        if (tmpdata > 0) {
            omnicore_stats_profile?.visibility = View.VISIBLE
            omnicore_stats_profile_label?.text = OmniCoreStats.OmnicoreStatType.PROFILESET.description
            omnicore_stats_profile_data?.text = tmpdata.toString()
        }
        else {
            omnicore_stats_profile?.visibility = View.GONE
        }

        tmpdata = stats.getStat(OmniCoreStats.OmnicoreStatType.PROFILESETTIME)
        if (tmpdata > 0) {
            omnicore_stats_profiletime?.visibility = View.VISIBLE
            omnicore_stats_profiletime_label?.text = OmniCoreStats.OmnicoreStatType.PROFILESETTIME.description
            omnicore_stats_profiletime_data?.text = stats.getDurationAsString(OmniCoreStats.OmnicoreStatType.PROFILESETTIME)
        }
        else {
            omnicore_stats_profiletime?.visibility = View.GONE
        }
      /*
        var statsOut = ""
        for (key in stats.keys) {
            var value = ""
            if (statsOut.length > 0) {
                statsOut += "\n"
            }
            if (key == OmniCoreStats.OmnicoreStatType.STARTDATE
                    || key == OmniCoreStats.OmnicoreStatType.ENDDATE) {
                value = DateUtil.dateAndTimeString(stats.getStat(key))
            }
            else if (key == OmniCoreStats.OmnicoreStatType.TOTALTIME
                    || key == OmniCoreStats.OmnicoreStatType.BOLUSTIME
                    || key == OmniCoreStats.OmnicoreStatType.PROFILESETTIME
                    || key == OmniCoreStats.OmnicoreStatType.TBRTIME) {
                value = stats.getDurationAsString(key)
            }
            else {
                value = stats.getStat(key).toString()
            }
            statsOut += key.description + ": \t" + value

        }
        omnicorestatus_stats?.text = statsOut;
*/
    }

}