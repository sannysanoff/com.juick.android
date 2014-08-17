package com.juick.android.facebook;

import android.content.Context;
import com.juick.android.MainActivity;
import com.juick.android.MicroBlog;
import com.juick.android.Utils;
import com.juick.android.juick.MessagesSource;
import com.juick.android.point.PointAuthorizer;
import com.juickadvanced.RESTResponse;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.facebook.FacebookMessageID;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.point.PointMessageID;
import com.juickadvanced.parsers.URLParser;
import com.juickadvanced.protocol.FacebookTransport;
import com.juickadvanced.sources.PureFacebookFeedMessagesSource;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/9/12
 * Time: 12:59 AM
 * To change this template use File | Settings | File Templates.
 */
public class FacebookFeedMessagesSource extends MessagesSource {

    PureFacebookFeedMessagesSource pure = new PureFacebookFeedMessagesSource(this);

    String title;

    public FacebookFeedMessagesSource(Context ctx, String kind, String title) {
        super(ctx, kind);
        this.title = title;
        this.pureMessageSource = pure;
    }

    @Override
    public void getChildren(MessageID mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        pure.getChildren(mid, notifications, cont);
    }

    @Override
    public boolean supportsBackwardRefresh() {
        return true;
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
        return MainActivity.getMicroBlog(FacebookMessageID.CODE);
    }

    public RESTResponse getJSONWithRetries(Context ctx, String url, Utils.Notification notifications) {
        return Utils.getJSONWithRetries(ctx,url, notifications);
    }




}
