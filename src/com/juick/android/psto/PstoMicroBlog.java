package com.juick.android.psto;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ListView;
import com.juick.android.*;
import com.juick.android.api.JuickMessage;
import com.juick.android.api.MessageID;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.R;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/10/12
 * Time: 1:30 PM
 * To change this template use File | Settings | File Templates.
 */
public class PstoMicroBlog implements MicroBlog {
    public static PstoMicroBlog instance;
    public static final String CODE = "psto";

    public PstoMicroBlog() {
        instance = this;
    }


    @Override
    public void addNavigationSources(ArrayList<MainActivity.NavigationItem> navigationItems, final MainActivity mainActivity) {
        SharedPreferences sp = mainActivity.sp;
        if (sp.getBoolean("msrcPSTORecent", false)) {
            navigationItems.add(new MainActivity.NavigationItem(R.string.navigationPSTORecent) {
                @Override
                public void action() {
                    final Bundle args = new Bundle();
                    PstoCompatibleMessageSource ms = new PstoCompatibleMessageSource(mainActivity, mainActivity.getString(labelId), "http://psto.net/recent");
                    args.putSerializable("messagesSource", ms);
                    mainActivity.runDefaultFragmentWithBundle(args, this);
                }
            });
        }
        if (sp.getBoolean("msrcPSTOPopular", false)) {
            navigationItems.add(new MainActivity.NavigationItem(R.string.navigationPSTOPopular) {
                @Override
                public void action() {
                    final Bundle args = new Bundle();
                    PstoCompatibleMessageSource ms = new PstoCompatibleMessageSource(mainActivity, mainActivity.getString(labelId), "http://psto.net/top");
                    args.putSerializable("messagesSource", ms);
                    mainActivity.runDefaultFragmentWithBundle(args, this);
                }
            });
        }
    }

    @Override
    public void initialize() {
        Utils.authorizers.add(0, new PstoAuthorizer());
    }

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public UserpicStorage.AvatarID getAvatarID(final JuickMessage jmsg) {
        return new UserpicStorage.AvatarID() {
            @Override
            public String toString() {
                return "PSTO:"+jmsg.User.UName;
            }

            @Override
            public String getURL() {
                return "http://psto.net/img/a/40/"+jmsg.User.UName+".png";
            }
        };
    }

    @Override
    public MessageID createKey(String keyString) {
        return new PstoMessageID(keyString);
    }

    @Override
    public MessageMenu getMessageMenu(Activity activity, MessagesSource messagesSource, ListView listView, JuickMessagesAdapter listAdapter) {
        return new MessageMenu(activity, messagesSource, listView, listAdapter);
    }

    @Override
    public void postReply(Activity context, MessageID mid, JuickMessage selectedReply, String msg, String attachmentUri, String attachmentMime, Utils.Function<Void, String> then) {
        then.apply("Not implemented yet");
    }

    @Override
    public void postNewMessage(NewMessageActivity newMessageActivity, String msg, int pid, double lat, double lon, int acc, String attachmentUri, String attachmentMime, ProgressDialog progressDialog, Handler progressHandler, NewMessageActivity.BooleanReference progressDialogCancel, Utils.Function<Void, String> then) {
        then.apply("Not implemented");
    }

    @Override
    public ThreadFragment.ThreadExternalUpdater getThreadExternalUpdater(Activity activity, MessageID mid) {
        return null;
    }

    @Override
    public int getPiority() {
        return 20;
    }
}
