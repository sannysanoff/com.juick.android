package com.juick.android;


import android.content.Context;
import com.juickadvanced.IHTTPClient;
import com.juickadvanced.IHTTPClientService;
import com.juickadvanced.RESTResponse;

/**
 * Created by san on 8/8/14.
 */
public class DefaultHTTPClientService implements IHTTPClientService {

    Context context;

    public DefaultHTTPClientService(Context context) {
        this.context = context;
    }

    @Override
    public RESTResponse getJSON(String url, Utils.Notification progressNotification) {
        return Utils.getJSON(context, url, progressNotification);
    }

    @Override
    public IHTTPClient createClient() {
        return new Utils.AndroidHTTPClient(context);
    }
}
