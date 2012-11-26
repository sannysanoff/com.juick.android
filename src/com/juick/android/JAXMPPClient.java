package com.juick.android;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;
import com.google.gson.Gson;
import com.juickadvanced.xmpp.ClientToServer;
import com.juickadvanced.xmpp.ServerToClient;
import com.juickadvanced.xmpp.XMPPConnectionSetup;
import com.juickadvanced.xmpp.commands.*;
import com.juickadvanced.xmpp.messages.ContactOffline;
import com.juickadvanced.xmpp.messages.ContactOnline;
import com.juickadvanced.xmpp.messages.TimestampedMessage;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/18/12
 * Time: 12:32 AM
 * To change this template use File | Settings | File Templates.
 */
public class JAXMPPClient {
    //String url = "http://192.168.1.77:8222/xmpp/control";
    String url = "http://ja.ip.rt.ru:8222/xmpp/control";
    String sessionId;
    HashSet<String> wachedJids;
    long since;
    Context context;
    private Handler handler;
    XMPPConnectionSetup setup;
    String username;

    public JAXMPPClient() {
    }

    public String loginXMPP(Context context, Handler handler, XMPPConnectionSetup setup, HashSet<String> wachedJids) {
        this.wachedJids = wachedJids;
        this.context = context;
        this.setup = setup;
        this.handler = handler;
        return performLogin(context, setup);
    }

    public String loginLocal(Context context, Handler handler, String username) {
        this.context = context;
        this.handler = handler;
        this.username = username;
        return performLoginLocal(context, username);
    }

    private String performLogin(Context context, XMPPConnectionSetup setup) {
        sessionId = Math.random()+"-"+System.currentTimeMillis()+"-"+setup.login;
        ClientToServer c2s = new ClientToServer(sessionId);
        c2s.setLogin(new Login(setup, this.wachedJids, JuickAdvancedApplication.registrationId));
        ServerToClient serverToClient = callXmppControl(context, c2s);
        return serverToClient.getErrorMessage();
    }

    private String performLoginLocal(Context context, String username) {
        sessionId = Math.random()+"-"+System.currentTimeMillis()+"-"+setup.login;
        ClientToServer c2s = new ClientToServer(sessionId);
        XMPPConnectionSetup setup = new XMPPConnectionSetup();
        setup.jid = username+"@local";
        c2s.setLogin(new Login(setup, new HashSet<String>(), JuickAdvancedApplication.registrationId));
        ServerToClient serverToClient = callXmppControl(context, c2s);
        return serverToClient.getErrorMessage();
    }

    private ServerToClient callXmppControl(Context context, ClientToServer c2s) {
        String data = "data="+ Uri.encode(new Gson().toJson(c2s));
        Utils.RESTResponse restResponse = Utils.postJSON(context, url, data);
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

    public void handleMessageFromServer(String messag) {
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
        ClientToServer c2s = new ClientToServer(sessionId);
        c2s.setDisconnect(new Disconnect());
        callXmppControl(context, c2s);
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
}
