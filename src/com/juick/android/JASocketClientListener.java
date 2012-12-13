package com.juick.android;

import java.io.IOException;

/**
* Created with IntelliJ IDEA.
* User: san
* Date: 12/12/12
* Time: 3:15 PM
* To change this template use File | Settings | File Templates.
*/
public interface JASocketClientListener {

    public void onWebSocketTextFrame(String data) throws IOException;
}
