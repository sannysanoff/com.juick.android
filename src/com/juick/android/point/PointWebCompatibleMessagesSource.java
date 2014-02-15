package com.juick.android.point;

import android.content.Context;
import com.juick.android.MainActivity;
import com.juick.android.MicroBlog;
import com.juickadvanced.parsers.URLParser;
import com.juick.android.Utils;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.point.PointMessageID;
import com.juickadvanced.parsers.PointNetParser;

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

    URLParser urlParser;
    String title;
    int page;

    public PointWebCompatibleMessagesSource(Context ctx, String kind, String title, String path) {
        super(ctx, "point_"+kind);
        this.title = title;
        urlParser = new URLParser(path);
        PointAuthorizer.skipAskPassword = false;
    }

    @Override
    public void getChildren(MessageID mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        String midString = ((PointMessageID)mid).getId();
        String user = ((PointMessageID)mid).user;
        String url = user != null && user.length() > 0 ? "http://" + user.toLowerCase() + ".point.im/" + midString : "http://point.im/" + midString;
        Utils.RESTResponse jsonWithRetries = getJSONWithRetries(ctx, url, notifications);
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


    HashSet<String> loadedMessages = new HashSet<String>();

    @Override
    public void getFirst(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        page = 1;
        loadedMessages.clear();
        fetchURLAndProcess(notifications, cont);
    }

    protected void fetchURLAndProcess(Utils.Notification notification, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        // put in page
        String pathPart = urlParser.getPathPart();
        int ix = pathPart.lastIndexOf("/");
        if (ix != -1) {
            try {
                Integer.parseInt(pathPart.substring(ix+1));
                urlParser.setPath(pathPart.substring(0, ix));  // replace page
            } catch (NumberFormatException e) {
                // not a number
            }
        } else {
            try {
                Integer.parseInt(pathPart);
                urlParser.setPath("");  // pathPart is already number
            } catch (Exception ex) {}
            // have no number
        }
        if (page != 0) {
            if (urlParser.getPathPart().length() > 0) {
                urlParser.setPath(urlParser.getPathPart()+"/"+page);
            } else {
                urlParser.setPath(""+page);
            }
        }
        final String jsonStr = getJSONWithRetries(ctx, urlParser.getFullURL(), notification).getResult();
        if (jsonStr != null) {
            ArrayList<JuickMessage> messages = new PointNetParser().parseWebMessageListPure(jsonStr);
            if (messages.size() > 0) {
                for (Iterator<JuickMessage> iterator = messages.iterator(); iterator.hasNext(); ) {
                    JuickMessage message = iterator.next();
                    if (!loadedMessages.add(""+message.getMID())) {
                        iterator.remove();
                    }
                }
                if (loadedMessages.size() == 0) {
                    page++;
                    fetchURLAndProcess(notification, cont);
                    return;
                }
            }
            cont.apply(messages);
        } else {
            // error (notified via Notification)
            cont.apply(new ArrayList<JuickMessage>());
        }
    }

    @Override
    public void getNext(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        page++;
        fetchURLAndProcess(notifications, cont);
    }

    @Override
    public CharSequence getTitle() {
        return title;
    }

    @Override
    public MicroBlog getMicroBlog() {
        return MainActivity.getMicroBlog(PointMessageID.CODE);
    }

    public Utils.RESTResponse getJSONWithRetries(Context ctx, String url, Utils.Notification notifications) {
        return Utils.getJSONWithRetries(ctx,url, notifications);
    }




}
