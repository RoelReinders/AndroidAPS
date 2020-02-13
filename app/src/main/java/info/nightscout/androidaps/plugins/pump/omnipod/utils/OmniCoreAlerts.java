package info.nightscout.androidaps.plugins.pump.omnipod.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.omnipod.history.OmniCoreCommandHistoryItem;
import info.nightscout.androidaps.plugins.pump.omnipod.history.OmnicoreCommandHistoryStatus;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;

public class OmniCoreAlerts {


    private final Logger _log;

    private int _failedCommands = 0;
//    protected int _failedCommandThreshold = 3;
    private Boolean _failedCommandSentToNS;

    private Boolean[] _changeBlackout = new Boolean[24];

    private long _lastExpirationAlert = 0;
    private long _expirationAlertSnooze = 20 * 60 * 1000;

    private long _lastUrgentAlert = 0;
    private long _urgentAlertSnooze = 10 * 60 * 1000;

    private long _lastInsulinLowAlert = 0;
    private long _insunlinLowAlertSnooze = 60 * 60 * 1000;


    public OmniCoreAlerts() {
        _log =  LoggerFactory.getLogger(L.PUMP);

        if (L.isEnabled(L.PUMP)) {
            _log.debug("OmniCoreAlerts: Creating Change Window");
        }

    }

    private boolean isSnoozing(long lastAlert, long snoozeTime) {

        return (System.currentTimeMillis() < lastAlert + snoozeTime);

    }


    public void processLowInsulinAlert(double reservoir) {
        if (!isSnoozing(_lastInsulinLowAlert,_insunlinLowAlertSnooze)) {
            if (reservoir < SP.getInt(R.string.key_omnicore_alert_res_units,20)) {
                RxBus.INSTANCE.send(new EventDismissNotification(Notification.OMNIPY_POD_STATUS));
                Notification notification = new Notification(Notification.OMNIPY_POD_STATUS, MainApp.gs(R.string.omnipod_pod_alerts_Low_reservoir), Notification.NORMAL);
                RxBus.INSTANCE.send(new EventNewNotification(notification));
                _lastInsulinLowAlert = System.currentTimeMillis();
                _insunlinLowAlertSnooze = SP.getBoolean(R.string.key_omnicore_alert_res_refire,true)?  60 * 60 * 1000 : 10 * 60 * 60  * 1000;
            }
        }
    }

