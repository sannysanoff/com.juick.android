package com.juick.android.datasource;

import android.content.Context;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/6/12
 * Time: 1:07 AM
 * To change this template use File | Settings | File Templates.
 */
public abstract class JuickMessagesSource extends MessagesSource {

    String kind;

    public JuickMessagesSource(Context ctx) {
        super(ctx);
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }
}
