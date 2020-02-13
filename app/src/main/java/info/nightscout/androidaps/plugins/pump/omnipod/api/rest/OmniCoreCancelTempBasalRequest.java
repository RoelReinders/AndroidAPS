package info.nightscout.androidaps.plugins.pump.omnipod.api.rest;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class OmniCoreCancelTempBasalRequest extends OmniCoreRequest {

    public OmniCoreCancelTempBasalRequest()
    {
        super();
        joRequest.addProperty("Type", "CancelTempBasal");
    }

    @Override
    public String getRequestDetails()  {
        return MainApp.gs(R.string.omnipod_command_canceltbr);
    }
}
