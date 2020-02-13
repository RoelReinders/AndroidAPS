package info.nightscout.androidaps.plugins.pump.omnipod.api.rest;

import java.math.BigDecimal;

public class OmniCoreTempBasalRequest extends OmniCoreRequest {

    public OmniCoreTempBasalRequest(BigDecimal temporaryRate, BigDecimal durationInHours)
    {
        super();
        joRequest.addProperty("Type", "SetTempBasal");
        joRequest.addProperty("TemporaryRate", temporaryRate);
        joRequest.addProperty("DurationHours", durationInHours);
    }

}
