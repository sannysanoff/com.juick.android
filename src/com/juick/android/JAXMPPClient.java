package com.juick.android;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;
import com.google.gson.Gson;
import com.juick.android.bnw.BNWMicroBlog;
import com.juick.android.bnw.BnwAuthorizer;
import com.juick.android.facebook.Facebook;
import com.juick.android.facebook.FacebookAuthorizer;
import com.juick.android.juick.JuickAPIAuthorizer;
import com.juick.android.juick.JuickMicroBlog;
import com.juick.android.point.PointAuthorizer;
import com.juick.android.point.PointMicroBlog;
import com.juickadvanced.R;
import com.juickadvanced.RESTResponse;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.bnw.BnwMessageID;
import com.juickadvanced.data.facebook.FacebookMessageID;
import com.juickadvanced.data.juick.JuickMessageID;
import com.juickadvanced.data.point.PointMessageID;
import com.juickadvanced.xmpp.ClientToServer;
import com.juickadvanced.xmpp.ServerToClient;
import com.juickadvanced.xmpp.XMPPConnectionSetup;
import com.juickadvanced.xmpp.commands.*;
import com.juickadvanced.xmpp.messages.ContactOffline;
import com.juickadvanced.xmpp.messages.ContactOnline;
import com.juickadvanced.xmpp.messages.PongFromServer;
import com.juickadvanced.xmpp.messages.TimestampedMessage;
import org.acra.ACRA;

import java.io.IOException;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/18/12
 * Time: 12:32 AM
 * To change this template use File | Settings | File Templates.
 */
public class JAXMPPClient implements GCMIntentService.GCMMessageListener, GCMIntentService.ServerPingTimerListener, JASocketClientListener {
    public static String jahost = "ja.servebeer.com";
    //String jahost = "192.168.1.77";
    //String jahost = "10.236.35.24";
    int jaSocketPort = 8228;
    String controlURL = "https://"+ jahost +":8222/xmpp/control";
    String sessionId;
    HashSet<String> wachedJids;
    long since;
    Context context;
    private Handler handler;
    XMPPConnectionSetup setup;
    volatile JASocketClient wsClient;
    Thread wsclientLoop;
    public boolean loggedIn;
    private boolean someMessages;
    private String socketName;
    private long lastPongFromServer;
    private long expectPongFromServer;

    public JAXMPPClient() {
    }

    public String loginXMPP(Context context, Handler handler, XMPPConnectionSetup setup, HashSet<String> wachedJids) {
        this.wachedJids = wachedJids;
        this.context = context;
        if (context == null) {
            ACRA.getErrorReporter().handleException(new RuntimeException("NULL context in constructor! "));
        }
        this.setup = setup;
        this.handler = handler;
        socketName = "xmppSocket";
        String retval = performLogin(context, setup);
        if (retval == null) {
            log("loginXMPP success: "+setup.jid);
            loggedIn = true;
            addListeners();
        } else {
            log("loginXMPP error: "+setup.jid+": "+retval);
        }
        return retval;
    }

    private void addListeners() {
        GCMIntentService.listeners.add(this);
        GCMIntentService.serverPingTimerListeners.add(this);
        maybeStartWSClient();
    }

    // if server is down for long time
    static int increasingReconnectDelay = 60;

    private void maybeStartWSClient() {
        if (wsClient != null) return;
        final JASocketClient myClient = wsClient = new JASocketClient(socketName);
        boolean connected = wsClient.connect(jahost, jaSocketPort);
        if (!connected) {
            log("could not connect, schedule retry");
            wsClient = null;
            scheduleRetryConnection();
            return;
        }
        increasingReconnectDelay = 15;
        myClient.send(createClientPing());
        (wsclientLoop = new Thread("JAXMPPClient restarter ("+setup.jid+")") {
            long connectTime = System.currentTimeMillis();
            @Override
            public void run() {
                if(!myClient.shuttingDown) {
                    if (myClient != wsClient) {
                        // replaced by another one
                        myClient.disconnect();
                        return;
                    }
                    myClient.setListener(JAXMPPClient.this);
                    myClient.readLoop();
                    connectTime = System.currentTimeMillis() - connectTime;
                    if (someMessages) {
                        increasingReconnectDelay = 15;  // was working connection! delay 15 seconds
                    }
                    someMessages = false;
                    wsClient = null;        // something goes wrong, will try later
                    scheduleRetryConnection();
                }
            }
        }).start();
    }

