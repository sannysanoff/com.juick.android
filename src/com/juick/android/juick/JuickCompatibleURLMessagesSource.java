package com.juick.android.juick;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.juick.android.DatabaseService;
import com.juickadvanced.IHTTPClientService;
import com.juickadvanced.RESTResponse;
import com.juickadvanced.parsers.URLParser;
import com.juick.android.Utils;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.R;
import com.juickadvanced.protocol.JuickHttpAPI;
import com.juickadvanced.sources.PureJuickCompatibleURLMessagesSource;

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

    PureJuickCompatibleURLMessagesSource pure = new PureJuickCompatibleURLMessagesSource(this);

    int useBackupServer = -1;
    String title;

    public JuickCompatibleURLMessagesSource(Context ctx, String kind) {
        this(ctx.getString(R.string.All_messages), kind, ctx);
    }

    @Override
    public boolean supportsBackwardRefresh() {
        Map<String,String> argsMap = pure.urlParser.getArgsMap();
        if (argsMap.containsKey("tag") || argsMap.containsKey("user_id") || argsMap.containsKey("search")) {
            return false;
        }
        return true;
    }

    public JuickCompatibleURLMessagesSource(String label, String kind, Context ctx) {
        this(label, kind, ctx, JuickHttpAPI.getAPIURL() + "messages");
    }

    public JuickCompatibleURLMessagesSource(String title, String kind, Context ctx, String baseURL) {
        super(ctx, kind);
        this.title = title;
        pureMessageSource = pure;
        pure.init(baseURL);
        String useBackupServerS = sp.getString("useBackupServer", "-1");
        useBackupServer = (int)(Double.parseDouble(useBackupServerS) * 1000);
        if (useBackupServer < 0) useBackupServer = -1;      // instead of -1000;
    }

    public JuickCompatibleURLMessagesSource putArg(String name, String value) {
        pure.putArg(name, value);
        return this;
    }

    public String getArg(String name) {
        return pure.urlParser.getArgsMap().get(name);
    }

    @Override
    public void getFirst(Utils.Notification notification, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        pure.getFirst(notification, cont);
    }

    protected void fetchURLAndProcess(Utils.Notification notification, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        pure.fetchURLAndProcess(notification, cont);
    }

    protected boolean areMessagesInRow() {
        return false;
    }

    @Override
    public void getNext(Utils.Notification notification, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        pure.getNext(notification, cont);
    }

    public ArrayList<JuickMessage> parseJSONpure(String jsonStr) {
        return PureJuickCompatibleURLMessagesSource.parseJSONpure(jsonStr, false);
    }


    @Override
    public void getChildren(final MessageID mid, final Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        getChildrenWithDBCache(ctx, this, new PureJuickCompatibleURLMessagesSource.PureJuickChildrenDownloader(), mid, notifications, cont);
    }
    
    public static void getChildrenWithDBCache(Context context, IHTTPClientService service, final PureJuickCompatibleURLMessagesSource.PureJuickChildrenDownloader pureJuickChildrenDownloader, final MessageID mid, final Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        final boolean retrieved[] = new boolean[1]; // concurrency indicator

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        boolean messageDB = sp.getBoolean("enableMessageDB", false);
        if (messageDB && notifications instanceof Utils.HasCachedCopyNotification) {
            // try to concurrently get from DB
            Utils.ServiceGetter<DatabaseService> databaseGetter = new Utils.ServiceGetter<DatabaseService>(context, DatabaseService.class);
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
                                ArrayList<JuickMessage> alt = pureJuickChildrenDownloader.parseJSONpure(sb.toString());
                                if (retrieved[0]) return;
                                if (alt.size() > 0 && alt.get(0).getRID() == 0) {
                                    // notify
                                    retrieved[0] = true;        // debug
                                    ((Utils.HasCachedCopyNotification)notifications).onCachedCopyObtained(alt);
                                } else {
                                    // incomplete thread (no beginning)
                                }
                            }
                        }
                    }.start();
                }
            });
        }
        // get from original location
        final ArrayList<JuickMessage> stuff = pureJuickChildrenDownloader.download(mid, service, notifications, messageDB);
        retrieved[0] = true;


        if (messageDB) {
            // save it for later use
            Utils.ServiceGetter<DatabaseService> databaseGetter = new Utils.ServiceGetter<DatabaseService>(context, DatabaseService.class);
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

    @Override
    public RESTResponse getJSON(String url, com.juickadvanced.Utils.Notification progressNotification) {
        return getJSONWithRetries(getContext(), url, progressNotification);
    }

    public RESTResponse getJSONWithRetries(Context ctx, String url, Utils.Notification notifications) {
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
        RESTResponse lastResponse = null;
        if (backupServerApplies) {
            int retry = 0;
            for(int i=0; i<5; i++) {
                int timeout = useBackupServer;
                if (notifications instanceof Utils.BackupServerNotification) {
                    ((Utils.BackupServerNotification)notifications).notifyBackupInUse(false);
                }
                if (timeout != 0) {
                    RESTResponse s = Utils.getJSON(ctx,url, notifications, timeout);
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
                RESTResponse s = Utils.getJSON(ctx,urlParser.getFullURL(), notifications);
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

}
