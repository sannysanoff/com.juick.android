package com.juick.android.ja;

import android.content.Context;
import com.juick.android.MainActivity;
import com.juick.android.MicroBlog;
import com.juick.android.Utils;
import com.juick.android.juick.JuickCompatibleURLMessagesSource;
import com.juick.android.juick.MessagesSource;
import com.juickadvanced.R;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickMessageID;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 4/12/13
 * Time: 5:44 AM
 * To change this template use File | Settings | File Templates.
 */
public class JAPastConversationMessagesSource extends MessagesSource {

    private final String uname;

    public JAPastConversationMessagesSource(Context ctx, String uname) {
        super(ctx);
        this.uname = uname;
        setCanNext(false);
    }

    @Override
    public boolean supportsBackwardRefresh() {
        return true;
    }

    @Override
    public void getFirst(final Utils.Notification notifications, final Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        final String url = "https://ja.ip.rt.ru:8444/api/pending?command=past_conversations&uname="+uname;
        final Utils.Function<Void, Utils.RESTResponse> then = new Utils.Function<Void, Utils.RESTResponse>() {
            @Override
            public Void apply(Utils.RESTResponse response) {
                ArrayList<JuickMessage> messages = JuickCompatibleURLMessagesSource.parseJSONpure(response.getResult(), false);
                cont.apply(messages);
                return null;
            }
        };
        Network.executeJAHTTPS(getContext(), notifications, url, then);
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
        return ctx.getString(R.string.PastDialogsWithUser);
    }

    @Override
    public MicroBlog getMicroBlog() {
        return MainActivity.getMicroBlog(JuickMessageID.CODE);
    }
}
