package com.juick.android.bnw;

import android.app.Activity;
import android.widget.ListView;
import com.juick.android.MessageMenu;
import com.juick.android.JuickMessagesAdapter;
import com.juick.android.juick.MessagesSource;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/10/12
 * Time: 10:54 AM
 * To change this template use File | Settings | File Templates.
 */
public class BNWMessageMenu extends MessageMenu {
    public BNWMessageMenu(Activity activity, MessagesSource messagesSource, ListView listView, JuickMessagesAdapter listAdapter) {
        super(activity, messagesSource, listView, listAdapter);
    }

}
