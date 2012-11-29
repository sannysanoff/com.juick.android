package com.juick.android.juick;

import android.content.Context;
import android.util.Log;
import com.juick.android.DatabaseService;
import com.juick.android.URLParser;
import com.juick.android.Utils;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.R;
import com.juickadvanced.data.juick.JuickMessageID;
import com.juickadvanced.parsers.DevJuickComMessages;
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
public class JuickCompatibleURLMessagesSource extends JuickMessagesSource {

    URLParser urlParser;
    int lastRetrievedMID;
    int page = 0;
    int useBackupServer = -1;
    String title;
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
            lastRetrievedMID = ((JuickMessageID)juickMessage.getMID()).getMid();
        }
        cont.apply(messages);
    }

    private ArrayList<JuickMessage> parseAndProcess(String jsonStr) {
        ArrayList<JuickMessage> messages = parseJSONpure(jsonStr);
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
        return parseJSONpure(jsonStr, false);
    }

    public static ArrayList<JuickMessage> parseJSONpure(String jsonStr, boolean storeSource) {
        ArrayList<JuickMessage> messages = new ArrayList<JuickMessage>();
        if (jsonStr != null) {
            try {
                JSONArray json = new JSONArray(jsonStr);
                int cnt = json.length();
                for (int i = 0; i < cnt; i++) {
                    JSONObject jsonObject = json.getJSONObject(i);
                    JuickMessage msg = initFromJSON(jsonObject);
                    msg.Text = DevJuickComMessages.unjuick(msg.Text);
                    messages.add(msg);
                    if (!storeSource)
                        msg.source = null;
                }
            } catch (Exception e) {
                Log.e("initOpinionsAdapter", e.toString());
            }
        }
        return messages;
    }

    @Override
    public void getChildren(final MessageID mid, final Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        final boolean retrieved[] = new boolean[1]; // concurrency indicator

        boolean messageDB = sp.getBoolean("enableMessageDB", false);
        if (messageDB && notifications instanceof Utils.HasCachedCopyNotification) {
            // try to concurrently get from DB
            Utils.ServiceGetter<DatabaseService> databaseGetter = new Utils.ServiceGetter<DatabaseService>(ctx, DatabaseService.class);
            databaseGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                @Override
                public void withService(DatabaseService service) {
                    // list of json entries
                    final ArrayList<String> raw = service.getStoredThread(mid);
                    new Thread() {
                        @Override
                        public void run() {
                            if (raw != null) {
                                try {
                                    Thread.sleep(500);  // to give fast connections priority
                                } catch (InterruptedException e) {
                                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                                }
                                if (retrieved[0]) return;
                                    // reconstruct whole thread
                                StringBuilder sb = new StringBuilder();
                                sb.append("[");
                                for (String s : raw) {
                                    sb.append(s);
                                    sb.append(",");
                                }
                                sb.setLength(sb.length()-1);        // last comma
                                sb.append("]");
                                // parse
                                ArrayList<JuickMessage> alt = parseJSONpure(sb.toString());
                                if (retrieved[0]) return;
                                // notify
                                retrieved[0] = true;        // debug
                                ((Utils.HasCachedCopyNotification)notifications).onCachedCopyObtained(alt);
                            }
                        }
                    }.start();
                }
            });
        }
        // get from original location
        Utils.RESTResponse result = getJSONWithRetries(ctx, "http://api.juick.com/thread?mid=" + ((JuickMessageID) mid).getMid(), notifications);
        final String jsonStr = result.getResult();
        retrieved[0] = true;
        final ArrayList<JuickMessage> stuff = parseJSONpure(jsonStr, messageDB);
        if (messageDB) {
            // save it for later use
            Utils.ServiceGetter<DatabaseService> databaseGetter = new Utils.ServiceGetter<DatabaseService>(ctx, DatabaseService.class);
            databaseGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
                @Override
                public void withService(DatabaseService service) {
                    final ArrayList<String> raw = new ArrayList<String>();
                    for (JuickMessage juickMessage : stuff) {
                        raw.add(juickMessage.source);
                    }
                    service.storeThread(mid, raw);
                }
            });
        }
        cont.apply(stuff);
    }

    public Utils.RESTResponse getJSONWithRetries(Context ctx, String url, Utils.Notification notifications) {
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
                String[] hostPort = Utils.JA_ADDRESS.split(":");
                urlParser.setHost(hostPort[0]);
                urlParser.setPort(hostPort[1]);
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


    @Override
    public boolean canNext() {
        return canNext;
    }

    public void setCanNext(boolean canNext) {
        this.canNext = canNext;
    }
}
