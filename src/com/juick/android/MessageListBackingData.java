package com.juick.android;

import com.juick.android.juick.MessagesSource;
import com.juickadvanced.data.MessageID;
import com.juickadvanced.data.juick.JuickMessage;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 12/13/12
 * Time: 5:28 PM
 * To change this template use File | Settings | File Templates.
 */
public class MessageListBackingData implements Serializable {

    int navigationItemLabelId;
    MessagesSource messagesSource;
    ArrayList<JuickMessage> messages;
    MessageID topMessageId;

    public int topMessageScrollPos;
}
