package info.nightscout.androidaps.plugins.pump.omnipod.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.pump.omnipod.api.rest.OmniCoreRequest;
import info.nightscout.androidaps.plugins.pump.omnipod.api.rest.OmniCoreResult;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodUpdateGui;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;


public class OmniCoreCommandHistory {
//TODO: Persist this
//TODO: implement "executing" status and "Cancelled" status

    private final Logger _log;

    private final int _historyMaxSize = 30;
    private List<OmniCoreCommandHistoryItem> _commandHistory;
    //private int consecutiveFailCount = 0;

    public OmniCoreCommandHistory() {
        _log =  LoggerFactory.getLogger(L.PUMP);

        _commandHistory = new ArrayList<>();
    }


    public void addOrUpdateHistory(OmniCoreRequest request, OmniCoreResult result) {
        addOrUpdateHistory(request,result, null);
    }

    public void addOrUpdateHistory(OmniCoreRequest request, OmniCoreResult result, DetailedBolusInfo bolusInfo) {
        if (L.isEnabled(L.PUMP)) {
            _log.debug("OmniCoreCommandHistory Processing: " + request.getRequestDetails() + " at Time " + request.created);
        }


        if (!SP.getBoolean(R.string.key_omnicore_status_history,false) && (request.getRequestType().equals("GetStatus"))) {
            //@string/key_omnicore_status_history
        }
        else {
            OmniCoreCommandHistoryItem hi = getMatchingHistoryItem(request);
            if (hi != null) {
                hi.setResult(result);
                hi.setBolusInfo(bolusInfo);
            }
            else {
                if (L.isEnabled(L.PUMP)) {
                    _log.debug("OmniCoreCommandHistory Could not find matching request. Adding it");
                }
                hi = new OmniCoreCommandHistoryItem(request,result);
                hi.setBolusInfo(bolusInfo);
                _commandHistory.add(hi);
                trim();
            }
        }



        RxBus.INSTANCE.send(new EventOmnipodUpdateGui());


    }

    private void trim() {
        while (_commandHistory.size() > _historyMaxSize) {
            _commandHistory.remove(0);
        }
    }

    public void setRequestFailed(OmniCoreRequest request) {
        if (L.isEnabled(L.PUMP)) {
            _log.debug("OmniCoreCommandHistory Setting as Failed: " + request.getRequestDetails() + " at Time " + request.requested);
        }
       OmniCoreCommandHistoryItem hi = getMatchingHistoryItem(request);
        if (hi != null) {
            hi.setFailed();
        }
        RxBus.INSTANCE.send(new EventOmnipodUpdateGui());

    }

    public OmniCoreCommandHistoryItem getCommand(int id) {
        return _commandHistory.get(id);
    }

    public OmniCoreCommandHistoryItem getMatchingHistoryItem(UUID itemId) {
        OmniCoreCommandHistoryItem match = null;
        for (OmniCoreCommandHistoryItem h : _commandHistory) {

            if (h.getId() == itemId) {
                if (L.isEnabled(L.PUMP)) {
                    _log.debug("Found Matching History Entry");
                }
                match = h;
                break;
            }

        }
        return match;
    }


    public OmniCoreCommandHistoryItem getMatchingHistoryItem(OmniCoreRequest request) {
        OmniCoreCommandHistoryItem match = null;

        for (OmniCoreCommandHistoryItem h : _commandHistory) {
            if (L.isEnabled(L.PUMP)) {
                _log.debug("OmniCoreCommandHistory Comparing to History Entry: " + h.getStartTime());
            }

            if (h.isSameRequest(request)) {
                if (L.isEnabled(L.PUMP)) {
                    _log.debug("Found Matching History Entry");
                }
                match = h;
                break;
            }

        }

        return match;
    }

    public OmniCoreCommandHistoryItem getMatchingHistoryItem(long resultDate) {
        OmniCoreCommandHistoryItem match = null;

        for (OmniCoreCommandHistoryItem h : _commandHistory) {
            if (L.isEnabled(L.PUMP)) {
                _log.debug("OmniCoreCommandHistory Comparing to History Entry: " + h.getStartTime());
            }

            if (h.getResult() != null && h.getResult().ResultDate == resultDate) {
                if (L.isEnabled(L.PUMP)) {
                    _log.debug("Found Matching History Entry");
                }
                match = h;
                break;
            }

        }

        return match;
    }

    public OmniCoreCommandHistoryItem getMatchingHistoryItem(OmniCoreResult result) {
        OmniCoreCommandHistoryItem match = null;

        for (OmniCoreCommandHistoryItem h : _commandHistory) {
            if (L.isEnabled(L.PUMP)) {
                _log.debug("OmniCoreCommandHistory Comparing to History Entry: " + h.getStartTime());
            }

            if (h.isSameRequest(result)) {
                if (L.isEnabled(L.PUMP)) {
                    _log.debug("Found Matching History Entry");
                }
                match = h;
                break;
            }

        }

        return match;
    }

    public List<OmniCoreCommandHistoryItem> getAllHistory() {
   /*     //Return in reverse order
        List<OmniCoreCommandHistoryItem> tmpList =  new ArrayList<>();
        for(int i = _commandHistory.size() - 1; i >=0; i--) {
            OmniCoreCommandHistoryItem tmp = _commandHistory.get(i);
            tmpList.add(tmp);
        }*/
        return _commandHistory;
    }


    public OmniCoreCommandHistoryItem getLastSuccess() {
        OmniCoreCommandHistoryItem _lastSuccess = null;

        for (OmniCoreCommandHistoryItem h : _commandHistory) {
            if (h.getStatus() == OmnicoreCommandHistoryStatus.SUCCESS || h.getStatus() == OmnicoreCommandHistoryStatus.EXECUTED) {
                if (_lastSuccess == null || h.getEndTime() > _lastSuccess.getEndTime()) {
                    _lastSuccess = h;
                }
            }
        }
        return _lastSuccess;
    }

    public OmniCoreCommandHistoryItem getLastCommand() {
        OmniCoreCommandHistoryItem lastCommand = null;
        if (_commandHistory.size() > 0) {
            lastCommand = _commandHistory.get(_commandHistory.size() -1);
        }
        return lastCommand;
    }

    public OmniCoreCommandHistoryItem getLastFailure() {
        OmniCoreCommandHistoryItem _lastFailure = null;

        for (OmniCoreCommandHistoryItem h : _commandHistory) {
            if (h.getStatus() == OmnicoreCommandHistoryStatus.FAILED) {
                if (_lastFailure == null || h.getEndTime() > _lastFailure.getEndTime()) {
                    _lastFailure = h;
                }
            }
        }
        return _lastFailure;
    }

}