    private String createClientPing() {
        ClientToServer ping = ClientToServer.createPing(sessionId);
        HashMap<String,Long> bestPeriods = ConnectivityChangeReceiver.getBestPeriods(context);
        ping.setConnectivityInfoStatistics(new Gson().toJson(bestPeriods));
        ping.setConnectivityInfo(ConnectivityChangeReceiver.getCurrentConnectivityTypeKey(context));
        expectPongFromServer = System.currentTimeMillis() + 60000;
        lastPongFromServer = -1;
        return new Gson().toJson(ping);
    }

    private void scheduleRetryConnection() {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connManager.getActiveNetworkInfo();
        if (activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting()) {
            GCMIntentService.rescheduleAlarm(context, increasingReconnectDelay);
            increasingReconnectDelay *= 2;
            if (increasingReconnectDelay > 10*60) {       // max 10 minutes
                increasingReconnectDelay = 10*60;
            }
        }
    }

    public String loginLocal(Context context, Handler handler) {
        this.context = context;
        if (context == null) {
            ACRA.getErrorReporter().handleException(new RuntimeException("NULL context in constructor! "));
        }
        JuickAdvancedApplication.showXMPPToast("JAXMPPClient loginLocal");
        this.handler = handler;
        socketName = "jamSocket";
        String retval = performLoginLocal(context);
        if (retval == null) {
            JuickAdvancedApplication.showXMPPToast("JAXMPPClient loginLocal success");
            loggedIn = true;
            addListeners();
        } else {
            JuickAdvancedApplication.showXMPPToast("JAXMPPClient loginLocal failure: "+retval);
        }
        return retval;
    }

    private String performLogin(Context context, XMPPConnectionSetup setup) {
        HashSet<AccountProof> accountProofs = getAccountProofs(context);
        if (accountProofs.size() < 1)
            return new String("No accounts, need smth to use JAXMPP");
        sessionId = createJASessionId(context, setup);
        ClientToServer c2s = new ClientToServer(sessionId);
        Login login = new Login(setup, this.wachedJids, JuickAdvancedApplication.registrationId, JuickAdvancedApplication.version);
        login.setProofAccountId(JuickAPIAuthorizer.getJuickAccountName(context));
        login.setProofAccountToken(JuickAPIAuthorizer.getBasicAuthString(context));
        login.setProofAccountType(JuickMessageID.CODE);
        c2s.setLogin(login);
        ServerToClient serverToClient = callXmppControl(context, c2s);
        onKeepAlive();
        return serverToClient.getErrorMessage();
    }

    private String createJASessionId(Context context, XMPPConnectionSetup setup) {
        String value = DatabaseService.getUniqueInstallationId(context, setup.getJid());
        int timestampBegin = value.indexOf("__");
        if (timestampBegin != -1) {
            value = value.substring(0, timestampBegin);     // do not saturate pool with app different versions
        }
        return value;
    }

    String relogin() {
        if (setup == null || setup.jid == null) {
            ACRA.getErrorReporter().handleException(new RuntimeException("relogin: null setup or jid: "+setup), false);
            return "Incomplete JAXMPP configuration.";
        }
        if (setup.jid.endsWith("@local")) {
            return performLoginLocal(context);
        } else {
            return performLogin(context, setup);
        }
    }

    private String performLoginLocal(Context context) {
        HashSet<AccountProof> accountProofs = getAccountProofs(context);
        if (accountProofs.size() < 1)
            return new String("No Accounts");
        setup = new XMPPConnectionSetup();
        setup.jid = DatabaseService.getUniqueInstallationId(context, "")+"@local";
        setup.password = "";
        sessionId = createJASessionId(context, setup);
        ClientToServer c2s = new ClientToServer(sessionId);
        LoginMultiple login = new LoginMultiple(setup, new HashSet<String>(), JuickAdvancedApplication.registrationId, JuickAdvancedApplication.version);
        HashSet<AccountProof> proofs = getAccountProofs(context);
        login.setProofs(proofs);
        c2s.setLoginMultiple(login);
        ServerToClient serverToClient = callXmppControl(context, c2s);
        onKeepAlive();
        return serverToClient.getErrorMessage();
    }

    public static HashSet<AccountProof> getAccountProofs(Context context) {
        return getAccountProofs(context, true);
    }

