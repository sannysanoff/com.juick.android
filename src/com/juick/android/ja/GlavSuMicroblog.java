package com.juick.android.ja;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ListView;
import android.widget.TextView;
import com.juick.android.*;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.R;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.juick.JuickMessage;

import java.util.ArrayList;

/**
 * Created by san on 6/1/14.
 */
public class GlavSuMicroblog implements MicroBlog, OwnRenderItems {
    public static MicroBlog INSTANCE;

    public GlavSuMicroblog() {
    }

    @Override
    public View getView(Context context, JuickMessage jmsg, View convertView) {
        View v1 = convertView;
        if (v1 == null) {
            LayoutInflater vi = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v1 = vi.inflate(R.layout.listitem_glavsu_message, null);
        } else {
            WebView wv = (WebView)v1.findViewById(R.id.html);
            wv.loadUrl("about:blank");
        }
        TextView tv = (TextView)v1.findViewById(R.id.username);
        tv.setText(jmsg.User.UName);
        WebView wv = (WebView)v1.findViewById(R.id.html);
        WebSettings settings = wv.getSettings();
        settings.setDefaultTextEncodingName("utf-8");
        wv.loadData(context.getString(R.string.HTMLStart_glavsu) + jmsg.Text, "text/html; charset=utf-8", "UTF-8");
        return v1;
    }

    @Override
    public void initialize() {
        INSTANCE = this;
    }

    @Override
    public String getCode() {
        return GlavSuForumMessagesSource.GlavSuMessageID.CODE;
    }

    @Override
    public String getMicroblogName(Context context) {
        return "Glav.su";
    }

    @Override
    public UserpicStorage.AvatarID getAvatarID(JuickMessage jmsg) {
        return null;
    }

    @Override
    public MessageID createKey(String keyString) {
        return new GlavSuForumMessagesSource.GlavSuMessageID(keyString);
    }

    @Override
    public MessageMenu getMessageMenu(Activity activity, MessagesSource messagesSource, ListView listView, JuickMessagesAdapter listAdapter) {
        return null;
    }

    @Override
    public String getPostNote(NewMessageActivity newMessageActivity) {
        return null;
    }

    @Override
    public void launchTagsForNewPost(NewMessageActivity newMessageActivity) {

    }

    @Override
    public OperationInProgress postReply(Activity context, MessageID mid, JuickMessage threadStarter, JuickMessage selectedReply, String msg, String attachmentUri, String attachmentMime, Utils.Function<Void, String> then) {
        return null;
    }

    @Override
    public void postNewMessage(NewMessageActivity newMessageActivity, String msg, int pid, double lat, double lon, int acc, String attachmentUri, String attachmentMime, ProgressDialog progressDialog, Handler progressHandler, NewMessageActivity.BooleanReference progressDialogCancel, Utils.Function<Void, String> then) {

    }

    @Override
    public ThreadFragment.ThreadExternalUpdater getThreadExternalUpdater(Activity activity, MessageID mid) {
        return null;
    }

    @Override
    public int getPiority() {
        return 50;
    }

    @Override
    public void addNavigationSources(ArrayList<MainActivity.NavigationItem> navigationItems, final MainActivity mainActivity) {
        navigationItems.add(new MainActivity.NavigationItem(61103, R.string.navigationGlavSu, R.drawable.navicon_glavsu, "msrcGlavSU") {
            @Override
            public void action() {
                final Bundle args = new Bundle();
                args.putSerializable("messagesSource", new GlavSuForumMessagesSource(mainActivity));
                mainActivity.runDefaultFragmentWithBundle(args, this);
            }
        });
    }

    @Override
    public void decorateNewMessageActivity(NewMessageActivity newMessageActivity) {

    }

    @Override
    public void getChildren(Activity context, MessageID mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        cont.apply(new ArrayList<JuickMessage>());
    }

    @Override
    public JuickMessage createMessage() {
        return null;
    }
}
