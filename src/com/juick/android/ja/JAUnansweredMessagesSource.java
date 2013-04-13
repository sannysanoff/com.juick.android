package com.juick.android.ja;

import android.app.Activity;
import android.content.Context;
import android.util.Pair;
import com.juick.android.MainActivity;
import com.juick.android.MicroBlog;
import com.juick.android.Utils;
import com.juick.android.juick.JuickComAuthorizer;
import com.juick.android.juick.JuickCompatibleURLMessagesSource;
import com.juick.android.juick.JuickMicroBlog;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.R;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickMessageID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 4/12/13
 * Time: 5:44 AM
 * To change this template use File | Settings | File Templates.
 */
public class JAUnansweredMessagesSource extends MessagesSource {

    public JAUnansweredMessagesSource(Context ctx) {
        super(ctx);
        setCanNext(false);
    }

    @Override
    public boolean supportsBackwardRefresh() {
        return true;
    }

    @Override
    public void getFirst(final Utils.Notification notifications, final Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        final String url = "https://ja.ip.rt.ru:8444/api/pending?command=list";
        final Utils.Function<Void, Utils.RESTResponse> then = new Utils.Function<Void, Utils.RESTResponse>() {
            @Override
            public Void apply(Utils.RESTResponse response) {
                ArrayList<JuickMessage> messages = parseAndProcess(response.getResult());
                cont.apply(messages);
                return null;
            }
        };
        Network.executeJAHTTPS(getContext(), notifications, url, then);
    }

    private ArrayList<JuickMessage> parseAndProcess(String jsonStr) {
        if (jsonStr == null) return new ArrayList<JuickMessage>();
        try {
            final JSONObject jsonObject = new JSONObject(jsonStr);
            final ArrayList<JuickMessage> juickMessages = JuickCompatibleURLMessagesSource.parseJSONpure(jsonObject.get("replies").toString(), false);
            final ArrayList<JuickMessage> posts = JuickCompatibleURLMessagesSource.parseJSONpure(jsonObject.get("posts").toString(), false);
            final ArrayList<JuickMessage> myReplies = JuickCompatibleURLMessagesSource.parseJSONpure(jsonObject.get("my_replies").toString(), false);
            // join context to messages
            for (JuickMessage juickMessage : juickMessages) {
                final MessageID mid = juickMessage.getMID();
                for (JuickMessage post : posts) {
                    if (post.getMID().equals(mid)) {
                        juickMessage.contextPost = post;
                        break;
                    }
                }
                for (JuickMessage myreply : myReplies) {
                    if (myreply.getMID().equals(mid) && myreply.getRID() == juickMessage.getReplyTo()) {
                        juickMessage.contextReply = myreply;
                        break;
                    }
                }
            }
            return juickMessages;
        } catch (JSONException e) {
            return new ArrayList<JuickMessage>();
        }
    }

    @Override
    public void getNext(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        cont.apply(new ArrayList<JuickMessage>());
    }

    @Override
    public void getChildren(MessageID mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        new JuickCompatibleURLMessagesSource(ctx).getChildren(mid, notifications, cont);
    }

    @Override
    public CharSequence getTitle() {
        return ctx.getString(R.string.navigationUnanswered);
    }

    @Override
    public MicroBlog getMicroBlog() {
        return MainActivity.getMicroBlog(JuickMessageID.CODE);
    }
}
