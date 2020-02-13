package info.nightscout.androidaps.plugins.pump.omnipod.api.rest;

import com.google.gson.JsonObject;

public class OmniCoreStatusRequest extends OmniCoreRequest {

    public OmniCoreStatusRequest()
    {
        super();
        joRequest.addProperty("Type", "GetStatus");
    }
}
