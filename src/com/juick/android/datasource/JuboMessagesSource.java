package com.juick.android.datasource;

import android.content.Context;
import com.juick.android.Utils;
import com.juick.android.api.JuickMessage;
import com.juickadvanced.R;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 9/19/12
 * Time: 10:56 PM
 * To change this template use File | Settings | File Templates.
 */
public class JuboMessagesSource extends MessagesSource {

    public JuboMessagesSource(Context ctx) {
        super(ctx);
    }

    @Override
    public void getChildren(int mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        new JuickCompatibleURLMessagesSource(ctx).getChildren(mid, notifications, cont);
    }

    @Override
    public boolean supportsBackwardRefresh() {
        return false;
    }

    @Override
    public void getFirst(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        cont.apply(new ArrayList<JuickMessage>());
    }

    @Override
    public void getNext(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        cont.apply(new ArrayList<JuickMessage>());
    }

    @Override
    public CharSequence getTitle() {
        return ctx.getString(R.string.navigationJuboRSS);
    }
}
