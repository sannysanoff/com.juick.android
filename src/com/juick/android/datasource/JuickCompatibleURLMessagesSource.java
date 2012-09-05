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
    String title;

    public JuickCompatibleURLMessagesSource(Context ctx) {
        this(ctx.getString(R.string.All_messages), ctx);
    }

    @Override
    public boolean supportsBackwardRefresh() {
        return true;
    }

    public JuickCompatibleURLMessagesSource(String label, Context ctx) {
        this(label, ctx, "http://api.juick.com/messages");
    }

    public JuickCompatibleURLMessagesSource(String title, Context ctx, String baseURL) {
        super(ctx);
        this.title = title;
        urlParser = new URLParser(baseURL);
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
        fetchURLAndProcess(notification, cont);
    }

    private void fetchURLAndProcess(Utils.Notification notification, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        final String jsonStr = Utils.getJSONWithRetries(ctx, urlParser.getFullURL(), notification);
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
        final String jsonStr = Utils.getJSONWithRetries(ctx, "http://api.juick.com/thread?mid=" + mid, notifications);
        cont.apply(parseJSONpure(jsonStr));
    }

    @Override
    public CharSequence getTitle() {
        return title;
    }
}