    public void processExpirationAlerts(long reservoirExpiration, long podExpiration) {
        long soonestExpire = Math.min(reservoirExpiration, podExpiration);
        long currentTime = System.currentTimeMillis();
//TODO: I don't like this.
        try {
            //Check Urgent Alarm
            if ((soonestExpire - currentTime) < (SP.getInt(R.string.key_omnicore_alert_urgent_expire,30) * 60 * 1000)) {
                if (!isSnoozing(_lastUrgentAlert,_urgentAlertSnooze)) {
                    RxBus.INSTANCE.send(new EventDismissNotification(Notification.OMNIPY_POD_STATUS));
                    Notification notification = new Notification(Notification.OMNIPY_POD_STATUS, String.format(MainApp.gs(R.string.omnicore_expire_soon),DateUtil.timeString(soonestExpire)), Notification.URGENT);
                    if (SP.getBoolean(R.string.key_omnicore_urgent_audible,false)) {
                        notification.soundId = R.raw.alarm;
                    }
                    RxBus.INSTANCE.send(new EventNewNotification(notification));
                    _lastUrgentAlert = currentTime;
                    _urgentAlertSnooze = SP.getBoolean(R.string.key_omnicore_urgent_refire,true) ? 20 * 60 * 1000 : 10 * 60 * 60  * 1000;
                }
            }
            //Check Standard Alarm
            else {
                //Don't bother if we're snoozing
                if (!isSnoozing(_lastExpirationAlert, _expirationAlertSnooze)) {
                    long soonestExpireWithBlackout = getAdjustedExpirationTime(soonestExpire);
                    Boolean blackoutInEffect = ((soonestExpireWithBlackout < soonestExpire) && (soonestExpireWithBlackout > currentTime));
                    long expirationPriorMS = SP.getInt(R.string.key_omnicore_alert_prior_expire, 8) * 60 * 60 * 1000;
                    String message = reservoirExpiration < podExpiration ? MainApp.gs(R.string.omnicore_reservoir_will_expire) : MainApp.gs(R.string.omnicore_pod_will_expire);
                    message = String.format(message,DateUtil.timeString(soonestExpire));

                    if (blackoutInEffect && (soonestExpireWithBlackout - currentTime < expirationPriorMS)) {
                        message += "\n";
                        message += String.format(MainApp.gs(R.string.omnicore_pod_change_window_ends), DateUtil.timeString(soonestExpireWithBlackout));
                        RxBus.INSTANCE.send(new EventDismissNotification(Notification.OMNIPY_POD_STATUS));
                        Notification notification = new Notification(Notification.OMNIPY_POD_STATUS, message, Notification.URGENT);
                        RxBus.INSTANCE.send(new EventNewNotification(notification));
                        _lastExpirationAlert = currentTime;
                        _expirationAlertSnooze = SP.getBoolean(R.string.key_omnicore_alert_expire_refire,true) ? 60 * 60 * 1000 : 10 * 60 * 60  * 1000;
                    } else if (soonestExpire - currentTime < expirationPriorMS) {
                        RxBus.INSTANCE.send(new EventDismissNotification(Notification.OMNIPY_POD_STATUS));
                        Notification notification = new Notification(Notification.OMNIPY_POD_STATUS, message, Notification.URGENT);
                        RxBus.INSTANCE.send(new EventNewNotification(notification));
                        _lastExpirationAlert = currentTime;
                        _expirationAlertSnooze = SP.getBoolean(R.string.key_omnicore_alert_expire_refire,true) ? 60 * 60 * 1000 : 10 * 60 * 60  * 1000;

                    }
                }
            }

        }
        catch (Exception e) {
            //Couldn't process expiration Alert
            if (L.isEnabled(L.PUMP)) {
                _log.debug("OmniCoreAlerts: exception " + e.getMessage()) ;
            }
        }
    }

    public void processCommandAlerts(OmniCoreCommandHistoryItem hi) {
        try {
            if (hi.getStatus() == OmnicoreCommandHistoryStatus.FAILED) {
                _failedCommands ++;
                if (L.isEnabled(L.PUMP)) {
                    _log.debug("OmniCoreAlerts: " + hi.getRequest().getRequestDetails() + " failed. This is " + _failedCommands + " consecutive failures");
                }
            }
            else if (hi.getStatus() == OmnicoreCommandHistoryStatus.SUCCESS || hi.getStatus() == OmnicoreCommandHistoryStatus.EXECUTED) {
                if (_failedCommands > 0) {
                    RxBus.INSTANCE.send(new EventDismissNotification(Notification.OMNIPY_COMMAND_STATUS));
                }
                _failedCommands = 0;
                _failedCommandSentToNS = false;
            }

            if (_failedCommands >= SP.getInt(R.string.key_omnicore_command_failures_threshold,3)) {
             //   int alertLevel = SP.getInt(R.string.key_omnicore_failure_alerttype,-1);
                int alertLevel = Notification.NORMAL;
                String alertText = String.format(MainApp.gs(R.string.omnipod_command_state_command_failed),  _failedCommands);
                alertText += String.format(MainApp.gs(R.string.omnipod_command_state_lastcommand_failed), hi.getRequest().getRequestDetails());
                if (alertLevel >=0) {
                    Notification notification = new Notification(Notification.OMNIPY_COMMAND_STATUS, alertText, alertLevel);
                    if (SP.getBoolean(R.string.key_omnicore_failure_audible,false)) {
                        notification.soundId = R.raw.alarm;
                    }
                    RxBus.INSTANCE.send(new EventNewNotification(notification));

                }
                //Log Failure to NS

                if (SP.getBoolean(R.string.key_omnicore_log_failures, false) && !_failedCommandSentToNS) {
                    NSUpload.uploadError(alertText);
                    _failedCommandSentToNS = true;
                }
            }

        }
        catch (Exception e) {
            //Couldn't process command history
            if (L.isEnabled(L.PUMP)) {
                _log.debug("OmniCoreAlerts: exception " + e.getMessage()) ;
            }
        }
    }


