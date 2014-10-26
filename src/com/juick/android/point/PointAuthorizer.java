package com.juick.android.point;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import com.juick.android.DefaultHTTPClientService;
import com.juickadvanced.RESTResponse;
import com.juick.android.Utils;
import com.juickadvanced.R;
import com.juickadvanced.data.point.PointMessageID;
import com.juickadvanced.parsers.URLParser;
import com.juickadvanced.protocol.PointLoginProcedure;
import org.acra.ACRA;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

public class PointAuthorizer extends Utils.URLAuth {
    @Override
    public boolean isNoAuthInResponse(RESTResponse restResponse) {
        if (restResponse.getResult() != null) {
            if (restResponse.getResult().contains("{\"error\": \"PostAuthorError\"}")) return true;
        }
        return false;
    }

    @Override
    public boolean isForBlog(String microblogCode) {
        return microblogCode.equals(PointMessageID.CODE);
    }

    @Override
    public void maybeLoadCredentials(Context context) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        if (token == null) {
            token = sp.getString("point.token", null);
            csrfToken = sp.getString("point.csrf_token", null);
            cookie = sp.getString("point.api_cookie", null);
            if ("".equals(token)) token = null;
        }
    }

    @Override
    public boolean acceptsURL(String url) {
        if (url.indexOf("point.im/") != -1) return true;
        return false;
    }

    public PointAuthorizer() {
    }



    public boolean allowsOptionalAuthorization(String url) {
        if (url.contains("/required")) return false;
        if (url.contains("http://point.im/api/login")) return true;
        return true;
    }

    public static String token;
    public static String csrfToken;
    public static String cookie;
    public static boolean skipAskPassword;
    @Override
    public void authorize(final Context context, boolean forceOptionalAuth, boolean forceAttachCredentials, String url, final Utils.Function<Void, String> cont) {
        if (context == null) {
            ACRA.getErrorReporter().handleException(new RuntimeException("Authorizer, null context"));
            cont.apply(null);
            return;
        }
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        maybeLoadCredentials(context);
        if (!(context instanceof Activity)) {
            cont.apply(null);
        } else if (token == null && (!allowsOptionalAuthorization(url) || forceOptionalAuth)) {
            if (skipAskPassword && !forceOptionalAuth) {
                cont.apply(null);
            } else {
                ((Activity)context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        final Runnable uiThreadWithMaybeDialog = this;
                        String webLogin = sp.getString("point.api_login", null);
                        String webPassword = sp.getString("point.api_password", null);
                        if (webLogin != null && webPassword != null) {
                            tryLoginWithPassword(webLogin, webPassword, new Utils.Function<Void, RESTResponse>() {
                                @Override
                                public Void apply(RESTResponse restResponse) {
                                    if (restResponse.getErrorText() != null) {
                                        // invalid password or whatever
                                        sp.edit().remove("point.api_password").commit();
                                        uiThreadWithMaybeDialog.run();
                                    } else {
                                        // ok
                                        new Thread() {
                                            @Override
                                            public void run() {
                                                cont.apply(token);
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
                                .setTitle("Point.im API login")
                                .setView(content)
                                .setPositiveButton("Login", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        final String loginS = login.getText().toString().trim();
                                        final String passwordS = password.getText().toString().trim();
                                        sp.edit()
                                                .putString("point.api_login", loginS)
                                                .putString("point.api_password", passwordS)
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
                                final RESTResponse s = PointLoginProcedure.obtainCookieByLoginPassword(new DefaultHTTPClientService(context), loginS, passwordS);
                                if (s.result != null) {
                                    String[] token_csrf_cookie = s.result.split("\\|");
                                    token = token_csrf_cookie[0];
                                    csrfToken = token_csrf_cookie[1];
                                    cookie = token_csrf_cookie[2];
                                    ((Activity) context).runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                                            sp.edit()
                                                    .putString("point.token", token)
                                                    .putString("point.csrf_token", csrfToken)
                                                    .putString("point.api_cookie", cookie)
                                                    .putString("point.api_login", loginS)
                                                    .putString("point.api_password", passwordS)
                                                    .commit();
                                            safeCont.apply(s);
                                        }
                                    });
                                } else {
                                    ((Activity) context).runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(context, s.errorText, Toast.LENGTH_LONG).show();
                                            safeCont.apply(s);
                                        }
                                    });
                                }
                            }
                        }.start();
                    }
                });
            }
        } else {
            cont.apply(token);
        }
    }

    /**
     * unsafe
     */
    static void obtainCookieByLoginPassword_OLD(final Context activity, String login, String password, final Utils.Function<Void, RESTResponse> result) {
        final DefaultHttpClient client = new DefaultHttpClient();
        try {
            URL u = new URL("http://point.im/login");
            String data = "login="+URLEncoder.encode(login.toString(), "UTF-8")
                    + "&password="+URLEncoder.encode(password.toString(), "UTF-8")
                    + "&referer=";
            final HttpURLConnection conn = (HttpURLConnection) u.openConnection();

            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setInstanceFollowRedirects(false);
            conn.connect();

            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(data);
            wr.close();

            RESTResponse resp;
            if (conn.getResponseCode() == 302) {
                resp = new RESTResponse(null,false,"OK");
                Map<String,List<String>> headerFields = conn.getHeaderFields();
                List<String> strings = headerFields.get("Set-Cookie");
                if (strings.size() == 0) {
                    resp = new RESTResponse("Site did not set cookie, login failed", false, null);
                } else {
                    String[] userAndValue = strings.get(0).split(";")[0].split("=");
                    if (userAndValue[0].equals("user")) {
                        result.apply(new RESTResponse(null, false, userAndValue[1]));
                    } else {
                        resp = new RESTResponse("Site returned cookie, but not what I expect", false, null);
                    }
                }
            } else if (conn.getResponseCode() == 200) {
                InputStream inputStream = conn.getInputStream();
                resp = Utils.streamToString(inputStream, null);
                inputStream.close();
                resp = new RESTResponse("POINT Auth failed, maybe wrong pass",false,null);
            } else {
                final int responseCode = conn.getResponseCode();
                final String responseMessage = conn.getResponseMessage();
                resp = new RESTResponse("HTTP error: "+ responseCode +" " + responseMessage,false,null);
            }
            conn.disconnect();
            if (resp.errorText != null) {
                result.apply(resp);
            } else {
                // handled
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
//        request.addHeader("Cookie","user="+cookie);
        if (request.getURI().toString().equals("http://point.im/api/login")) {
            // do nothing
        } else {
            if (token != null)
                request.addHeader("Authorization", token);
            if (cookie != null)
                request.addHeader("Cookie", "user=" + cookie);
            if (request instanceof HttpPost) {
                if (csrfToken != null)
                    request.addHeader("X-CSRF", csrfToken);
            }
        }
        request.addHeader("User-Agent","JuickAdvanced");
    }

    @Override
    public void authorizeRequest(Context context, HttpURLConnection conn, String cookie, String url) {
        if (token != null)
            conn.setRequestProperty("Authorization",token);
        String version = getUserAgent(context);
        conn.setRequestProperty("User-Agent", "JuickAdvanced/" + version);
        conn.setRequestProperty("Cookie", "user="+cookie);
        if (postingSomething(url)) {
            conn.setRequestProperty("X-CSRF",csrfToken);
        }
//            final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
//            String login = sp.getString("point.api_login", null);
//            if (login != null) {
//                int ix = url.lastIndexOf("/");
//                String origin = url.substring(0, ix);
//                conn.setRequestProperty("Origin",origin);
//                conn.setRequestProperty("Referer",url);
//            }
//        }
    }

    private String getUserAgent(Context context) {
        String version = "unknown";
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo("com.juickadvanced", 0);
            version = packageInfo.versionName+"/"+packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return version;
    }

    private boolean postingSomething(String url) {
        if (url.contains("/api/post")) return true;
        return false;
    }

    @Override
    public String authorizeURL(String url, String cookie) {
        URLParser parser = new URLParser(url);
        parser.getArgsMap().put("csrf_token", csrfToken);
        return parser.getFullURL();
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
                    sp.edit().remove("point.token").commit();
                    sp.edit().remove("point.csrf_token").commit();
                    sp.edit().remove("point.api_cookie").commit();
                    token = null;
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
        sp.edit().remove("point.token").remove("point.csrf_token").remove("point.api_login").remove("point.api_password").commit();
        csrfToken = null;
        token = null;
    }


    public static String getPointAccountName(Context context) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getString("point.api_login", null);
    }

    public static String getPointAccountPassword(Context context) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getString("point.api_password", null);
    }
}
