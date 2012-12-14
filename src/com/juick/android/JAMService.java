package com.juick.android;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import com.juick.android.juick.JuickComAuthorizer;
import com.juickadvanced.xmpp.ClientToServer;

/**
 */
public class JAMService extends Service {

    private Handler handler;
    private final IBinder mBinder = new Utils.ServiceGetter.LocalBinder<JAMService>(this);
    JAXMPPClient client;

    static JAMService instance;

    @Override
    public void onCreate() {
        instance = this;
        super.onCreate();    //To change body of overridden methods use File | Settings | File Templates.
        handler = new Handler();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra("terminate", false)) {
            stopSelf();
        } else if (intent != null && intent.getBooleanExtra("listen_all", false)){
            client.listenAll();
        } else if (intent != null && intent.getBooleanExtra("unlisten_all", false)){
            client.unlistenAll();
        } else if (intent != null && intent.getStringExtra("unsubscribeMessage") != null){
            client.unsubscribeMessage(intent.getStringExtra("unsubscribeMessage"));
        } else if (intent != null && intent.getStringExtra("subscribeMessage") != null){
            client.subscribeMessage(intent.getStringExtra("subscribeMessage"));
        } else {
            if (startId != 2) {     // i don't know what is this, really
                startup();
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private synchronized void startup() {
        final Utils.ServiceGetter<XMPPService> getter = new Utils.ServiceGetter<XMPPService>(JAMService.this, XMPPService.class);
        if (client == null) {
            new Thread("JAM.startup") {
                @Override
                public void run() {
                    synchronized (JAMService.this) {
                        if (client == null) {
                            client = new JAXMPPClient();
                        } else {
                            return;
                        }
                    }
                    String juickAccountName = JuickComAuthorizer.getJuickAccountName(JAMService.this);
                    String authString = JuickComAuthorizer.getBasicAuthString(JAMService.this);
                    client.setXmppClientListener(new JAXMPPClient.XMPPClientListener() {
                        @Override
                        public boolean onMessage(final String jid, final String message) {
                            getter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                                @Override
                                public void withService(XMPPService service) {
                                    if (jid.equals(XMPPService.JUICKADVANCED_ID)) {
                                        service.handleJuickMessage(XMPPService.JUICK_ID, message);
                                    }
                                }

                                @Override
                                public void withoutService() {
                                }
                            });
                            return false;
                        }

                        @Override
                        public boolean onPresence(String jid, boolean on) {
                            return false;
                        }
                    });
                    String error = client.loginLocal(JAMService.this, handler, juickAccountName, authString);
                    if (error == null) {
                        // ok
                    } else {
                        XMPPService.lastException = error;
                        XMPPService.lastExceptionTime = System.currentTimeMillis();
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                stopSelf();
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

    @Override
    public void onDestroy() {
        cleanup();
        instance = null;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();    //To change body of overridden methods use File | Settings | File Templates.
    }
}
