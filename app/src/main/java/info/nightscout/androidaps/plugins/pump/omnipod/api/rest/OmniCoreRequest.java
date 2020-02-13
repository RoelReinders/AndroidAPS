package info.nightscout.androidaps.plugins.pump.omnipod.api.rest;

import com.google.gson.JsonObject;


public abstract class OmniCoreRequest {

    public long created;
    public long requested;

    protected JsonObject joRequest;

    public OmniCoreRequest()
    {
        created = System.currentTimeMillis();
        joRequest = new JsonObject();
    }

    public JsonObject GetRequestJson()
    {
        return joRequest;
    }
}

