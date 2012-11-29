package com.juick.android.juick;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.juick.android.DatabaseService;
import com.juick.android.Utils;
import com.juickadvanced.data.juick.JuickMessage;
import com.juickadvanced.R;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 9/5/12
 * Time: 10:45 AM
 * To change this template use File | Settings | File Templates.
 */
public class UnreadSegmentMessagesSource extends JuickCompatibleURLMessagesSource {

    private DatabaseService.Period period;

    public UnreadSegmentMessagesSource(String label, Context ctx, DatabaseService.Period period) {
        super(label, ctx);
        this.period = period;
    }

    @Override
    public boolean supportsBackwardRefresh() {
        return false;
    }

    @Override
    public void getFirst(Utils.Notification notification, final Utils.Function<Void, ArrayList<JuickMessage>> cont) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        putArg("before_mid", ""+period.startMid);
        fetchURLAndProcess(notification, new Utils.Function<Void, ArrayList<JuickMessage>>() {
            @Override
            public Void apply(ArrayList<JuickMessage> first) {
                if (first.size() > 0) {
                    first.get(0).continuationInformation = ctx.getString(R.string.UnreadPeriodStart);
                }
                cont.apply(first);
                return null;
            }
        });
    }

    protected boolean areMessagesInRow() {
        return true;
    }

    @Override
    public String getKind() {
        return "unread_segments";
    }
}
