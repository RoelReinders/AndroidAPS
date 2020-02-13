package info.nightscout.androidaps.plugins.pump.omnipod;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Intervals;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileIntervals;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.Interval;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.omnipod.api.rest.OmniCoreHistoricalResult;
import info.nightscout.androidaps.plugins.pump.omnipod.api.rest.OmniCoreSetProfileRequest;
import info.nightscout.androidaps.plugins.pump.omnipod.api.rest.OmniCoreBolusRequest;
import info.nightscout.androidaps.plugins.pump.omnipod.api.rest.OmniCoreCancelBolusRequest;
import info.nightscout.androidaps.plugins.pump.omnipod.api.rest.OmniCoreCancelTempBasalRequest;
import info.nightscout.androidaps.plugins.pump.omnipod.api.rest.OmniCoreRequest;
import info.nightscout.androidaps.plugins.pump.omnipod.api.rest.OmniCoreStatusRequest;
import info.nightscout.androidaps.plugins.pump.omnipod.api.rest.OmniCoreTempBasalRequest;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodUpdateGui;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.plugins.pump.omnipod.api.rest.OmniCoreResult;

public class OmnipodPdm {

    private final Context _context;

    private OmniCoreResult _lastResult;
    private Timer _omniCoreTimer;
    private OmniCoreServiceConnection _serviceConnection;

    private final Logger _log;

    public OmnipodPdm(Context context)
    {
        _context = context;
        _log =  LoggerFactory.getLogger(L.PUMP);
        _serviceConnection = new OmniCoreServiceConnection(context);
    }

    public void OnStart() {
        _lastResult = OmniCoreResult.fromJson(SP.getString(R.string.key_omnicore_last_result, null));

        if (_lastResult == null)
        {
            _lastResult = new OmniCoreResult();
            _lastResult.Success = true;
            _lastResult.BasalSchedule = new BigDecimal[48];
            for (int i = 0; i < 48; i++) {
                _lastResult.BasalSchedule[i] = new BigDecimal(0);
            }
            SP.putString(R.string.key_omnicore_last_result, _lastResult.asJson());
        }
    }

    public void OnStop() {
        synchronized (this) {
            _omniCoreTimer.cancel();
        }
    }

    public boolean IsInitialized() {
        return IsConnected();
    }

    public boolean IsSuspended() {
        return !_lastResult.PodRunning;
    }

    public boolean IsBusy() {
        return false;
    }

    public boolean IsConnected() {
        return _serviceConnection.IsConnected();
    }

    public void Connect() {
        _serviceConnection.Connect();
    }

    public boolean IsConnecting() {
        return _serviceConnection.IsConnecting();
    }

    public void StopConnecting() {
    }

    public void FinishHandshaking() { }

    public boolean IsHandshakeInProgress() {
        return false;
    }

    public void Disconnect() {
        _serviceConnection.Disconnect();
    }