    public static HashSet<AccountProof> getAccountProofs(Context context, boolean onlyJASupported) {
        JuickAdvancedApplication.initAuthorizers(context);
        HashSet<AccountProof> proofs = new HashSet<AccountProof>();
        if (JuickAPIAuthorizer.getJuickAccountName(context) != null) {
            proofs.add(new AccountProof(
                    JuickAPIAuthorizer.getJuickAccountName(context),
                    JuickAPIAuthorizer.getBasicAuthString(context),
                    JuickMessageID.CODE
                    ));
        }
        if (PointMicroBlog.instance != null && PointMicroBlog.instance.authorizer != null) {
            PointMicroBlog.instance.authorizer.maybeLoadCredentials(context);
            if (PointAuthorizer.token != null) {
                proofs.add(new AccountProof(
                        PointAuthorizer.getPointAccountName(context),
                        PointAuthorizer.getPointAccountPassword(context),
                        PointMessageID.CODE
                ));
            }
        }
        if (BNWMicroBlog.instance != null && BNWMicroBlog.instance.authorizer.getLogin() != null) {
            proofs.add(new AccountProof(
                    BNWMicroBlog.instance.authorizer.getLogin(),
                    BNWMicroBlog.instance.authorizer.getPassword(),
                    BnwMessageID.CODE
            ));
        }
        if (!onlyJASupported) {
            if (FacebookAuthorizer.oauth != null) {
                proofs.add(new AccountProof(
                        "uzer@facebook",
                        FacebookAuthorizer.oauth,
                        FacebookMessageID.CODE
                ));
            }
        }
        return proofs;
    }

