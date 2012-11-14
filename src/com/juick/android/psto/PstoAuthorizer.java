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
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.*;
import java.util.List;
import java.util.Map;

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
        if (url.indexOf("http://psto.net/login") != -1) return false;
        if (url.indexOf("psto.net/") != -1) return true;
        return false;
    }

    // 0 = not accepting 1 = accepting, required -1 = accepting not requred
    public boolean allowsOptionalAuthorization(String url) {
        if (url.indexOf("http://psto.net/top") != -1) return true;
        if (url.indexOf("/post") != -1) return false;
        if (url.indexOf(".psto.net") != -1) return true;
        return false;
    }

    public static String myCookie;
    public static boolean skipAskPassword;
    @Override
    public void authorize(final Activity activity, boolean forceOptionalAuth, String url, final Utils.Function<Void, String> cont) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        if (myCookie == null) {
            myCookie = sp.getString("psto.web_cookie", null);
        }
        if (myCookie == null && (!allowsOptionalAuthorization(url) || forceOptionalAuth)) {
            if (skipAskPassword && !forceOptionalAuth) {
                cont.apply(null);
            } else {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        final Runnable uiThreadWithMaybeDialog = this;
                        String webLogin = sp.getString("psto.web_login", null);
                        String webPassword = sp.getString("psto.web_password", null);
                        if (webLogin != null && webPassword != null) {
                            tryLoginWithPassword(webLogin, webPassword, new Utils.Function<Void, Utils.RESTResponse>() {
                                @Override
                                public Void apply(Utils.RESTResponse restResponse) {
                                    if (restResponse.getErrorText() != null) {
                                        // invalid password or whatever
                                        sp.edit().remove("psto.web_password").commit();
                                        uiThreadWithMaybeDialog.run();
                                    } else {
                                        // ok
                                        new Thread() {
                                            @Override
                                            public void run() {
                                                cont.apply(myCookie);
                                            }
                                        }.start();
                                    }
                                    return null;
                                }
                            });
                            return;
                        }
                        final View content = activity.getLayoutInflater().inflate(R.layout.web_login, null);
                        final EditText login = (EditText) content.findViewById(R.id.login);
                        final String accountName = JuickComAuthorizer.getJuickAccountName(activity);
                        final EditText password = (EditText) content.findViewById(R.id.password);
                        login.setText(accountName);
                        AlertDialog dlg = new AlertDialog.Builder(activity)
                                .setTitle("PSTO Web login")
                                .setView(content)
                                .setPositiveButton("Login", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        final String loginS = login.getText().toString().trim();
                                        final String passwordS = password.getText().toString().trim();
                                        sp.edit()
                                                .putString("psto.web_login", loginS)
                                                .putString("psto.web_password", passwordS)
                                                .commit();
                                        uiThreadWithMaybeDialog.run();  // try to login with this
                                    }
                                })
                                .setCancelable(false)
                                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        new Thread() {
                                            @Override
                                            public void run() {
                                                skipAskPassword = true;
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
                        dlg.show();
                    }

                    private void tryLoginWithPassword(final String loginS, final String passwordS, final Utils.Function<Void, Utils.RESTResponse> safeCont) {
                        new Thread() {
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
                                                            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
                                                            sp.edit()
                                                                    .putString("psto.web_cookie", myCookie)
                                                                    .putString("psto.web_login", loginS)
                                                                    .putString("psto.web_password", passwordS)
                                                                    .commit();
                                                            safeCont.apply(s);

                                                        }
                                                    });
                                                } else {
                                                    activity.runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            Toast.makeText(activity, s.errorText, Toast.LENGTH_LONG).show();
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
            cont.apply(myCookie);
        }
    }

    /**
     * unsafe
     */
    static void obtainCookieByLoginPassword(final Activity activity, String login, String password, final Utils.Function<Void, Utils.RESTResponse> result) {
        final DefaultHttpClient client = new DefaultHttpClient();
        try {
            URL u = new URL("http://psto.net/login");
            String data = "l="+URLEncoder.encode(login.toString(), "UTF-8")
                    + "&p="+URLEncoder.encode(password.toString(), "UTF-8")
                    + "&r=http://psto.net/recent";
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

            Utils.RESTResponse resp;
            if (conn.getResponseCode() == 302) {
                resp = new Utils.RESTResponse(null,false,"OK");
                Map<String,List<String>> headerFields = conn.getHeaderFields();
                String loginKey = null;
                List<String> strings = headerFields.get("Set-Cookie");
                if (strings.size() == 0) {
                    resp = new Utils.RESTResponse("Site did not set cookie, login failed", false, null);
                } else {
                    String[] sessidAndValue = strings.get(0).split(";")[0].split("=");
                    if (sessidAndValue[0].equals("sessid")) {
                        result.apply(new Utils.RESTResponse(null, false, sessidAndValue[1]));
                    } else {
                        resp = new Utils.RESTResponse("Site returned cookie, but not what I expect", false, null);
                    }
                }
            } else if (conn.getResponseCode() == 200) {
                InputStream inputStream = conn.getInputStream();
                resp = Utils.streamToString(inputStream, null);
                inputStream.close();
                resp = new Utils.RESTResponse("PSTO Auth failed, maybe wrong pass",false,null);
            } else {
                final int responseCode = conn.getResponseCode();
                final String responseMessage = conn.getResponseMessage();
                resp = new Utils.RESTResponse("HTTP error: "+ responseCode +" " + responseMessage,false,null);
            }
            conn.disconnect();


            if (resp.errorText != null) {
                result.apply(resp);
            } else {
                // handled
            }
        } catch (Exception e) {
            result.apply(new Utils.RESTResponse("Other error: " + e.toString(), false, null));
            //
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Override
    public void authorizeRequest(HttpRequestBase request, String cookie) {
        request.addHeader("Cookie","sessid="+cookie);
    }

    @Override
    public void authorizeRequest(Activity activity, HttpURLConnection conn, String cookie, String url) {
        conn.setRequestProperty("Cookie","sessid="+cookie);
        if (postingSomething(url)) {
            final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
            String login = sp.getString("psto.web_login", null);
            if (login != null) {
                conn.setRequestProperty("Origin","http://"+login+".psto.net");
                conn.setRequestProperty("Referer","http://"+login+".psto.net/");
            }
        }
    }

    private boolean postingSomething(String url) {
        return url.indexOf("/post") != -1 || url.indexOf("/comment") != -1;
    }

    @Override
    public String authorizeURL(String url, String cookie) {
        return url;
    }

    @Override
    public ReplyCode validateNon200Reply(HttpURLConnection conn, String url) throws IOException {
        if (postingSomething(url) && conn.getResponseCode() == 302) return ReplyCode.NORMAL;
        if (conn.getResponseCode() == 403) return ReplyCode.FORBIDDEN;
        return ReplyCode.FAIL;
    }

    @Override
    public ReplyCode validateNon200Reply(HttpResponse o, String url) {
        if (o.getStatusLine().getStatusCode() == 403) return ReplyCode.FORBIDDEN;
        return ReplyCode.FAIL;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void clearCookie(final Activity context, final Runnable then) {
        context.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                sp.edit().remove("psto.web_cookie").commit();
                myCookie = null;
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
