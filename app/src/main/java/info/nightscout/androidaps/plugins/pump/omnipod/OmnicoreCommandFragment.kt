package info.nightscout.androidaps.plugins.pump.omnipod

import android.graphics.Color
import android.graphics.PorterDuff
import android.opengl.Visibility
import android.os.Bundle
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodUpdateGui
import info.nightscout.androidaps.plugins.pump.omnipod.history.OmniCoreCommandHistory
import info.nightscout.androidaps.plugins.pump.omnipod.history.OmniCoreCommandHistoryItem
import info.nightscout.androidaps.plugins.pump.omnipod.history.OmnicoreCommandHistoryStatus
import info.nightscout.androidaps.plugins.pump.omnipod.utils.OmniCoreStats
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.SP
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.omnicore_command_fragment.*
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt


class OmnicoreCommandFragment : Fragment(){
    private val disposable = CompositeDisposable()
    private val log = LoggerFactory.getLogger(L.PUMP)


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        super.onCreate(savedInstanceState)
        if (L.isEnabled(L.PUMP))
            log.debug("Omnicore Command Fragment Create View")
        return inflater.inflate(R.layout.omnicore_command_fragment, container, false)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (L.isEnabled(L.PUMP))
            log.debug("Omnicore Command Fragment View Created")

//TODO:Null check
        val historyItemAdapter = ListAdapter(OmnipodPlugin.getPlugin().pdm.commandHistory.allHistory)
        val historyItemlayoutManager = LinearLayoutManager(activity);
        historyItemlayoutManager.reverseLayout = true
        historyItemlayoutManager.stackFromEnd = true
        omnicorestatus_history_list.apply{
            layoutManager = historyItemlayoutManager
            // set the custom adapter to the RecyclerView
            adapter = historyItemAdapter
        }
        updateGui()

    }


    @Synchronized
    override fun onResume() {
        if (L.isEnabled(L.PUMP))
            log.debug("Omnicore Command Fragment Resume")
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

        //   loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        if (L.isEnabled(L.PUMP))
            log.debug("Omnicore command Fragment pause")
        super.onPause()
        disposable.clear()
    }


    @Synchronized
    private fun updateGui() {
        if (L.isEnabled(L.PUMP))
            log.debug("Omnicore Command Fragment updateGUI")
        //TODO:Null check
        val omnicorePump = OmnipodPlugin.getPlugin()
        val lastResult = omnicorePump.pdm.commandHistory.lastCommand
        val lastSuccessfulResult = omnicorePump.pdm.commandHistory.lastSuccess
        if (lastResult != null) {
            omnicorestatus_lastcommand?.text = lastResult.request.requestDetails
            omnicorestatus_lastresult?.text = lastResult.status.description + "\n(" + DateUtil.minAgo(lastResult.request.requested) + ")"
        }

        if (lastSuccessfulResult != null) {
            omnicorestatus_lastsuccess_command?.text = lastSuccessfulResult.request.requestDetails + "\n(" + DateUtil.minAgo(lastSuccessfulResult.result.ResultDate) +")"
        }

        omnicorestatus_history_list?.adapter?.notifyDataSetChanged()

    }

    class ListAdapter(private val list: List<OmniCoreCommandHistoryItem>)
        : RecyclerView.Adapter<HistoryItemHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryItemHolder {
            val inflater = LayoutInflater.from(parent.context)
            return HistoryItemHolder(inflater, parent)
        }

        override fun onBindViewHolder(holder: HistoryItemHolder, position: Int) {
            val historyItem: OmniCoreCommandHistoryItem = list[position]
            holder.bind(historyItem)
        }

        override fun getItemCount(): Int = list.size

        fun dataRefreshed() {
            notifyDataSetChanged();
        }

    }

    class HistoryItemHolder(inflater: LayoutInflater, parent: ViewGroup) :
            RecyclerView.ViewHolder(inflater.inflate(R.layout.omnicore_history_item, parent, false)) , View.OnLongClickListener {
        private var commandName: TextView? = null
        private var commandStatus: TextView? = null
        private var commandTime: TextView? = null
        private var commandRunTime: TextView? = null
        private var commandRssiRlVal: TextView? = null
        private var commandRssiPodVal: TextView? = null
        private var commandRssiRlChart: ProgressBar? = null
        private var commandRssiPodChart: ProgressBar? = null
        private var commandRunTimeChart: ProgressBar? = null
        private var commandRunTimeWrapper: LinearLayout? = null




        init {
            commandName = itemView.findViewById(R.id.omnicore_history_command)
            commandStatus = itemView.findViewById(R.id.omnicore_history_status)
            commandTime = itemView.findViewById(R.id.omnicore_history_time)
            commandRunTime = itemView.findViewById(R.id.omnicore_history_runtime)
            commandRssiRlVal = itemView.findViewById(R.id.omnicore_history_rssi_rl_value)
            commandRssiPodVal = itemView.findViewById(R.id.omnicore_history_rssi_pod_value)
            commandRssiRlChart = itemView.findViewById(R.id.omnicore_history_rssi_rl_chart)
            commandRssiPodChart = itemView.findViewById(R.id.omnicore_history_rssi_pod_chart)
            commandRunTimeChart = itemView.findViewById(R.id.omnicore_history_runtime_chart)
            commandRunTimeWrapper = itemView.findViewById(R.id.omnicore_runtime_wrapper)
            itemView.setOnLongClickListener(this)

        }

        override fun onLongClick(view: View): Boolean {
            val position = adapterPosition
            var title = ""
            var message = ""
            if (position >= 0) {
                var historyItem = OmnipodPlugin.getPlugin().pdm.commandHistory.getCommand(position)

                message = OmnipodPlugin.getPlugin().pdm.commandHistory.getCommand(position).toString()
                title = "Command Details: " + historyItem.request.requestDetails
            }
            else {
                message = "Cannot find Command"
                title = "Unknown Command"
            }

            var builder =  AlertDialog.Builder(view.context)
            builder.setTitle(title)
            builder.setIcon(MainApp.getIcon())
            val messageSpanned = SpannableString(message)
            builder.setMessage(messageSpanned)
            builder.setPositiveButton(MainApp.gs(R.string.ok), null)
            val alertDialog = builder.create()
            alertDialog.show()
            //((TextView) alertDialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
            //return true;

//            Toast.makeText(view.context, message, Toast.LENGTH_SHORT).show()
            // Return true to indicate the click was handled
            return true
        }


        fun bind(historyItem: OmniCoreCommandHistoryItem) {
            commandName?.text = historyItem.request.requestDetails
            commandStatus?.text = historyItem.status.description
            val commandStats = OmnipodPlugin.getPlugin().pdm.pdmStats

            when (historyItem.status) {
                OmnicoreCommandHistoryStatus.PENDING -> commandStatus?.setTextColor(Color.YELLOW)
                OmnicoreCommandHistoryStatus.SUCCESS -> commandStatus?.setTextColor(Color.GREEN)
                OmnicoreCommandHistoryStatus.EXECUTED -> commandStatus?.setTextColor(Color.GREEN)
                OmnicoreCommandHistoryStatus.FAILED -> commandStatus?.setTextColor(Color.RED)
            }
            commandTime?.text = DateUtil.dateAndTimeString(historyItem.request.requested)
            commandRunTime?.text = historyItem.runTime.toString() + "ms"

            //Runtime Chart
            if (SP.getBoolean(R.string.key_omnicore_history_show_timing,false)) {
                var timeKey = OmniCoreStats.OmnicoreStatType.TOTALTIME
                var countKey = OmniCoreStats.OmnicoreStatType.TOTALCOMMANDS
                try {
               when (historyItem.request.requestType) {
                    "Bolus" -> {
                        timeKey = OmniCoreStats.OmnicoreStatType.BOLUSTIME
                        countKey =OmniCoreStats.OmnicoreStatType.BOLUS
                    }
                    "CancelBolus" -> {
                        timeKey = OmniCoreStats.OmnicoreStatType.BOLUSTIME
                        countKey = OmniCoreStats.OmnicoreStatType.BOLUS
                    }
                    "CancelTempBasal" -> {
                        timeKey = OmniCoreStats.OmnicoreStatType.TBRTIME
                        countKey = OmniCoreStats.OmnicoreStatType.TBRTOTAL
                    }
                    "SetProfile" -> {
                        timeKey = OmniCoreStats.OmnicoreStatType.PROFILESET
                        countKey = OmniCoreStats.OmnicoreStatType.PROFILESETTIME
                    }
                    "GetStatus" -> {
                        timeKey = OmniCoreStats.OmnicoreStatType.TOTALTIME
                        countKey = OmniCoreStats.OmnicoreStatType.TOTALCOMMANDS
                    }
                    "SetTempBasal" -> {
                        timeKey = OmniCoreStats.OmnicoreStatType.TBRTIME
                        countKey = OmniCoreStats.OmnicoreStatType.TBRTOTAL
                    }
                }
                    val avgRunTime = commandStats.getStat(timeKey) * 1.0 / commandStats.getStat(countKey)
                    val max = avgRunTime * 1.1
                    val reportedTime = minOf(max.toInt(),historyItem.runTime.toInt())
                    commandRunTimeChart?.max = max.roundToInt()
                    commandRunTimeChart?.progress = reportedTime
                    when  {
                        reportedTime/max < 0.85 -> commandRunTimeChart?.progressDrawable?.setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN)
                        reportedTime/max > 0.95 -> commandRunTimeChart?.progressDrawable?.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN)
                        else -> {
                            commandRunTimeChart?.progressDrawable?.setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN)
                        }
                    }
                }
                catch (e: Exception) {
                    commandRunTimeChart?.visibility = View.GONE
                }

            }
            else {
                commandRunTimeChart?.visibility = View.GONE

            }


            val rndRssiRl = historyItem.rssiRl
            val rndRssiPod =historyItem.rssiPod
            commandRssiRlVal?.text = "-" + rndRssiRl.toString()
            commandRssiRlChart?.progress = rndRssiRl
            when {
                rndRssiRl < 70 ->  commandRssiRlChart?.progressDrawable?.setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN)
                rndRssiRl > 90 ->  commandRssiRlChart?.progressDrawable?.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN)
                else -> {
                    commandRssiRlChart?.progressDrawable?.setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN)
                }
            }
            commandRssiPodVal?.text = "-" + rndRssiPod.toString()
            commandRssiPodChart?.progress = rndRssiPod
            when {
                rndRssiPod < 70 ->  commandRssiPodChart?.progressDrawable?.setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN)
                rndRssiPod > 90 ->  commandRssiPodChart?.progressDrawable?.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN)
                else -> {
                    commandRssiPodChart?.progressDrawable?.setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN)
                }
            }
        }
    }
}