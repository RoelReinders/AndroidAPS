package info.nightscout.androidaps.plugins.pump.omnipod.api.rest;

import com.google.gson.JsonObject;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class OmniCoreStatusRequest extends OmniCoreRequest {

    public OmniCoreStatusRequest()
    {
        super();
        joRequest.addProperty("Type", "GetStatus");
    }

    @Override
    public String getRequestDetails()  {
        return MainApp.gs(R.string.omnipod_command_getstatus);
    }
}
