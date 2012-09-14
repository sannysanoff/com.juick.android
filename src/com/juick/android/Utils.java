/*
 * Juick
 * Copyright (C) 2008-2012, Ugnich Anton
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.juick.android;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Service;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Toast;
import com.juickadvanced.R;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.BasicManagedEntity;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeader;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author Ugnich Anton
 */
public class Utils {

    //public static final String JA_IP = "192.168.1.77";
    public static final String JA_IP = "79.133.74.9";
    public static final String JA_PORT = "8080";

    public static void verboseDebug(final Activity context, final String s) {
        boolean verboseDebug = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("verboseDebug", false);
        if (!Utils.hasAuth(context))
            verboseDebug = true;
        if (verboseDebug) {
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, s, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    public static void setupWebView(WebView wv, String content) {
        try {
            File file = new File(wv.getContext().getCacheDir(), "temp.html");
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(content);
            fileWriter.close();
            Uri uri = Uri.fromFile(file);
            wv.loadUrl(uri.toString());
        } catch (IOException e) {
            //
        }

    }

    public static interface Function<T, A> {
        T apply(A a);
    }


    public static class ServiceGetter<T extends Service> {

        Context context;
        Class<T> serviceClass;

        public ServiceGetter(Context context, Class<T> serviceClass) {
            this.context = context;
            this.serviceClass = serviceClass;
        }

        public abstract static class Receiver<T extends Service> {
            public void withService(T service) {

            }

            public void withoutService() {

            }
        }

        public static class LocalBinder<T extends Service> extends Binder implements IBinder {

            T service;

            public LocalBinder(T service) {
                this.service = service;
            }


            T getService() {
                return service;
            }
        }

        public void getService(final Receiver<T> receive) {
            ServiceConnection mConnection = new ServiceConnection() {

                @Override
                public void onServiceConnected(ComponentName className,
                                               IBinder ibinder) {
                    // We've bound to LocalService, cast the IBinder and get LocalService instance
                    if (LocalBinder.class.isAssignableFrom(ibinder.getClass())) {
                        LocalBinder<T> binder = (LocalBinder<T>) ibinder;
                        try {
                            receive.withService(binder.getService());
                        } finally {
                            context.unbindService(this);
                        }
                    } else {
                        throw new RuntimeException("getService: bad binder: " + ibinder);
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName arg0) {
                    receive.withoutService();
                    context.unbindService(this);
                }
            };

            Intent intent = new Intent(context, serviceClass);
            context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        }
    }


    public static void updateTheme(Activity activity) {
        /*
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        if (sp.getString("theme", "0").equals("0")) {
        activity.setTheme(android.R.style.Theme_Light);
        }
         */
    }


    public static void updateThemeHolo(Activity activity) {
        /*
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        if (sp.getString("theme", "0").equals("0")) {
        activity.setTheme(R.style.Theme_Sherlock_Light);
        }
         */
    }

    public static int doHttpGetRequest(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) (new URL(url)).openConnection();
            conn.setUseCaches(false);
            conn.connect();
            int status = conn.getResponseCode();
            conn.disconnect();
            return status;
        } catch (Exception e) {
            Log.e("doHttpGetRequest", e.toString());
        }
        return 0;
    }

    public static boolean hasAuth(Context context) {
        AccountManager am = AccountManager.get(context);
        Account accs[] = am.getAccountsByType(context.getString(R.string.com_juick));
        return accs.length > 0;
    }

    public static String getAuthHash(Context context) {
        String jsonStr = getJSON(context, "http://api.juick.com/auth", null);
        if (jsonStr != null && !jsonStr.equals("")) {
            try {
                JSONObject json = new JSONObject(jsonStr);
                if (json.has("hash")) {
                    return json.getString("hash");
                }
            } catch (JSONException e) {
            }
        }
        return null;
    }

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
                return "Basic " + Base64.encodeToString(authStr.getBytes(), Base64.NO_WRAP);
            }
        }
        return "";
    }

    public static interface Notification {

    }

    public static interface RetryNotification extends Notification {
        public void notifyRetryIsInProgress(int retry);
    }

    public static interface BackupServerNotification extends Notification {
        public void notifyBackupInUse(boolean backup);
    }

    public static interface DownloadProgressNotification extends Notification {
        public void notifyDownloadProgress(int progressBytes);

        public void notifyHttpClientObtained(HttpClient client);
    }

    public static interface DownloadErrorNotification extends Notification {
        public void notifyDownloadError(String error);
    }

    public static String getJSONWithRetries(Context context, String url, Notification notification) {
        String retval = null;
        for (int i = 0; i < 5; i++) {
            retval = getJSON(context, url, notification instanceof DownloadProgressNotification ? (DownloadProgressNotification) notification : null);
            if (retval == null) {
                if (notification instanceof RetryNotification) {
                    ((RetryNotification) notification).notifyRetryIsInProgress(i + 1);
                }
                continue;
            } else {
                break;
            }
        }
        return retval;
    }

    public static String getJSON(Context context, String url, Notification progressNotification) {
        return getJSON(context, url, progressNotification, -1);
    }

    public static String getJSON(Context context, String url, final Notification progressNotification, int timeout) {
        final String[] ret = new String[]{null};
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpGet httpGet = new HttpGet(url);
            if (timeout > 0) {
                client.getParams().setParameter("http.connection.timeout", new Integer(timeout));
                httpGet.getParams().setParameter("http.socket.timeout", new Integer(timeout));
                httpGet.getParams().setParameter("http.protocol.head-body-timeout", new Integer(timeout));
            }
            String basicAuth = getBasicAuthString(context);
            if (basicAuth.length() > 0 && url.indexOf("api.juick.com") != -1) {
                httpGet.addHeader(new BasicHeader("Authorization", basicAuth));
                //conn.setRequestProperty("Authorization", basicAuth);
            }
            client.execute(httpGet, new ResponseHandler<Object>() {
                @Override
                public Object handleResponse(HttpResponse o) throws ClientProtocolException, IOException {
                    if (o.getStatusLine().getStatusCode() == 200) {
                        HttpEntity e = o.getEntity();
                        if (progressNotification instanceof DownloadProgressNotification) {
                            ((DownloadProgressNotification) progressNotification).notifyDownloadProgress(0);
                        }
                        ret[0] = streamToString(e.getContent(), progressNotification);
                    } else {
                        if (progressNotification instanceof DownloadErrorNotification) {
                            ((DownloadErrorNotification) progressNotification).notifyDownloadError("HTTP response code: " + o.getStatusLine().getStatusCode());
                        }
                    }
                    return o;
                }
            });
        } catch (Exception e) {
            if (progressNotification instanceof DownloadErrorNotification) {
                ((DownloadErrorNotification) progressNotification).notifyDownloadError("HTTP connect: " + e.toString());
            }
            Log.e("getJSON", e.toString());
        } finally {
            client.getConnectionManager().shutdown();
        }
        return ret[0];
    }

    public static String postJSON(Context context, String url, String data) {
        String ret = null;
        try {
            URL jsonURL = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) jsonURL.openConnection();

            String basicAuth = getBasicAuthString(context);
            if (basicAuth.length() > 0) {
                conn.setRequestProperty("Authorization", basicAuth);
            }

            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.connect();

            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(data);
            wr.close();

            if (conn.getResponseCode() == 200) {
                ret = streamToString(conn.getInputStream(), null);
            }

            conn.disconnect();
        } catch (Exception e) {
            Log.e("getJSON", e.toString());
        }
        return ret;
    }

    public static String postJSONHome(Context context, String path, String data) {
        String ret = null;
        try {
            URL jsonURL = new URL("http://" + JA_IP + ":" + JA_PORT + path);
            HttpURLConnection conn = (HttpURLConnection) jsonURL.openConnection();

            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.connect();

            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(data);
            wr.close();

            if (conn.getResponseCode() == 200) {
                ret = streamToString(conn.getInputStream(), null);
            }

            conn.disconnect();
        } catch (Exception e) {
            Log.e("getJSONHome", e.toString());
        }
        return ret;
    }


    public static String streamToString(InputStream is, Notification progressNotification) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            long l = System.currentTimeMillis();
            while (true) {
                int c = is.read();
                if (c <= 0) break;
                baos.write(c);
                if (System.currentTimeMillis() - l > 100) {
                    l = System.currentTimeMillis();
                    if (progressNotification instanceof DownloadProgressNotification)
                        ((DownloadProgressNotification) progressNotification).notifyDownloadProgress(baos.size());
                }
            }
            return new String(baos.toByteArray(), "UTF-8");
        } catch (Exception e) {
            if (progressNotification instanceof DownloadErrorNotification)
                ((DownloadErrorNotification) progressNotification).notifyDownloadError(e.toString());
            Log.e("streamReader", e.toString());
        }
        return null;
    }

    public static String getMD5DigestForString(String str) {
        final MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        final byte[] bytes = str.getBytes();
        md.update(bytes, 0, bytes.length);
        byte[] digest = md.digest();
        StringBuffer sb = new StringBuffer();
        Base64.encode(digest, 0, digest.length, sb);
        return sb.toString();
    }


    public static Bitmap downloadImage(String url) {
        try {
            URL imgURL = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) imgURL.openConnection();
            conn.setDoInput(true);
            conn.connect();
            return BitmapFactory.decodeStream(conn.getInputStream());
        } catch (Exception e) {
            Log.e("downloadImage", e.toString());
        }
        return null;
    }

    public static Set<String> string2set(String str) {
        return new HashSet<String>(Arrays.asList(str.split("@")));
    }

    public static String set2string(Set<String> set) {
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (sb.length() != 0)
                sb.append("@");
            sb.append(s);
        }
        return sb.toString();
    }

    public static String joinStrings(String[] results, String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.length; i++) {
            String result = results[i];
            sb.append(results);
            sb.append("\n");
        }
        return sb.toString();
    }

    public static String nvl(String value, String def) {
        if (value == null || value.length() == 0) return def;
        return value;
    }



}
