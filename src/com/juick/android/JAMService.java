package com.juick.android;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import com.juick.android.juick.JuickComAuthorizer;
import com.juickadvanced.xmpp.ClientToServer;
import com.juickadvanced.xmpp.ServerToClient;

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
        handler = new Handler();
        super.onCreate();    //To change body of overridden methods use File | Settings | File Templates.
        XMPPService.log("JAM.onCreate()");
        startup();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean handled = false;
        JAXMPPClient clientLocal = client;
        if (intent != null && intent.getBooleanExtra("terminate", false)) {
            if (clientLocal != null) {
                XMPPService.log("Sent client disconnect (JAM)");
                clientLocal.sendDisconnect(new Utils.Function<Void, ServerToClient>() {
                    @Override
                    public Void apply(ServerToClient serverToClient) {
                        return null;  //To change body of implemented methods use File | Settings | File Templates.
                    }
                });
            }
            stopSelf();
            handled = true;
        }
        if (clientLocal != null) {
            if (intent != null && intent.getBooleanExtra("listen_all", false)){
                clientLocal.listenAll();
                handled = true;
            } else if (intent != null && intent.getBooleanExtra("unlisten_all", false)){
                clientLocal.unlistenAll();
                handled = true;
            } else if (intent != null && intent.getStringExtra("unsubscribeMessage") != null){
                clientLocal.unsubscribeMessage(intent.getStringExtra("unsubscribeMessage"));
                handled = true;
            } else if (intent != null && intent.getStringExtra("subscribeMessage") != null){
                clientLocal.subscribeMessage(intent.getStringExtra("subscribeMessage"));
                handled = true;
            }
        }
        if (!handled) {
            startup();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private synchronized void startup() {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        boolean useJAM = sp.getBoolean("enableJAMessaging", false);
        if (!useJAM) return;
        if (client == null) {
            final Utils.ServiceGetter<XMPPService> getter = new Utils.ServiceGetter<XMPPService>(JAMService.this, XMPPService.class);
            new Thread("JAM.startup") {
                @Override
                public void run() {
                    JAXMPPClient localClient = client;
                    synchronized (JAMService.this) {
                        if (client == null) {
                            localClient = client = new JAXMPPClient();
                        } else {
                            return;
                        }
                    }
                    String juickAccountName = JuickComAuthorizer.getJuickAccountName(JAMService.this);
                    String authString = JuickComAuthorizer.getBasicAuthString(JAMService.this);
                    if (localClient == client) {
                        // [race condition here]
                        localClient.setXmppClientListener(new JAXMPPClient.XMPPClientListener() {
                            @Override
                            public boolean onMessage(final String jid, final String message) {
                                getter.getService(new Utils.ServiceGetter.Receiver<XMPPService>() {
                                    @Override
                                    public void withService(XMPPService service) {
                                        boolean useJAM = sp.getBoolean("enableJAMessaging", false);
                                        if (useJAM) {
                                            if (jid.equals(XMPPService.JUICKADVANCED_ID)) {
                                                service.handleJuickMessage(XMPPService.JUICK_ID, message);
                                            }
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
                        String error = localClient.loginLocal(JAMService.this, handler, juickAccountName, authString);
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
                }
            }.start();
        }
    }

    private void cleanup() {
        final JAXMPPClient clientLocal = client;
        if (clientLocal != null) {
            client = null;
            new Thread("JAM.cleanp") {
                @Override
                public void run() {
                    try {
                        Thread.sleep(3000);     // disconnect message must go.
                    } catch (InterruptedException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                    if (clientLocal != null) {
                        clientLocal.disconnect();
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
        XMPPService.log("JAM.onCreate()");
    }
}