    public synchronized OmniCoreResult getResult(OmniCoreRequest request) {

        if (_omniCoreTimer != null)
        {
            _omniCoreTimer.cancel();
            _omniCoreTimer = null;
        }

        OmniCoreResult result = _serviceConnection.GetResult(request);

        if (result != null) {
            if (_lastResult.LastResultDateTime == 0)
                processHistory(result, false);
            else
                processHistory(result, _lastResult.PodRunning);

            if (_lastResult.PodRunning && !result.PodRunning)
            {
                MainApp.bus().post(new EventDismissNotification(Notification.OMNIPY_POD_CHANGE));
                Notification notification = new Notification(Notification.OMNIPY_POD_CHANGE,
                        String.format(MainApp.gs(R.string.omnipod_pod_state_POD_IDs_has_been_removed), _lastResult.PodId), Notification.NORMAL);       //"Pod with Lot %d and Serial %d has been removed."
                MainApp.bus().post(new EventNewNotification(notification));
            }
            else if (!_lastResult.PodRunning && result.PodRunning)
            {
                MainApp.bus().post(new EventDismissNotification(Notification.OMNIPY_POD_STATUS));
                Notification notification = new Notification(Notification.OMNIPY_POD_STATUS, MainApp.gs(R.string.omnipod_pod_state_Pod_is_activated_and_running), Notification.INFO);      //"Pod is activated and running"
                MainApp.bus().post(new EventNewNotification(notification));
            }
            SP.putString(R.string.key_omnicore_last_result, _lastResult.asJson());
            _lastResult = result;
        }

        MainApp.bus().post(new EventOmnipodUpdateGui());

        if (IsConnected()) {
            long delay;
            if (_lastResult.PodRunning)
                delay = 150000;
            else
                delay = 30000;

            _omniCoreTimer = new Timer();
            _omniCoreTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    _omniCoreTimer = null;
                    getResult(new OmniCoreStatusRequest());
                }
            }, delay);
        }

        return result;
    }


    private synchronized void processHistory(OmniCoreResult result, boolean wasRunning) {

        if (result.ResultsToDate == null)
            return;

        new HistoryProcessor(wasRunning).execute(result);
    }

    private long _lastStatusRequest = 0;
    public void UpdateStatus() {
        if (IsConnected() && IsInitialized() && !IsBusy()) {
            long t0 = System.currentTimeMillis();
            if (t0 - _lastStatusRequest > 60000) {
                _lastStatusRequest = t0;
                getResult(new OmniCoreStatusRequest());
            }
        }
    }

    public long GetLastUpdated() {
        return _lastResult.ResultDate;
    }

    public String getPodStatusText()
    {
        if (!_lastResult.PodRunning)
            return MainApp.gs(R.string.omnipod_pod_status_Not_yet_running);
        else
            return MainApp.gs(R.string.omnipod_pod_status_Running);
    }

    public boolean IsProfileSet(Profile profile) {
        TimeZone tz = profile.getTimeZone();
        int offset_minutes = (tz.getRawOffset() + tz.getDSTSavings()) / (60 * 1000);
        if (_lastResult.UtcOffset != offset_minutes)
            return false;

        BigDecimal[] scheduleToVerify = getBasalScheduleFromProfile(profile);
        for(int i=0; i<48; i++)
            if (_lastResult.BasalSchedule[i].compareTo(scheduleToVerify[i]) != 0)
                return false;
        return true;
    }


    public double GetBaseBasalRate() {
        long t = System.currentTimeMillis();
        t += _lastResult.UtcOffset;
        Date dt = new Date(t);
        int h = dt.getHours();
        int m = dt.getMinutes();

        int index = h * 2;
        if (m >= 30)
            index++;

        return _lastResult.BasalSchedule[index].doubleValue();
}

    public OmniCoreResult SetNewBasalProfile(Profile profile) {
        OmniCoreResult result = null;
        if (IsConnected()) {
            TimeZone tz = profile.getTimeZone();
            int offset_minutes = (tz.getRawOffset() + tz.getDSTSavings()) / (60 * 1000);
            BigDecimal[] basalSchedule = getBasalScheduleFromProfile(profile);
            result = getResult(new OmniCoreSetProfileRequest(basalSchedule, offset_minutes));
        }
        return result;
    }

    public OmniCoreResult Bolus(BigDecimal bolusUnits) {
        OmniCoreResult r = null;
        if (IsConnected() && IsInitialized()) {
            r = getResult(new OmniCoreBolusRequest(bolusUnits));
        }
        return r;
    }

    public OmniCoreResult CancelBolus() {
        OmniCoreResult r = null;
        if (IsConnected() && IsInitialized()) {
            r = getResult(new OmniCoreCancelBolusRequest());
        }
        return r;
    }

    public OmniCoreResult SetTempBasal(BigDecimal iuRate, BigDecimal durationHours) {
        OmniCoreResult r = null;
        if (IsConnected() && IsInitialized()) {
            r = getResult(new OmniCoreTempBasalRequest(iuRate, durationHours));
        }
        return r;
    }

    public OmniCoreResult CancelTempBasal() {
        OmniCoreResult result = null;
        if (IsConnected() && IsInitialized()) {
            result = getResult(new OmniCoreCancelTempBasalRequest());
        }
        return result;
    }

    public String GetPodId() {
        if (!_lastResult.PodRunning)
            return "NO POD";
        else
            return _lastResult.PodId;
    }

    public String GetStatusShort() {
        if (_lastResult.PodRunning) {
                return "OK";
        }
        return "NO POD";
    }

    public BigDecimal GetExactInsulinUnits(double iu)
    {
        BigDecimal big20 = new BigDecimal("20");
        // round to 0.05's complements
        return new BigDecimal(iu).multiply(big20).setScale(0, RoundingMode.HALF_UP).setScale(2).divide(big20);
    }

    public BigDecimal GetExactHourUnits(int minutes)
    {
        BigDecimal big30 = new BigDecimal("30");
        return new BigDecimal(minutes).divide(big30).setScale(0, RoundingMode.HALF_UP).setScale(1).divide(new BigDecimal(2));
    }

    public BigDecimal[] getBasalScheduleFromProfile(Profile profile)
    {
        BigDecimal[] basalSchedule = new BigDecimal[48];
        int secondsSinceMidnight = 0;
        for(int i=0; i<48; i++)
        {
            basalSchedule[i] = GetExactInsulinUnits(profile.getBasalTimeFromMidnight(secondsSinceMidnight));
            secondsSinceMidnight += 60*30;
        }
        return basalSchedule;
    }

    public double GetReservoirLevel() {
        return _lastResult.ReservoirLevel;
    }

    public int getBatteryLevel() {
        return _lastResult.BatteryLevel;
    }
}

