package info.nightscout.androidaps.plugins.pump.omnipod.api.rest;

import java.math.BigDecimal;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class OmniCoreTempBasalRequest extends OmniCoreRequest {

    public OmniCoreTempBasalRequest(BigDecimal temporaryRate, BigDecimal durationInHours)
    {
        super();
        joRequest.addProperty("Type", "SetTempBasal");
        joRequest.addProperty("TemporaryRate", temporaryRate);
        joRequest.addProperty("DurationHours", durationInHours);
    }

    @Override
    public String getRequestDetails()  {
        Float basalRate = joRequest.get("TemporaryRate").getAsFloat();
        Float duration = joRequest.get("DurationHours").getAsFloat();
        return String.format(MainApp.gs(R.string.omnipod_command_tbr),basalRate,duration);
    }

}
