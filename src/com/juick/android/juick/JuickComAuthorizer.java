package com.juick.android.juick;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;
import com.juick.android.Base64;
import com.juick.android.Utils;
import com.juick.android.ja.Network;
import com.juickadvanced.R;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.message.BasicHeader;

import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/9/12
 * Time: 12:03 AM
 * To change this template use File | Settings | File Templates.
 */
public class JuickComAuthorizer extends Utils.URLAuth {
    static String accountName;

    public static String getJuickAccountName(Context context) {
        if (accountName == null) {
            AccountManager am = AccountManager.get(context);
            Account accs[] = am.getAccountsByType("com.juickadvanced");
            if (accs.length > 0) {
                accountName = accs[0].name.trim();
            }
        }
        return accountName;
    }

    @Override
    public boolean acceptsURL(String url) {
        return url.indexOf("api.juick.com") != -1;
    }

    @Override
    public void authorize(Context act, boolean forceOptionalAuth, String url, Utils.Function<Void, String> withCookie) {
        withCookie.apply(getBasicAuthString(act.getApplicationContext()));
    }

    @Override
    public void authorizeRequest(HttpRequestBase request, String cookie) {
        if (cookie.length() > 0) {
            request.addHeader(new BasicHeader("Authorization", cookie));
        }
        if (cachedIPAddress != null && cachedIPAddress.length() > 0) {
            if (request.getURI().toString().contains(cachedIPAddress)) {
                request.addHeader("Host","api.juick.com");
            }
        }
    }

    @Override
    public void authorizeRequest(Context context, HttpURLConnection conn, String cookie, String url) {
        if (cachedIPAddress != null && cachedIPAddress.length() > 0) {
            if (url.contains(cachedIPAddress)) {
                conn.addRequestProperty("Host","api.juick.com");
            }
        }
        conn.setRequestProperty("Authorization", cookie);
    }

    public String cachedIPAddress = null;

    @Override
    public String authorizeURL(String url, String cookie) {
        if (url.startsWith("http://api.juick.com/")) {
            if (cachedIPAddress == null) {
                try {
                    InetAddress byName = Inet4Address.getByName("api.juick.com");
                    cachedIPAddress = byName.getHostAddress();
                } catch (UnknownHostException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
            if (cachedIPAddress != null && cachedIPAddress.length() > 0) {
                url = url.replace("api.juick.com", cachedIPAddress);
            }
        }
        return url;
    }

    @Override
    public ReplyCode validateNon200Reply(HttpURLConnection conn, String url) {
        return ReplyCode.FAIL;
    }

    @Override
    public ReplyCode validateNon200Reply(HttpResponse o, String url) {
        return ReplyCode.FAIL;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void clearCookie(Context context, Runnable then) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * must call on secondary thread
     */
    public static String getBasicAuthString(Context context) {
        AccountManager am = AccountManager.get(context);
        Account accs[] = am.getAccountsByType(context.getString(R.string.com_juick));
        if (accs.length > 0) {
            Bundle b = null;
            try {
                b = am.getAuthToken(accs[0], "", false, null, null).getResult();
            } catch (Exception e) {
                Log.e("getBasicAuthString", Log.getStackTraceString(e));
            }
            if (b != null) {
                String authStr = b.getString(AccountManager.KEY_ACCOUNT_NAME) + ":" + b.getString(AccountManager.KEY_AUTHTOKEN);
                final String auth = "Basic " + Base64.encodeToString(authStr.getBytes(), Base64.NO_WRAP);
                if (context instanceof Activity) {
                    final Activity act = (Activity)context;
                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                    boolean verboseDebug = sp.getBoolean("verboseDebug", false);
                    if (verboseDebug) {
                        act.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(act, "Auth: " + auth, Toast.LENGTH_LONG).show();
                            }
                        });

                    }
                }
                return auth;
            }
        }
        return "";
    }

    /**
     * must call on secondary thread
     */
    public static String getPassword(Context context) {
        AccountManager am = AccountManager.get(context);
        Account accs[] = am.getAccountsByType(context.getString(R.string.com_juick));
        if (accs.length > 0) {
            Bundle b = null;
            try {
                b = am.getAuthToken(accs[0], "", false, null, null).getResult();
            } catch (Exception e) {
                Log.e("getBasicAuthString", Log.getStackTraceString(e));
            }
            if (b != null) {
                return b.getString(AccountManager.KEY_AUTHTOKEN);
            }
        }
        return null;
    }

}
