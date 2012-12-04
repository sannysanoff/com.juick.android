package com.juick.android;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;
import com.google.gson.Gson;
import com.juick.android.juick.JuickComAuthorizer;
import com.juickadvanced.R;
import com.juickadvanced.xmpp.ClientToServer;
import com.juickadvanced.xmpp.ServerToClient;
import com.juickadvanced.xmpp.XMPPConnectionSetup;
import com.juickadvanced.xmpp.commands.*;
import com.juickadvanced.xmpp.messages.ContactOffline;
import com.juickadvanced.xmpp.messages.ContactOnline;
import com.juickadvanced.xmpp.messages.TimestampedMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/18/12
 * Time: 12:32 AM
 * To change this template use File | Settings | File Templates.
 */
public class JAXMPPClient implements GCMIntentService.GCMMessageListener, GCMIntentService.ServerPingTimerListener, JettyWsClient.WsClientListener {
    String url = "https://192.168.1.77:8222/xmpp/control";
    //String url = "https://ja.ip.rt.ru:8222/xmpp/control";
    String sessionId;
    HashSet<String> wachedJids;
    long since;
    Context context;
    private Handler handler;
    XMPPConnectionSetup setup;
    String username;
    JettyWsClient wsClient;
    Thread wsclientLoop;
    public boolean loggedIn;

    public JAXMPPClient() {
    }

    public String loginXMPP(Context context, Handler handler, XMPPConnectionSetup setup, HashSet<String> wachedJids) {
        this.wachedJids = wachedJids;
        this.context = context;
        this.setup = setup;
        this.handler = handler;
        String retval = performLogin(context, setup);
        if (retval == null) {
            loggedIn = true;
            addListeners();
        }
        return retval;
    }

    private void addListeners() {
        GCMIntentService.listeners.add(this);
        GCMIntentService.serverPingTimerListeners.add(this);
        maybeStartWSClient();
    }

    // if server is down for long time
    static int increasingReconnectDelay = 1;

    private void maybeStartWSClient() {
        if (wsClient != null) return;
        wsClient = new JettyWsClient();
        final URLParser pa = new URLParser(url);
        boolean connected = wsClient.connect(pa.getHost(), Integer.parseInt(pa.getPort()), "/zebra", null);
        if (!connected) {
            wsClient = null;
            scheduleRetryConnection();
            return;
        }
        wsClient.send("sessionId|"+sessionId);
        (wsclientLoop = new Thread() {
            JettyWsClient myClient = wsClient;
            long connectTime = System.currentTimeMillis();
            @Override
            public void run() {
                while(!myClient.shuttingDown) {
                    if (myClient != wsClient) {
                        // replaced by another one
                        myClient.disconnect();
                        return;
                    }
                    myClient.setListener(JAXMPPClient.this);
                    myClient.readLoop();
                    connectTime = System.currentTimeMillis() - connectTime;
                    if (connectTime < 5000) {
                        wsClient = null;        // something goes wrong, will try later
                        scheduleRetryConnection();
                        return;
                    } else {
                        increasingReconnectDelay = 1;
                        if (!myClient.shuttingDown) {
                            myClient.connect(pa.getHost(), Integer.parseInt(pa.getPort()), "/zebra", null);
                            wsClient.send("sessionId|"+sessionId);
                            connectTime = System.currentTimeMillis();
                        }
                    }
                }
            }
        }).start();
    }

    private void scheduleRetryConnection() {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connManager.getActiveNetworkInfo();
        if (activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting()) {
            GCMIntentService.rescheduleAlarm(context, increasingReconnectDelay);
            increasingReconnectDelay *= 2;
        }
    }

    public String loginLocal(Context context, Handler handler, String username, String cookie) {
        JuickAdvancedApplication.showToast("JAXMPPClient loginLocal");
        this.context = context;
        this.handler = handler;
        this.username = username;
        String retval = performLoginLocal(context, username, cookie);
        if (retval == null) {
            JuickAdvancedApplication.showToast("JAXMPPClient loginLocal success");
            loggedIn = true;
            addListeners();
        } else {
            JuickAdvancedApplication.showToast("JAXMPPClient loginLocal failure");
        }
        return retval;
    }

