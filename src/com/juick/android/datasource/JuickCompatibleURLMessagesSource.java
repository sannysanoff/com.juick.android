package com.juick.android.datasource;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import com.juick.android.DatabaseService;
import com.juick.android.URLParser;
import com.juick.android.Utils;
import com.juick.android.api.JuickMessage;
import com.juickadvanced.R;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 9/5/12
 * Time: 10:45 AM
 * To change this template use File | Settings | File Templates.
 */
public class JuickCompatibleURLMessagesSource extends MessagesSource {

    URLParser urlParser;
    int lastRetrievedMID;
    int page = 0;
    int useBackupServer = -1;
    String title;
    String kind;
    public boolean canNext = true;



    public JuickCompatibleURLMessagesSource(Context ctx) {
        this(ctx.getString(R.string.All_messages), ctx);
    }

    @Override
    public boolean supportsBackwardRefresh() {
        Map<String,String> argsMap = urlParser.getArgsMap();
        if (argsMap.containsKey("tag") || argsMap.containsKey("user_id") || argsMap.containsKey("search")) {
            return false;
        }
        return true;
    }

    public JuickCompatibleURLMessagesSource(String label, Context ctx) {
        this(label, ctx, "http://api.juick.com/messages");
    }

    public JuickCompatibleURLMessagesSource(String title, Context ctx, String baseURL) {
        super(ctx);
        this.title = title;
        urlParser = new URLParser(baseURL);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        String useBackupServerS = sp.getString("useBackupServer", "-1");
        useBackupServer = (int)(Double.parseDouble(useBackupServerS) * 1000);
        if (useBackupServer < 0) useBackupServer = -1;      // instead of -1000;
    }

    public JuickCompatibleURLMessagesSource putArg(String name, String value) {
        urlParser.getArgsMap().put(name, value);
        return this;
    }

    public String getArg(String name) {
        return urlParser.getArgsMap().get(name);
    }

