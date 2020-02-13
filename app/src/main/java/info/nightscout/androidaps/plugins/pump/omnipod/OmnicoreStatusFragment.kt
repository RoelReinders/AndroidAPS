package info.nightscout.androidaps.plugins.pump.omnipod

import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodUpdateGui
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.SP
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.omnicore_status_fragment.*
import org.slf4j.LoggerFactory
import java.util.*
import android.widget.DatePicker
import android.app.DatePickerDialog
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodGetStatus


class OmnicoreStatusFragment : Fragment(){
    private val disposable = CompositeDisposable()

    private val log = LoggerFactory.getLogger(L.PUMP)



    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreate(savedInstanceState)
        if (L.isEnabled(L.PUMP))
            log.debug("Omnicore Status Fragment Create View")
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.omnicore_status_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        omnicore_updatestatusbutton?.setOnClickListener {
            if (L.isEnabled(L.PUMP))
                log.debug("Omnicore Status Button clicked")
            //TODO: Null check
         //   OmnipodPlugin.getPlugin().pdm.getPodStatus()
            RxBus.send( EventOmnipodGetStatus())

        }

        omnicore_setpodtime_button?.setOnClickListener {
            if (L.isEnabled(L.PUMP))
                log.debug("Omnicore reset pod start button clicked")
            resetPodStart()
        }
    }

    @Synchronized
    override fun onResume() {
        if (L.isEnabled(L.PUMP))
            log.debug("Omnicore Status Fragment Resume")
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
            log.debug("Omnicore Status Fragment pause")
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    fun resetPodStart() {
        val context = this.context as Context
        val currentDate = Calendar.getInstance()
        var date = Calendar.getInstance()
        DatePickerDialog(context, object : DatePickerDialog.OnDateSetListener {

            override fun onDateSet(view: DatePicker, year: Int, monthOfYear: Int, dayOfMonth: Int) {
                date.set(year, monthOfYear, dayOfMonth)
                TimePickerDialog(context, TimePickerDialog.OnTimeSetListener { view, hourOfDay, minute ->
                    date.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    date.set(Calendar.MINUTE, minute)
                    val builder = AlertDialog.Builder(context)
                    builder.setTitle(R.string.omnicore_tab_resetpodstart_button)
                    builder.setMessage(String.format(MainApp.gs(R.string.omnicore_tab_resetpodstart_confirmation), DateUtil.dateAndTimeString(date.time)))
                    builder.setPositiveButton(R.string.yes, DialogInterface.OnClickListener { dialog, id ->
                        dialog.dismiss()
                        //ToDo: Null check
                        OmnipodPlugin.getPlugin().pdm.podStartTime=date.timeInMillis
                        RxBus.send(EventOmnipodUpdateGui())

                    })
                    builder.setNegativeButton(R.string.no, DialogInterface.OnClickListener { dialog, id -> dialog.dismiss() })
                    val alert = builder.create()
                    alert.show()

                }, currentDate.get(Calendar.HOUR_OF_DAY), currentDate.get(Calendar.MINUTE), false).show()
            }
        }, currentDate.get(Calendar.YEAR), currentDate.get(Calendar.MONTH), currentDate.get(Calendar.DATE)).show()



    }

    @Synchronized
    private fun updateGui() {
        if (L.isEnabled(L.PUMP))
            log.debug("Omnicore Status Fragment GUI Update")
        val omnicorePump = OmnipodPlugin.getPlugin()

        if (omnicorePump.serialNumber() != "NO POD") {
            omnicorestatus_podid?.text = omnicorePump.serialNumber()
        }
        else {
            omnicorestatus_podid?.text= MainApp.gs(R.string.key_no_pod)
        }
        omnicorestatus_connectionstatus?.text = omnicorePump.pdm.podStatusText
        if (omnicorePump.serialNumber() != "NO POD") {
            val reservoir = omnicorePump.pdm.GetReservoirLevel()
            omnicorestatus_reservoir?.text = if (reservoir > 50)  "> 50U" else reservoir.toString() + "U"
            if (reservoir < SP.getInt(R.string.key_omnicore_alert_res_units, 20)) {
                omnicorestatus_reservoir?.setTextColor(Color.RED)
            }
            val defaultColor = omnicorestatus_connectionstatus?.textColors

            omnicorestatus_podage?.text = String.format(MainApp.gs(R.string.omnicore_tab_expire_time),DateUtil.dateAndTimeRelativeString(omnicorePump.pdm.expirationTime))
            if (omnicorePump.pdm.expirationTime - System.currentTimeMillis() < SP.getInt(R.string.key_omnicore_alert_prior_expire, 8) * 60 * 60 * 1000) {
                omnicorestatus_podage?.setTextColor(Color.RED)
            }
            else {
                omnicorestatus_podage?.setTextColor(defaultColor)
            }

            omnicorestatus_podstarttime?.text = DateUtil.dateAndTimeRelativeString(omnicorePump.pdm.podStartTime)

            omnicorestatus_reservoir_empty?.text =  String.format(MainApp.gs(R.string.omnicore_tab_expire_reservoir),DateUtil.dateAndTimeRelativeString(omnicorePump.pdm.reservoirTime))

            if (omnicorePump.pdm.reservoirTime - System.currentTimeMillis() < SP.getInt(R.string.key_omnicore_alert_prior_expire, 8) * 60 * 60 * 1000) {
                omnicorestatus_reservoir_empty?.setTextColor(Color.RED)
            }
            else {
                omnicorestatus_reservoir_empty?.setTextColor(defaultColor)
            }
            omnicorestatus_podchange?.text = DateUtil.dateAndTimeRelativeString(omnicorePump.pdm.blackoutExpirationTime)
            if (omnicorePump.pdm.blackoutExpirationTime - System.currentTimeMillis() < SP.getInt(R.string.key_omnicore_alert_prior_expire, 8) * 60 * 60 * 1000) {
                omnicorestatus_podchange?.setTextColor(Color.RED)
            }
            else {
                omnicorestatus_podchange?.setTextColor(defaultColor)
            }
        }

        if (omnicorePump.pdm.lastStatusResponse > 0) {
            omnicorestatus_laststatustime?.text = DateUtil.minAgo(omnicorePump.pdm.lastStatusResponse)
        }
        else {
            omnicorestatus_laststatustime?.text = MainApp.gs(R.string.omnicore_tab_last_status_never)
        }    }

}