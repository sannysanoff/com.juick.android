package com.juick.android.bnw;

import android.content.Context;
import android.util.Log;
import com.juick.android.MainActivity;
import com.juick.android.MicroBlog;
import com.juick.android.URLParser;
import com.juick.android.Utils;
import com.juickadvanced.data.bnw.BNWMessage;
import com.juickadvanced.data.bnw.BnwMessageID;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickUser;
import com.juickadvanced.data.MessageID;
import com.juick.android.juick.MessagesSource;
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

    URLParser urlParser;
    String title;
    int page;

    public BnwCompatibleMessagesSource(Context ctx, String title, String path) {
        super(ctx);
        this.title = title;
        urlParser = new URLParser("http://ipv4.bnw.im/api"+path);
    }

    @Override
    public void getChildren(MessageID mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        String midString = ((BnwMessageID)mid).getId();
        final String jsonStr = getJSONWithRetries(ctx, "http://ipv4.bnw.im/api/show?message=" + midString + "&replies=1", notifications).getResult();
        if (jsonStr != null) {
            try {
                JSONObject fullThread = new JSONObject(jsonStr);
                JSONObject root = fullThread.getJSONObject("message");
                ArrayList<BNWMessage> msgs = new ArrayList<BNWMessage>();
                msgs.add(initFromJSON(root));
                JSONArray replies = fullThread.getJSONArray("replies");
                HashMap<String,Integer> numbersRemap = new HashMap<String, Integer>();
                int replyNo = 1;
                for(int i = 0; i < replies.length(); i++) {
                    BNWMessage reply = initFromJSON(replies.getJSONObject(i));
                    msgs.add(reply);
                    reply.setRID(replyNo);
                    numbersRemap.put(reply.getRIDString(), replyNo);
                    replyNo++;
                }
                for (int i = 1; i < msgs.size(); i++) {
                    BNWMessage msg = msgs.get(i);
                    String replyToString = msg.getReplyToString();
                    if (replyToString == null) {
                        msg.setReplyTo(0);
                    } else {
                        Integer prevComment = numbersRemap.get(replyToString);
                        if (prevComment == null) prevComment = 0;
                        msg.setReplyTo(prevComment);
                    }
                }
                cont.apply(new ArrayList<JuickMessage>(msgs));
            } catch (JSONException e) {
                cont.apply(new ArrayList<JuickMessage>());
            }
        } else {
            cont.apply(new ArrayList<JuickMessage>());
        }
        System.out.println("oh");

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
        if (page == 0) {
            urlParser.getArgsMap().remove("page");
        } else {
            urlParser.getArgsMap().put("page", "" + page);
        }
        final String jsonStr = getJSONWithRetries(ctx, urlParser.getFullURL(), notification).getResult();
        ArrayList<JuickMessage> messages = parseJSONpure(jsonStr);
        if (messages.size() > 0) {
            ArrayList<JuickMessage> reverse = new ArrayList<JuickMessage>();
            for (Iterator<JuickMessage> iterator = messages.iterator(); iterator.hasNext(); ) {
                JuickMessage message = iterator.next();
                if (!loadedMessages.add(message.getMID().toString())) {
                    iterator.remove();
                } else {
                    reverse.add(0, message);
                }
            }
            if (loadedMessages.size() == 0) {
                page++;
                fetchURLAndProcess(notification, cont);
                return;
            } else {
                messages = reverse;
            }
        }
        cont.apply(messages);
    }

    public ArrayList<JuickMessage> parseJSONpure(String jsonStr) {
        return parseJSONpure(jsonStr, false);
    }

    public ArrayList<JuickMessage> parseJSONpure(String jsonStr, boolean storeSource) {
        ArrayList<JuickMessage> messages = new ArrayList<JuickMessage>();
        if (jsonStr != null) {
            try {
                JSONObject objMessages = new JSONObject(jsonStr);
                JSONArray json = objMessages.getJSONArray("messages");
                int cnt = json.length();
                for (int i = 0; i < cnt; i++) {
                    JSONObject jsonObject = json.getJSONObject(i);
                    JuickMessage msg = BnwCompatibleMessagesSource.initFromJSON(jsonObject);
                    messages.add(msg);
                    if (!storeSource)
                        msg.source = null;
                }
            } catch (Exception e) {
                Log.e("initOpinionsAdapter", e.toString());
            }
        }
        return messages;
    }

    public static BNWMessage initFromJSON(JSONObject json) throws JSONException {
        BNWMessage jmsg = new BNWMessage();
        if (json.has("message")) {
            jmsg.setMID(new BnwMessageID(json.getString("message")));
            jmsg.setRIDString(json.getString("id"));
        } else {
            jmsg.setMID(new BnwMessageID(json.getString("id")));
        }
        jmsg.Text = json.getString("text");
        Calendar cal = new GregorianCalendar();
        cal.setTimeZone(TimeZone.getTimeZone("GMT"));
        cal.setTimeInMillis((long) (json.getDouble("date") * 1000));
        jmsg.Timestamp = cal.getTime();
        jmsg.User = new JuickUser();
        jmsg.User.UName = json.getString("user");
        if (json.has("replyto"))
            jmsg.setReplyToString(json.getString("replyto"));

        if (json.has("tags")) {
            JSONArray tags = json.getJSONArray("tags");
            for (int n = 0; n < tags.length(); n++) {
                jmsg.tags.add(tags.getString(n).replace("&quot;", "\""));
            }
        }
        if (json.has("clubs")) {
            JSONArray clubs = json.getJSONArray("clubs");
            for (int n = 0; n < clubs.length(); n++) {
                jmsg.clubs.add(clubs.getString(n).replace("&quot;", "\""));
            }
        }
        if (json.has("replycount")) {
            jmsg.replies = json.getInt("replycount");
        }
        jmsg.microBlogCode = BnwMessageID.CODE;
        return jmsg;
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
        return MainActivity.getMicroBlog(BnwMessageID.CODE);
    }

    public Utils.RESTResponse getJSONWithRetries(Context ctx, String url, Utils.Notification notifications) {
        return Utils.getJSONWithRetries(ctx,url, notifications);
    }

}
