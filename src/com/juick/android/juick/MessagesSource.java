package com.juick.android.juick;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;
import com.juick.android.MicroBlog;
import com.juick.android.Utils;
import com.juick.android.api.JuickMessage;
import com.juick.android.api.MessageID;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 9/5/12
 * Time: 10:44 AM
 * To change this template use File | Settings | File Templates.
 */
public abstract  class MessagesSource implements Serializable {

    protected transient Context ctx;
    protected transient SharedPreferences sp;

    public MessagesSource(Context ctx) {
        setContext(ctx);
    }

    public void setContext(Context ctx) {
        this.ctx = ctx;
        sp = PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    public abstract boolean supportsBackwardRefresh();

    public void rememberSavedPosition(MessageID mid) {

    }

    public void resetSavedPosition() {

    }

    public abstract void getFirst(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont);

    public abstract void getNext(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont);

    public boolean canNext() {
        return true;
    };


    public abstract void getChildren(MessageID mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont);

    public abstract CharSequence getTitle();

    public String getKind() {
        return "other";
    }

    public abstract MicroBlog getMicroBlog();

}
