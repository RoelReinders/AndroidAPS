package info.nightscout.androidaps.plugins.pump.omnipod.api.rest;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class OmniCoreCancelBolusRequest extends OmniCoreRequest {

    public OmniCoreCancelBolusRequest()
    {
        super();
        joRequest.addProperty("Type", "CancelBolus");
    }

    @Override
    public String getRequestDetails()  {
        return MainApp.gs(R.string.omnipod_command_cancelbolus);
    }
}
