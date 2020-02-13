package info.nightscout.androidaps.plugins.pump.omnipod.utils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.utils.SP;

public class OmniCoreStats {
    private Map<OmnicoreStatType, Long> _omnicoreStats;
    private final Logger _log;

    private final long _saveTimer = 30 * 1000;
    private long _lastSave = 0;

    public enum OmnicoreStatType {
        TBRTOTAL(R.string.omnicore_stat_tbrtotal),
        TBRHIGH(R.string.omnicore_stat_tbrhigh),
        TBRLOW(R.string.omnicore_stat_tbrlow),
        TBRZERO(R.string.omnicore_stat_tbrzero),
        TBRCANCEL(R.string.omnicore_stat_tbrcancel),
        TBRTIME(R.string.omnicore_stat_tbrtime),
        BOLUS(R.string.omnicore_stat_bolus),
        BOLUSSMB(R.string.omnicore_stat_bolussmb),
        BOLUSTIME(R.string.omnicore_stat_bolustime),
        BOLUSCANCEL(R.string.omnicore_stat_boluscancel),
        POD(R.string.omnicore_stat_pod),
        PROFILESET(R.string.omnicore_stat_profileset),
        PROFILESETTIME(R.string.omnicore_stat_profilesettime),
        STARTDATE(R.string.omnicore_stat_startdate),
        ENDDATE(R.string.omnicore_stat_enddate),
        COMMANDFAIL(R.string.omnicore_stat_commandfail),
        TOTALTIME(R.string.omnicore_stat_totaltime),
        TBRFAIL(R.string.omnicore_stat_tbrfail),
        BOLUSFAIL(R.string.omnicore_stat_bolusfail),
        TOTALCOMMANDS(R.string.omnicore_stat_totalcommands);

        private int resourceId;

        OmnicoreStatType(int resourceId) {
            this.resourceId = resourceId;
        }

        public String getDescription() {
            return MainApp.gs(resourceId);
        }
    }


    public OmniCoreStats() {
        _log =  LoggerFactory.getLogger(L.PUMP);

     //   _omnicoreStats = new EnumMap<>(OmnicoreStatType.class);

        loadStats();

        if (getStat(OmnicoreStatType.STARTDATE) == 0) {
            putStat(OmnicoreStatType.STARTDATE,System.currentTimeMillis());
        }

        if (getStat(OmnicoreStatType.TOTALCOMMANDS) == 0) {
            putStat(OmnicoreStatType.TOTALCOMMANDS,getStat(OmnicoreStatType.BOLUS) + getStat(OmnicoreStatType.TBRTOTAL) + getStat(OmnicoreStatType.PROFILESET));
        }


    }

    public long getStat(OmnicoreStatType key) {
        long retVal = 0;
        try {
            Long val = _omnicoreStats.get(key);
            retVal = (val != null) ? val : 0;
        }
        catch (Exception e) {
            _log.debug("OmniCoreStats: Error getting value for: " + key.toString());
            _log.debug(e.getMessage());
        }
        return retVal;
    }

    public String getKeyDescription(OmnicoreStatType key) {
        return key.getDescription();
    }



    public long putStat(OmnicoreStatType key, long value) {
        try {
            long currentTime = System.currentTimeMillis();
            _omnicoreStats.put(key,value);
            _omnicoreStats.put(OmnicoreStatType.ENDDATE,currentTime);
            if (currentTime > _lastSave + _saveTimer) {
                saveStats();
            }
        }
        catch (Exception e) {
            _log.debug("OmniCoreStats: Error putting value: " + value + " for key " + key.toString());
            _log.debug(e.getMessage());
        }
        return getStat(key);
    }

    public long incrementStat(OmnicoreStatType key) {
        return addToStat(key,1);
    }

    public long addToStat(OmnicoreStatType key, long delta) {
        long val = getStat(key);
        val += delta;
        return putStat(key,val);
    }

    private void loadStats() {
        _omnicoreStats = new EnumMap<>(OmnicoreStatType.class);
        String savedStats = SP.getString(R.string.key_omnicore_stats,"");
        String[] nameValuePairs = savedStats.split("&");
        for (String nameValuePair : nameValuePairs) {
            String[] nameValue = nameValuePair.split("=");
            try {
                _omnicoreStats.put(OmnicoreStatType.valueOf(nameValue[0]), nameValue.length > 1 ? Long.parseLong(nameValue[1]) : 0);
            } catch (Exception e) {
                _log.debug("OmniCoreStats: Error loading stats: " + e.getMessage() );
            }
        }

    }

    private void saveStats() {
        StringBuilder stringBuilder = new StringBuilder();

        for (OmnicoreStatType key : _omnicoreStats.keySet()) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append("&");
            }

            try {
                if (key != null) {
                    stringBuilder.append(key.toString());
                    stringBuilder.append("=");
                    stringBuilder.append(getStat(key));
                }
            } catch (Exception e) {
                _log.debug("OmniCoreStats: Error saving stats: " + e.getMessage() );
            }
        }

        SP.putString(R.string.key_omnicore_stats,stringBuilder.toString());
        _lastSave = System.currentTimeMillis();

    }

    public String getDurationAsString(OmnicoreStatType key) {
        long millis = getStat(key);
        if(millis < 0) {
            throw new IllegalArgumentException("Duration must be greater than zero!");
        }
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
      //  long milliseconds = millis % 1000;

        return String.format(MainApp.gs(R.string.omnicore_duration_format),
                days, hours, minutes, seconds);
    }

    public void resetStats() {
        SP.putString(R.string.key_omnicore_stats,"");
        _lastSave = 0;
        loadStats();
        putStat(OmnicoreStatType.STARTDATE,System.currentTimeMillis());
    }

    public Set<OmnicoreStatType> getKeys() {
        return _omnicoreStats.keySet();
    }
}
