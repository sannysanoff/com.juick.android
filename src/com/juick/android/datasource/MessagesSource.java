package com.juick.android.datasource;

import android.content.Context;
import com.juick.android.Utils;
import com.juick.android.api.JuickMessage;

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

    transient Context ctx;

    public MessagesSource(Context ctx) {
        this.ctx = ctx;
    }

    public void setContext(Context ctx) {
        this.ctx = ctx;
    }

    public abstract boolean supportsBackwardRefresh();

    public void rememberSavedPosition(int mid) {

    }

    public void resetSavedPosition() {

    }

    public abstract void getFirst(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont);

    public abstract void getNext(Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont);

    public abstract void getChildren(int mid, Utils.Notification notifications, Utils.Function<Void, ArrayList<JuickMessage>> cont);

    public abstract CharSequence getTitle();
}
