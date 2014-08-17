package com.juick.android.facebook;

import android.app.Activity;
import android.content.Intent;
import android.widget.ListView;
import android.widget.Toast;
import com.juick.android.JuickMessagesAdapter;
import com.juick.android.MessageMenu;
import com.juick.android.MessagesActivity;
import com.juick.android.juick.MessagesSource;
import com.juick.android.point.PointWebCompatibleMessagesSource;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/14/12
 * Time: 2:33 PM
 * To change this template use File | Settings | File Templates.
 */
public class FacebookMessageMenu extends MessageMenu {

    public FacebookMessageMenu(Activity activity, MessagesSource messagesSource, ListView listView, JuickMessagesAdapter listAdapter) {
        super(activity, messagesSource, listView, listAdapter);
    }

    @Override
    protected void addMicroblogSpecificCommands(String UName) {
        //
    }



    @Override
    public boolean isDialogMode() {
        return false;
    }

    protected void actionUserBlog() {
        Toast.makeText(activity, "Not implemented yet", Toast.LENGTH_SHORT);
//        Intent i = new Intent(activity, MessagesActivity.class);
//        i.putExtra("messagesSource", new PointWebCompatibleMessagesSource(activity, "userblog", "@" + listSelectedItem.User.UName, "http://"+listSelectedItem.User.UName+".point.im/"));
//        activity.startActivity(i);
    }


    @Override
    protected void maybeAddDeleteItem() {
        //
    }
}
