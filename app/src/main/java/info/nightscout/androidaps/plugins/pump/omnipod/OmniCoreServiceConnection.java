package info.nightscout.androidaps.plugins.pump.omnipod;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.google.gson.JsonObject;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.omnipod.api.rest.OmniCoreRequest;
import info.nightscout.androidaps.plugins.pump.omnipod.api.rest.OmniCoreResult;

import static android.content.Context.BIND_AUTO_CREATE;

public class OmniCoreServiceConnection implements ServiceConnection {

    private Context _context;
    private HandlerThread _handlerThread;
    private boolean _isConnected = false;
    private boolean _isConnecting = false;
    private long _lastResultDateTime = 0;
    private Messenger _serviceMessenger = null;

    public OmniCoreServiceConnection(Context context)
    {
        _context = context;
    }

    public boolean IsConnected() { return _isConnected; }

    public boolean IsConnecting() { return _isConnecting; }

    public boolean Connect()
    {
        if (_isConnected || _isConnecting)
            return true;

        Intent serviceToStart = new Intent();
        serviceToStart.setClassName("net.balya.OmniCore.Mobile.Android", "OmniCore.CommandService");
        try {
            if (_context.bindService(serviceToStart, this, BIND_AUTO_CREATE)) {
                _handlerThread = new HandlerThread("OmniCoreCommandResponseHandler");
                _handlerThread.start();
                _isConnecting = true;
            } else {
                _isConnected = false;
                _isConnecting = false;
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            _isConnected = false;
            _isConnecting = false;
        }

        return _isConnecting;
    }

    public boolean Disconnect()
    {
        if (_isConnecting)
            return false;

        if (!_isConnected)
            return true;

        try {
            _context.unbindService(this);
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

    public synchronized OmniCoreResult GetResult(OmniCoreRequest request)
    {
        if (!_isConnected)
            return null;

        JsonObject joRequest = request.GetRequestJson();
        joRequest.addProperty("LastResultDateTime", _lastResultDateTime);
        String strRequest = joRequest.toString();
        Log.d("OMNICORE_AAPS", "getRemoteResult sending request: " + strRequest);

        OmniCoreResponseHandler handler = new OmniCoreResponseHandler(_handlerThread.getLooper());

        Message sendMsg = new Message();
        Bundle bundle = new Bundle();
        bundle.putString("request", strRequest);
        bundle.putParcelable("responseMessenger", new Messenger(handler));
        sendMsg.setData(bundle);

        boolean initializeCalled = false;
        try {
            _serviceMessenger.send(sendMsg);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        while(true) {
            OmniCoreServiceCallResult callResult = handler.WaitForResult();
            if (callResult == OmniCoreServiceCallResult.OK) {
                try {
                    OmniCoreResult ocResult = OmniCoreResult.fromJson(handler.GetResponse());
                    _lastResultDateTime = ocResult.LastResultDateTime;
                    return ocResult;
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                    return null;
                }
            } else if (callResult == OmniCoreServiceCallResult.ServiceNotInitialized) {
                if (!initializeCalled) {
                    try {
                        Intent activityIntent = new Intent("EnsureServiceRunning");
                        activityIntent.setClassName("net.balya.OmniCore.Mobile.Android", "OmniCore.MainActivity");
                        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        MainApp.instance().startActivity(activityIntent);
                        initializeCalled = true;
                    }
                    catch(Exception e)
                    {
                        e.printStackTrace();
                        return null;
                    }
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

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        if (iBinder == null)
        {
            _isConnecting = false;
            return;
        }

        _serviceMessenger = new Messenger(iBinder);
        _isConnected = true;
        _isConnecting = false;

        MainApp.bus().post(new EventDismissNotification(Notification.OMNIPY_CONNECTION_STATUS));
        Notification notification = new Notification(Notification.OMNIPY_CONNECTION_STATUS,
                MainApp.gs(R.string.omnicore_connected), Notification.INFO, 1);
        MainApp.bus().post(new EventNewNotification(notification));
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        _serviceMessenger = null;
        _isConnected = false;
        _isConnecting = false;

        MainApp.bus().post(new EventDismissNotification(Notification.OMNIPY_CONNECTION_STATUS));
        Notification notification = new Notification(Notification.OMNIPY_CONNECTION_STATUS,
                MainApp.gs(R.string.omnicore_not_connected), Notification.NORMAL, 60);
        MainApp.bus().post(new EventNewNotification(notification));
    }

    @Override
    public void onBindingDied(ComponentName name) {
        _serviceMessenger = null;
        _isConnected = false;
        _isConnecting = false;

        MainApp.bus().post(new EventDismissNotification(Notification.OMNIPY_CONNECTION_STATUS));
        Notification notification = new Notification(Notification.OMNIPY_CONNECTION_STATUS,
                MainApp.gs(R.string.omnicore_not_connected), Notification.NORMAL, 60);
        MainApp.bus().post(new EventNewNotification(notification));
    }
}

class OmniCoreResponseHandler extends Handler
{
    public OmniCoreResponseHandler(Looper looper)
    {
        super(looper);
        notifySemaphore = new Semaphore(0);
    }

    private long lastBusy = 0;
    private Semaphore notifySemaphore;
    private String response;
    private OmniCoreServiceCallResult callResult = OmniCoreServiceCallResult.OK;

    public OmniCoreServiceCallResult WaitForResult()
    {
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
                    callResult = OmniCoreServiceCallResult.TimedOut;
                    break;
                }
            }
            catch (InterruptedException e)
            {
                callResult = OmniCoreServiceCallResult.Interrupted;
                break;
            }
        }
        return callResult;
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
        lastBusy = System.currentTimeMillis();
        if (!busy)
        {
            if (!initialized)
            {
                Log.d("OMNICORE_AAPS", "handleMessage: received not initialized");
                callResult = OmniCoreServiceCallResult.ServiceNotInitialized;
                notifySemaphore.release();
            }
            else if (finished)
            {
                Log.d("OMNICORE_AAPS", "handleMessage: finished received");
                response = b.getString("response", null);
                if (response == null) {
                    callResult = OmniCoreServiceCallResult.Failed;
                } else {
                    callResult = OmniCoreServiceCallResult.OK;
                }
                notifySemaphore.release();
            }
            else {
                callResult = OmniCoreServiceCallResult.Failed;
                notifySemaphore.release();
            }
        }
    }
}

enum OmniCoreServiceCallResult {
    ServiceNotInitialized,
    TimedOut,
    Interrupted,
    Failed,
    OK
}