    private String performLogin(Context context, XMPPConnectionSetup setup) {
        sessionId = DatabaseService.getUniqueInstallationId(context, setup.getJid());
        ClientToServer c2s = new ClientToServer(sessionId);
        Login login = new Login(setup, this.wachedJids, JuickAdvancedApplication.registrationId);
        login.setProofAccountId(JuickComAuthorizer.getJuickAccountName(context));
        login.setProofAccountToken(JuickComAuthorizer.getBasicAuthString(context));
        login.setProofAccountType("juick");
        c2s.setLogin(login);
        ServerToClient serverToClient = callXmppControl(context, c2s);
        if (serverToClient.haveMoreMessages) {
            startSync(new Runnable() {
                @Override
                public void run() {

                }
            });
        }
        return serverToClient.getErrorMessage();
    }

    private String performLoginLocal(Context context, String username, String cookie) {
        setup = new XMPPConnectionSetup();
        setup.jid = username+"@local";
        setup.password = cookie;
        sessionId = DatabaseService.getUniqueInstallationId(context, setup.getJid());
        ClientToServer c2s = new ClientToServer(sessionId);
        Login login = new Login(setup, new HashSet<String>(), JuickAdvancedApplication.registrationId);
        login.setProofAccountId(JuickComAuthorizer.getJuickAccountName(context));
        login.setProofAccountToken(JuickComAuthorizer.getBasicAuthString(context));
        login.setProofAccountType("juick");
        c2s.setLogin(login);
        ServerToClient serverToClient = callXmppControl(context, c2s);
        if (serverToClient.haveMoreMessages) {
            startSync(new Runnable() {
                @Override
                public void run() {

                }
            });
        }
        return serverToClient.getErrorMessage();
    }

