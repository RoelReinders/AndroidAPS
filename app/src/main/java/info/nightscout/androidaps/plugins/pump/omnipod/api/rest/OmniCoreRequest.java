package info.nightscout.androidaps.plugins.pump.omnipod.api.rest;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.tools.jsc.Main;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

import static android.content.Context.ACTIVITY_SERVICE;

public abstract class OmniCoreRequest {

    public long created;
    public long requested;

    protected JsonObject joRequest;
    private static HandlerThread mHandlerThread;

    public OmniCoreRequest()
    {
        created = System.currentTimeMillis();
        joRequest = new JsonObject();
    }

    public String getRequestType() {
        String requestType = "Omnicore Request";
        if (joRequest.has("Type")) {
            requestType = joRequest.get("Type").getAsString();
        }
        return requestType;
    }

    public String getRequestDetails() {
        return getRequestType();
    }

    public String toString() {
        return joRequest.toString();
    }

    public synchronized OmniCoreResult getRemoteResult(long lastResultDateTime) {
        this.requested = System.currentTimeMillis();

        if (mHandlerThread == null) {
            mHandlerThread = new HandlerThread("OmniCoreHandler");
            mHandlerThread.start();
        }

        boolean initializeCalled = false;
        while(true) {
            OmniCoreHandler handler = new OmniCoreHandler(mHandlerThread.getLooper());
            Intent intent = new Intent("OmniCoreIntentService.REQUEST_COMMAND");
            intent.setClassName("net.balya.OmniCore.Mobile.Android","OmniCore.IntentService");
          //  intent.setClassName(MainApp.gs(R.string.omnicore_package_name),"OmniCore.IntentService");
            joRequest.addProperty("LastResultDateTime", lastResultDateTime);
            String jsonRequest = joRequest.toString();
            intent.putExtra("request", jsonRequest);
            Log.d("OMNICORE_AAPS", "getRemoteResult sending request: " + jsonRequest);
            OmniCoreIntentResult ir =  handler.SendIntent(intent);
            if (ir == OmniCoreIntentResult.OK) {
                return OmniCoreResult.fromJson(handler.GetResponse());
            } else if (ir == OmniCoreIntentResult.ServiceNotInitialized) {
                if (!initializeCalled) {
                    Intent activityIntent = new Intent("EnsureServiceRunning");
                    activityIntent.setClassName("net.balya.OmniCore.Mobile.Android","OmniCore.MainActivity");
      //              activityIntent.setClassName(MainApp.gs(R.string.omnicore_package_name),"OmniCore.MainActivity");
                    activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    MainApp.instance().startActivity(activityIntent);
                    initializeCalled = true;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }
            else {
                return null;
            }
        }
    }

    enum OmniCoreIntentResult {
        OmniCoreNotInstalled,
        ServiceNotInitialized,
        TimedOut,
        Interrupted,
        Failed,
        OK
    }

    class OmniCoreHandler extends Handler {

        public OmniCoreHandler(Looper looper)
        {
            super(looper);
            notifySemaphore = new Semaphore(0);
        }

        private Semaphore notifySemaphore;
        private OmniCoreIntentResult intentResult;
        private String response;
        private long lastBusy;

        public OmniCoreIntentResult SendIntent(Intent intent)
        {
            Messenger messenger = new Messenger(this);
            intent.putExtra("messenger", messenger);

            this.post(() -> {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        MainApp.instance().startForegroundService(intent);
                    } else {
                        MainApp.instance().startService(intent);
                    }
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            });

            lastBusy = System.currentTimeMillis();
            while(true)
            {
                try
                {
                    if (notifySemaphore.tryAcquire(30000, TimeUnit.MILLISECONDS)) {
                        break;
                    }

                    if (System.currentTimeMillis() - lastBusy > 25000)
                    {
                        intentResult = OmniCoreIntentResult.TimedOut;
                        break;
                    }
                }
                catch (InterruptedException e)
                {
                    intentResult = OmniCoreIntentResult.Interrupted;
                    break;
                }
            }
            return intentResult;
        }

        public String GetResponse()
        {
            return response;
        }

        @Override
        public void handleMessage(Message msg) {
            Bundle b = msg.getData();
            boolean busy = b.getBoolean("busy", false);
            boolean initialized = b.getBoolean("initialized", false);
            boolean finished = b.getBoolean("finished", false);
            if (busy)
            {
                Log.d("OMNICORE_AAPS", "handleMessage: keep-alive received");
                lastBusy = System.currentTimeMillis();
            }
            else
            {
                if (!initialized)
                {
                    Log.d("OMNICORE_AAPS", "handleMessage: received not initialized");
                    intentResult = OmniCoreIntentResult.ServiceNotInitialized;
                    notifySemaphore.release();
                }
                else if (finished)
                {
                    Log.d("OMNICORE_AAPS", "handleMessage: finished received");
                    response = b.getString("response", null);
                    if (response == null) {
                        intentResult = OmniCoreIntentResult.Failed;
                    } else {
                        intentResult = OmniCoreIntentResult.OK;
                    }
                    notifySemaphore.release();
                }
                else {
                    intentResult = OmniCoreIntentResult.Failed;
                    notifySemaphore.release();
                }
            }
        }
    }
}

