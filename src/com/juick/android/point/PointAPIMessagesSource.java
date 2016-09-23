package com.juick.android.point;

import android.content.Context;
import com.juick.android.MainActivity;
import com.juick.android.MicroBlog;
import com.juickadvanced.RESTResponse;
import com.juick.android.Utils;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.point.PointMessageID;
import com.juickadvanced.parsers.PointNetParser;
import com.juickadvanced.parsers.URLParser;
import com.juickadvanced.sources.PurePointAPIMessagesSource;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/9/12
 * Time: 12:59 AM
 * To change this template use File | Settings | File Templates.
 */
public class PointAPIMessagesSource extends MessagesSource {

    PurePointAPIMessagesSource pure = new PurePointAPIMessagesSource(this);
    String title;

    public PointAPIMessagesSource(Context ctx, String kind, String title, String path) {
        super(ctx, "point_"+kind);
        this.title = title;
        this.pureMessageSource = pure;
        pure.urlParser = path != null ? new URLParser(path): null;
        PointAuthorizer.skipAskPassword = false;
    }

    @Override
    public void getChildren(MessageID mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        ArrayList<JuickMessage> download = new PurePointAPIMessagesSource.PureChildrenDownloader().download(mid, this, notifications, false);
        cont.apply(download);
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
        return MainActivity.getMicroBlog(PointMessageID.CODE);
    }

    public RESTResponse getJSONWithRetries(Context ctx, String url, Utils.Notification notifications) {
        return Utils.getJSONWithRetries(ctx,url, notifications);
    }




}
