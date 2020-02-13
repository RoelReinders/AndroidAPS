package info.nightscout.androidaps.plugins.pump.omnipod.api.rest;

public class OmniCoreCancelTempBasalRequest extends OmniCoreRequest {

    public OmniCoreCancelTempBasalRequest()
    {
        super();
        joRequest.addProperty("Type", "CancelTempBasal");
    }
}