    public void callXmppControlSafe(final Context context, final ClientToServer c2s, final Utils.Function<Void, ServerToClient> then) {
        if (!loggedIn) {
            then.apply(new ServerToClient("","JAXMPPClient: not logged in (yet)"));
            return;
        }
        final Exception ex = new Exception("callXmppControlSafe: handler==null");
        new Thread("callXmppControlInThread") {
            @Override
            public void run() {
                final ServerToClient serverToClient = callXmppControl(context, c2s);
                if (handler == null) {
                    ACRA.getErrorReporter().handleException(ex, false);
                } else {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (then != null) then.apply(serverToClient);
                        }
                    });
                }
            }
        }.start();
    }

    public ServerToClient callXmppControl(Context context, ClientToServer c2s) {
        String dataValue = new Gson().toJson(c2s);
        RESTResponse restResponse = Utils.postJA(context, controlURL, dataValue);
        ServerToClient result;
        if (restResponse.getErrorText() != null) {
            result = new ServerToClient(sessionId, restResponse.getErrorText());
        } else {
            try {
                result = new Gson().fromJson(restResponse.getResult(), ServerToClient.class);
            } catch (Exception e) {
                result = null;
            }
            if (result == null || !sessionId.equals(result.getSessionId())) {
                result = new ServerToClient(sessionId, "Unable to decode server reply");
            }
        }
        return result;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public boolean sendMessage(final String jid, final String message) {
        if (!loggedIn) return false;
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
        return true;
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
        JuickAdvancedApplication.showXMPPToast("JAXMPPClient disconnect");
        if (wsClient != null) {
            wsClient.shuttingDown = true;
            wsClient.disconnect();
        }
        GCMIntentService.listeners.remove(this);
        GCMIntentService.serverPingTimerListeners.remove(this);
    }

    @Override
    public void onWebSocketTextFrame(String data) throws IOException {
        someMessages = true;
        XMPPService.nWSMessages++;
        XMPPService.lastWSMessage = new Date();
        ServerToClient serverToClient = new Gson().fromJson(data, ServerToClient.class);
        if (serverToClient != null) {
            if (serverToClient.getNewInfoNotification() != null) {
                XMPPService.lastWSMessageID = serverToClient.getNewInfoNotification().getRequestId();
                ClientToServer c2s = new ClientToServer(sessionId);
                c2s.setWillSynchronize(new WillSynchronize());
                wsClient.send(new Gson().toJson(c2s));
                startSync(new Runnable() {
                    @Override
                    public void run() {}});
            }
            if (serverToClient.getPongFromServer() != null) {
                PongFromServer pongFromServer = serverToClient.getPongFromServer();
                if (pongFromServer.isShouldResetConnectionStatistics()) {
                    ConnectivityChangeReceiver.resetStatistics(context);
                }
                if (pongFromServer.getAdjustSleepInterval() != 0) {
                    ConnectivityChangeReceiver.adjustMaximumSleepInterval(context, pongFromServer.getAdjustSleepInterval());
                }
                log("pong from server ok");
                lastPongFromServer = System.currentTimeMillis();
            }
        }
    }

    @Override
    public boolean onNoDataFromSocket() {
        if (expectPongFromServer > 0) {
            if (System.currentTimeMillis() > expectPongFromServer && lastPongFromServer == -1) {
                return false;
            }
            expectPongFromServer = 0;
            log("pong time check ok");
        }
        return true;
    }



    private void callXMPPControlSafeWithArgs(Utils.Function<Void, ClientToServer> configurer, final Utils.Function<Void, ServerToClient> then) {
        ClientToServer clientToServer = new ClientToServer(sessionId);
        configurer.apply(clientToServer);
        callXmppControlSafe(context, clientToServer, new Utils.Function<Void, ServerToClient>() {
            @Override
            public Void apply(ServerToClient serverToClient) {
                try {
                    if (then == null) {
                        if (serverToClient.getErrorMessage() != null) {
                            Toast.makeText(context, serverToClient.getErrorMessage(), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(context, R.string.Done, Toast.LENGTH_LONG).show();
                        }
                    } else {
                        then.apply(serverToClient);
                    }
                } catch (Throwable e) {
                    final RuntimeException ne = new RuntimeException("Probably toast error: context=" + context + " cr=" + (context != null ? context.getResources() : null)+" em="+serverToClient.getErrorMessage(), e);
                    ACRA.getErrorReporter().handleException(ne, false);
                }
                return null;
            }
        });
    }

    String[] allXMPPMicroblogs = new String[] { JuickMessageID.CODE, PointMessageID.CODE };

    public void listenAll() {
        for (final String microblog : allXMPPMicroblogs) {
            callXMPPControlSafeWithArgs(new Utils.Function<Void, ClientToServer>() {
                @Override
                public Void apply(ClientToServer clientToServer) {
                    clientToServer.setSubscribeToAll(new SubscribeToAll("S", microblog));
                    return null;
                }
            }, null);
        }
    }

    public void unlistenAll() {
        for (final String microblog : allXMPPMicroblogs) {
            callXMPPControlSafeWithArgs(new Utils.Function<Void, ClientToServer>() {
                @Override
                public Void apply(ClientToServer clientToServer) {
                    clientToServer.setSubscribeToAll(new SubscribeToAll("U", microblog));
                    return null;
                }
            }, null);
        }
    }

    public void setupSubscriptions(final ArrayList<String> subscriptions) {
        callXMPPControlSafeWithArgs(new Utils.Function<Void, ClientToServer>() {
            @Override
            public Void apply(ClientToServer clientToServer) {
                clientToServer.setSetupSubscriptions(new SetupSubscriptions(subscriptions));
                return null;
            }
        }, null);
    }

    public void unsubscribeMessage(final MessageID mid) {
        callXMPPControlSafeWithArgs(new Utils.Function<Void, ClientToServer>() {
            @Override
            public Void apply(ClientToServer clientToServer) {
                clientToServer.setSubscribeToThread(new SubscribeToThread(mid.toString(), "R"));
                return null;
            }
        }, null);
    }

    public void subscribeMessage(final MessageID mid) {
        callXMPPControlSafeWithArgs(new Utils.Function<Void, ClientToServer>() {
            @Override
            public Void apply(ClientToServer clientToServer) {
                clientToServer.setSubscribeToThread(new SubscribeToThread(mid.toString(), "S"));
                return null;
            }
        }, null);
    }

    public void subscribeToComments(final String uname, final String microblogId) {
        callXMPPControlSafeWithArgs(new Utils.Function<Void, ClientToServer>() {
            @Override
            public Void apply(ClientToServer clientToServer) {
                clientToServer.setSubscribeToComments(new SubscribeToComments(uname, microblogId, "S"));
                return null;
            }
        }, null);
    }

    public void unsubscribeFromComments(final String uname, final String microblogId) {
        callXMPPControlSafeWithArgs(new Utils.Function<Void, ClientToServer>() {
            @Override
            public Void apply(ClientToServer clientToServer) {
                clientToServer.setSubscribeToComments(new SubscribeToComments(uname, microblogId, null));
                return null;
            }
        }, null);
    }

    public void sendDisconnect(final Utils.Function<Void, ServerToClient> then) {
        callXMPPControlSafeWithArgs(new Utils.Function<Void, ClientToServer>() {
            @Override
            public Void apply(ClientToServer clientToServer) {
                clientToServer.setDisconnect(new Disconnect());
                return null;
            }
        }, then);
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
                for(int cnt = 0; cnt < 20; cnt++) {
                    try {
                        if (sessionId != null) {
                            JASocketClient localWSClient = wsClient;
                            if (localWSClient != null) {
                                localWSClient.send(createClientPing());
                            }
                            ClientToServer c2s = new ClientToServer(sessionId);
                            c2s.setPoll(new Poll(since));
                            final ServerToClient serverToClient = callXmppControl(context, c2s);
                            if (serverToClient.getErrorMessage() == null) {
                                XMPPService.lastSuccessfulConnect = System.currentTimeMillis();
                                ArrayList<TimestampedMessage> incomingMessages = serverToClient.getIncomingMessages();
                                boolean hasSomething = false;
                                if (incomingMessages != null) {
                                    for (TimestampedMessage incomingMessage : incomingMessages) {
                                        if (incomingMessage != null && incomingMessage.getFrom() != null && incomingMessage.getMessage() != null) {
                                            if (xmppClientListener != null)
                                                xmppClientListener.onMessage(incomingMessage.getFrom(), incomingMessage.getMessage());
                                            since = Math.max(since, incomingMessage.getTimestamp());
                                            hasSomething = true;
                                        }
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
                                log("Poll: smth="+hasSomething);
                                if (hasSomething) {
                                    c2s = new ClientToServer(sessionId);
                                    c2s.setConfirmPoll(new ConfirmPoll(since));
                                    ServerToClient confirmResult = callXmppControl(context, c2s);
                                    if (confirmResult.haveMoreMessages) {
                                        continue;
                                    }
                                } else {
                                    if (xmppClientListener != null) {
                                        xmppClientListener.onAfterPoll();
                                    }
                                }
                                if (serverToClient.getLogreq()) {
                                    JuickAdvancedApplication.sendGlobalLog();
                                }
                            } else {
                                String error = serverToClient.getErrorMessage();
                                log("Poll: "+error);
                                if (error.equals(ServerToClient.NO_SUCH_SESSION)) {
                                    error = relogin();
                                }
                                if (error != null && error.startsWith(ServerToClient.NETWORK_CONNECT_ERROR)) {
                                    error = null;   // silently ignore connection errors.
                                }
                                if (error != null) {
                                    XMPPService.lastException = error;
                                    XMPPService.lastExceptionTime = System.currentTimeMillis();
                                    final String finalError = error;
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            JuickAdvancedApplication.showXMPPToast(finalError);
                                        }
                                    });
                                }
                            }
                        }
                    } catch (Exception ex) {
                        ACRA.getErrorReporter().handleException(new RuntimeException("While Ext.XMPP.Poll (not fatal)", ex));
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
                                    continue;
                            }
                        }
                    }
                    break;
                }
            }
        }.start();
    }

    private void presence(String jid, boolean b) {
        if (xmppClientListener != null)
            xmppClientListener.onPresence(jid, b);
    }

    interface XMPPClientListener {
        boolean onMessage(String jid, String message);
        boolean onPresence(String jid, boolean on);

        void onAfterPoll();
    }

    XMPPClientListener xmppClientListener;

    public void setXmppClientListener(XMPPClientListener xmppClientListener) {
        this.xmppClientListener = xmppClientListener;
    }


    @Override
    public void onGCMMessage(String message, Set<String> categories) {
        handleGCMessageFromServer(message);
    }

    @Override
    public void onUnregistration(String registrationId) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void onRegistration(final String regid) {
        Log.i("JA.SendGCMReg", "should send reg reg, sessionId="+sessionId);
        new Thread("SendGCMReg") {
            @Override
            public void run() {
                log("sending reg, sessionId="+sessionId);
                if (sessionId != null) {
                    ClientToServer c2s = new ClientToServer(sessionId);
                    c2s.setSendGCMRegistration(new SendGCMRegistration(regid));
                    callXmppControl(context, c2s);
                }
            }
        }.start();
    }

    public void onKeepAlive() {
        new Thread("Check WS") {
            @Override
            public void run() {
                if (wsClient == null) {
                    log("onKeepAlive: restarting");
                    maybeStartWSClient();
                } else {
                    try {
                        final String clientPing = createClientPing();
                        log("onKeepAlive: sending "+clientPing);
                        if (!wsClient.send(clientPing)) {
                            log("onKeepAlive: send failed");
                            wsClient.disconnect();
                            wsClient = null;
                            maybeStartWSClient();
                        } else {
                            log("onKeepAlive: send ok");
                        }
                    } catch (Exception e) {
                        log("onKeepAlive: " + e.toString());
                        if (wsClient != null) {
                            wsClient.disconnect();
                            wsClient = null;
                        }
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

    private void log(String s) {
        XMPPService.log(s);
        String jid = setup != null ? setup.jid : "null";
        if (jid == null) jid="null";
        JuickAdvancedApplication.addToGlobalLog("JXC"+(jid.contains("@local")?"(L)":"")+": "+s, null);
    }


}
