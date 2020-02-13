package info.nightscout.androidaps.plugins.pump.omnipod.api.rest;

import java.math.BigDecimal;

public class OmniCoreBolusRequest extends OmniCoreRequest {
    public OmniCoreBolusRequest(BigDecimal bolusAmount) {
        super();
        joRequest.addProperty("Type", "Bolus");
        joRequest.addProperty("ImmediateUnits", bolusAmount);
    }
}
