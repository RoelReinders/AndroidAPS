package info.nightscout.androidaps.plugins.pump.omnipod;

import android.content.Context;
import android.os.SystemClock;

import com.squareup.otto.Subscribe;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventNetworkChange;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.omnipod.api.rest.OmniCoreResult;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodUpdateGui;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;


import org.json.JSONException;

import java.math.BigDecimal;
import java.util.List;


public class OmnipodPlugin extends PluginBase implements PumpInterface {
    private final Logger log = LoggerFactory.getLogger(L.PUMP);


    private static OmnipodPlugin instance = null;
    private final PumpDescription pumpDescription = new PumpDescription();
    private final OmnipodPdm _pdm;
    private DetailedBolusInfo _runningBolusInfo;

    public static OmnipodPlugin getPlugin() {
        if (instance == null)
            instance = new OmnipodPlugin();
        return instance;
    }
    public OmnipodPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.PUMP)
                //.fragmentClass(OmnipodFragment.class.getName())
                .pluginName(R.string.omnipod)
                .shortName(R.string.omnipod_shortname)
                .preferencesId(R.xml.pref_omnipod)
                .neverVisible(Config.NSCLIENT)
                .description(R.string.omnipod_description)
        );

        // explicitly disable aaps interfering with bluetooth connections
        // as it assumes all pumps connect via bluetooth
        SP.putBoolean(R.string.key_btwatchdog, false);

        pumpDescription.setPumpDescription(PumpType.Omnipy_Omnipod);
        log.debug("OMNIPOD_PLUGIN instantiate");
        Context context = MainApp.instance().getApplicationContext();
        _pdm = new OmnipodPdm(context);
    }

    @Override
    protected void onStart() {
        super.onStart();
        MainApp.bus().register(this);
        log.debug("OMNIPOD_PLUGIN onstart");
        _pdm.OnStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        log.debug("OMNIPOD_PLUGIN onstop");
        _pdm.OnStop();
        MainApp.bus().unregister(this);
    }

    @Subscribe
    public void onStatusEvent(final EventNetworkChange enc) {
        MainApp.bus().post(new EventOmnipodUpdateGui());
    }

    public OmnipodPdm getPdm()
    {
        return _pdm;
    }

    @Override
    public boolean isBotheredByConstantConnectionRequests() { return false; }

    @Override
    public boolean isInitialized() {
        log.debug("OMNIPOD_PLUGIN isinitialized");
        return _pdm.IsInitialized();
    }

    @Override
    public boolean isSuspended() {
        log.debug("OMNIPOD_PLUGIN issuspended");
        return _pdm.IsSuspended();
    }

    @Override
    public boolean isBusy() {
        log.debug("OMNIPOD_PLUGIN isbusy");
        return _pdm.IsBusy();
    }

    @Override
    public boolean isConnected() {
        log.debug("OMNIPOD_PLUGIN isconnected");
        return _pdm.IsConnected();
    }

    @Override
    public boolean isConnecting() {
        log.debug("OMNIPOD_PLUGIN isconnecting");
        return _pdm.IsConnecting();
    }

    @Override
    public boolean isHandshakeInProgress() {
        log.debug("OMNIPOD_PLUGIN ishsinprogress");
        return _pdm.IsHandshakeInProgress();
    }

    @Override
    public void finishHandshaking() {
        log.debug("OMNIPOD_PLUGIN finish_hs");
        _pdm.FinishHandshaking(); }

    @Override
    public void connect(String reason) {
        log.debug("OMNIPOD_PLUGIN connect");
        _pdm.Connect();
        MainApp.bus().post(new EventOmnipodUpdateGui());
    }

    @Override
    public void disconnect(String reason)
    {
        log.debug("OMNIPOD_PLUGIN disconnect");
        _pdm.Disconnect();
    }

    @Override
    public void stopConnecting() {
        log.debug("OMNIPOD_PLUGIN stop_connecting");
        _pdm.StopConnecting();
    }

    @Override
    public void getPumpStatus()
    {
        log.debug("OMNIPOD_PLUGIN get_status");
        _pdm.UpdateStatus();
    }

    private String getCommentString(OmniCoreResult result)
    {
        String comment = "0";
        if (result != null)
        {
            comment = Long.toString(result.ResultDate);
        }
        return comment;
    }

    @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {

        log.debug("OMNIPOD_PLUGIN set new basal profile");
        PumpEnactResult r = new PumpEnactResult();
        r.enacted = false;
        r.success = false;

        final LoopPlugin loopPlugin = LoopPlugin.getPlugin();
        boolean runLoop = false;
        boolean warnUser = false;

        if (isInitialized() && TreatmentsPlugin.getPlugin().isTempBasalInProgress())
        {
            warnUser = true;
        }

        Constraint<Boolean> closedLoopEnabled = MainApp.getConstraintChecker().isClosedLoopAllowed();
        if (loopPlugin.isEnabled(loopPlugin.getType()) && !loopPlugin.isSuspended()
                && !loopPlugin.isDisconnected() && closedLoopEnabled.value()) {
            runLoop = true;
            warnUser = false;
        }


        OmniCoreResult result = _pdm.SetNewBasalProfile(profile);
        if (result != null) {
            r.enacted = result.Success;
            r.success = result.Success;
            if (result.Success) {
                MainApp.bus().post(new EventDismissNotification(Notification.PROFILE_SET_OK));
                MainApp.bus().post(new EventDismissNotification(Notification.PROFILE_SET_FAILED));
                Notification notification = new Notification(Notification.PROFILE_SET_OK, MainApp.gs(R.string.profile_set_ok), Notification.INFO, 60);
                MainApp.bus().post(new EventNewNotification(notification));
                if (warnUser) {
                    MainApp.bus().post(new EventNewNotification(new Notification(Notification.OMNIPY_TEMP_BASAL_CANCELED,
                            "Temporary basal canceled before setting a new basal profile. Please set temporary basal again." , Notification.NORMAL, 60)));
                }
            } else {
                r.comment = getCommentString(result);
                MainApp.bus().post(new EventDismissNotification(Notification.PROFILE_SET_OK));
                MainApp.bus().post(new EventDismissNotification(Notification.PROFILE_SET_FAILED));
                Notification notification = new Notification(Notification.PROFILE_SET_FAILED, "Basal profile not updated", Notification.NORMAL, 60);
                MainApp.bus().post(new EventNewNotification(notification));
                if (warnUser) {
                    MainApp.bus().post(new EventNewNotification(new Notification(Notification.OMNIPY_TEMP_BASAL_CANCELED,
                            "Temporary basal canceled trying to set a new basal profile. Please set temporary basal again." , Notification.NORMAL, 60)));
                }
            }
        }
        else
        {
            if (warnUser) {
                MainApp.bus().post(new EventNewNotification(new Notification(Notification.OMNIPY_TEMP_BASAL_CANCELED,
                        "Temporary basal canceled trying to set a new basal profile. Please set temporary basal again." , Notification.NORMAL, 60)));
            }
        }

        if (runLoop)
        {
            loopPlugin.invoke("OmnipodProfileReset", true);
        }

        return r;
    }

    @Override
    public boolean isThisProfileSet(Profile profile) {
        log.debug("OMNIPOD_PLUGIN is this profile set");
        return _pdm.IsProfileSet(profile);
    }

    @Override
    public long lastDataTime()
    {
        log.debug("OMNIPOD_PLUGIN lastdatatime");

        return _pdm.GetLastUpdated();
    }

    @Override
    public double getBaseBasalRate() {
        log.debug("OMNIPOD_PLUGIN getbasebasalrate");
        return _pdm.GetBaseBasalRate();
    }

    @Override
    public double getReservoirLevel() {
        log.debug("OMNIPOD_PLUGIN get reservoir");

        return _pdm.GetReservoirLevel();
    }

    @Override
    public int getBatteryLevel() {

        log.debug("OMNIPOD_PLUGIN get battery level");

        return _pdm.getBatteryLevel();
    }

    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {

        log.debug("OMNIPOD_PLUGIN deliver treatment");

        PumpEnactResult r = new PumpEnactResult();
        r.enacted = false;
        r.success = false;

        BigDecimal units = _pdm.GetExactInsulinUnits(detailedBolusInfo.insulin);
        OmniCoreResult result = _pdm.Bolus(units);
        if (result != null)
        {
            r.enacted = result.Success;
            r.success = result.Success;
            if (!result.Success) {
                r.bolusDelivered = 0;
                r.comment = getCommentString(result);
            }
            else {
                detailedBolusInfo.deliverAt = result.ResultDate;
                if (detailedBolusInfo.carbTime != 0)
                {
                    TreatmentsPlugin.getPlugin().addToHistoryCarbTreatment(detailedBolusInfo);
                }
                r.carbsDelivered = detailedBolusInfo.carbs;

                _runningBolusInfo = detailedBolusInfo;
                Double delivering = 0.05d;

                while (delivering < detailedBolusInfo.insulin) {
                    EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
                    bolusingEvent.status = String.format(MainApp.gs(R.string.bolusdelivering), delivering);
                    bolusingEvent.percent = Math.min((int) (delivering / detailedBolusInfo.insulin * 100), 100);
                    MainApp.bus().post(bolusingEvent);
                    delivering += 0.05d;
                    SystemClock.sleep(2000);
                }
                SystemClock.sleep(200);
                EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
                bolusingEvent.status = String.format(MainApp.gs(R.string.bolusdelivered), detailedBolusInfo.insulin);
                bolusingEvent.percent = 100;
                MainApp.bus().post(bolusingEvent);
                SystemClock.sleep(1000);
                if (L.isEnabled(L.PUMPCOMM))
                    log.debug("Delivering treatment insulin: " + detailedBolusInfo.insulin + "U carbs: " + detailedBolusInfo.carbs + "g " + result);
                MainApp.bus().post(new EventOmnipodUpdateGui());
            }
        }
        return r;
    }

    @Override
    public void stopBolusDelivering() {
        log.debug("OMNIPOD_PLUGIN stop bolus");

        if (_runningBolusInfo != null) {
            OmniCoreResult result = _pdm.CancelBolus();
            double canceled = -1d;
            if (result.Success) {
                canceled = result.InsulinCanceled;
            }

            EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();

            Double supposedToDeliver = _runningBolusInfo.insulin;
            if (canceled <= 0d) {
                if (bolusingEvent != null)
                    bolusingEvent.status = String.format("Couldn't stop bolus in time, delivered: %f.2u", supposedToDeliver);
            } else {
                if (bolusingEvent != null)
                    bolusingEvent.status = MainApp.gs(R.string.bolusstopped);
                _runningBolusInfo.insulin = supposedToDeliver - canceled;
            }
            if (bolusingEvent != null) {
                MainApp.bus().post(bolusingEvent);
                MainApp.bus().post(new EventOmnipodUpdateGui());
            }
            SystemClock.sleep(100);
            if (canceled > 0d)
                _runningBolusInfo.notes = String.format("Delivery stopped at %f.2u. Original bolus request was: %f.2u", supposedToDeliver - canceled, supposedToDeliver);

            //if (result.Success) {
                //_runningBolusInfo.pumpId = getHistoryId(result);
            //}
            //TreatmentsPlugin.getPlugin().addToHistoryTreatment(_runningBolusInfo, true);

            if (bolusingEvent != null) {
                bolusingEvent.status = MainApp.gs(R.string.bolusstopping);
                MainApp.bus().post(bolusingEvent);
                MainApp.bus().post(new EventOmnipodUpdateGui());
            }
        }
    }

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        log.debug("OMNIPOD_PLUGIN set temp basal");

        PumpEnactResult r = new PumpEnactResult();
        r.enacted = false;
        r.success = false;

        BigDecimal iuRate = _pdm.GetExactInsulinUnits(absoluteRate);
        BigDecimal durationHours = _pdm.GetExactHourUnits(durationInMinutes);
        OmniCoreResult result = _pdm.SetTempBasal(iuRate, durationHours);

        if (result != null) {
            r.enacted = result.Success;
            r.success = result.Success;
            if (result.Success)
            {
                r.absolute = iuRate.doubleValue();
                r.duration = durationHours.multiply(new BigDecimal(60)).intValue();
                r.isPercent = false;

//                TemporaryBasal tempBasal = new TemporaryBasal()
//                        .date(result.ResultDate)
//                        .absolute(r.absolute)
//                        .duration(r.duration)
//                        .pumpId(getHistoryId(result))
//                        .source(Source.USER);
//                TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempBasal);
                if (L.isEnabled(L.PUMPCOMM))
                    log.debug("Setting temp basal absolute: " + result);
            }
            else
            {
                r.comment = getCommentString(result);
            }
        }
        MainApp.bus().post(new EventOmnipodUpdateGui());
        return r;
    }

    @Override
    public PumpEnactResult cancelTempBasal(boolean enforceNew) {
        log.debug("OMNIPOD_PLUGIN cancel temp basal");

        PumpEnactResult r = new PumpEnactResult();
        r.enacted = false;
        r.success = false;

        OmniCoreResult result = _pdm.CancelTempBasal();
        if (result != null) {
            r.enacted = result.Success;
            r.success = result.Success;
            if (result.Success) {
                r.isTempCancel = true;
//                if (TreatmentsPlugin.getPlugin().isTempBasalInProgress()) {
//                    TemporaryBasal tempStop = new TemporaryBasal()
//                            .date(result.ResultDate)
//                            .pumpId(getHistoryId(result))
//                            .source(Source.USER);
//                    TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempStop);
//                    MainApp.bus().post(new EventOmnipodUpdateGui());
//                }
            }
            else
            {
                r.comment = getCommentString(result);
            }
        }
        return r;
    }

    @Override
    public JSONObject getJSONStatus(Profile profile, String profileName) {
        log.debug("OMNIPOD_PLUGIN get json status");

        long now = System.currentTimeMillis();

        JSONObject pump = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            battery.put("percent", getBatteryLevel());
            status.put("status", _pdm.getPodStatusText());
            extended.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            try {
                extended.put("ActiveProfile", profileName);
            } catch (Exception ignored) {
            }
            TemporaryBasal tb = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(now);
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", tb.tempBasalConvertedToAbsolute(now, profile));
                extended.put("TempBasalStart", DateUtil.dateAndTimeString(tb.date));
                extended.put("TempBasalRemaining", tb.getPlannedRemainingMinutes());
            }
            ExtendedBolus eb = TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(now);
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.absoluteRate());
                extended.put("ExtendedBolusStart", DateUtil.dateAndTimeString(eb.date));
                extended.put("ExtendedBolusRemaining", eb.getPlannedRemainingMinutes());
            }
            status.put("timestamp", DateUtil.toISOString(now));

            pump.put("battery", battery);
            pump.put("status", status);
            pump.put("extended", extended);
            pump.put("reservoir", getReservoirLevel());
            pump.put("clock", DateUtil.toISOString(now));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return pump;
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        log.debug("OMNIPOD_PLUGIN set temp basal percent");
        PumpEnactResult per = new PumpEnactResult();
        per.enacted = false;
        per.success = false;
        return per;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        log.debug("OMNIPOD_PLUGIN set ext. bolus");
        PumpEnactResult per = new PumpEnactResult();
        per.enacted = false;
        per.success = false;
        return per;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        log.debug("OMNIPOD_PLUGIN cancel ext bolus");

        PumpEnactResult per = new PumpEnactResult();
        per.enacted = false;
        per.success = false;
        return per;
    }

    @Override
    public String deviceID() {
        log.debug("OMNIPOD_PLUGIN device id");

        log.debug("DeviceID()");
        return _pdm.GetPodId();
    }

    @Override
    public PumpDescription getPumpDescription() {
        //log.debug("OMNIPOD_PLUGIN get pump descr");
        return pumpDescription;
    }

    @Override
    public String shortStatus(boolean veryShort) {
        log.debug("OMNIPOD_PLUGIN short status");

        return _pdm.GetStatusShort();
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        //log.debug("isFaking()");
        return false;
    }

    @Override
    public PumpEnactResult loadTDDs() {
        log.debug("OMNIPOD_PLUGIN loadtdd");

        PumpEnactResult per = new PumpEnactResult();
        per.enacted = false;
        per.success = false;
        return per;
    }

    @Override
    public boolean canHandleDST() {
        log.debug("OMNIPOD_PLUGIN canhandledst");
        return true;
    }

    @Override
    public List<CustomAction> getCustomActions()
    {
        log.debug("OMNIPOD_PLUGIN get custom actions");

        return null;
    }

    @Override
    public void executeCustomAction(CustomActionType customActionType) {
        log.debug("OMNIPOD_PLUGIN exec custom action");

    }
}
