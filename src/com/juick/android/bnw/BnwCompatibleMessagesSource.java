package com.juick.android.bnw;

import android.content.Context;
import android.util.Log;
import com.juick.android.MainActivity;
import com.juick.android.MicroBlog;
import com.juickadvanced.RESTResponse;
import com.juickadvanced.parsers.URLParser;
import com.juick.android.Utils;
import com.juickadvanced.data.bnw.BNWMessage;
import com.juickadvanced.data.bnw.BnwMessageID;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickUser;
import com.juickadvanced.data.MessageID;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.sources.PureBnwMessagesSource;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/9/12
 * Time: 12:59 AM
 * To change this template use File | Settings | File Templates.
 */
public class BnwCompatibleMessagesSource extends MessagesSource {

    String title;
    PureBnwMessagesSource pure = new PureBnwMessagesSource(this);

    public BnwCompatibleMessagesSource(Context ctx, String title, String path, String bnw_kind) {
        super(ctx, "bnw_"+bnw_kind);
        this.title = title;
        pure.setPath(path);
        pureMessageSource = pure;
    }

    @Override
    public void getChildren(MessageID mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        pureMessageSource.getChildren(mid, notifications, cont);
    }

    @Override
    public boolean supportsBackwardRefresh() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void getFirst(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        pure.getFirst(notifications, cont);
    }

    @Override
    public void getNext(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        pure.getNext(notifications, cont);
    }

    @Override
    public CharSequence getTitle() {
        return title;
    }

    @Override
    public MicroBlog getMicroBlog() {
        return MainActivity.getMicroBlog(BnwMessageID.CODE);
    }

    public RESTResponse getJSONWithRetries(Context ctx, String url, Utils.Notification notifications) {
        return Utils.getJSONWithRetries(ctx,url, notifications);
    }

}