class HistoryProcessor extends AsyncTask<OmniCoreResult,Void,Void>
{
    class BasalParameters {
        public BigDecimal BasalRate;
        public BigDecimal Duration;
    }

    class BolusParameters {
        public BigDecimal ImmediateUnits;
    }

    class CancelBolusParameters {
        public BigDecimal NotDeliveredInsulin;
    }

    private boolean _podWasRunning;
    public HistoryProcessor(boolean podWasRunning)
    {
        _podWasRunning = podWasRunning;
    }

    private DetailedBolusInfo getBolusInfoFromTreatments(long pumpId, List<Treatment> treatments)
    {
        for (Treatment treatment : treatments) {
            if (treatment.pumpId == pumpId)
            {
                DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                detailedBolusInfo.pumpId = treatment.pumpId;
                detailedBolusInfo.insulin = treatment.insulin;
                detailedBolusInfo.isSMB = treatment.isSMB;
                detailedBolusInfo.date = treatment.date;
                detailedBolusInfo.source = treatment.source;
                return detailedBolusInfo;
            }
        }
        return null;
    }

    private TemporaryBasal getTempBasal(long pumpId, Intervals<TemporaryBasal> tempBasals)
    {
        for (TemporaryBasal tempBasal : tempBasals.getList()) {
            if (tempBasal.pumpId == pumpId)
                return tempBasal;
        }
        return null;
    }


