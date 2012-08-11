package com.juick.android;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.widget.Toast;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;

import javax.net.SocketFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 8/8/12
 * Time: 3:12 AM
 * To change this template use File | Settings | File Templates.
 */
public class XMPPService extends Service {

    XMPPConnection connection;
    Handler handler;
    Chat juickChat;
    ArrayList<Utils.Function<Void,Message>> messageReceivers = new ArrayList<Utils.Function<Void, Message>>();
    public static final String ACTION_MESSAGE_RECEIVED = "com.juick.android.action.ACTION_MESSAGE_RECEIVED";
    private final IBinder mBinder = new Utils.ServiceGetter.LocalBinder<XMPPService>(this);


    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getBooleanExtra("terminate", false)) {
            cleanup("user terminated");
            stopSelf();
        } else {
            startup();
        }
        return super.onStartCommand(intent, flags, startId);
    }


    Thread currentThread;


    String JUICK_ID="juick@juick.com/Juick";

    private void startup() {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        boolean useXMPP = sp.getBoolean("useXMPP", false);
        final String username = sp.getString("xmpp_username", "");
        final String password = sp.getString("xmpp_password", "");
        final String resource = sp.getString("xmpp_resource","");
        final String server = sp.getString("xmpp_server","");
        String port = sp.getString("xmpp_port","");
        String priority = sp.getString("xmpp_priority","55");
        final boolean secure = sp.getBoolean("xmpp_force_encryption", false);
        int iPort = 0;
        int iPriority = 0;
        try {iPort = Integer.parseInt(port); } catch (NumberFormatException e) {
            Toast.makeText(XMPPService.this, "Invalid port. ", Toast.LENGTH_SHORT).show();
            return;
        }
        try {iPriority = Integer.parseInt(priority); } catch (NumberFormatException e) {
            Toast.makeText(XMPPService.this, "Invalid priority. ", Toast.LENGTH_SHORT).show();
            return;
        }
        if (useXMPP) {
            final int finalIPort = iPort;
            final int finalIPriority = iPriority;
            (currentThread = new Thread() {
                @Override
                public void run() {
                    try {
                        ConnectionConfiguration configuration = new ConnectionConfiguration(server, finalIPort, server);
                        configuration.setSecurityMode(secure ? ConnectionConfiguration.SecurityMode.required : ConnectionConfiguration.SecurityMode.enabled);
                        configuration.setReconnectionAllowed(true);
                        SASLAuthentication.supportSASLMechanism("PLAIN");
                        //configuration.setSASLAuthenticationEnabled(secure);
                        //configuration.setCompressionEnabled(true);
                        connection = new XMPPConnection(configuration);
                        int delay = 1000;
                        while(true) {
                            connection.connect();
                            if (connection.isConnected()) break;
                            try {
                                Thread.sleep(delay);
                            } catch (InterruptedException e) {
                                cleanup("connect interrupted");
                                return;
                            }
                            delay *= 2;
                            if (delay > 15*60*1000) {
                                delay = 15*60*1000;
                            }
                        }
                        String fullUsername = username;
                        if (fullUsername.indexOf("@") != -1)
                            fullUsername = fullUsername.substring(0, fullUsername.indexOf("@"));
                        connection.login(fullUsername, password, resource);
                    } catch (final XMPPException e) {
                        if (currentThread() == currentThread) {
                            connection.disconnect();
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(XMPPService.this, "XMPP Connect: " + e.toString(), Toast.LENGTH_LONG).show();
                                }
                            });
                            connection = null;
                            currentThread = null;
                        }
                        return;
                    }
                    if (currentThread() != currentThread) return;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(XMPPService.this, "XMPP connect OK", Toast.LENGTH_SHORT).show();
                        }
                    });
                    Roster roster = connection.getRoster();
                    roster.addRosterListener(new RosterListener() {
                        @Override
                        public void entriesAdded(Collection<String> addresses) {
                            System.out.println();
                        }

                        @Override
                        public void entriesUpdated(Collection<String> addresses) {
                            System.out.println();
                        }

                        @Override
                        public void entriesDeleted(Collection<String> addresses) {
                            //To change body of implemented methods use File | Settings | File Templates.
                        }

                        @Override
                        public void presenceChanged(Presence presence) {
                            if (presence.isAvailable() && presence.getFrom().equals(JUICK_ID)) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(XMPPService.this, "JUICK BOT ONLINE", Toast.LENGTH_SHORT).show();
                                    }
                                });
                                juickChat = connection.getChatManager().createChat(JUICK_ID, new MessageListener() {
                                    @Override
                                    public void processMessage(Chat chat, Message message) {
                                        for (Utils.Function<Void, Message> messageReceiver : (Iterable<? extends Utils.Function<Void,Message>>) messageReceivers.clone()) {
                                            messageReceiver.apply(message);
                                        }
                                    }
                                });
                                sendJuickMessage("ON", new Utils.Function<Void,Message>() {
                                    @Override
                                    public Void apply(Message message) {
                                        if (message.getBody().contains("Delivery of messages is enabled")) {
                                            messageReceivers.remove(this);
                                            messageReceivers.add(new Utils.Function<Void, Message>() {
                                                @Override
                                                public Void apply(Message message) {
                                                    // general juick message receiver
                                                    if (JUICK_ID.equals(message.getFrom())) {
                                                        handleJuickMessage(message);
                                                    }
                                                    return null;
                                                }
                                            });
                                        }
                                        return null;
                                    }
                                });
                                connection.addPacketListener(new PacketListener() {
                                    @Override
                                    public void processPacket(Packet packet) {
                                        if (packet instanceof Message && !JUICK_ID.equals(packet.getFrom())) {
                                            handleTextMessage((Message) packet);
                                        }
                                    }
                                }, new MessageTypeFilter(Message.Type.chat));
                                connection.addPacketListener(new PacketListener() {
                                    @Override
                                    public void processPacket(Packet packet) {
                                        if (packet instanceof Message && !JUICK_ID.equals(packet.getFrom())) {
                                            handleTextMessage((Message) packet);
                                        }
                                    }
                                }, new MessageTypeFilter(Message.Type.normal));
                                connection.sendPacket(new Presence(Presence.Type.available, "android juick client here", finalIPriority, Presence.Mode.available));
                            }
                        }
                    });
                }

            }).start();
        }

    }

    public void removeReceivedMessages(int mid) {
        Iterator<IncomingMessage> iterator = incomingMessages.iterator();
        while (iterator.hasNext()) {
            IncomingMessage next = iterator.next();
            if (next instanceof JuickIncomingMessage) {
                JuickIncomingMessage jim = (JuickIncomingMessage)next;
                if (jim.getPureThread() == mid) {
                    iterator.remove();
                }
            }
        }
    }

    public void removeMessage(IncomingMessage incomingMessage) {
        incomingMessages.remove(incomingMessage);
    }

    public void removeMessages(Class messageClass) {
        Iterator<IncomingMessage> iterator = incomingMessages.iterator();
        while (iterator.hasNext()) {
            IncomingMessage next = iterator.next();
            if (next.getClass() == messageClass) {
                iterator.remove();
            }
        }
    }

    public static abstract class IncomingMessage {
        protected String from;
        String body;

        IncomingMessage(String from, String body) {
            this.from = from;
            this.body = body;
        }

        public String getBody() {

            return body;
        }

        public String getFrom() {
            return from;
        }


    }

    public static abstract class JuickIncomingMessage extends IncomingMessage {
        String messageNo;

        public JuickIncomingMessage(String from, String body, String messageNo) {
            super(from, body);
            this.messageNo = messageNo;
        }
        public String getFrom() {
            if (from.startsWith("@")) return from;
            return "@"+from;
        }
        public int getPureThread() {
            try {
                int ix = messageNo.indexOf("/");
                if (ix ==-1) return Integer.parseInt(messageNo.substring(1));
                int ix2 = messageNo.indexOf("#");   // -1 not found
                return Integer.parseInt(messageNo.substring(ix2+1, ix));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }

    public static class JuickPrivateIncomingMessage extends JuickIncomingMessage {
        public JuickPrivateIncomingMessage(String from, String body, String messageNo) {
            super(from, body, messageNo);
        }
    }

    public static class JuickThreadIncomingMessage extends JuickIncomingMessage {
        public JuickThreadIncomingMessage(String from, String body, String messageNo) {
            super(from, body, messageNo);
        }
    }

    public static class JuickSubscriptionIncomingMessage extends JuickIncomingMessage {
        public JuickSubscriptionIncomingMessage(String from, String body, String messageNo) {
            super(from, body, messageNo);
        }
    }

    public static class JabberIncomingMessage extends IncomingMessage {
        JabberIncomingMessage(String from, String body) {
            super(from, body);
        }
    }

    public ArrayList<IncomingMessage> incomingMessages = new ArrayList<IncomingMessage>();

    private void handleJuickMessage(Message message) {
        String[] split = message.getBody().split("\n");
        String head = split[0];
        boolean handled = false;
        if (head.startsWith("Reply by @")) {
            int colon = head.indexOf(":");
            String username = head.substring(10, colon);
            try {
                String msgno = split[split.length - 1].trim().split(" ")[0].trim();
                if (msgno.startsWith("#")) {
                    StringBuilder sb = new StringBuilder();
                    for(int i=3; i<split.length-2; i++) {
                        sb.append(split[i]);
                        sb.append("\n");
                    }
                    handled = true;
                    incomingMessages.add(new JuickThreadIncomingMessage(username, sb.toString(), msgno));
                }
            } catch (Exception ex) {
                //
            }
        }
        if (head.startsWith("@") && head.contains(":")) {
            int colon = head.indexOf(":");
            if (colon != -1) {
                String username = head.substring(0, colon);
                if (head.indexOf("*private") != -1) {
                    if (split.length >= 7) {
                        try {
                            String msgno = split[split.length - 1].trim().split(" ")[0].trim();
                            if (msgno.startsWith("#")) {
                                StringBuilder sb = new StringBuilder();
                                for(int i=1; i<split.length-4; i++) {
                                    sb.append(split[i]);
                                    sb.append("\n");
                                }
                                if (sb.length() > 1 && sb.charAt(sb.length()-2) == '\n') {
                                    sb. setLength(sb.length()-1);       // remove last empty line

                                }
                                handled = true;
                                incomingMessages.add(new JuickPrivateIncomingMessage(username, sb.toString(), msgno));
                            }
                        } catch (Exception ex) {
                            //
                        }
                    }
                } else {
                    String last = split[split.length-1];
                    String[] msgNoAndURL = last.trim().split(" ");
                    if (msgNoAndURL.length == 2 && msgNoAndURL[0].trim().startsWith("#")) {
                        String msgNo = msgNoAndURL[0].trim();
                        String url = msgNoAndURL[1].trim();
                        if (url.equals("http://juick.com/"+msgNo.substring(1))) {
                            StringBuilder sb = new StringBuilder();
                            for(int i=1; i<split.length-1; i++) {
                                sb.append(split[i]);
                                sb.append("\n");
                            }
                            incomingMessages.add(new JuickSubscriptionIncomingMessage(username, sb.toString(), msgNo));
                            handled = true;
                        }
                    }
                }
            }
        }
        if (!handled) {
            incomingMessages.add(new JabberIncomingMessage(message.getFrom(), message.getBody()));
        }
        sendMyBroadcast();
        System.out.println();
    }

    private void sendMyBroadcast() {
        Intent intent = new Intent();
        intent.setAction(ACTION_MESSAGE_RECEIVED);
        intent.putExtra("messagesCount", incomingMessages.size());
        sendBroadcast(intent);
    }

    private void handleTextMessage(Message message) {
        incomingMessages.add(new JabberIncomingMessage(message.getFrom(), message.getBody()));
        sendMyBroadcast();
    }

    private void sendJuickMessage(String text, Utils.Function<Void, Message> function) {
        try {
            messageReceivers.add(function);
            juickChat.sendMessage(text);
        } catch (XMPPException e) {
            cleanup("Error sending message");
        }
    }

    private void cleanup(final String reason) {
        if (connection != null) {
            connection.disconnect();
            connection = null;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(XMPPService.this, "XMPP Disconnected: "+reason, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    public void onCreate() {
        handler = new Handler();
        super.onCreate();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(this);
        super.onDestroy();    //To change body of overridden methods use File | Settings | File Templates.
    }
}
