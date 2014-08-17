package com.juick.android.facebook;

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
import com.juick.android.Utils;
import com.juickadvanced.R;
import com.juickadvanced.RESTResponse;
import com.juickadvanced.protocol.FacebookTransport;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.json.JSONException;

import java.io.IOException;
import java.net.HttpURLConnection;

public class FacebookAuthorizer extends Utils.URLAuth implements FacebookTransport.ReauthCallback {

    public static FacebookAuthorizer instance;
    private SharedPreferences sp;

    public FacebookAuthorizer() {
        instance = this;
        FacebookTransport.reauthCallback = this;
    }

    @Override
    public boolean acceptsURL(String url) {
        if (url.contains("graph.facebook.com/")) return true;
        return false;
    }

    public boolean allowsOptionalAuthorization(String url) {
        return false;
    }

    public static String oauth;
    public static boolean skipAskPassword;
    @Override
    public void authorize(final Context context, boolean forceOptionalAuth, boolean forceAttachCredentials, String url, final Utils.Function<Void, String> cont) {
        maybeLoadCredentials(context);
        if (!(context instanceof Activity)) {
            cont.apply(null);
        } else if (oauth == null && (!allowsOptionalAuthorization(url) || forceOptionalAuth)) {
            if (skipAskPassword && !forceOptionalAuth) {
                cont.apply(null);
            } else {
                ((Activity)context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        final Runnable uiThreadWithMaybeDialog = this;
                        String webLogin = sp.getString("facebook.login", null);
                        String webPassword = sp.getString("facebook.password", null);
                        if (webLogin != null && webPassword != null) {
                            tryLoginWithPassword(webLogin, webPassword, new Utils.Function<Void, RESTResponse>() {
                                @Override
                                public Void apply(RESTResponse restResponse) {
                                    if (restResponse.getErrorText() != null) {
                                        // invalid password or whatever
                                        sp.edit().remove("facebook.password").commit();
                                        uiThreadWithMaybeDialog.run();
                                    } else {
                                        // ok
                                        new Thread() {
                                            @Override
                                            public void run() {
                                                cont.apply(oauth);
                                            }
                                        }.start();
                                    }
                                    return null;
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
                                .setTitle("Facebook login")
                                .setView(content)
                                .setPositiveButton("Login", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        final String loginS = login.getText().toString().trim();
                                        final String passwordS = password.getText().toString().trim();
                                        sp.edit()
                                                .putString("facebook.login", loginS)
                                                .putString("facebook.password", passwordS)
                                                .commit();
                                        uiThreadWithMaybeDialog.run();  // try to login with this
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
                            // not running ;(
                            doCancelButton();
                        }
                    }

                    private void doCancelButton() {
                        new Thread() {
                            @Override
                            public void run() {
                                skipAskPassword = true;
                                cont.apply(null);
                            }
                        }.start();
                    }

                    private void tryLoginWithPassword(final String loginS, final String passwordS, final Utils.Function<Void, RESTResponse> safeCont) {
                        new Thread() {
                            @Override
                            public void run() {
                                obtainCookieByLoginPassword(context, loginS, passwordS,
                                        new Utils.Function<Void, RESTResponse>() {
                                            @Override
                                            public Void apply(final RESTResponse s) {
                                                if (s.result != null) {
                                                    String[] token_csrf_cookie = s.result.split("\\|");
                                                    oauth = token_csrf_cookie[0];
                                                    ((Activity)context).runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                                                            sp.edit()
                                                                    .putString("facebook.oauth", oauth)
                                                                    .putString("facebook.login", loginS)
                                                                    .putString("facebook.password", passwordS)
                                                                    .commit();
                                                            safeCont.apply(s);
                                                        }
                                                    });
                                                } else {
                                                    ((Activity)context).runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            Toast.makeText(context, s.errorText, Toast.LENGTH_LONG).show();
                                                            safeCont.apply(s);
                                                        }
                                                    });
                                                }
                                                return null;
                                            }
                                        });
                            }
                        }.start();
                    }
                });
            }
        } else {
            cont.apply(oauth);
        }
    }

    public void maybeLoadCredentials(Context context) {
        if (sp == null) {
            sp = PreferenceManager.getDefaultSharedPreferences(context);
            applicationContext = context.getApplicationContext();
            if (oauth == null) {
                oauth = sp.getString("facebook.oauth", null);
                if ("".equals(oauth)) oauth = null;
            }
        }
    }


    /**
     * unsafe
     */
    static void obtainCookieByLoginPassword(final Context activity, String login, String password, final Utils.Function<Void, RESTResponse> result) {
        FacebookTransport facebookTransport = new FacebookTransport(new Utils.AndroidHTTPClient(activity));
        try {
            RESTResponse restResponse = facebookTransport.performLogin(login, password);
            if (restResponse.getResult() != null) {
                result.apply(new RESTResponse(null, false, facebookTransport.oauth));
            } else {
                result.apply(restResponse);
            }
        } catch (Exception e) {
            result.apply(new RESTResponse(e.toString(), false, null));
        }

    }

    @Override
    public void authorizeRequest(HttpRequestBase request, String cookie) {
//        request.addHeader("Cookie","user="+cookie);
        request.setHeader("Authorization","OAuth "+oauth);
        request.setHeader("X-FB-Connection-Type","WIFI");
        request.setHeader("User-Agent", FacebookTransport.getUserAgent());
    }

    Context applicationContext;

    @Override
    public void authorizeRequest(Context context, HttpURLConnection conn, String cookie, String url) {
        conn.setRequestProperty("Authorization", "OAuth " + oauth);
        conn.setRequestProperty("X-FB-Connection-Type", "WIFI");
        conn.setRequestProperty("User-Agent", FacebookTransport.getUserAgent());
    }

    private boolean postingSomething(String url) {
        return false;
    }

    @Override
    public String authorizeURL(String url, String cookie) {
        return url;
    }

    @Override
    public ReplyCode validateNon200Reply(HttpURLConnection conn, String url, boolean wasForcedAuth) throws IOException {
        if (postingSomething(url) && conn.getResponseCode() == 302) return ReplyCode.NORMAL;
        if (conn.getResponseCode() == 403) return ReplyCode.FORBIDDEN;
        return ReplyCode.FAIL;
    }

    @Override
    public ReplyCode validateNon200Reply(HttpResponse o, String url, boolean wasForcedAuth) {
        if (o.getStatusLine().getStatusCode() == 403) return ReplyCode.FORBIDDEN;
        return ReplyCode.FAIL;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void clearCookie(final Context context, final Runnable then) {
        if (context instanceof Activity) {
            ((Activity)context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                    sp.edit().remove("facebook.oauth").commit();
                    oauth = null;
                    new Thread() {
                        @Override
                        public void run() {
                            then.run();
                        }
                    }.start();
                }
            });
        }
    }

    @Override
    public void reset(Context context, Handler handler) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().remove("facebook.oauth").remove("facebook.password").commit();
        oauth = null;
    }


    @Override
    public boolean reauthorizeFacebook(FacebookTransport transport, com.juickadvanced.Utils.Notification notifications) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        String webLogin = sp.getString("facebook.login", null);
        String webPassword = sp.getString("facebook.password", null);
        if (webLogin == null || webPassword == null) return false;
        try {
            transport.oauth = null;
            FacebookTransport tempTransport = new FacebookTransport(new Utils.AndroidHTTPClient(applicationContext));
            RESTResponse restResponse = tempTransport.performLogin(webLogin, webPassword);
            if (tempTransport.oauth != null) {
                oauth = tempTransport.oauth;
                sp.edit().putString("facebook.oauth", oauth).commit();
                transport.oauth = oauth;
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }
}
