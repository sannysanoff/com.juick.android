package com.juick.android.datasource;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.juick.android.Utils;
import com.juick.android.api.JuickMessage;
import com.juickadvanced.R;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 9/5/12
 * Time: 10:45 AM
 * To change this template use File | Settings | File Templates.
 */
public class AllMessagesSource extends JuickCompatibleURLMessagesSource {

    public AllMessagesSource(Context ctx) {
        super(ctx.getString(R.string.All_messages), ctx);
    }

    @Override
    public void getFirst(Utils.Notification notification, final Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        boolean addContinuation = false;
        if (sp.getBoolean("persistLastMessagesPosition", false)) {
            int lastMessagesSavedPosition = sp.getInt("lastMessagesSavedPosition", -1);
            if (lastMessagesSavedPosition != -1) {
                putArg("before_mid", ""+lastMessagesSavedPosition);
                addContinuation = true;
            }
        }
        final boolean finalAddContinuation = addContinuation;
        super.getFirst(notification, new Utils.Function<Void, ArrayList<JuickMessage>>() {
            @Override
            public Void apply(ArrayList<JuickMessage> first) {
                if (first.size() > 0 && finalAddContinuation) {
                    first.get(0).continuationInformation = ctx.getString(R.string.ResumingFromLastTime);
                }
                cont.apply(first);
                return null;
            }
        });
    }

    @Override
    public void resetSavedPosition() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        sp.edit().remove("lastMessagesSavedPosition").commit();
    }

    @Override
    public void rememberSavedPosition(int mid) {
        super.rememberSavedPosition(mid);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        sp.edit().putInt("lastMessagesSavedPosition", mid).commit();
    }

    protected boolean areMessagesInRow() {
        return true;
    }


}
