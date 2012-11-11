package com.juick.android.psto;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import com.juick.android.Utils;
import com.juick.android.juick.JuickComAuthorizer;
import com.juickadvanced.R;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import java.net.*;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/9/12
 * Time: 12:03 AM
 * To change this template use File | Settings | File Templates.
 */
public class PstoAuthorizer extends Utils.URLAuth {
    @Override
    public boolean acceptsURL(String url) {
        if (url.indexOf("psto.net/") != -1) return true;
        return false;
    }

    // 0 = not accepting 1 = accepting, required -1 = accepting not requred
    public boolean allowsOptionalAuthorization(String url) {
        if (url.indexOf("http://psto.net/top") != -1) return true;
        if (url.indexOf("http://psto.net/recent") != -1) return true;
        if (url.indexOf(".psto.net") != -1) return true;
        return false;
    }

    public static String myCookie;
    @Override
    public void authorize(final Activity activity, String url, final Utils.Function<Void, String> cont) {
        cont.apply(null);
    }

    @Override
    public void authorizeRequest(HttpRequestBase request, String cookie) {
    }

    @Override
    public void authorizeRequest(HttpURLConnection conn, String cookie) {
    }

    @Override
    public String authorizeURL(String url, String cookie) {
        return url;
    }
}
