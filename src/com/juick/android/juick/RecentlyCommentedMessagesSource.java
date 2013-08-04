package com.juick.android.juick;

import android.content.Context;
import com.juick.android.DatabaseService;
import com.juickadvanced.R;
import com.juickadvanced.data.juick.JuickMessage;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 8/5/13
 * Time: 2:28 AM
 * To change this template use File | Settings | File Templates.
 */
public class RecentlyCommentedMessagesSource extends RecentMessagesSource {

    public RecentlyCommentedMessagesSource(Context ctx) {
        super(ctx);
    }

    @Override
    protected ArrayList<JuickMessage> askForRecentMessages(DatabaseService service) {
        return service.getRecentlyCommentedThreads();
    }

    @Override
    public CharSequence getTitle() {
        return ctx.getString(R.string.navigationRecentlyCommented);
    }
}
