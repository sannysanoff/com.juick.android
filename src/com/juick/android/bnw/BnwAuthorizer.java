package com.juick.android.bnw;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import com.juickadvanced.RESTResponse;
import com.juick.android.Utils;
import com.juickadvanced.R;
import com.juickadvanced.data.bnw.BnwMessageID;
import org.apache.http.HttpResponse;
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
public class BnwAuthorizer extends Utils.URLAuth {

    String login;
    String password;

    @Override
    public boolean isForBlog(String microblogCode) {
        return microblogCode.equals(BnwMessageID.CODE);
    }

    @Override
    public void maybeLoadCredentials(Context context) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        if (myCookie == null) {
            myCookie = sp.getString("bnw.web_cookie", null);
        }
        login = sp.getString("bnw.web_login", null);
        password = sp.getString("bnw.web_password", null);
    }

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public boolean acceptsURL(String url) {
        if (url.indexOf("http://ipv4.bnw.im/api/passlogin") != -1) return false;
        if (allowsOptionalAuthorization(url)) return true;
        return url.indexOf("//ipv4.bnw.im/") != -1;
    }

    // 0 = not accepting 1 = accepting, required -1 = accepting not requred
    public boolean allowsOptionalAuthorization(String url) {
        if (url.indexOf("http://ipv4.bnw.im/api/show") != -1) return true;
        if (url.indexOf("http://ipv4.bnw.im/api/today") != -1) return true;
        if (url.indexOf("ipv4.bnw.im/api/passlogin") != -1) return true;
        return false;
    }

    public static String myCookie;
    @Override
    public void authorize(final Context context, boolean forceOptionalAuth, boolean forceAttachCredentials, String url, final Utils.Function<Void, String> cont) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        maybeLoadCredentials(context);
        if (!(context instanceof Activity)) {
            cont.apply(null);
        } else if (myCookie == null && !allowsOptionalAuthorization(url)) {
            ((Activity)context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String webLogin = sp.getString("bnw.web_login", null);
                    String webPassword = sp.getString("bnw.web_password", null);
                    final Runnable thiz = this;
                    if (webLogin != null && webPassword != null) {
                        tryLoginWithPassword(webLogin, webPassword, new Runnable() {
                            @Override
                            public void run() {
                                sp.edit().remove("bnw.web_login").remove("bnw.web_password").commit();
                                thiz.run();
                            }
                        });
                        return;
                    }
                    final View content = ((Activity) context).getLayoutInflater().inflate(R.layout.web_login, null);
                    final EditText login = (EditText) content.findViewById(R.id.login);
                    final EditText password = (EditText) content.findViewById(R.id.password);
                    final CheckBox insecure = (CheckBox) content.findViewById(R.id.insecure);
                    insecure.setChecked(true);
                    login.setText(webLogin);
                    AlertDialog dlg = new AlertDialog.Builder(context)
                            .setTitle("BNW Web login")
                            .setView(content)
                            .setPositiveButton("Login", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    new Thread() {
                                        @Override
                                        public void run() {
                                            final String loginS = login.getText().toString().trim();
                                            final String passwordS = password.getText().toString().trim();
                                            tryLoginWithPassword(loginS, passwordS, thiz);
                                        }
                                    }.start();
                                }
                            })
                            .setCancelable(false)
                            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    doCancelButton();
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
                    } catch (WindowManager.BadTokenException _) {
                        // window not running
                        doCancelButton();
                    }
                }

                private void doCancelButton() {
                    new Thread() {
                        @Override
                        public void run() {
                            cont.apply(null);
                        }
                    }.start();
                }

                private void tryLoginWithPassword(final String loginS, final String passwordS, final Runnable thiz) {
                    obtainCookieByLoginPassword(context, loginS, passwordS,
                            new Utils.Function<Void, RESTResponse>() {
                                @Override
                                public Void apply(final RESTResponse s) {
                                    if (s.result != null) {
                                        myCookie = s.result;
                                        cont.apply(s.result);
                                        ((Activity) context).runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                                                sp.edit()
                                                        .putString("bnw.web_cookie", myCookie)
                                                        .putString("bnw.web_login", loginS)
                                                        .putString("bnw.web_password", passwordS)
                                                        .commit();

                                            }
                                        });
                                    } else {
                                        ((Activity) context).runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(context, s.errorText, Toast.LENGTH_LONG).show();
                                                thiz.run();
                                            }
                                        });
                                    }
                                    return null;
                                }
                            });
                }
            });
        } else {
            cont.apply(myCookie);
        }
    }

    static void obtainCookieByLoginPassword(final Context context, String login, String password, final Utils.Function<Void, RESTResponse> result) {
        final DefaultHttpClient client = new DefaultHttpClient();
        try {
            final RESTResponse response = Utils.getJSON(context,
                    "http://ipv4.bnw.im/api/passlogin?user=" + login + "&password="
                            + URLEncoder.encode(password.toString(), "ISO-8859-1"),
                    null);

            if (response.errorText != null) {
                result.apply(response);
            } else {
                JSONObject json = new JSONObject(response.result);
                if (json.getBoolean("ok")) {
                    String login_key = json.getString("desc");
                    if (login_key != null) {
                        result.apply(new RESTResponse(null, false, login_key));
                    }
                } else {
                    result.apply(new RESTResponse("login not ok", true, null));
                }
            }
        } catch (Exception e) {
            result.apply(new RESTResponse("Other error: " + e.toString(), false, null));
            //
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Override
    public void authorizeRequest(HttpRequestBase request, String cookie) {
    }

    @Override
    public void authorizeRequest(Context context, HttpURLConnection conn, String cookie, String url) {

    }

    @Override
    public String authorizeURL(String url, String cookie) {
        if(cookie == null) return url;
        if (url.indexOf("?") == -1) {
            url += "?";
        } else {
            url += "&";
        }
        url += "login=" + cookie;
        return url;
    }

    @Override
    public ReplyCode validateNon200Reply(HttpURLConnection conn, String url, boolean wasForcedAuth) {
        return ReplyCode.FAIL;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public ReplyCode validateNon200Reply(HttpResponse o, String url, boolean wasForcedAuth) {
        return ReplyCode.FAIL;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void clearCookie(Context context, Runnable then) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void reset(Context context, Handler handler) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().remove("bnw.web_cookie").remove("bnw.web_login").remove("bnw.web_password").commit();
    }
}
