package com.juick.android.juick;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import com.juick.android.Base64;
import com.juick.android.Utils;
import com.juickadvanced.R;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.message.BasicHeader;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStreamWriter;
import java.net.*;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/9/12
 * Time: 12:03 AM
 * To change this template use File | Settings | File Templates.
 */
public class JuickAPIAuthorizer extends Utils.URLAuth {
    static String accountName;

    public static String getJuickAccountName(Context context) {
        if (accountName == null) {
            AccountManager am = AccountManager.get(context);
            Account accs[] = am.getAccountsByType("com.juickadvanced");
            if (accs.length > 0) {
                accountName = accs[0].name.trim();
            }
        }
        if (accountName == null) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            accountName = sp.getString("juick_account_name", null);
            if (getPassword(context) == null)
                accountName = null;
        }
        return accountName;
    }

    @Override
    public boolean acceptsURL(String url) {
        return url.indexOf("api.juick.com") != -1;
    }

    String lastLoginName;

    @Override
    public void authorize(final Context act, final boolean forceLoginDialog, boolean forceAttachCredentials, final String url, final Utils.Function<Void, String> withCookie) {
        if (!authNeeded(url) && !forceAttachCredentials) {
            withCookie.apply(null);
            return;
        }
        final String basicAuthString = getBasicAuthString(act.getApplicationContext());
        if (getPassword(act.getApplicationContext()) == null || basicAuthString.length() == 0 || forceLoginDialog) {
            final Activity activity = (Activity)act;
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
                        final View content = activity.getLayoutInflater().inflate(R.layout.web_login, null);
                        final EditText login = (EditText)content.findViewById(R.id.login);
                        final CheckBox insecure = (CheckBox) content.findViewById(R.id.insecure);
                        PackageManager pm = activity.getPackageManager();
                        try {
                            PackageInfo pi = pm.getPackageInfo("com.juickadvanced", 0);
                            ApplicationInfo ai = pi.applicationInfo;
                            if ((ai.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) == ApplicationInfo.FLAG_EXTERNAL_STORAGE) {
                                insecure.setChecked(true);
                            }
                        } catch (PackageManager.NameNotFoundException e) {
                            // do something
                        }
                        insecure.setEnabled(true);
                        final EditText password = (EditText)content.findViewById(R.id.password);
                        String loginName = JuickAPIAuthorizer.getJuickAccountName(activity);
                        if (lastLoginName != null) loginName = lastLoginName;
                        login.setText(loginName);
                        login.setHint("JuickUser");
                        AlertDialog dlg = new AlertDialog.Builder(activity)
                                .setTitle("Juick.com API login")
                                .setView(content)
                                .setPositiveButton("Login", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        final String loginS = login.getText().toString().trim();
                                        lastLoginName = loginS;
                                        final String passwordS = password.getText().toString().trim();
                                        final boolean insecureB = insecure.isChecked();
                                        if (insecureB) {
                                            sp.edit().putString("juick_account_name", loginS).commit();
                                        }

                                        new Thread(new Runnable() {

                                            public void run() {
                                                int status = 0;
                                                Utils.RESTResponse json = Utils.getJSON(act, "http://api.juick.com/users?uname=" + loginS, null);
                                                if (json.getErrorText() != null) {
                                                    Utils.verboseDebugString(activity, "Unknown username!");
                                                } else {
                                                    String canonicalName = loginS;
                                                    try {
                                                        JSONArray idname = new JSONArray(json.getResult());
                                                        if (idname.length() != 1)
                                                            throw new RuntimeException("Unknown username (zero length API response)!");
                                                        canonicalName = (String)((JSONObject) idname.get(0)).get("uname");
                                                        sp.edit().putString("juick_account_name", canonicalName).commit();
                                                        String authStr = canonicalName + ":" + passwordS;
                                                        final String basicAuth = "Basic " + Base64.encodeToString(authStr.getBytes(), Base64.NO_WRAP);
                                                        Utils.verboseDebugString(activity, "Authorization: " + basicAuth+" Canonical name: "+canonicalName);
                                                        URL apiUrl = new URL("http://api.juick.com/post");
                                                        HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
                                                        conn.setConnectTimeout(10000);
                                                        conn.setUseCaches(false);
                                                        conn.setRequestMethod("POST");
                                                        conn.setDoOutput(true);
                                                        conn.setRequestProperty("Authorization", basicAuth);
                                                        conn.connect();
                                                        OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
                                                        wr.write("body=PING");
                                                        wr.close();
                                                        status = conn.getResponseCode();
                                                        conn.disconnect();
                                                    } catch (Exception e) {
                                                        Utils.verboseDebugString(activity, e.toString());
                                                        Log.e("checkingNickPassw", e.toString());
                                                    }
                                                    if (status == 200) {
                                                        Account account = new Account(canonicalName, activity.getString(R.string.com_juick));
                                                        AccountManager am = AccountManager.get(activity);
                                                        boolean accountCreated = am.addAccountExplicitly(account, passwordS, null);
                                                        if (!accountCreated) {
                                                            Utils.verboseDebugString(activity, "(warning) android account for juick not created");
                                                        }

                                                        if (insecureB) {
                                                            sp.edit().putString("juick_account_name", canonicalName).putString("juick_account_password", passwordS).commit();
                                                        }
                                                        withCookie.apply(getBasicAuthString(act));

                                                    } else {
                                                        final int finalStatus = status;
                                                        activity.runOnUiThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                Toast.makeText(activity, "auth: HTTP status: " + finalStatus, Toast.LENGTH_LONG);
                                                                authorize(act, forceLoginDialog, false, url, withCookie);
                                                            }
                                                        });
                                                    }
                                                }
                                            }
                                        }).start();
                                    }})
                                .setCancelable(false)
                                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        new Thread() {
                                            @Override
                                            public void run() {
                                                withCookie.apply(REFUSED_AUTH);
                                            }
                                        }.start();
                                    }
                                }).create();
                        dlg.setOnShowListener(new DialogInterface.OnShowListener() {
                            @Override
                            public void onShow(DialogInterface dialog) {
                                if (login.getText().length() == 0)
                                    login.requestFocus();
                                else
                                    password.requestFocus();
                            }
                        });
                        try {
                            dlg.show();
                        } catch (Exception e) {
                            //
                        }
                    } catch (Exception e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                }
            });

        } else {
            withCookie.apply(basicAuthString);
        }
    }

    private boolean authNeeded(String url) {
        if (url.contains("thread?mid=")) return false;
        if (url.contains("popular=1")) return false;
        if (url.contains("media=all")) return false;
        if (url.contains("/messages")) return false;
        if (url.contains("users?uname")) return false;
        return true;
    }

    @Override
    public void authorizeRequest(HttpRequestBase request, String cookie) {
        if (cookie != null && cookie.length() > 0) {
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
    public ReplyCode validateNon200Reply(HttpURLConnection conn, String url, boolean wasForcedAuth) {
        return ReplyCode.FAIL;
    }

    @Override
    public ReplyCode validateNon200Reply(HttpResponse o, String url, boolean wasForcedAuth) {
        if (o.getStatusLine().getStatusCode() == 403) {
            return ReplyCode.FORBIDDEN;
        }
        if (o.getStatusLine().getStatusCode() == 404) {
            if (url.contains("thread?mid=")) {
                if (!wasForcedAuth) {
                    return ReplyCode.FORBIDDEN;
                } else {
                    return ReplyCode.FAIL;
                }
            }
        }
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
        if (getJuickAccountName(context) != null) {
            String authStr = getJuickAccountName(context) + ":" + getPassword(context);
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
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getString("juick_account_password", null);
    }

    @Override
    public void reset(Context context, Handler handler) {
        AccountManager am = AccountManager.get(context);
        Account accs[] = am.getAccountsByType(context.getString(R.string.com_juick));
        for (Account acc : accs) {
            am.removeAccount(acc, null, handler);
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().remove("juick_account_password").remove("juick_account_name").commit();
    }
}