    public void callXmppControlSafe(final Context context, final ClientToServer c2s, final Utils.Function<Void, ServerToClient> then) {
        new Thread("callXmppControlInThread") {
            @Override
            public void run() {
                final ServerToClient serverToClient = callXmppControl(context, c2s);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        then.apply(serverToClient);
                    }
                });
            }
        }.start();
    }

    public ServerToClient callXmppControl(Context context, ClientToServer c2s) {
        String dataValue = new Gson().toJson(c2s);
        Utils.RESTResponse restResponse = Utils.postJA(context, url, dataValue);
        ServerToClient result;
        if (restResponse.getErrorText() != null) {
            result = new ServerToClient(sessionId, restResponse.getErrorText());
        } else {
            result = new Gson().fromJson(restResponse.getResult(), ServerToClient.class);
            if (result == null || !sessionId.equals(result.getSessionId())) {
                result = new ServerToClient(sessionId, "Unable to decode server reply");
            }
        }
        return result;
    }

    public void sendMessage(final String jid, final String message) {
        new Thread("SendMessage") {
            @Override
            public void run() {
                if (sessionId != null) {
                    ClientToServer c2s = new ClientToServer(sessionId);
                    c2s.setSendMessage(new SendMessage(message, jid));
                    Log.i("JuickAdvanced", "ExtXMPP Client2Server: send message: "+jid+": "+message);
                    final ServerToClient serverToClient = callXmppControl(context, c2s);
                    if (serverToClient.getErrorMessage() == null) {
                        //
                    } else {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, serverToClient.getErrorMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            }
        }.start();
    }

    public void handleGCMessageFromServer(String messag) {
        if (messag.startsWith("sync")) {
            String[] args = messag.split("\\|");
            XMPPService.lastGCMMessageID = args[2];
            if (args[1].equals(sessionId)) {
                // real xmpp extxmpp
                startSync(new Runnable() {
                    @Override
                    public void run() {
                        //To change body of implemented methods use File | Settings | File Templates.
                    }
                });
            }
        }
    }

    public void disconnect() {
        JuickAdvancedApplication.showToast("JAXMPPClient disconnect");
        if (wsClient != null) {
            wsClient.shuttingDown = true;
            wsClient.disconnect();
        }
        ClientToServer c2s = new ClientToServer(sessionId);
        c2s.setDisconnect(new Disconnect());
        callXmppControl(context, c2s);
        GCMIntentService.listeners.remove(this);
        GCMIntentService.serverPingTimerListeners.remove(this);
    }

    @Override
    public void onWebSocketTextFrame(String data) throws IOException {
        XMPPService.nWSMessages++;
        XMPPService.lastWSMessage = new Date();
        if (data.startsWith("sync")) {
            String[] args = data.split("\\|");
            XMPPService.lastWSMessageID = args[1];
            wsClient.send("pong");
            startSync(new Runnable() {
                @Override
                public void run() {}});
        }
    }

    private void callXMPPControlSafeWithArgs(Utils.Function<Void, ClientToServer> configurer, final Utils.Function<Void, ServerToClient> then) {
        ClientToServer clientToServer = new ClientToServer(sessionId);
        configurer.apply(clientToServer);
        callXmppControlSafe(context, clientToServer, new Utils.Function<Void, ServerToClient>() {
            @Override
            public Void apply(ServerToClient serverToClient) {
                if (then == null) {
                    if (serverToClient.getErrorMessage() != null) {
                        Toast.makeText(context, serverToClient.getErrorMessage(), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(context, R.string.Done, Toast.LENGTH_LONG).show();
                    }
                } else {
                    then.apply(serverToClient);
                }
                return null;
            }
        });
    }

    public void listenAll() {
        callXMPPControlSafeWithArgs(new Utils.Function<Void, ClientToServer>() {
            @Override
            public Void apply(ClientToServer clientToServer) {
                clientToServer.setSubscribeToAll(new SubscribeToAll("S"));
                return null;
            }
        }, null);
    }

    public void unlistenAll() {
        callXMPPControlSafeWithArgs(new Utils.Function<Void, ClientToServer>() {
            @Override
            public Void apply(ClientToServer clientToServer) {
                clientToServer.setSubscribeToAll(new SubscribeToAll("U"));
                return null;
            }
        }, null);
    }

    public void unsubscribeMessage(final String mid) {
        callXMPPControlSafeWithArgs(new Utils.Function<Void, ClientToServer>() {
            @Override
            public Void apply(ClientToServer clientToServer) {
                clientToServer.setSubscribeToThread(new SubscribeToThread(Integer.parseInt(mid), "R"));
                return null;
            }
        }, null);
    }

    public void subscribeMessage(final String mid) {
        callXMPPControlSafeWithArgs(new Utils.Function<Void, ClientToServer>() {
            @Override
            public Void apply(ClientToServer clientToServer) {
                clientToServer.setSubscribeToThread(new SubscribeToThread(Integer.parseInt(mid), "S"));
                return null;
            }
        }, null);
    }

    public void subscribeToComments(final String uname) {
        callXMPPControlSafeWithArgs(new Utils.Function<Void, ClientToServer>() {
            @Override
            public Void apply(ClientToServer clientToServer) {
                clientToServer.setSubscribeToComments(new SubscribeToComments(uname, "S"));
                return null;
            }
        }, null);
    }

    public void unsubscribeFromComments(final String uname) {
        callXMPPControlSafeWithArgs(new Utils.Function<Void, ClientToServer>() {
            @Override
            public Void apply(ClientToServer clientToServer) {
                clientToServer.setSubscribeToComments(new SubscribeToComments(uname, null));
                return null;
            }
        }, null);
    }

    enum SyncState {
        NOT_SYNCING,
        SYNC_IN_PROGRESS,
        SYNC_IN_PROGRESS_AND_ANOTHER_PENDING
    };
    SyncState syncState = SyncState.NOT_SYNCING;
    public void startSync(final Runnable then) {
        synchronized (this) {
            switch (syncState) {
                case NOT_SYNCING:
                    syncState = SyncState.SYNC_IN_PROGRESS;
                    break;
                case SYNC_IN_PROGRESS:
                    syncState = SyncState.SYNC_IN_PROGRESS_AND_ANOTHER_PENDING;
                case SYNC_IN_PROGRESS_AND_ANOTHER_PENDING:
                    then.run();
                    return;
            }
        }
        new Thread("Ext.XMPP.Poll") {
            @Override
            public void run() {
                try {
                    if (sessionId != null) {
                        ClientToServer c2s = new ClientToServer(sessionId);
                        c2s.setPoll(new Poll(since));
                        final ServerToClient serverToClient = callXmppControl(context, c2s);
                        if (serverToClient.getErrorMessage() == null) {
                            XMPPService.lastException = null;
                            XMPPService.lastSuccessfulConnect = System.currentTimeMillis();
                            ArrayList<TimestampedMessage> incomingMessages = serverToClient.getIncomingMessages();
                            boolean hasSomething = false;
                            if (incomingMessages != null) {
                                for (TimestampedMessage incomingMessage : incomingMessages) {
                                    xmppClientListener.onMessage(incomingMessage.getFrom(), incomingMessage.getMessage());
                                    since = Math.max(since, incomingMessage.getTimestamp());
                                    hasSomething = true;
                                }
                            }
                            ArrayList<ContactOnline> contactOnline = serverToClient.getContactOnline();
                            if (contactOnline != null) {
                                for (ContactOnline online : contactOnline) {
                                    presence(online.getJid(), true);
                                    hasSomething = true;
                                }
                            }
                            ArrayList<ContactOffline> contactOffline = serverToClient.getContactOffline();
                            if (contactOffline != null) {
                                for (ContactOffline offline : contactOffline) {
                                    presence(offline.getJid(), false);
                                    hasSomething = true;
                                }
                            }
                            if (hasSomething) {
                                c2s = new ClientToServer(sessionId);
                                c2s.setConfirmPoll(new ConfirmPoll(since));
                                ServerToClient confirmResult = callXmppControl(context, c2s);
                                if (confirmResult.haveMoreMessages)
                                    run();
                            }
                        } else {
                            String error = serverToClient.getErrorMessage();
                            if (error.equals(ServerToClient.NO_SUCH_SESSION)) {
                                error = performLogin(context, setup);
                            }
                            if (error != null && error.startsWith(ServerToClient.NETWORK_CONNECT_ERROR)) {
                                error = null;   // silently ignore connection errors.
                            }
                            if (error != null) {
                                XMPPService.lastException = error;
                                final String finalError = error;
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(context, finalError, Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }
                    }
                } finally {
                    synchronized (JAXMPPClient.this) {
                        switch (syncState) {
                            case SYNC_IN_PROGRESS:
                                syncState = SyncState.NOT_SYNCING;
                                then.run();
                                break;
                            case NOT_SYNCING:
                                // bad ;-(
                                then.run();
                                break;
                            case SYNC_IN_PROGRESS_AND_ANOTHER_PENDING:
                                syncState = SyncState.SYNC_IN_PROGRESS;
                                run();
                                return;
                        }
                    }
                }
            }
        }.start();
    }

    private void presence(String jid, boolean b) {
        xmppClientListener.onPresence(jid, b);
    }

    interface XMPPClientListener {
        boolean onMessage(String jid, String message);
        boolean onPresence(String jid, boolean on);
    }

    XMPPClientListener xmppClientListener;

    public void setXmppClientListener(XMPPClientListener xmppClientListener) {
        this.xmppClientListener = xmppClientListener;
    }

    @Override
    public void onGCMMessage(String message) {
        handleGCMessageFromServer(message);
    }

    @Override
    public void onRegistration(final String regid) {
        new Thread("SendGCMReg") {
            @Override
            public void run() {
                if (sessionId != null) {
                    ClientToServer c2s = new ClientToServer(sessionId);
                    c2s.setSendGCMRegistration(new SendGCMRegistration(regid));
                    final ServerToClient serverToClient = callXmppControl(context, c2s);
                }
            }
        }.start();
    }

    public void onServerPingTime() {
        new Thread("Check WS") {
            @Override
            public void run() {
                if (wsClient == null) {
                    maybeStartWSClient();
                } else {
                    try {
                        if (!wsClient.send("ping")) {
                            wsClient.disconnect();
                            wsClient = null;
                            maybeStartWSClient();
                        }
                    } catch (Exception e) {
                        //
                    }
                }
            }
        }.start();
        startSync(new Runnable() {
            @Override
            public void run() {
            }
        });
    }




}
