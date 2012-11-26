package com.juick.android;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import com.juick.android.juick.JuickComAuthorizer;

/**
 */
public class JAMService extends Service {

    private Handler handler;
    private final IBinder mBinder = new Utils.ServiceGetter.LocalBinder<JAMService>(this);
    JAXMPPClient client;

    @Override
    public void onCreate() {
        super.onCreate();    //To change body of overridden methods use File | Settings | File Templates.
        handler = new Handler();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra("terminate", false)) {
            handler.removeCallbacksAndMessages(null);
            cleanup();
            stopSelf();
        } else {
            if (startId != 2) {     // i don't know what is this, really
                startup();
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void startup() {
        if (client == null) {
            new Thread("JAM.startup") {
                @Override
                public void run() {
                    if (client == null) {
                        String juickAccountName = JuickComAuthorizer.getJuickAccountName(JAMService.this);
                        client.loginLocal(JAMService.this, handler, juickAccountName);
                        client.setXmppClientListener(new JAXMPPClient.XMPPClientListener() {
                            @Override
                            public boolean onMessage(String jid, String message) {
                                return false;
                            }

                            @Override
                            public boolean onPresence(String jid, boolean on) {
                                return false;
                            }
                        });
                    }
                }
            }.start();
        }
    }

    private void cleanup() {
        if (client != null) {
            new Thread("JAM.cleanp") {
                @Override
                public void run() {
                    if (client != null) {
                        client.disconnect();
                        client = null;
                    }
                }
            }.start();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
