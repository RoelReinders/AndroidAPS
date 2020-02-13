package info.nightscout.androidaps.plugins.pump.omnipod.api.rest;

import com.google.gson.JsonArray;

import java.math.BigDecimal;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class OmniCoreSetProfileRequest extends OmniCoreRequest {
    public OmniCoreSetProfileRequest(BigDecimal[] schedule, int utcOffsetMinutes){
        super();
        joRequest.addProperty("Type", "SetProfile");
        joRequest.addProperty("UtcOffsetMinutes", utcOffsetMinutes);

        JsonArray scheduleArray = new JsonArray();
        for(int i=0; i<48; i++)
            scheduleArray.add(schedule[i]);

        joRequest.add("BasalSchedule", scheduleArray);
    }

    @Override
    public String getRequestDetails()  {
        return MainApp.gs(R.string.omnipod_command_setprofile);
    }

}