    public void setupWindows() {
        //This is really ugly.
        _changeBlackout[0] = SP.getBoolean(R.string.key_omnicore_podchange_0,false);
        _changeBlackout[1] = SP.getBoolean(R.string.key_omnicore_podchange_1,false);
        _changeBlackout[2] = SP.getBoolean(R.string.key_omnicore_podchange_2,false);
        _changeBlackout[3] = SP.getBoolean(R.string.key_omnicore_podchange_3,false);
        _changeBlackout[4] = SP.getBoolean(R.string.key_omnicore_podchange_4,false);
        _changeBlackout[5] = SP.getBoolean(R.string.key_omnicore_podchange_5,false);
        _changeBlackout[6] = SP.getBoolean(R.string.key_omnicore_podchange_6,false);
        _changeBlackout[7] = SP.getBoolean(R.string.key_omnicore_podchange_7,false);
        _changeBlackout[8] = SP.getBoolean(R.string.key_omnicore_podchange_8,false);
        _changeBlackout[9] = SP.getBoolean(R.string.key_omnicore_podchange_9,false);
        _changeBlackout[10] = SP.getBoolean(R.string.key_omnicore_podchange_10,false);
        _changeBlackout[11] = SP.getBoolean(R.string.key_omnicore_podchange_11,false);
        _changeBlackout[12] = SP.getBoolean(R.string.key_omnicore_podchange_12,false);
        _changeBlackout[13] = SP.getBoolean(R.string.key_omnicore_podchange_13,false);
        _changeBlackout[14] = SP.getBoolean(R.string.key_omnicore_podchange_14,false);
        _changeBlackout[15] = SP.getBoolean(R.string.key_omnicore_podchange_15,false);
        _changeBlackout[16] = SP.getBoolean(R.string.key_omnicore_podchange_16,false);
        _changeBlackout[17] = SP.getBoolean(R.string.key_omnicore_podchange_17,false);
        _changeBlackout[18] = SP.getBoolean(R.string.key_omnicore_podchange_18,false);
        _changeBlackout[19] = SP.getBoolean(R.string.key_omnicore_podchange_19,false);
        _changeBlackout[20] = SP.getBoolean(R.string.key_omnicore_podchange_20,false);
        _changeBlackout[21] = SP.getBoolean(R.string.key_omnicore_podchange_21,false);
        _changeBlackout[22] = SP.getBoolean(R.string.key_omnicore_podchange_22,false);
        _changeBlackout[23] = SP.getBoolean(R.string.key_omnicore_podchange_23,false);

    }

    public long getAdjustedExpirationTime(long expirationTime) {
        try {
            if (L.isEnabled(L.PUMP)) {
                _log.debug("OmniCoreAlerts: Checking Expiration Time: " + DateUtil.dateAndTimeString(expirationTime));
            }
            setupWindows();
            if (!isBlackoutWindowValid()) {
                if (L.isEnabled(L.PUMP)) {
                    _log.debug("OmniCoreAlerts: Blackout Window is invalid. Returing entered value");
                }
                return expirationTime;
            }

            Date dt = new Date(expirationTime);
            int h = dt.getHours();

            if (!_changeBlackout[h]) {
                return expirationTime;
            }

            int hoursBack = 0;
            while (_changeBlackout[h]) {
                h = (h -1 + 24) % 24;
                hoursBack++;
            }


            if (hoursBack >= 24) {
                //that shouldn't happen.
                return expirationTime;
            }

            //round expiration time down to the hour, then subtract hoursBack hours to get the new expiration time
            expirationTime = expirationTime - expirationTime % (1000 * 60 * 60);
            expirationTime = expirationTime - (hoursBack -1) * (1000 * 60 * 60);
            return  expirationTime;

        }
        catch (Exception e) {
            //Couldn't get Expiration Time
            if (L.isEnabled(L.PUMP)) {
                _log.debug("OmniCoreAlerts: exception " + e.getMessage()) ;
            }
            return expirationTime;
        }
    }

    public Boolean isBlackoutWindowValid() {
        //We have to have at least one hour of the day to change pods
        Boolean isValid = false;

        for (int i = 0; i < 23; i++) {
            isValid = isValid || !_changeBlackout[i];
        }
        return isValid;
    }


}
