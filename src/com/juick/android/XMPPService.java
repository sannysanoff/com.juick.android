package com.juick.android;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import com.google.gson.*;
import com.juick.android.juick.JuickAPIAuthorizer;
import com.juick.android.juick.JuickCompatibleURLMessagesSource;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickMessageID;
import com.juickadvanced.xmpp.ServerToClient;
import com.juickadvanced.xmpp.XMPPConnectionSetup;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 */
public class XMPPService extends Service {

    private static final IncomingMessage DUMMY = new IncomingMessage("", "", null) {
    };
    Handler handler;
    public static final String ACTION_MESSAGE_RECEIVED = "com.juickadvanced.android.action.ACTION_MESSAGE_RECEIVED";
    public static final String ACTION_LAUNCH_MESSAGELIST = "com.juickadvanced.android.action.ACTION_LAUNCH_MESSAGELIST";
    private final IBinder mBinder = new Utils.ServiceGetter.LocalBinder<XMPPService>(this);
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
    static public Date lastWSMessage;
    static public String lastGCMMessageID;
    static public String lastWSMessageID;
    static public long lastAlarmScheduled;
    static public long lastAlarmFired;
    public static int nGCMMessages;
    public static int nWSMessages;

    public static ArrayList<String> log = new ArrayList<String>();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent != null && intent.getBooleanExtra("terminate", false)) {
                if (currentThread instanceof ExternalXMPPThread) {
                    ExternalXMPPThread th = (ExternalXMPPThread) currentThread;
                    JAXMPPClient client = th.client;
                    if (client != null) {
                        client.sendDisconnect(new Utils.Function<Void, ServerToClient>() {
                            @Override
                            public Void apply(ServerToClient serverToClient) {
                                return null;
                            }
                        });
                    }
                }
                handler.removeCallbacksAndMessages(null);
                String message = intent.getStringExtra("terminateMessage");
                if (message == null) message = "user terminated";
                cleanup(message);
            } else {
                if (startId != 2) {     // i don't know what is this, really
                    startup();
                }
            }
            return super.onStartCommand(intent, flags, startId);
        } finally {
        }
    }

    public static void log(String str) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        StringBuilder sb = new StringBuilder(sdf.format(System.currentTimeMillis()));
        sb.append(" ");
        sb.append(str);
        log.add(sb.toString());
        while (log.size() > 50) {
            log.remove(0);
        }
        Log.w("com.juickadvanced", "XMPPService: " + str);
    }


    public Thread currentThread;
    SharedPreferences sp;


    public final static String JUICKADVANCED_ID = "juickadvanced@local";
    public final static String JUICK_ID = "juick@juick.com/Juick";
    public final static String JUBO_ID = "jubo@nologin.ru/jubo";
    public static String lastException;
    public static long lastExceptionTime;

    public void startup() {
        if (up) return;
        log("XMPP service startup()");
        botOnline = false;
        juboOnline = false;
        boolean useXMPP = sp.getBoolean("useXMPP", false);
        boolean useXMPPOnlyForBL = sp.getBoolean("useXMPPOnlyForBL", false);
        if (useXMPP && useXMPPOnlyForBL && juboMessageFilter != null && juickBlacklist != null) {
            // we have everything.
            useXMPP = false;
        }
        Gson gson = DatabaseService.getGson();
        final XMPPConnectionSetup connectionArgs = gson.fromJson(sp.getString("xmpp_config", ""), XMPPConnectionSetup.class);
        if (useXMPP && connectionArgs != null) {
            (currentThread = new ExternalXMPPThread(connectionArgs)).start();
        }
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
            if (message instanceof JuickSubscriptionIncomingMessage) {
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
                boolean tag = s.contains("{tag:*");
                boolean stop = s.contains("-{") || s.contains("{tag:-*") || s.startsWith("-");
                if (tag) {
                    if (stop) {
                        if (s.startsWith("-")) s = s.substring(1);
                        if (s.contains("tag:-*")) s = s.replace("tag:-*", "");
                    }
                    s = s.replace("{tag:", "").replace("}", "").replace("*", "");
                }
                if (tag && stop) filter.stopTags.add(s.toLowerCase());
                if (tag && !stop) filter.subscribedTags.add(s.toLowerCase());
                if (!tag && stop) filter.stopWords.add(s.toLowerCase());
                if (!tag && !stop) filter.subscribedWords.add(s.toLowerCase());
            }
        }
        juboMessageFilter = filter;
        juboMessageFilter_tmp = null;
        writeStringToFile(getCachedJuboFile(), new Gson().toJson(juboMessageFilter));
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
        juickBlacklist = filter;
        juickBlacklist_tmp = null;
        writeStringToFile(getCachedJuickFile(), new Gson().toJson(juickBlacklist));
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
            ExternalXMPPThread th = (ExternalXMPPThread) currentThread;
            if (th != null) {
                JAXMPPClient client = th.client;
                if (client != null) {
                    log("Sent client disconnect (XMPP)");
                    client.sendDisconnect(new Utils.Function<Void, ServerToClient>() {
                        @Override
                        public Void apply(ServerToClient serverToClient) {
                            cleanup("blacklists got ok");
                            return null;
                        }
                    });
                }
            }
        }
    }

    private File getCachedJuickFile() {
        return new File(getCacheDir(), "cached_juick_blacklist.json");
    }

    private boolean verboseXMPP() {
        return sp.getBoolean("xmpp_verbose", false);
    }

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
        synchronized (incomingMessages) {
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

    public class MyBroadcastSender implements Runnable {
        @Override
        public void run() {
            sendMyBroadcast(false);
        }
    }

    MyBroadcastSender broadcastSender = new MyBroadcastSender();

    public void requestMessageBody(final MessageID topicMessageId) {
        final int mid = ((JuickMessageID) topicMessageId).getMid();
        final String messageRequest = "#" + mid;
        Utils.ServiceGetter<DatabaseService> databaseGetter = new Utils.ServiceGetter<DatabaseService>(this, DatabaseService.class);
        databaseGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
            @Override
            public void withService(DatabaseService service) {
                ArrayList<String> storedThread = service.getStoredThread(topicMessageId);
                if (storedThread != null && storedThread.size() > 0) {
                    String msg = "[" + storedThread.get(0) + "]";
                    final ArrayList<JuickMessage> juickMessages = JuickCompatibleURLMessagesSource.parseJSONpure(msg, false);
                    if (juickMessages != null && juickMessages.size() == 1 && juickMessages.get(0) instanceof JuickMessage) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                JuickMessage juickMessage = juickMessages.get(0);
                                JuickSubscriptionIncomingMessage jsim = new JuickSubscriptionIncomingMessage(
                                        juickMessage.User.UName,
                                        juickMessage.Text,
                                        juickMessage.getDisplayMessageNo(),
                                        juickMessage.Timestamp
                                );
                                handleObtainedTopicStarter(jsim);
                            }
                        });
                    }
                }
                new Thread() {
                    @Override
                    public void run() {
                        boolean success = false;
                        try {
                            Utils.RESTResponse json = Utils.getJSON(XMPPService.this, "http://" + Utils.JA_ADDRESS + "/api/thread?mid=" + mid + "&onlybody=true", null);
                            if (json.getResult() != null) {
                                ArrayList<JuickMessage> messages = JuickCompatibleURLMessagesSource.parseJSONpure(json.getResult(), false);
                                if (messages != null && messages.size() > 0) {
                                    JuickMessage juickMessage = messages.get(0);
                                    final JuickSubscriptionIncomingMessage obtained = new JuickSubscriptionIncomingMessage(
                                            juickMessage.User.UName,
                                            juickMessage.Text,
                                            "#" + mid,
                                            juickMessage.Timestamp
                                    );
                                    obtained.tags.addAll(juickMessage.tags);
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            handleObtainedTopicStarter(obtained);
                                        }
                                    });
                                    // requested topic starter obtained, now just repaint messages
                                    handler.removeCallbacks(broadcastSender);
                                    handler.postDelayed(broadcastSender, 3000);
                                    success = true;
                                }
                            }
                        } finally {
                            if (!success) {
                                if (currentThread instanceof ExternalXMPPThread) {
                                    ExternalXMPPThread externalXMPPThread = (ExternalXMPPThread) currentThread;
                                    externalXMPPThread.client.sendMessage(JUICK_ID, messageRequest);
                                }
                            }
                        }
                    }
                }.start();
            }
        });
    }

    private void handleObtainedTopicStarter(JuickSubscriptionIncomingMessage obtained) {
        boolean receiveOnlyPersonal = sp.getBoolean("receive_only_personal", false);
        boolean notifyOnlyPersonal = sp.getBoolean("notify_only_personal", false);
        switch (handleIncomingTopicStarter(obtained)) {
            case DELAYED_FOUND_NO_PERSONALS:
                if (receiveOnlyPersonal)
                    removeMessages(obtained.getMID(), false);
                if (notifyOnlyPersonal || receiveOnlyPersonal)
                    sendMyBroadcast(false);
                break;
            case DELAYED_FOUND_PERSONALS:
                if (receiveOnlyPersonal || notifyOnlyPersonal) {
                    sendMyBroadcast(true);
                }
                break;
            case NO_DELAYED_MESSAGES_FOUND:
                System.out.println("Ok");
                break;
        }
    }


    static int message_seq = 0;

    public void askJuboRSS() {
        juboRSSError = "No response from JuBo";
        if (currentThread instanceof ExternalXMPPThread) {
            final ExternalXMPPThread et = (ExternalXMPPThread) currentThread;
            et.nextListener = new JAXMPPClient.XMPPClientListener() {
                @Override
                public boolean onMessage(String jid, String message) {

                    if (jid.equals(JUBO_ID)) {
                        if (message.contains("rss start")) {              // issue 'rss start' to start
                            juboRSSError = "Response to 'rss start' did not come yet";
                            et.client.sendMessage(JUBO_ID, "rss start");
                        }
                        if (message.contains("http://") && message.contains("/rss/")) {     // rss start response
                            int start = message.indexOf("http://");
                            int end = message.indexOf(".xml");
                            if (start != -1 && end != -1) {
                                end += 4;
                            }
                            juboRSS = message.substring(start, end);
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
        }
    }

    public static abstract class IncomingMessage implements Serializable {
        protected String from;
        String body;
        String id;
        Date datetime;
        boolean delayedNotificationResolution;       // should notify with sound upon receival of topic starter if parent is me.

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

            return !(id != null ? !id.equals(that.id) : that.id != null);

        }

        @Override
        public int hashCode() {
            return id != null ? id.hashCode() : 0;
        }
    }

    public static abstract class JuickIncomingMessage extends IncomingMessage {
        String messageNoPlain;
        ArrayList<String> tags = new ArrayList<String>();
        transient JuickMessageID parsedMid;


        public JuickIncomingMessage(String from, String body, String messageNoPlain, Date date) {
            super(from, body, date);
            this.messageNoPlain = messageNoPlain;
        }

        public String getFrom() {
            if (from.startsWith("@")) return from;
            return "@" + from;
        }

        public ArrayList<String> getTags() {
            return tags;
        }

        public void setTags(ArrayList<String> tags) {
            this.tags = tags;
        }

        public JuickMessageID getMID() {
            try {
                if (parsedMid == null) {
                    int ix = messageNoPlain.indexOf("/");
                    if (ix == -1) {
                        parsedMid = new JuickMessageID(Integer.parseInt(messageNoPlain.substring(1)));
                    } else {
                        int ix2 = messageNoPlain.indexOf("#");   // -1 not found
                        parsedMid = new JuickMessageID(Integer.parseInt(messageNoPlain.substring(ix2 + 1, ix)));
                    }
                }
                return parsedMid;
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
        private JuickIncomingMessage originalMessage;

        public JuickThreadIncomingMessage(String from, String body, String messageNo, Date date) {
            super(from, body, messageNo, date);
        }

        public JuickIncomingMessage getOriginalMessage() {
            return originalMessage;
        }

        public void setOriginalMessage(JuickIncomingMessage originalMessage) {
            this.originalMessage = originalMessage;
        }
    }

    public static class JuickSubscriptionIncomingMessage extends JuickIncomingMessage {

        public JuickSubscriptionIncomingMessage(String from, String body, String messageNo, Date date) {
            super(from, body, messageNo, date);
        }
    }

    public static class JabberIncomingMessage extends IncomingMessage {
        JabberIncomingMessage(String from, String body, Date date) {
            super(from, body, date);
        }
    }

    final public ArrayList<IncomingMessage> incomingMessages = new ArrayList<IncomingMessage>();

    // duplicates from various sources
    ArrayList<String> recentlyReceivedMessages = new ArrayList<String>();

    public void handleJuickMessage(String from, String body) {
        IncomingMessage handled = null;
        JuickIncomingMessage topicStarter = null;
        boolean receiveOnlyPersonal = sp.getBoolean("receive_only_personal", false);
        boolean notifyOnlyPersonal = sp.getBoolean("notify_only_personal", false);
        boolean skipUpdateNotification = false;
        boolean skipSoundInNotification = false;
        boolean handledTopicStarterThatMustNotify = false;
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
                        for (int i = 3; i < split.length - 1; i++) {
                            if (split[i].indexOf("" + msgno) == 0)
                                break;
                            sb.append(split[i]);
                            sb.append("\n");
                        }
                        final JuickThreadIncomingMessage threadIncomingMessage = new JuickThreadIncomingMessage(username, sb.toString(), msgno, new Date());
                        XMPPService.JuickIncomingMessage existingTopicStarter = cachedTopicStarters.get(threadIncomingMessage.getMID());
                        if (existingTopicStarter == null) {
                            // reply; no master message found ;-(
                            existingTopicStarter = new JuickThreadIncomingMessage("@???", "", "#" + threadIncomingMessage.getMID().getMid(), new Date());    // put placeholder for details
                            cachedTopicStarters.put(threadIncomingMessage.getMID(), existingTopicStarter);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    // make it later than saveMessage below.
                                    requestMessageBody(threadIncomingMessage.getMID());
                                }
                            });
                        } else {
                            // reply; master message found !
                            threadIncomingMessage.setOriginalMessage(existingTopicStarter);
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
                    if (head.contains("*private")) {
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
                                JuickSubscriptionIncomingMessage subscriptionIncomingMessage = new JuickSubscriptionIncomingMessage(username, sb.toString(), msgNo, new Date());
                                subscriptionIncomingMessage.tags.addAll(Arrays.asList(tags));
                                topicStarter = cachedTopicStarters.get(subscriptionIncomingMessage.getMID());
                                if (topicStarter != null && topicStarter.getBody().length() == 0) {
                                    // this message was requested to obtain /0 body for some reply!
                                    switch (handleIncomingTopicStarter(subscriptionIncomingMessage)) {
                                        case DELAYED_FOUND_NO_PERSONALS:
                                            if (receiveOnlyPersonal)
                                                removeMessages(subscriptionIncomingMessage.getMID(), false);
                                            if (notifyOnlyPersonal)
                                                skipSoundInNotification = true;
                                            break;
                                        case DELAYED_FOUND_PERSONALS:
                                            if (receiveOnlyPersonal || notifyOnlyPersonal) {
                                                handledTopicStarterThatMustNotify = true;
                                            }
                                            break;
                                        case NO_DELAYED_MESSAGES_FOUND:
                                            break;

                                    }
                                    handled = DUMMY;
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
                    skipUpdateNotification = true;
                }

            }
            if (handled == null && !skipUpdateNotification) {
                if (isFromJuBo) {
                    skipUpdateNotification = true;
                    // keep silence
                } else if (isFromJuick && body.startsWith("Your blacklist:")) {
                    parseJuickBlacklist(body);
                    skipUpdateNotification = true;
                } else if (isFromJuick && body.startsWith("You are subscribed to users:")) {
                    parseJuickSubscriptions(body);
                    skipUpdateNotification = true;
                } else {
                    JabberIncomingMessage messag = new JabberIncomingMessage(from, body, new Date());
                    saveMessage(messag);
                    incomingMessages.add(messag);
                    handled = messag;
                }
            }

            boolean shouldDelete = false;


            boolean messageToMe = true;
            if (handled != null && handled != DUMMY) {
                Set<String> filteredOutUsers = JuickMessagesAdapter.getFilteredOutUsers(this);
                String fromm = handled.getFrom();
                if (fromm.startsWith("@")) {
                    fromm = fromm.substring(1);
                }
                //
                // Filter message through blacklists
                //
                shouldDelete |= filteredOutUsers.contains(fromm);
                if (!shouldDelete) {
                    if (isFromJuBo && getAnyJuboMessageFilter() != null) {
                        shouldDelete = !getAnyJuboMessageFilter().allowXMPPMessage(handled, sp);
                    }
                }

                messageToMe = isMessageToMe(handled);

                if (handled.delayedNotificationResolution) {
                    shouldDelete = false;
                    receiveOnlyPersonal = false;    // keep it until delayedNotificationResolution
                }

                if (!shouldDelete) {
                    if (handled instanceof JuickIncomingMessage) {
                        JuickIncomingMessage jim = (JuickIncomingMessage) handled;
                        String ky = jim.getMID() + "/" + jim.getRID();
                        if (recentlyReceivedMessages.contains(ky)) {
                            shouldDelete = true;
                        } else {
                            recentlyReceivedMessages.add(ky);

                            maybeSaveInDatabase(jim);

                            if (recentlyReceivedMessages.size() > 500) {    // that big!
                                recentlyReceivedMessages.remove(0);
                            }
                        }
                    }
                }
            }


            if (handledTopicStarterThatMustNotify) {
                messageToMe = true;
            }

            if (!messageToMe) {
                if (receiveOnlyPersonal) {
                    shouldDelete = true;
                    skipSoundInNotification = true;
                }
                if (notifyOnlyPersonal) {
                    skipSoundInNotification = true;
                }
            }


            if (shouldDelete) {
                // kill-em
                removeMessageFile(handled.id);
                incomingMessages.remove(handled);
                skipUpdateNotification = true;
            }
        }
        if (handledTopicStarterThatMustNotify) {
            skipUpdateNotification = false;
        }
        if (!skipUpdateNotification)
            sendMyBroadcast((handled != DUMMY && !skipSoundInNotification) || handledTopicStarterThatMustNotify);
    }

    /**
     * @param body incoming juick message with tag list
     * @return list of tags (with leading '*'s)
     */
    private ArrayList<String> parseJuickSubscriptions(String body) {
        String[] lines = body.split("\n");
        boolean inTags = true;
        ArrayList<String> retval = new ArrayList<String>();
        for (String line : lines) {
            if (line.equals("Tags:")) {
                inTags = true;
                continue;
            }
            if (inTags && line.startsWith("*")) {
                retval.add(line);
            }
        }
        return retval;
    }

    private boolean isMessageToMe(IncomingMessage handled) {
        boolean messageToMe = true;
        if (handled instanceof JuickSubscriptionIncomingMessage) {
            messageToMe = false;
        } else if (handled instanceof JuickThreadIncomingMessage) {
            JuickThreadIncomingMessage jtim = (JuickThreadIncomingMessage) handled;
            String accountName = JuickAPIAuthorizer.getJuickAccountName(this);
            if (accountName != null) {
                accountName = accountName.toLowerCase();
                if (!jtim.getBody().startsWith("@")) {  // if not, message is to /0
                    if (jtim.originalMessage != null && !jtim.originalMessage.from.contains("???")) {
                        String starterFrom = jtim.originalMessage.getFrom();
                        if (starterFrom.startsWith("@")) starterFrom = starterFrom.substring(1);
                        messageToMe = starterFrom.toLowerCase().equals(accountName); // topic starter author is me? then this reply is to me!
                    } else {
                        handled.delayedNotificationResolution = true;
                        saveMessage(jtim);
                        messageToMe = false;
                    }
                } else {
                    messageToMe = XMPPIncomingMessagesActivity.hasMyNickAnywhereInBody(accountName, handled.getBody());
                }
            } else {
                messageToMe = false;
            }
        } else if (handled instanceof JuickIncomingMessage) {
            messageToMe = true;
        } else {
            // psto etc
            // NOT to me (not flood)
        }
        return messageToMe;
    }

    private void maybeSaveInDatabase(final JuickIncomingMessage jim) {
        boolean messageDB = sp.getBoolean("enableMessageDB", false);
        if (messageDB) {
            // save it for later use
            Utils.ServiceGetter<DatabaseService> databaseGetter = new Utils.ServiceGetter<DatabaseService>(this, DatabaseService.class);
            databaseGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                @Override
                public void withService(DatabaseService service) {
                    JuickMessageID mid = jim.getMID();
                    JsonObject obj = new JsonObject();
                    obj.addProperty("mid", mid.getMid());
                    if (jim.getTags() != null && jim.getTags().size() > 0) {
                        JsonArray tags = new JsonArray();
                        for (String t : jim.getTags()) {
                            tags.add(new JsonPrimitive(t));
                        }
                        obj.add("tags", tags);
                    }
                    obj.addProperty("body", jim.getBody());
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
                    obj.addProperty("timestamp", sdf.format(new Date()));
                    JsonObject user = new JsonObject();
                    user.addProperty("uname", jim.getFrom());
                    obj.add("user", user);
                    String string = new Gson().toJson(obj);
                    service.appendToStoredThread(mid, string);
                }
            });
        }
    }


    public enum TopicStarterDelayedNotificationsHandled {
        NO_DELAYED_MESSAGES_FOUND,
        DELAYED_FOUND_NO_PERSONALS,
        DELAYED_FOUND_PERSONALS,
    }

    /**
     * @return has any pending isMessageToMe?
     */
    private TopicStarterDelayedNotificationsHandled handleIncomingTopicStarter(JuickSubscriptionIncomingMessage subscriptionIncomingMessage) {
        TopicStarterDelayedNotificationsHandled result = TopicStarterDelayedNotificationsHandled.NO_DELAYED_MESSAGES_FOUND;
        cachedTopicStarters.put(subscriptionIncomingMessage.getMID(), subscriptionIncomingMessage);
        boolean forMe = false;
        synchronized (incomingMessages) {
            for (IncomingMessage incomingMessage : new ArrayList<IncomingMessage>(incomingMessages)) {
                if (incomingMessage instanceof JuickThreadIncomingMessage && ((JuickThreadIncomingMessage) incomingMessage).getMID().equals(subscriptionIncomingMessage.getMID())) {
                    // details came!
                    JuickThreadIncomingMessage imsg = (JuickThreadIncomingMessage) incomingMessage;
                    imsg.setOriginalMessage(subscriptionIncomingMessage);
                    if (imsg.delayedNotificationResolution) {
                        result = TopicStarterDelayedNotificationsHandled.DELAYED_FOUND_NO_PERSONALS;
                        imsg.delayedNotificationResolution = false;
                        forMe |= isMessageToMe(imsg);
                    } else {
                        forMe |= isMessageToMe(imsg);
                    }
                    saveMessage(imsg);
                }
            }
        }
        switch (result) {
            case NO_DELAYED_MESSAGES_FOUND:
                return result;
            case DELAYED_FOUND_NO_PERSONALS:
                return forMe ? TopicStarterDelayedNotificationsHandled.DELAYED_FOUND_PERSONALS : result;
            default:
                throw new RuntimeException("Illegal case");
        }
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


    public void cleanup(final String reason) {
        final boolean wasUp = up;
        up = false;
        if (currentThread != null) {
            currentThread.interrupt();
            currentThread = null;
        }
        if (reason != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (verboseXMPP() || reason.startsWith("!"))
                        if (wasUp) {
                            JuickAdvancedApplication.showXMPPToast("XMPP Disconnected: " + reason);
                        }
                }
            });
        }
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

    public static byte[] readFile(File f, int maxlen) {
        if (!f.exists()) return null;
        try {
            FileInputStream fis = new FileInputStream(f);
            byte[] arr = new byte[1024];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while (true) {
                int len = fis.read(arr);
                if (len < 1) break;
                baos.write(arr, 0, len);
                if (baos.size() >= maxlen)
                    break;
            }
            fis.close();
            return baos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    public boolean up = false;

    @Override
    public void onCreate() {
        handler = new Handler();
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        String cachedJubo = readFile(getCachedJuboFile());
        if (cachedJubo != null) {
            juboMessageFilter_tmp = new Gson().fromJson(cachedJubo, JuboMessageFilter.class);
            if (juboMessageFilter_tmp != null && !juboMessageFilter_tmp.valid()) {
                juboMessageFilter_tmp = null;
            }
        }
        String cachedJuick = readFile(getCachedJuickFile());
        if (cachedJuick != null) {
            juickBlacklist_tmp = new Gson().fromJson(cachedJuick, JuickBlacklist.class);
            if (juickBlacklist_tmp != null && !juickBlacklist_tmp.valid()) {
                juickBlacklist_tmp = null;
            }
        }
        super.onCreate();

        new Thread() {
            @Override
            public void run() {
                ArrayList<IncomingMessage> readMessages = new ArrayList<IncomingMessage>();
                File savedMessagesDirectory = getSavedMessagesDirectory();
                String[] list = savedMessagesDirectory.list();
                try {
                    if (list != null) {
                        for (String fname : list) {
                            File file = new File(savedMessagesDirectory, fname);
                            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
                            try {
                                IncomingMessage o = (IncomingMessage) ois.readObject();
                                readMessages.add(o);
                            } catch (OutOfMemoryError ex) {
                                // maybe broken file, maybe not.
                                file.delete();
                                break;
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
                synchronized (incomingMessages) {
                    incomingMessages.addAll(readMessages);
                    if (incomingMessages.size() > 0) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                XMPPMessageReceiver.updateInfo(XMPPService.this, incomingMessages.size(), true);
                                HashSet<Integer> alreadyRequestedTopicStarters = new HashSet<Integer>();
                                synchronized (incomingMessages) {
                                    for (IncomingMessage incomingMessage : incomingMessages) {
                                        if (incomingMessage instanceof JuickThreadIncomingMessage) {
                                            JuickThreadIncomingMessage jtim = (JuickThreadIncomingMessage) incomingMessage;
                                            if (jtim.originalMessage == null || jtim.originalMessage.from.contains("???")) {
                                                JuickMessageID mid = jtim.getMID();
                                                if (!alreadyRequestedTopicStarters.contains(mid.getMid())) {
                                                    alreadyRequestedTopicStarters.add(mid.getMid());
                                                    requestMessageBody(mid);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        });
                    }
                }
            }
        }.start();

    }

    @Override
    public void onDestroy() {
        up = false;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();    //To change body of overridden methods use File | Settings | File Templates.
    }


    public class ExternalXMPPThread extends Thread implements JAXMPPClient.XMPPClientListener {
        XMPPConnectionSetup connectionArgs;
        JAXMPPClient client;

        public ExternalXMPPThread(XMPPConnectionSetup connectionArgs) {
            this.connectionArgs = connectionArgs;

        }

        @Override
        public void run() {
            final ExternalXMPPThread thiz = this;
            synchronized (this) {
                client = new JAXMPPClient();
                client.setXmppClientListener(thiz);
            }
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
                        JuickAdvancedApplication.showXMPPToast("ExtXMPP:" + error);
                    }
                });
            }
            try {
                synchronized (this) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } finally {
                JAXMPPClient localClient = client;
                synchronized (this) {
                    client = null;
                }
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                }
                localClient.disconnect();
            }
        }

        JAXMPPClient.XMPPClientListener nextListener;

        @Override
        public boolean onMessage(String jid, String message) {
            boolean useXMPP = sp.getBoolean("useXMPP", false);
            if (!useXMPP) return true;
            if (nextListener != null && nextListener.onMessage(jid, message)) return true;
            Log.i("JuickAdvanced", "ExtXMPP Message: " + jid + ": " + message);
            if (jid.equals(JUBO_ID)) {
                if (message.length() > 0) {
                    String firstLine = message.split("\n")[0];
                    if (firstLine.contains("на которые вы подписаны")) {
                        parseJuboSubscriptions(message);
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
            JAXMPPClient localClient = client;
            Log.i("JuickAdvanced", "ExtXMPP Presence: " + jid + ": " + on);
            juboOnline = jid.equals(JUBO_ID) ? on : juboOnline;
            botOnline = jid.equals(JUICK_ID) ? on : juboOnline;
            if (localClient != null) {
                if (on) {
                    if (jid.equals(JUBO_ID)) {
                        if (!talkedToJubo) {
                            talkedToJubo = true;
                            localClient.sendMessage(jid, "ls");
                            localClient.sendMessage(jid, "ping");
                        }
                    }
                    if (jid.equals(JUICK_ID)) {
                        if (!talkedToJuick) {
                            talkedToJuick = true;
                            String sendOn = sp.getString("juickBotOn", "skip");
                            if (sendOn.equals("on")) {
                                localClient.sendMessage(JUICK_ID, "ON");
                            }
                            if (sendOn.equals("off")) {
                                localClient.sendMessage(JUICK_ID, "OFF");
                            }
                            localClient.sendMessage(JUICK_ID, "BL");
                            log("Sent BL command to juick");
                            localClient.sendMessage(JUICK_ID, "S");
                            log("Sent S command to juick");
                        }
                    }
                }
            }
            return true;
        }

    }

}
