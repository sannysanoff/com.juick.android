package com.juick.android.juick;

import android.app.Activity;
import android.content.Context;
import com.juick.android.DatabaseService;
import com.juick.android.MainActivity;
import com.juick.android.MicroBlog;
import com.juick.android.Utils;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickMessageID;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 9/5/12
 * Time: 10:45 AM
 * To change this template use File | Settings | File Templates.
 */
public abstract class RecentMessagesSource extends MessagesSource {

    public RecentMessagesSource(Context ctx) {
        super(ctx, "recent");
    }

    @Override
    public boolean supportsBackwardRefresh() {
        return false;
    }

    @Override
    public void getChildren(MessageID mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        MainActivity.getMicroBlog(mid.getMicroBlogCode()).getChildren((Activity) ctx, mid, notifications, cont);
    }

    @Override
    public void getFirst(final Utils.Notification notifications, final Utils.Function<Void, ArrayList<JuickMessage>> cont) {
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
                ArrayList<JuickMessage> savedMessages = askForRecentMessages(service);
                cont.apply(savedMessages);
            }
        });
    }

    protected abstract ArrayList<JuickMessage> askForRecentMessages(DatabaseService service);

    @Override
    public abstract CharSequence getTitle();

    @Override
    public MicroBlog getMicroBlog() {
        return MainActivity.getMicroBlog(JuickMessageID.CODE);
    }

    @Override
    public boolean canNext() {
        return false;
    }
}
