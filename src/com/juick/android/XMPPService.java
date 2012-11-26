package com.juick.android;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;
import com.google.gson.Gson;
import com.juick.android.api.JuickMessage;
import com.juick.android.api.MessageID;
import com.juick.android.juick.JuickMessageID;
import com.juickadvanced.xmpp.XMPPConnectionSetup;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;

import java.io.*;
import java.net.SocketException;
import java.util.*;

/**
 */
public class XMPPService extends Service  {

    private static final IncomingMessage DUMMY = new IncomingMessage("", "", null) {
    };
    ArrayList<XMPPConnection> connections = new ArrayList<XMPPConnection>();
    Handler handler;
    Chat juickChat;
    Chat juboChat;
    ArrayList<Utils.Function<Void, Message>> messageReceivers = new ArrayList<Utils.Function<Void, Message>>();
    public static final String ACTION_MESSAGE_RECEIVED = "com.juickadvanced.android.action.ACTION_MESSAGE_RECEIVED";
    public static final String ACTION_LAUNCH_MESSAGELIST = "com.juickadvanced.android.action.ACTION_LAUNCH_MESSAGELIST";
    private final IBinder mBinder = new Utils.ServiceGetter.LocalBinder<XMPPService>(this);
    int reconnectDelay = 10000;
    int messagesReceived = 0;
    public boolean botOnline;
    public boolean juboOnline;
    static HashMap<MessageID, JuickIncomingMessage> cachedTopicStarters = new HashMap<MessageID, XMPPService.JuickIncomingMessage>();
    public String juboRSS;
    public String juboRSSError;
    public static JuboMessageFilter juboMessageFilter;
    public static JuickBlacklist juickBlacklist;
    public static JuboMessageFilter juboMessageFilter_tmp;  // restored from file until fresh comes
    public static JuickBlacklist juickBlacklist_tmp; // restored from file until fresh comes
    public static long lastSuccessfulConnect;
    static public Date lastGCMMessage;
    static public String lastGCMMessageID;
    static public long lastAlarmScheduled;
    static public long lastAlarmFired;
    public int nGCMMessages;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log("onStartCommand");
        try {
            if (intent != null && intent.getBooleanExtra("terminate", false)) {
                handler.removeCallbacksAndMessages(null);
                String message = intent.getStringExtra("terminateMessage");
                if (message == null) message = "user terminated";
                cleanup(message);
                stopSelf();
            } else {
                if (startId != 2) {     // i don't know what is this, really
                    if (!up)
                        startup();
                }
            }
            return super.onStartCommand(intent, flags, startId);
        } finally {
            log("onStartCommand ended.");
        }
    }

    void log(String str) {
        Log.w("com.juickadvanced", "XMPPService: " + str);
    }


    Thread currentThread;
    SharedPreferences sp;


    String JUICK_ID = "juick@juick.com/Juick";
    String JUBO_ID = "jubo@nologin.ru/jubo";
    public static String lastException;

    public void startup() {
        log("startup()");
        lastException = null;
        botOnline = false;
        juboOnline = false;
        if (sp == null)
            sp = PreferenceManager.getDefaultSharedPreferences(this);
        boolean useXMPP = sp.getBoolean("useXMPP", false);
        boolean xmppExternal = sp.getBoolean("xmpp_external", false);
        boolean useXMPPOnlyForBL = sp.getBoolean("useXMPPOnlyForBL", false);
        if (useXMPP && useXMPPOnlyForBL && juboMessageFilter != null && juboMessageFilter != null) {
            // we have everything.
            useXMPP = false;
        }
        Gson gson = new Gson();
        final XMPPConnectionSetup connectionArgs = gson.fromJson(sp.getString("xmpp_config", ""), XMPPConnectionSetup.class);
        synchronized (connections) {
            log("enter sync");
            if (useXMPP && connectionArgs != null && connections.size() == 0) {
                if (xmppExternal) {
                    (currentThread = new ExternalXMPPThread(connectionArgs)).start();
                } else {
                    (currentThread = new SmackThread(connectionArgs)).start();
                }
            }
        }
        log("leave startup()");
        up = true;
    }

    public static JuboMessageFilter getAnyJuboMessageFilter() {
        if (juboMessageFilter != null) return juboMessageFilter;
        if (juboMessageFilter_tmp != null) return juboMessageFilter_tmp;
        return null;
    }

    public static JuickBlacklist getAnyJuickBlacklist() {
        if (juickBlacklist != null) return juickBlacklist;
        if (juickBlacklist_tmp != null) return juickBlacklist_tmp;
        return null;
    }

    public static class JuickBlacklist {
        public ArrayList<String> stopUsers = new ArrayList<String>();
        public ArrayList<String> stopTags = new ArrayList<String>();

        public boolean allowMessage(JuickMessage message) {
            for (String stopTag : stopTags) {
                for (String tag : message.tags) {
                    if (tag.startsWith("*")) tag = tag.substring(1);
                    if (tag.toLowerCase().equals(stopTag)) return false;
                }
            }
            for (String stopUser : stopUsers) {
                if (stopUser.equals(message.User.UName.toLowerCase())) {
                    return false;
                }
            }
            return true;
        }

        public boolean valid() {
            return stopUsers != null && stopTags != null;
        }

        public String info() {
            return (stopUsers.size() + stopTags.size()) + " rules";
        }
    }

    public static class JuboMessageFilter {
        public ArrayList<String> stopWords = new ArrayList<String>();
        public ArrayList<String> stopTags = new ArrayList<String>();
        public ArrayList<String> subscribedWords = new ArrayList<String>();
        public ArrayList<String> subscribedTags = new ArrayList<String>();

        public boolean allowXMPPMessage(IncomingMessage message, SharedPreferences sp) {
            if (message instanceof JuickThreadIncomingMessage) {
                JuickSubscriptionIncomingMessage jtim = (JuickSubscriptionIncomingMessage) message;
                for (String stopTag : stopTags) {
                    for (String tag : jtim.tags) {
                        if (tag.startsWith("*")) tag = tag.substring(1);
                        if (tag.toLowerCase().equals(stopTag)) return false;
                    }
                }
            }
            String s = message.getBody().toLowerCase();
            StringBuilder sb = preparePipedStringBuilder(s);
            for (String stopWord : stopWords) {
                if (sb.indexOf(stopWord) != -1) return false;
            }
            if (sp.getBoolean("juboFixJuickKeyword", true)) {
                if (s.startsWith("http://i.juick.com/") && subscribedWords.indexOf("juick") != -1) {
                    // jubo selectes these messages because of the "juick" word inside url.
                    // i don't want that, Reparse manually.
                    int endOfLine = s.indexOf("\n");
                    sb.delete(0, endOfLine + 1);
                    // now we do not have url in the body.
                    boolean pass = false;
                    for (String subscribedWord : subscribedWords) {
                        if (sb.indexOf(subscribedWord) != -1) {
                            pass = true;
                            break;
                        }
                    }
                    if (!pass) {
                        // whitelist not hit. go away.
                        return false;
                    }
                }
            }
            return true;
        }

        private StringBuilder preparePipedStringBuilder(String s) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char cha = s.charAt(i);
                if (Character.isLetterOrDigit(cha) || cha == '#' || cha == '@') {
                    sb.append(cha);
                } else {
                    sb.append("|");
                }
            }
            return sb;
        }

        // only does blacklisting for ALL MESSAGES
        public boolean allowMessage(JuickMessage message) {
            for (String stopTag : stopTags) {
                for (String tag : message.tags) {
                    if (tag.startsWith("*")) tag = tag.substring(1);
                    if (tag.toLowerCase().equals(stopTag)) return false;
                }
            }
            StringBuilder sb = preparePipedStringBuilder(message.Text);
            for (String stopWord : stopWords) {
                if (sb.indexOf(stopWord) != -1) {
                    return false;
                }
            }
            return true;
        }

        public boolean valid() {
            return stopTags != null && stopWords != null && subscribedTags != null && subscribedWords != null;
        }

        public String info() {
            return (stopTags.size() + stopWords.size() + subscribedTags.size() + subscribedWords.size()) + " rules";
        }
    }

    private void parseJuboSubscriptions(String mbody) {
        String[] split = mbody.split("\n");
        boolean okToParse = false;
        JuboMessageFilter filter = new JuboMessageFilter();
        for (String s : split) {
            if (s.startsWith("Ключевые слова")) {
                okToParse = true;
                continue;
            }
            s = s.trim();
            if (okToParse && s.length() > 0) {
                boolean tag = s.indexOf("{tag:*") != -1;
                boolean stop = s.indexOf("-{") != -1 || s.indexOf("{tag:-*") != -1 || s.startsWith("-");
                if (tag) {
                    if (stop) {
                        if (s.startsWith("-")) s = s.substring(1);
                        if (s.indexOf("tag:-*") != -1) s = s.replace("tag:-*", "");
                    }
                    if (tag) {
                        s = s.replace("{tag:", "").replace("}", "").replace("*", "");
                    }
                }
                if (tag && stop) filter.stopTags.add(s.toLowerCase());
                if (tag && !stop) filter.subscribedTags.add(s.toLowerCase());
                if (!tag && stop) filter.stopWords.add(s.toLowerCase());
                if (!tag && !stop) filter.subscribedWords.add(s.toLowerCase());
            }
        }
        this.juboMessageFilter = filter;
        this.juboMessageFilter_tmp = null;
        writeStringToFile(getCachedJuboFile(), new Gson().toJson(this.juboMessageFilter));
        maybeTerminateXMPP();
    }

    private File getCachedJuboFile() {
        return new File(getCacheDir(), "cached_jubo_filter.json");
    }

    private void parseJuickBlacklist(String mbody) {
        String[] split = mbody.split("\n");
        boolean okToParse = false;
        JuickBlacklist filter = new JuickBlacklist();
        for (String s : split) {
            if (s.startsWith("Your blacklist:")) {
                okToParse = true;
                continue;
            }
            s = s.trim();
            if (okToParse && s.length() > 0) {
                if (s.startsWith("@")) {
                    filter.stopUsers.add(s.substring(1).toLowerCase());
                }
                if (s.startsWith("*")) {
                    filter.stopTags.add(s.substring(1).toLowerCase());
                }
            }
        }
        this.juickBlacklist = filter;
        this.juickBlacklist_tmp = null;
        writeStringToFile(getCachedJuickFile(), new Gson().toJson(this.juickBlacklist));
        maybeTerminateXMPP();

    }

    public static void writeStringToFile(File destFile, String stringToWrite) {
        try {
            FileOutputStream fos = new FileOutputStream(destFile);
            fos.write(stringToWrite.getBytes());
            fos.close();
        } catch (Exception ex) {
            Log.e("JuickAdvanced", ex.toString());
        }
    }

    private void maybeTerminateXMPP() {
        if (juickBlacklist != null && juboMessageFilter != null && sp.getBoolean("useXMPPOnlyForBL", false)) {
            cleanup("blacklists obtained OK");
        }
    }

    private File getCachedJuickFile() {
        return new File(getCacheDir(), "cached_juick_blacklist.json");
    }

    private boolean verboseXMPP() {
        return sp.getBoolean("xmpp_verbose", false);
    }

    boolean scheduledForReconnect = false;


    PacketListener packetListener = new PacketListener() {
        @Override
        public void processPacket(Packet packet) {
            messagesReceived++;
            if (packet instanceof Message) {
                Message msg = (Message) packet;
                if (JUBO_ID.equalsIgnoreCase(packet.getFrom())) {
                    handleJuickMessage(msg.getFrom(), msg.getBody());
                } else if (JUICK_ID.equals(packet.getFrom())) {
                    handleJuickMessage(msg.getFrom(), msg.getBody());
                    // handled elsewhere
                } else if (!JUICK_ID.equals(packet.getFrom())) {
                    handleTextMessage(msg.getFrom(), msg.getBody());
                }
            }
        }
    };

    PacketListener packetListener2 = new PacketListener() {
        @Override
        public void processPacket(Packet packet) {
            messagesReceived++;
            if (packet instanceof Message && !JUICK_ID.equals(packet.getFrom())) {
                handleTextMessage(packet.getFrom(), ((Message) packet).getBody());
            }
        }
    };

    public void removeMessagesFrom(String user) {
        boolean changed = false;
        synchronized (incomingMessages) {
            Iterator<IncomingMessage> iterator = incomingMessages.iterator();
            while (iterator.hasNext()) {
                IncomingMessage next = iterator.next();
                if (next instanceof JuickIncomingMessage) {
                    JuickIncomingMessage jim = (JuickIncomingMessage) next;
                    if (jim.getFrom().equals(user)) {
                        removeMessageFile(jim.id);
                        iterator.remove();
                        changed = true;
                    }
                }
            }
        }
        if (changed)
            maybeCancelNotification();
    }

    public void removeMessages(MessageID mid, boolean keepReplyNotifications) {
        boolean changed = false;
        synchronized (incomingMessages) {
            Iterator<IncomingMessage> iterator = incomingMessages.iterator();
            while (iterator.hasNext()) {
                IncomingMessage next = iterator.next();
                if (next instanceof JuickIncomingMessage) {
                    JuickIncomingMessage jim = (JuickIncomingMessage) next;
                    if (jim.getMID().equals(mid)) {
                        if (keepReplyNotifications && jim instanceof JuickThreadIncomingMessage) {
                            continue;
                        }
                        removeMessageFile(jim.id);
                        iterator.remove();
                        changed = true;
                    }
                }
            }
        }
        if (changed)
            maybeCancelNotification();
    }

    public void maybeCancelNotification() {
        if (incomingMessages.size() == 0)
            XMPPMessageReceiver.cancelInfo(this);
        else
            XMPPMessageReceiver.updateInfo(this, incomingMessages.size(), true);
    }

    public void removeMessage(IncomingMessage incomingMessage) {
        synchronized (incomingMessage) {
            removeMessageFile(incomingMessage.id);
            incomingMessages.remove(incomingMessage);
        }
        maybeCancelNotification();
    }

    public void removeMessages(Class messageClass) {
        synchronized (incomingMessages) {
            Iterator<IncomingMessage> iterator = incomingMessages.iterator();
            while (iterator.hasNext()) {
                IncomingMessage next = iterator.next();
                if (next.getClass() == messageClass) {
                    removeMessageFile(next.id);
                    iterator.remove();
                }
            }
        }
        maybeCancelNotification();
    }

    public void requestMessageBody(MessageID finalTopicMessageId) {
        try {
            String messageRequest = "#" + ((JuickMessageID) finalTopicMessageId).getMid();
            if (juickChat != null) {
                juickChat.sendMessage(messageRequest);
            }
            if (currentThread instanceof ExternalXMPPThread) {
                ExternalXMPPThread externalXMPPThread = (ExternalXMPPThread)currentThread;
                externalXMPPThread.client.sendMessage(JUICK_ID, messageRequest);
            }
        } catch (XMPPException e) {
            Toast.makeText(this, "requestMessageBody: " + e.toString(), Toast.LENGTH_LONG).show();
        }
    }


    static int message_seq = 0;

    public void askJuboRSS() {
        juboRSSError = "No response from JuBo";
        if (currentThread instanceof ExternalXMPPThread) {
            final ExternalXMPPThread et = (ExternalXMPPThread)currentThread;
            et.nextListener = new JAXMPPClient.XMPPClientListener() {
                @Override
                public boolean onMessage(String jid, String message) {
                    if (jid.equals(JUBO_ID)) {
                        String mbody = message;
                        if (mbody.contains("rss start")) {              // issue 'rss start' to start
                            juboRSSError = "Response to 'rss start' did not come yet";
                            et.client.sendMessage(JUBO_ID, "rss start");
                        }
                        if (mbody.contains("http://") && mbody.contains("/rss/")) {     // rss start response
                            int start = mbody.indexOf("http://");
                            int end = mbody.indexOf(".xml");
                            if (start != -1 && end != -1) {
                                end += 4;
                            }
                            juboRSS = mbody.substring(start, end);
                            if (et.nextListener == this) et.nextListener = null;
                        }
                        return true;
                    }
                    return false;
                }

                @Override
                public boolean onPresence(String jid, boolean on) {
                    return false;
                }
            };
            et.client.sendMessage(JUBO_ID, "rss");
        } else {
            juboChat = connections.get(connections.size() - 1).getChatManager().createChat(JUBO_ID, new MessageListener() {
                @Override
                public void processMessage(Chat chat, Message message) {
                    String mbody = message.getBody();
                    if (mbody.contains("rss start")) {              // issue 'rss start' to start
                        try {
                            juboRSSError = "Response to 'rss start' did not come yet";
                            juboChat.sendMessage("rss start");
                        } catch (XMPPException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                    }
                    if (mbody.contains("http://") && mbody.contains("/rss/")) {     // rss start response
                        int start = mbody.indexOf("http://");
                        int end = mbody.indexOf(".xml");
                        if (start != -1 && end != -1) {
                            end += 4;
                        }
                        juboRSS = mbody.substring(start, end);
                    }
                }
            });
            try {
                juboChat.sendMessage("rss");
            } catch (XMPPException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }

    public static abstract class IncomingMessage implements Serializable {
        protected String from;
        String body;
        String id;
        Date datetime;

        IncomingMessage(String from, String body, Date datetime) {
            this.from = from;
            this.body = body;
            this.datetime = datetime;
            synchronized (IncomingMessage.class) {
                this.id = System.currentTimeMillis() + "_" + (message_seq++);
            }
        }

        public String getBody() {

            return body;
        }

        public String getFrom() {
            return from;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            IncomingMessage that = (IncomingMessage) o;

            if (id != null ? !id.equals(that.id) : that.id != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return id != null ? id.hashCode() : 0;
        }
    }

    public static abstract class JuickIncomingMessage extends IncomingMessage {
        String messageNoPlain;

        public JuickIncomingMessage(String from, String body, String messageNoPlain, Date date) {
            super(from, body, date);
            this.messageNoPlain = messageNoPlain;
        }

        public String getFrom() {
            if (from.startsWith("@")) return from;
            return "@" + from;
        }

        public JuickMessageID getMID() {
            try {
                int ix = messageNoPlain.indexOf("/");
                if (ix == -1) return new JuickMessageID(Integer.parseInt(messageNoPlain.substring(1)));
                int ix2 = messageNoPlain.indexOf("#");   // -1 not found
                return new JuickMessageID(Integer.parseInt(messageNoPlain.substring(ix2 + 1, ix)));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public int getRID() {
            try {
                int ix = messageNoPlain.indexOf("/");
                if (ix == -1) return 0;
                return Integer.parseInt(messageNoPlain.substring(ix + 1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }

    public static class JuickPrivateIncomingMessage extends JuickIncomingMessage {
        public JuickPrivateIncomingMessage(String from, String body, String messageNo, Date date) {
            super(from, body, messageNo, date);
        }
    }

    public static class JuickThreadIncomingMessage extends JuickIncomingMessage {
        private String originalBody;
        private String originalFrom;

        public JuickThreadIncomingMessage(String from, String body, String messageNo, Date date) {
            super(from, body, messageNo, date);
        }

        public CharSequence getOriginalBody() {
            return originalBody;
        }

        public CharSequence getOriginalFrom() {
            return originalFrom;
        }

        public void setOriginalBody(String originalBody) {
            this.originalBody = originalBody;
        }

        public void setOriginalFrom(String originalFrom) {
            this.originalFrom = originalFrom;
        }
    }

    public static class JuickSubscriptionIncomingMessage extends JuickIncomingMessage {
        String[] tags;

        public JuickSubscriptionIncomingMessage(String from, String body, String messageNo, String[] tags, Date date) {
            super(from, body, messageNo, date);
            this.tags = tags;
        }
    }

    public static class JabberIncomingMessage extends IncomingMessage {
        JabberIncomingMessage(String from, String body, Date date) {
            super(from, body, date);
        }
    }

    public ArrayList<IncomingMessage> incomingMessages = new ArrayList<IncomingMessage>();

    private void handleJuickMessage(String from, String body) {
        IncomingMessage handled = null;
        boolean silent = false;
        synchronized (incomingMessages) {
            boolean isFromJuBo = JUBO_ID.equalsIgnoreCase(from);
            boolean isFromJuick = JUICK_ID.equals(from);
            if (body.startsWith("Найденные") && isFromJuBo || body.startsWith("Recommended by @") && isFromJuick) {
                int cr = body.indexOf("\n");
                if (cr != -1 && body.length() > cr) {
                    body = body.substring(cr + 1);
                }
            }
            String[] split = body.split("\n");
            String head = split[0];
            if (head.startsWith("Reply by @")) {
                int colon = head.indexOf(":");
                String username = head.substring(10, colon);
                try {
                    String msgno = split[split.length - 1].trim().split(" ")[0].trim();
                    if (msgno.startsWith("#")) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 3; i < split.length - 2; i++) {
                            sb.append(split[i]);
                            sb.append("\n");
                        }
                        JuickThreadIncomingMessage threadIncomingMessage = new JuickThreadIncomingMessage(username, sb.toString(), msgno, new Date());
                        XMPPService.JuickIncomingMessage topicStarter = cachedTopicStarters.get(threadIncomingMessage.getMID());
                        if (topicStarter == null) {
                            topicStarter = new JuickThreadIncomingMessage("@???", "", "#" + ((JuickMessageID) threadIncomingMessage.getMID()).getMid(), new Date());    // put placeholder for details
                            cachedTopicStarters.put(threadIncomingMessage.getMID(), topicStarter);
                            requestMessageBody(threadIncomingMessage.getMID());
                        } else {
                            threadIncomingMessage.setOriginalBody(topicStarter.getBody());
                            threadIncomingMessage.setOriginalFrom(topicStarter.getFrom());
                        }
                        saveMessage(threadIncomingMessage);
                        incomingMessages.add(threadIncomingMessage);
                        handled = threadIncomingMessage;
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
                                    for (int i = 1; i < split.length - 4; i++) {
                                        sb.append(split[i]);
                                        sb.append("\n");
                                    }
                                    if (sb.length() > 1 && sb.charAt(sb.length() - 2) == '\n') {
                                        sb.setLength(sb.length() - 1);       // remove last empty line

                                    }
                                    JuickPrivateIncomingMessage object = new JuickPrivateIncomingMessage(username, sb.toString(), msgno, new Date());
                                    saveMessage(object);
                                    handled = object;
                                    incomingMessages.add(object);
                                }
                            } catch (Exception ex) {
                                //
                            }
                        }
                    } else {
                        String last = split[split.length - 1];
                        String[] msgNoAndURL = last.trim().split(" ");
                        if (msgNoAndURL.length >= 2 && msgNoAndURL[0].trim().startsWith("#")) {
                            String msgNo = msgNoAndURL[0].trim();
                            String url = msgNoAndURL[msgNoAndURL.length - 1].trim();
                            if (url.equals("http://juick.com/" + msgNo.substring(1))) {
                                StringBuilder sb = new StringBuilder();
                                for (int i = 1; i < split.length - 1; i++) {
                                    sb.append(split[i]);
                                    sb.append("\n");
                                }
                                String[] tags = head.substring(colon + 1).split(" ");
                                JuickSubscriptionIncomingMessage subscriptionIncomingMessage = new JuickSubscriptionIncomingMessage(username, sb.toString(), msgNo, tags, new Date());
                                JuickIncomingMessage topicStarter = cachedTopicStarters.get(subscriptionIncomingMessage.getMID());
                                if (topicStarter != null && topicStarter.getBody().length() == 0) {
                                    cachedTopicStarters.put(subscriptionIncomingMessage.getMID(), subscriptionIncomingMessage);
                                    for (IncomingMessage incomingMessage : incomingMessages) {
                                        if (incomingMessage instanceof JuickThreadIncomingMessage && ((JuickThreadIncomingMessage) incomingMessage).getMID().equals(topicStarter.getMID())) {
                                            // details came!
                                            JuickThreadIncomingMessage imsg = (JuickThreadIncomingMessage) incomingMessage;
                                            imsg.setOriginalBody(subscriptionIncomingMessage.getBody());
                                            imsg.setOriginalFrom(subscriptionIncomingMessage.getFrom());
                                            saveMessage(imsg);
                                        }
                                        handled = DUMMY;
                                    }
                                } else {
                                    saveMessage(subscriptionIncomingMessage);
                                    incomingMessages.add(subscriptionIncomingMessage);
                                    handled = subscriptionIncomingMessage;
                                }
                            }
                        }
                    }
                }
            }
            if (from.equals(JUICK_ID)) {
                if (body.toLowerCase().contains("delivery of messages is")) {
                    silent = true;
                }

            }
            if (handled == null && !silent) {
                if (isFromJuBo) {
                    silent = true;
                    // keep silence
                } else if (isFromJuick && body.startsWith("Your blacklist:")) {
                    parseJuickBlacklist(body);
                    silent = true;
                } else {
                    JabberIncomingMessage messag = new JabberIncomingMessage(from, body, new Date());
                    saveMessage(messag);
                    incomingMessages.add(messag);
                    handled = messag;
                }
            }
            if (handled != null && handled != DUMMY) {
                Set<String> filteredOutUsers = JuickMessagesAdapter.getFilteredOutUsers(this);
                String fromm = handled.getFrom();
                if (fromm.startsWith("@")) {
                    fromm = fromm.substring(1);
                }
                boolean shouldDelete = filteredOutUsers.contains(fromm);

                if (!shouldDelete) {
                    if (isFromJuBo && getAnyJuboMessageFilter() != null) {
                        shouldDelete = !getAnyJuboMessageFilter().allowXMPPMessage(handled, sp);
                    }
                }

                if (shouldDelete) {
                    // kill-em
                    removeMessageFile(handled.id);
                    incomingMessages.remove(handled);
                    silent = true;
                }

            }
        }
        if (!silent)
            sendMyBroadcast(handled == DUMMY);
    }

    private void saveMessage(IncomingMessage message) {
        try {
            File xmpp_messages_v1 = getSavedMessagesDirectory();
            xmpp_messages_v1.mkdirs();
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(xmpp_messages_v1, message.id)));
            oos.writeObject(message);
            oos.close();
        } catch (IOException e) {
            Log.e("com.juickadvanced", "saveMessage", e);
        }
    }

    private void removeMessageFile(String id) {
        File xmpp_messages_v1 = getSavedMessagesDirectory();
        File file = new File(xmpp_messages_v1, id);
        file.delete();
    }

    private File getSavedMessagesDirectory() {
        return getDir("xmpp_messages_v1", MODE_PRIVATE);
    }

    private void sendMyBroadcast(boolean sound) {
        Intent intent = new Intent();
        intent.setAction(ACTION_MESSAGE_RECEIVED);
        intent.putExtra("messagesCount", incomingMessages.size());
        intent.putExtra("sound", sound);
        sendBroadcast(intent);
    }

    private void handleTextMessage(String from, String body) {
        if (body == null || body.length() == 0) {
            // other, non-text, transport-related messages
            return;
        }
        synchronized (incomingMessages) {
            JabberIncomingMessage msg = new JabberIncomingMessage(from, body, new Date());
            saveMessage(msg);
            incomingMessages.add(msg);
        }
        sendMyBroadcast(true);
    }

    private void sendJuickMessage(String text) {
        try {
            juickChat.sendMessage(text);
        } catch (XMPPException e) {
            cleanup("Error sending message");
        }
    }

    public void cleanup(final String reason) {
        ArrayList<XMPPConnection> xmppConnections = null;
        synchronized (connections) {
            xmppConnections = new ArrayList<XMPPConnection>(connections);
            connections.clear();
        }
        for (XMPPConnection connection : xmppConnections) {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception e) {
                    //
                }
                if (currentThread != null) {
                    currentThread.interrupt();
                    currentThread = null;
                }
                if (reason != null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (verboseXMPP() || reason.startsWith("!"))
                                Toast.makeText(XMPPService.this, "XMPP Disconnected: " + reason, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }
    }

    private void updateStateOnDisconnect() {
        synchronized (connections) {
            if (juickBlacklist != null) {
                juickBlacklist_tmp = juickBlacklist;
                juickBlacklist = null;
            }
            if (juboMessageFilter != null) {
                juboMessageFilter_tmp = juboMessageFilter;
                juboMessageFilter = null;
            }
        }
        botOnline = false;
        juboOnline = false;
    }

    public static String readFile(File f) {
        if (!f.exists()) return null;
        try {
            FileInputStream fis = new FileInputStream(f);
            byte[] arr = new byte[1024];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while (true) {
                int len = fis.read(arr);
                if (len < 1) break;
                baos.write(arr, 0, len);
            }
            fis.close();
            return new String(baos.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    public boolean up = false;

    @Override
    public void onCreate() {
        String cachedJubo = readFile(getCachedJuboFile());
        if (cachedJubo != null) {
            juboMessageFilter_tmp = new Gson().fromJson(cachedJubo, JuboMessageFilter.class);
            if (juboMessageFilter_tmp != null && !juboMessageFilter_tmp.valid()) {
                juboMessageFilter_tmp = null;
            }
        }
        String cachedJuick = readFile(getCachedJuickFile());
        if (cachedJuick != null) {
            juickBlacklist_tmp = new Gson().fromJson(cachedJubo, JuickBlacklist.class);
            if (juickBlacklist_tmp != null && !juickBlacklist_tmp.valid()) {
                juickBlacklist_tmp = null;
            }
        }
        handler = new Handler();
        super.onCreate();



        File savedMessagesDirectory = getSavedMessagesDirectory();
        String[] list = savedMessagesDirectory.list();
        try {
            if (list != null) {
                for (String fname : list) {
                    File file = new File(savedMessagesDirectory, fname);
                    ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
                    try {
                        IncomingMessage o = (IncomingMessage) ois.readObject();
                        incomingMessages.add(o);
                    } catch (Exception ex) {
                        file.delete();
                    } finally {
                        ois.close();
                    }

                }
            }
        } catch (Exception e) {
            Log.e("com.juickadvanced", "restoreMessages", e);
        }
        if (incomingMessages.size() > 0) {
            XMPPMessageReceiver.updateInfo(this, incomingMessages.size(), true);
        }

    }

    @Override
    public void onDestroy() {
        up = false;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();    //To change body of overridden methods use File | Settings | File Templates.
    }

    private class SmackThread extends Thread {

        private final XMPPConnectionSetup connectionArgs;
        XMPPConnection connection;

        public SmackThread(XMPPConnectionSetup connectionArgs) {
            super("XMPP worker");
            this.connectionArgs = connectionArgs;
        }

        @Override
        public void run() {
            try {
                ConnectionConfiguration configuration = new ConnectionConfiguration(connectionArgs.getServer(), connectionArgs.port, connectionArgs.getService().trim());
                configuration.setSecurityMode(connectionArgs.secure ? ConnectionConfiguration.SecurityMode.required : ConnectionConfiguration.SecurityMode.enabled);
                configuration.setReconnectionAllowed(true);
                SASLAuthentication.supportSASLMechanism("PLAIN", 0);
                //configuration.setSASLAuthenticationEnabled(secure);
                //configuration.setCompressionEnabled(true);
                int delay = 1000;
                while (true) {
                    if (currentThread != Thread.currentThread()) return;        // we are abandoned!
                    if (connection != null && connection.isConnected()) break;
                    connection = new XMPPConnection(configuration);
                    synchronized (connections) {
                        connections.add(connection);
                    }
                    connection.connect();
                    if (connection.isConnected()) break;
                    try {
                        cleanup(null);
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        scheduleReconnect();
                        return;
                    }
                    delay *= 2;
                    if (delay > 15 * 60 * 1000) {
                        delay = 15 * 60 * 1000;
                    }
                }
                reconnectDelay = 10000;     // reset value
                connection.addConnectionListener(new ConnectionListener() {
                    @Override
                    public void connectionClosed() {
                        System.out.println();
                        //To change body of implemented methods use File | Settings | File Templates.
                    }

                    @Override
                    public void connectionClosedOnError(Exception e) {
                        System.out.println();
                        //To change body of implemented methods use File | Settings | File Templates.
                    }

                    @Override
                    public void reconnectingIn(int seconds) {
                        System.out.println();
                    }

                    @Override
                    public void reconnectionSuccessful() {
                        System.out.println();
                    }

                    @Override
                    public void reconnectionFailed(Exception e) {
                        cleanup("failed to reconnect, juick will retry later");
                        scheduleReconnect();
                    }
                });
                connection.login(connectionArgs.getLogin(), connectionArgs.password, connectionArgs.resource);
            } catch (final IllegalStateException e) {
                cleanup(null);
                scheduleReconnect();
                return;
            } catch (final NullPointerException e) {
                // concurrent connection change
                // todo implement properly, shame on me
                return;
            } catch (final XMPPException e) {
                if (currentThread() == currentThread) {
                    String message = e.toString();
                    if (message.toLowerCase().indexOf("auth") >= 0)
                        message = "!" + message;
                    cleanup(message);
                }
                if (e.getWrappedThrowable() instanceof SocketException) {
                    scheduleReconnect();
                }
                lastException = e.toString();
                return;
            }
            try {
                if (currentThread() != currentThread) return;
                lastSuccessfulConnect = System.currentTimeMillis();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (verboseXMPP())
                            Toast.makeText(XMPPService.this, "XMPP connect OK", Toast.LENGTH_SHORT).show();
                    }
                });
                if (Thread.currentThread().isInterrupted()) return;
                Roster roster = connection.getRoster();
                juickChat = connection.getChatManager().createChat(JUICK_ID, new MessageListener() {
                    @Override
                    public void processMessage(Chat chat, Message message) {
                        for (Utils.Function<Void, Message> messageReceiver : (Iterable<? extends Utils.Function<Void, Message>>) messageReceivers.clone()) {
                            messageReceiver.apply(message);
                        }
                    }
                });
                if (Thread.currentThread().isInterrupted()) return;
                if (currentThread() != currentThread) return;
                connection.addPacketListener(packetListener, new MessageTypeFilter(Message.Type.chat));
                connection.addPacketListener(packetListener2, new MessageTypeFilter(Message.Type.normal));
                try {
                    connection.sendPacket(new Presence(Presence.Type.available, "android juick client here", connectionArgs.priority, Presence.Mode.available));
                } catch (Exception e) {
                    cleanup("error while sending presence");
                    scheduleReconnect();
                    return;
                }
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
                    public void presenceChanged(final Presence presence) {
                        boolean fromJubo = presence.getFrom().equals(JUBO_ID);
                        if (fromJubo) {
                            juboOnline = presence.isAvailable();

                            Chat chat = connections.get(connections.size() - 1).getChatManager().createChat(JUBO_ID, new MessageListener() {
                                @Override
                                public void processMessage(Chat chat, Message message) {
                                    String mbody = message.getBody();
                                    if (mbody.length() > 0) {
                                        String firstLine = mbody.split("\n")[0];
                                        if (firstLine.contains("на которые вы подписаны")) {
                                            parseJuboSubscriptions(mbody);

                                        }
                                    }
                                }
                            });
                            try {
                                chat.sendMessage("ls");
                            } catch (XMPPException e) {
                                //
                            }
                        }
                        if (presence.getFrom().equals(JUICK_ID)) {
                            botOnline = presence.isAvailable();
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (verboseXMPP()) {
                                        if (botOnline) {
                                            Toast.makeText(XMPPService.this, "juick bot online", Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(XMPPService.this, "JUICK BOT OFFLINE", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }
                            });
                            if (botOnline) {
                                String sendOn = sp.getString("juickBotOn", "skip");
                                if (sendOn.equals("on")) {
                                    sendJuickMessage("ON");
                                }
                                if (sendOn.equals("off")) {
                                    sendJuickMessage("OFF");
                                }
                                sendJuickMessage("BL");
                            }
                        }
                    }
                });
            } catch (Exception ex) {
                if (currentThread() != currentThread) return;
                Log.e("XMPPThread", "exception in main thread", ex);
                cleanup("Exception in main server thread");
                scheduleReconnect();
            }
        }

        private void scheduleReconnect() {
            if (scheduledForReconnect) return;
            scheduledForReconnect = true;
            reconnectDelay *= 2;
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(new Runnable() {
                final Connection retryConnection = connection;

                @Override
                public void run() {
                    scheduledForReconnect = false;
                    up = false;
                    startup();
                }
            }, reconnectDelay);
        }


    }

    public class ExternalXMPPThread extends Thread implements JAXMPPClient.XMPPClientListener, GCMIntentService.ServerPingTimerListener, GCMIntentService.GCMMessageListener {
        XMPPConnectionSetup connectionArgs;
        JAXMPPClient client;

        public ExternalXMPPThread(XMPPConnectionSetup connectionArgs) {
            this.connectionArgs = connectionArgs;

        }

        @Override
        public void onGCMMessage(String message) {
            lastGCMMessage = new Date();
            nGCMMessages++;
            if (client != null)
                client.handleMessageFromServer(message);
        }

        @Override
        public void run() {
            final ExternalXMPPThread thiz = this;
            if (JuickAdvancedApplication.registrationId == null || JuickAdvancedApplication.registrationId.length() == 0) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        new Thread("ExtXMPPLogin") {
                            @Override
                            public void run() {
                                thiz.run();
                            }
                        }.start();
                    }
                }, 2000);
            } else {
                client = new JAXMPPClient();
                client.setXmppClientListener(thiz);
                GCMIntentService.listeners.add(this);
                HashSet<String> watchedJids = new HashSet<String>();
                watchedJids.add(JUBO_ID);
                watchedJids.add(JUICK_ID);
                final String error = client.loginXMPP(XMPPService.this, handler, connectionArgs, watchedJids);
                if (error == null) {
                    // ok
                } else {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(XMPPService.this, "ExtXMPP:"+error, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }


            GCMIntentService.serverPingTimerListeners.add(this);
            try {
                synchronized (this) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                }
            } finally {
                if (client != null) {
                    client.disconnect();
                }
                GCMIntentService.listeners.remove(this);
                GCMIntentService.serverPingTimerListeners.remove(this);
            }
        }

        @Override
        public void onServerPingTime() {
            client.startSync(new Runnable() {
                @Override
                public void run() {
                    //To change body of implemented methods use File | Settings | File Templates.
                }
            });
        }

        JAXMPPClient.XMPPClientListener nextListener;

        @Override
        public boolean onMessage(String jid, String message) {
            if (nextListener != null && nextListener.onMessage(jid, message)) return true;
            Log.i("JuickAdvanced", "ExtXMPP Message: "+jid+": "+message);
            if (jid.equals(JUBO_ID)) {
                String mbody = message;
                if (mbody.length() > 0) {
                    String firstLine = mbody.split("\n")[0];
                    if (firstLine.contains("на которые вы подписаны")) {
                        parseJuboSubscriptions(mbody);
                        return true;
                    }
                }
                handleJuickMessage(jid, message);
            } else if (jid.equals(JUICK_ID)) {
                handleJuickMessage(jid, message);
            } else {
                handleTextMessage(jid, message);
            }
            return true;
        }

        boolean talkedToJubo;
        boolean talkedToJuick;

        @Override
        public boolean onPresence(String jid, boolean on) {
            if (nextListener != null && nextListener.onPresence(jid, on)) return true;
            Log.i("JuickAdvanced", "ExtXMPP Presence: "+jid+": "+on);
            juboOnline = jid.equals(JUBO_ID) ? on : juboOnline;
            botOnline = jid.equals(JUICK_ID) ? on : juboOnline;
            if (on) {
                if (jid.equals(JUBO_ID)) {
                    if (!talkedToJubo) {
                        talkedToJubo = true;
                        client.sendMessage(jid, "ls");
                        client.sendMessage(jid, "ping");
                    }
                }
                if (jid.equals(JUICK_ID)) {
                    if (!talkedToJuick) {
                        talkedToJuick = true;
                        String sendOn = sp.getString("juickBotOn", "skip");
                        if (sendOn.equals("on")) {
                            client.sendMessage(JUICK_ID, "ON");
                        }
                        if (sendOn.equals("off")) {
                            client.sendMessage(JUICK_ID, "OFF");
                        }
                        client.sendMessage(JUICK_ID, "BL");
                    }
                }
            }
            return true;
        }

    }

}
