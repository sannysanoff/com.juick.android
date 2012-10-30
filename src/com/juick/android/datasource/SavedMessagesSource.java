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
public class SavedMessagesSource extends MessagesSource {

    long lastMessage;

    public SavedMessagesSource(Context ctx) {
        super(ctx);
    }

    @Override
    public boolean supportsBackwardRefresh() {
        return false;
    }

    @Override
    public void getChildren(int mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        new JuickCompatibleURLMessagesSource(ctx).getChildren(mid, notifications, cont);
    }

    @Override
    public void getFirst(final Utils.Notification notifications, final Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        lastMessage = System.currentTimeMillis() + 100000000L;
        getNext(notifications, new Utils.Function<Void, ArrayList<JuickMessage>>() {
            @Override
            public Void apply(ArrayList<JuickMessage> juickMessages) {
                if (juickMessages.size() == 0) {
                    if (notifications instanceof Utils.DownloadErrorNotification) {
                        Utils.DownloadErrorNotification notifications1 = (Utils.DownloadErrorNotification)notifications;
                        notifications1.notifyDownloadError("Database is empty");
                    }
                }
                cont.apply(juickMessages);
                return null;
            }
        });
    }

    @Override
    public void getNext(Utils.Notification notifications, final Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        Utils.ServiceGetter<DatabaseService> databaseGetter;
        databaseGetter = new Utils.ServiceGetter<DatabaseService>(ctx, DatabaseService.class);
        databaseGetter.getService(new Utils.ServiceGetter.Receiver<DatabaseService>() {
            @Override
            public void withService(DatabaseService service) {
                ArrayList<JuickMessage> savedMessages = service.getSavedMessages(lastMessage);
                if (savedMessages.size() > 0) {
                    lastMessage = savedMessages.get(savedMessages.size()-1).messageSaveDate;
                }
                cont.apply(savedMessages);
            }
        });
    }

    @Override
    public CharSequence getTitle() {
        return ctx.getString(R.string.navigationSaved);
    }

    @Override
    public String getKind() {
        return "saved_messages";
    }

    @Override
    public boolean canNext() {
        return false;
    }
}
