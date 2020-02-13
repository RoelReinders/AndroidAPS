package info.nightscout.androidaps.plugins.pump.omnipod.api.rest;

public class OmniCoreCancelBolusRequest extends OmniCoreRequest {

    public OmniCoreCancelBolusRequest()
    {
        super();
        joRequest.addProperty("Type", "CancelBolus");
    }
}
