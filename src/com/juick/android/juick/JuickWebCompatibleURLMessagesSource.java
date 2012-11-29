package com.juick.android.juick;

import android.content.Context;
import com.juick.android.URLParser;
import com.juick.android.Utils;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickMessageID;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.parsers.DevJuickComMessages;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/6/12
 * Time: 12:59 AM
 * To change this template use File | Settings | File Templates.
 */
public class JuickWebCompatibleURLMessagesSource extends JuickMessagesSource  {

    String label;
    URLParser urlParser;
    private int lastRetrievedMID;

    public JuickWebCompatibleURLMessagesSource(String label, Context activity, String url) {
        super(activity);
        urlParser = new URLParser(url);
        this.label = label;
    }

    @Override
    public boolean supportsBackwardRefresh() {
        return true;
    }

    @Override
    public void getFirst(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        urlParser.getArgsMap().remove("before");
        lastRetrievedMID = -1;
        fetchURLAndProcess(notifications, cont);
    }

    protected void fetchURLAndProcess(Utils.Notification notification, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        Utils.RESTResponse result = new JuickCompatibleURLMessagesSource(label, ctx, urlParser.getFullURL()).getJSONWithRetries(ctx, urlParser.getFullURL(), notification);
        final String jsonStr = result.getResult();
        Utils.DownloadErrorNotification errorNotification = null;
        if (notification instanceof Utils.DownloadErrorNotification)
            errorNotification = (Utils.DownloadErrorNotification)notification;
        if (jsonStr == null) {
            if (errorNotification != null)
                errorNotification.notifyDownloadError("Page download error.");
            cont.apply(new ArrayList<JuickMessage>());
        } else {
            ArrayList<JuickMessage> messages = new DevJuickComMessages().parseWebPage(jsonStr);
            if (messages.size() > 0) {
                JuickMessage juickMessage = messages.get(messages.size() - 1);
                lastRetrievedMID = ((JuickMessageID)juickMessage.getMID()).getMid();
            } else {
                if (errorNotification != null)
                    errorNotification.notifyDownloadError("Page parse error.");
            }
            cont.apply(messages);
        }
    }


    @Override
    public void getNext(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        if (lastRetrievedMID > 0) {
            putArg("before",""+lastRetrievedMID);
        }
        fetchURLAndProcess(notifications, cont);
    }

    public void putArg(String name, String value) {
        urlParser.getArgsMap().put(name, value);
    }

    @Override
    public void getChildren(MessageID mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        new JuickCompatibleURLMessagesSource(ctx).getChildren(mid, notifications, cont);
    }

    @Override
    public CharSequence getTitle() {
        return label;
    }

}
