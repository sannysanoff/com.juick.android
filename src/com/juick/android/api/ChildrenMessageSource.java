package com.juick.android.api;

import android.content.Context;
import com.juick.android.bnw.BnwCompatibleMessagesSource;
import com.juick.android.juick.JuickCompatibleURLMessagesSource;
import com.juick.android.juick.MessagesSource;
import com.juick.android.point.PointAPIMessagesSource;
import com.juickadvanced.R;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.bnw.BnwMessageID;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.data.juick.JuickMessageID;
import com.juickadvanced.data.point.PointMessageID;

import java.util.ArrayList;

/**
 * Created by san on 8/15/14.
 */
public class ChildrenMessageSource {
    public static MessagesSource forMID(Context ctx, MessageID mid) {
        if (mid instanceof JuickMessageID) {
            return new JuickCompatibleURLMessagesSource(ctx,"xmpp_incoming");
        }
        if (mid instanceof PointMessageID) {
            return new PointAPIMessagesSource(ctx, "starter_fetcher", "", "http://point.im/api/recent");
        }
        if (mid instanceof BnwMessageID) {
            return new BnwCompatibleMessagesSource(ctx, "direct_mid", "nav-all","/show");
        }
        throw new IllegalArgumentException("ChildrenMessageSource: for "+mid.getClass().getName());
    }
}
