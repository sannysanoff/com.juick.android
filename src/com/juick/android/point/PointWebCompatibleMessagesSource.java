package com.juick.android.point;

import android.content.Context;
import com.juick.android.MainActivity;
import com.juick.android.MicroBlog;
import com.juickadvanced.RESTResponse;
import com.juickadvanced.parsers.URLParser;
import com.juick.android.Utils;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.point.PointMessageID;
import com.juickadvanced.parsers.PointNetParser;
import com.juickadvanced.sources.PurePointWebMessagesSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/9/12
 * Time: 12:59 AM
 * To change this template use File | Settings | File Templates.
 */
public class PointWebCompatibleMessagesSource extends MessagesSource {

    PurePointWebMessagesSource pure = new PurePointWebMessagesSource(this);

    String title;

    public PointWebCompatibleMessagesSource(Context ctx, String kind, String title, String path) {
        super(ctx, "point_"+kind);
        this.title = title;
        this.pureMessageSource = pure;
        pure.urlParser = new URLParser(path);
        PointAuthorizer.skipAskPassword = false;
    }

    @Override
    public void getChildren(MessageID mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        String midString = ((PointMessageID)mid).getId();
        String user = ((PointMessageID)mid).user;
        String url = user != null && user.length() > 0 ? "http://" + user.toLowerCase() + ".point.im/" + midString : "http://point.im/" + midString;
        RESTResponse jsonWithRetries = getJSONWithRetries(ctx, url, notifications);
        if (jsonWithRetries.errorText != null) {
            cont.apply(new ArrayList<JuickMessage>());
        } else {
            String result = jsonWithRetries.result;
            ArrayList<JuickMessage> messages = new PointNetParser().parseWebMessageListPure(result);
            cont.apply(messages);
        }

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
        return MainActivity.getMicroBlog(PointMessageID.CODE);
    }

    public RESTResponse getJSONWithRetries(Context ctx, String url, Utils.Notification notifications) {
        return Utils.getJSONWithRetries(ctx,url, notifications);
    }




}
