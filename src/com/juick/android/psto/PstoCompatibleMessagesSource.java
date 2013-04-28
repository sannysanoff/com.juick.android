package com.juick.android.psto;

import android.content.Context;
import com.juick.android.MainActivity;
import com.juick.android.MicroBlog;
import com.juick.android.URLParser;
import com.juick.android.Utils;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.MessageID;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.data.psto.PstoMessage;
import com.juickadvanced.data.psto.PstoMessageID;
import com.juickadvanced.parsers.PstoNetParser;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/9/12
 * Time: 12:59 AM
 * To change this template use File | Settings | File Templates.
 */
public class PstoCompatibleMessagesSource extends MessagesSource {

    URLParser urlParser;
    String title;
    int page;

    public PstoCompatibleMessagesSource(Context ctx, String psto_kind, String title, String path) {
        super(ctx, "psto_"+psto_kind);
        this.title = title;
        urlParser = new URLParser(path);
        PstoAuthorizer.skipAskPassword = false;
    }

    @Override
    public void getChildren(MessageID mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        String midString = ((PstoMessageID)mid).getId();
        String user = ((PstoMessageID)mid).user;
        String url = user != null && user.length() > 0 ? "http://" + user.toLowerCase() + ".psto.net/" + midString : "http://psto.net/" + midString;
        Utils.RESTResponse jsonWithRetries = getJSONWithRetries(ctx, url, notifications);
        if (jsonWithRetries.errorText != null) {
            cont.apply(PstoNetParser.badRetval);
        } else {
            String result = jsonWithRetries.result;
            ArrayList<JuickMessage> messages = new PstoNetParser().parseWebMessageListPure(result, PstoNetParser.ParseMode.PARSE_THREAD_FIRST);
            if (messages.size() == 1) {
                PstoMessage msg = (PstoMessage)messages.get(0);
                msg.setMID(mid);
                int commentsStart = result.indexOf("<div class=\"comments\">");
                if (commentsStart > 0) {
                    result = result.substring(commentsStart + 10);
                    ArrayList<JuickMessage> comments = new PstoNetParser().parseWebMessageListPure(result, PstoNetParser.ParseMode.PARSE_THREAD_COMMENTS);
                    HashMap<Integer, JuickMessage> replies = new HashMap<Integer, JuickMessage>();
                    for (JuickMessage comment : comments) {
                        replies.put(comment.getRID(), comment);
                    }
                    for (JuickMessage comment : comments) {
                        comment.setMID(mid);
                        // filling in "@User" into replies
                        if (comment.getReplyTo() != 0) {
                            JuickMessage juickMessage = replies.get(comment.getReplyTo());
                            if (juickMessage != null) {
                                comment.Text = "@"+juickMessage.User.UName+" "+comment.Text;
                            }
                        }
                    }
                    messages.addAll(comments);
                }
                cont.apply(messages);
            } else {
                cont.apply(PstoNetParser.badRetval);
            }
        }

    }

    @Override
    public boolean supportsBackwardRefresh() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }


    HashSet<String> loadedMessages = new HashSet<String>();

    @Override
    public void getFirst(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        page = 0;
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
            // have no number
        }
        if (page != 0) {
            urlParser.setPath(urlParser.getPathPart()+"/"+page);
        }
        final String jsonStr = getJSONWithRetries(ctx, urlParser.getFullURL(), notification).getResult();
        if (jsonStr != null) {
            ArrayList<JuickMessage> messages = new PstoNetParser().parseWebMessageListPure(jsonStr, PstoNetParser.ParseMode.PARSE_MESSAGE_LIST);
            if (messages.size() > 0) {
                for (Iterator<JuickMessage> iterator = messages.iterator(); iterator.hasNext(); ) {
                    JuickMessage message = iterator.next();
                    if (!loadedMessages.add(message.getMID().toString())) {
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
        return MainActivity.getMicroBlog(PstoMessageID.CODE);
    }

    public Utils.RESTResponse getJSONWithRetries(Context ctx, String url, Utils.Notification notifications) {
        return Utils.getJSONWithRetries(ctx,url, notifications);
    }




}
