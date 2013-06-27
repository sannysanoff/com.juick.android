package com.juick.android.juick;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import com.juick.android.Utils;
import com.juickadvanced.R;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 11/9/12
 * Time: 12:03 AM
 * To change this template use File | Settings | File Templates.
 */
public class JuickComWebAuthorizer extends Utils.URLAuth {
    @Override
    public boolean acceptsURL(String url) {
        return url.indexOf("//juick.com/") != -1;
    }

    @Override
    public void authorize(Context act, boolean forceOptionalAuth, boolean forceAttachCredentials, String url, Utils.Function<Void, String> withCookie) {
        getMyCookie(act, withCookie);
    }

    public static String myCookie;
    private static void getMyCookie(final Context ctx, final Utils.Function<Void,String> cont) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        if (myCookie == null) {
            myCookie = sp.getString("web_cookie", null);
        }
        if (myCookie == null) {
            if (ctx instanceof Activity) {
                final Activity activity = (Activity)ctx;
                final String juickComPassword = JuickComAPIAuthorizer.getPassword(activity);
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String webLogin = sp.getString("web_login",null);
                        String webPassword = sp.getString("web_password",null);
                        final Runnable thiz = this;
                        if (webLogin == null) {
                            webLogin = JuickComAPIAuthorizer.getJuickAccountName(activity);
                        }
                        if (webPassword == null) {
                            webPassword = juickComPassword;
                        }
                        if (webLogin != null && webPassword != null) {
                            tryLoginWithPassword(webLogin, webPassword, new Runnable() {
                                @Override
                                public void run() {
                                    sp.edit().remove("web_login").remove("web_password").commit();
                                    thiz.run();
                                }
                            });
                            return;
                        }
                        final View content = activity.getLayoutInflater().inflate(R.layout.web_login, null);
                        final EditText login = (EditText)content.findViewById(R.id.login);
                        final EditText password = (EditText)content.findViewById(R.id.password);
                        final CheckBox insecure = (CheckBox) content.findViewById(R.id.insecure);
                        insecure.setChecked(true);
                        login.setText(webLogin);
                        AlertDialog dlg = new AlertDialog.Builder(activity)
                                .setTitle("Juick.com Web login")
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
                                        new Thread() {
                                            @Override
                                            public void run() {
                                                cont.apply(null);
                                            }
                                        }.start();
                                    }
                                }).create();
                        dlg.setOnShowListener(new DialogInterface.OnShowListener() {
                            @Override
                            public void onShow(DialogInterface dialog) {
                                password.requestFocus();
                            }
                        });
                        try {
                            dlg.show();
                        } catch (Exception e) {
                            cont.apply(null);
                        }
                    }

                    private void tryLoginWithPassword(final String loginS, final String passwordS, final Runnable thiz) {
                        new Thread("tryLoginWithPassword") {
                            @Override
                            public void run() {
                                obtainCookieByLoginPassword(activity, loginS, passwordS,
                                        new Utils.Function<Void, Utils.RESTResponse>() {
                                            @Override
                                            public Void apply(final Utils.RESTResponse s) {
                                                if (s.result != null) {
                                                    myCookie = s.result;
                                                    activity.runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
                                                            sp.edit()
                                                                    .putString("web_cookie", myCookie)
                                                                    .putString("web_login", loginS)
                                                                    .putString("web_password", passwordS)
                                                                    .commit();
                                                            new Thread() {
                                                                @Override
                                                                public void run() {
                                                                    cont.apply(s.result);
                                                                }
                                                            }.start();
                                                        }
                                                    });
                                                } else {
                                                    activity.runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            Toast.makeText(activity, s.errorText, Toast.LENGTH_LONG).show();
                                                            thiz.run();
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
            } else {
                cont.apply(null);
            }

        } else {
            cont.apply(myCookie);
        }
    }

    static void obtainCookieByLoginPassword(final Activity activity, String login, String password, final Utils.Function<Void, Utils.RESTResponse> result) {
        final DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpPost httpPost = new HttpPost("http://juick.com/login");
            ArrayList<NameValuePair> formData = new ArrayList<NameValuePair>();
            formData.add(new BasicNameValuePair("username", login));
            formData.add(new BasicNameValuePair("password", password));
            httpPost.setEntity(new UrlEncodedFormEntity(formData));
            httpPost.setHeader("Referer", "http://juick.com/login");
            httpPost.setHeader("Accept-Charset", "UTF-8,*;q=0.5");
            httpPost.setHeader("Connection", "keep-alive");
            //httpPost.setHeader("Accept-Encoding", "gzip,deflate,sdch");
            httpPost.setHeader("Accept-Language", "en-US,en;q=0.8");
            httpPost.setHeader("Origin", "http://juick.com");
            httpPost.setHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/536.11 (KHTML, like Gecko) Ubuntu/12.04 Chromium/20.0.1132.47 Chrome/20.0.1132.47 Safari/536.11");
            httpPost.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            client.execute(httpPost, new ResponseHandler<Object>() {
                @Override
                public Object handleResponse(HttpResponse o) throws ClientProtocolException, IOException {
                    HttpEntity entity = o.getEntity();
                    InputStream content = entity.getContent();
                    Utils.RESTResponse responseBody = Utils.streamToString(content, null);
                    if (o.getStatusLine().getStatusCode() == 200) {
                        CookieStore cookieStore = client.getCookieStore();
                        List<Cookie> cookies = cookieStore.getCookies();
                        for (Cookie cookie : cookies) {
                            if (cookie.getName().equals("hash")) {
                                result.apply(new Utils.RESTResponse(null, false, cookie.getValue()));
                                return "";
                            }
                        }
                        result.apply(new Utils.RESTResponse("Result OK, but no cookies", false, null));
                        return "";
                    } else {
                        if (responseBody.result != null && responseBody.result.indexOf("forbidden") != -1) {
                            result.apply(new Utils.RESTResponse(activity.getString(R.string.InvalidLoginPassword), false, null));
                        } else {
                            result.apply(new Utils.RESTResponse("http://juick.com/login: Unknown response: code="+o.getStatusLine().getStatusCode()+" msg="+o.getStatusLine().getReasonPhrase()+" data="+responseBody.result, false, null));
                        }
                        return "";
                    }
                }
            });
        } catch (IOException e) {
            result.apply(new Utils.RESTResponse("Other error: "+e.toString(), false, null));
            //
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Override
    public void authorizeRequest(HttpRequestBase request, String cookie) {
        request.addHeader("Cookie", "hash=" + cookie);
    }

    @Override
    public void authorizeRequest(Context context, HttpURLConnection conn, String cookie, String url) {

    }

    @Override
    public String authorizeURL(String url, String cookie) {
        return url;
    }

    @Override
    public ReplyCode validateNon200Reply(HttpURLConnection conn, String url, boolean wasForcedAuth) {
        return ReplyCode.FAIL;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public ReplyCode validateNon200Reply(HttpResponse o, String url, boolean wasForcedAuth) {
        if (o.getStatusLine().getStatusCode() == 404) {
            if (url.contains("show=my") || url.contains("show=private")) {
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
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().remove("web_cookie").commit();
        then.run();
        myCookie = null;

    }

    @Override
    public void reset(Context context, Handler handler) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().remove("web_cookie").remove("web_login").remove("web_password").commit();
    }
}
