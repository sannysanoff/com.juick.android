package com.juick.android.psto;

import com.juick.android.bnw.BNWMessage;
import com.juick.android.bnw.BnwMessageID;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/10/12
 * Time: 1:46 PM
 * To change this template use File | Settings | File Templates.
 */
public class PstoMessage extends BNWMessage {

    @Override
    protected String webLinkToMessage(String msg) {
        PstoMessageID mid = (PstoMessageID)getMID();
        msg += "http://"+mid.user+".psto.net/"+mid.getId();
        return msg;
    }

}