    public void processPureMessages(Utils.ServiceGetter<DatabaseService> databaseGetter, ArrayList<JuickMessage> messages, int beforeMID) {
        databaseGetter = new Utils.ServiceGetter<DatabaseService>(ctx, DatabaseService.class);
        for(int i=0; i<messages.size(); i++) {
            final JuickMessage juickMessage = messages.get(i);
            if (databaseGetter != null) {
                final String source = juickMessage.source;
                juickMessage.previousMID = beforeMID;
                beforeMID = juickMessage.MID;
                databaseGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                    @Override
                    public void withService(DatabaseService service) {
                        service.storeMessage(juickMessage, source);
                    }
                });
            }
            juickMessage.source = null;
        }
    }

    @Override
    public void getFirst(Utils.Notification notification, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        urlParser.getArgsMap().remove("before_mid");
        urlParser.getArgsMap().remove("page");
        page = 0;
        lastRetrievedMID = -1;
        fetchURLAndProcess(notification, cont);
    }

    protected void fetchURLAndProcess(Utils.Notification notification, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        final String jsonStr = getJSONWithRetries(ctx, urlParser.getFullURL(), notification).getResult();
        ArrayList<JuickMessage> messages = parseAndProcess(jsonStr);
        if (messages.size() > 0) {
            JuickMessage juickMessage = messages.get(messages.size() - 1);
            lastRetrievedMID = juickMessage.MID;
        }
        cont.apply(messages);
    }

    private ArrayList<JuickMessage> parseAndProcess(String jsonStr) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        Utils.ServiceGetter<DatabaseService> databaseGetter = new Utils.ServiceGetter<DatabaseService>(ctx, DatabaseService.class);
        boolean enableMessageDB = sp.getBoolean("enableMessageDB", true);
        String fromS = getArg("before_mid");
        ArrayList<JuickMessage> messages = parseJSONpure(jsonStr);
        processPureMessages(enableMessageDB ? databaseGetter : null, messages, fromS != null && areMessagesInRow() ? Integer.parseInt(fromS) : -1);
        return messages;
    }

    protected boolean areMessagesInRow() {
        return false;
    }

    @Override
    public void getNext(Utils.Notification notification, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        page++;
        if (page > 0) {
            putArg("page",""+page);
        }
        if (lastRetrievedMID > 0) {
            putArg("before_mid",""+lastRetrievedMID);
        }
        fetchURLAndProcess(notification, cont);
    }

    public ArrayList<JuickMessage> parseJSONpure(String jsonStr) {
        ArrayList<JuickMessage> messages = new ArrayList<JuickMessage>();
        if (jsonStr != null) {
            try {
                JSONArray json = new JSONArray(jsonStr);
                int cnt = json.length();
                for (int i = 0; i < cnt; i++) {
                    JSONObject jsonObject = json.getJSONObject(i);
                    messages.add(JuickMessage.initFromJSON(jsonObject));
                }
            } catch (Exception e) {
                Log.e("initOpinionsAdapter", e.toString());
            }
        }
        return messages;
    }

    @Override
    public void getChildren(int mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        final String jsonStr = getJSONWithRetries(ctx, "http://api.juick.com/thread?mid=" + mid, notifications).getResult();
        cont.apply(parseJSONpure(jsonStr));
    }

    private Utils.RESTResponse getJSONWithRetries(Context ctx, String url, Utils.Notification notifications) {
        boolean backupServerApplies = false;
        if (url.contains("api.juick.com/messages") || url.contains("api.juick.com/thread")) {
            URLParser urlParser = new URLParser(url);
            backupServerApplies = true;
            Map<String, String> argsMap = urlParser.getArgsMap();
            for (String s : argsMap.keySet()) {
                if (isJuickCacheDisablingTag(s)) {
                    backupServerApplies = false;
                    break;
                }
            }
        }
        if (useBackupServer < 0) backupServerApplies = false;
        Utils.RESTResponse lastResponse = null;
        if (backupServerApplies) {
            int retry = 0;
            for(int i=0; i<5; i++) {
                int timeout = useBackupServer;
                if (notifications instanceof Utils.BackupServerNotification) {
                    ((Utils.BackupServerNotification)notifications).notifyBackupInUse(false);
                }
                if (timeout != 0) {
                    Utils.RESTResponse s = Utils.getJSON(ctx,url, notifications, timeout);
                    if (s.getResult() != null && s.getResult().length() > 0) return s;
                    lastResponse = s;
                    if (notifications instanceof Utils.RetryNotification) {
                        ((Utils.RetryNotification)notifications).notifyRetryIsInProgress(++retry);
                    }
                }
                if (notifications instanceof Utils.BackupServerNotification) {
                    ((Utils.BackupServerNotification)notifications).notifyBackupInUse(true);
                }
                // backup
                URLParser urlParser = new URLParser(url);
                urlParser.setPath("api/" + urlParser.getPathPart());
                urlParser.setHost(Utils.JA_IP);
                urlParser.setPort(Utils.JA_PORT);
                Utils.RESTResponse s = Utils.getJSON(ctx,urlParser.getFullURL(), notifications);
                if (s.getResult() != null && s.getResult().length() > 0) return s;
                lastResponse = s;
                if (notifications instanceof Utils.RetryNotification) {
                    ((Utils.RetryNotification)notifications).notifyRetryIsInProgress(++retry);
                }
            }
        } else {
            return Utils.getJSONWithRetries(ctx,url, notifications);
        }
        return lastResponse;
    }

    @Override
    public CharSequence getTitle() {
        return title;
    }

    public static boolean isJuickCacheDisablingTag(String tag) {
        // tags in '/messages' that are not supported by ja server
        if (tag.equals("search")) return true;
        if (tag.equals("popular")) return true;
        if (tag.equals("media")) return true;
        if (tag.equals("place_id")) return true;
        return false;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    @Override
    public String getKind() {
        if (kind != null) return kind;
        return super.getKind();
    }

    @Override
    public boolean canNext() {
        return canNext;
    }
}