    @Override
    protected Void doInBackground(OmniCoreResult... omniCoreResults) {
        OmniCoreResult result = (OmniCoreResult)omniCoreResults[0];
        TreatmentsPlugin treatmentsPlugin = TreatmentsPlugin.getPlugin();
        List<Treatment> treatments = treatmentsPlugin.getTreatmentsFromHistory();
        Intervals<TemporaryBasal> temporaryBasals = treatmentsPlugin.getTemporaryBasalsFromHistory();
        //ProfileIntervals<ProfileSwitch> profileSwitches = treatmentsPlugin.getProfileSwitchesFromHistory();

        DetailedBolusInfo cancelBolusCandidate = null;
        OmniCoreHistoricalResult cancelBolusHistoricalCandidate = null;

        for (JsonElement historicalResultJson : result.ResultsToDate) {

            OmniCoreHistoricalResult historicalResult = new Gson()
                    .fromJson(historicalResultJson.toString(), OmniCoreHistoricalResult.class);

            switch (historicalResult.Type) {
                case SetBasalSchedule:
                    break;
                case Bolus:
                    cancelBolusHistoricalCandidate = historicalResult;
                    DetailedBolusInfo existingBolusInfo =
                            getBolusInfoFromTreatments(historicalResult.ResultId, treatments);
                    if (existingBolusInfo == null) {
                        BolusParameters p1 = new Gson()
                                .fromJson(historicalResult.Parameters, BolusParameters.class);
                        DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                        detailedBolusInfo.pumpId = historicalResult.ResultId;
                        detailedBolusInfo.insulin = p1.ImmediateUnits.doubleValue();
                        detailedBolusInfo.isSMB = false;
                        detailedBolusInfo.date = historicalResult.ResultDate;
                        detailedBolusInfo.source = Source.PUMP;
                        treatmentsPlugin.addToHistoryTreatment(detailedBolusInfo, true);
                        cancelBolusCandidate = detailedBolusInfo;
                    } else {
                        cancelBolusCandidate = existingBolusInfo;
                    }

                    break;
                case CancelBolus:
                    if (cancelBolusCandidate != null && cancelBolusHistoricalCandidate != null) {
                        BolusParameters canceledBolusParameters = new Gson()
                                .fromJson(cancelBolusHistoricalCandidate.Parameters, BolusParameters.class);

                        CancelBolusParameters p2 = new Gson().fromJson(historicalResult.Parameters, CancelBolusParameters.class);
                        cancelBolusCandidate.insulin = canceledBolusParameters.ImmediateUnits.subtract(p2.NotDeliveredInsulin).doubleValue();
                        treatmentsPlugin.addToHistoryTreatment(cancelBolusCandidate, true);
                    }
                    break;
                case SetTempBasal:
                    TemporaryBasal tempBasalRecorded = getTempBasal(historicalResult.ResultId,
                            temporaryBasals);
                    if (tempBasalRecorded == null) {
                        BasalParameters p3 = new Gson().fromJson(historicalResult.Parameters,
                                BasalParameters.class);

                        double basalRate = p3.BasalRate.doubleValue();
                        int minutes = p3.Duration.multiply(new BigDecimal(60)).intValue();
                        TemporaryBasal tempBasal = new TemporaryBasal()
                                .date(historicalResult.ResultDate)
                                .absolute(basalRate)
                                .duration(minutes)
                                .pumpId(historicalResult.ResultId)
                                .source(Source.PUMP);
                        treatmentsPlugin.addToHistoryTempBasal(tempBasal);
                    }
                    break;
                case CancelTempBasal:
                    TemporaryBasal tempBasalCancelRecorded = getTempBasal(historicalResult.ResultId,
                            temporaryBasals);
                    if (tempBasalCancelRecorded == null) {
                        TemporaryBasal tempStop = new TemporaryBasal()
                                .date(historicalResult.ResultDate)
                                .pumpId(historicalResult.ResultId)
                                .source(Source.PUMP);

                        treatmentsPlugin.addToHistoryTempBasal(tempStop);
                    }
                    break;
                case StartExtendedBolus:
                    break;
                case StopExtendedBolus:
                    break;
                case Status:
                    break;
            }

            TemporaryBasal zeroBasal = new TemporaryBasal()
                    .date(historicalResult.ResultDate)
                    .absolute(0)
                    .duration(24 * 60 * 14)
                    .pumpId(historicalResult.ResultId)
                    .source(Source.PUMP);

            if (!_podWasRunning)
            {
                TemporaryBasal tempBasalAtTime = treatmentsPlugin.getTempBasalFromHistory(historicalResult.ResultDate);
                if (!historicalResult.PodRunning)
                {
                    if (tempBasalAtTime == null)
                    {
                        treatmentsPlugin.addToHistoryTempBasal(zeroBasal);
                    }
                }
                else
                {
                    if (tempBasalAtTime != null)
                    {
                        TemporaryBasal tempBasalCancel = new TemporaryBasal()
                                .date(historicalResult.ResultDate)
                                .pumpId(historicalResult.ResultId)
                                .source(Source.PUMP);
                        treatmentsPlugin.addToHistoryTempBasal(tempBasalCancel);
                    }
                }
            }
            else
            {
                if (!historicalResult.PodRunning)
                {
                    treatmentsPlugin.addToHistoryTempBasal(zeroBasal);
                }
            }
            _podWasRunning = historicalResult.PodRunning;
        }
        return null;
    }
}
