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
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.GZIPInputStream;

import org.apache.http.*;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.BasicManagedEntity;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.EofSensorInputStream;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;
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
        if (!Utils.hasAuth(context.getApplicationContext()))
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
        RESTResponse jsonStr = getJSON(context, "http://api.juick.com/auth", null);
        if (jsonStr.result != null && !jsonStr.result.equals("")) {
            try {
                JSONObject json = new JSONObject(jsonStr.result);
                if (json.has("hash")) {
                    return json.getString("hash");
                }
            } catch (JSONException e) {
            }
        }
        return null;
    }

    static String accountName;
    public static String getAccountName(Context context) {
        if (accountName == null) {
            AccountManager am = AccountManager.get(context);
            Account accs[] = am.getAccountsByType(context.getString(R.string.com_juick));
            if (accs.length > 0) {
                accountName = accs[0].name;
            }
        }
        return accountName;
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
                final String auth = "Basic " + Base64.encodeToString(authStr.getBytes(), Base64.NO_WRAP);
                if (context instanceof Activity) {
                    final Activity act = (Activity)context;
                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                    boolean verboseDebug = sp.getBoolean("verboseDebug", false);
                    if (verboseDebug) {
                        act.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(act, "Auth: "+auth, Toast.LENGTH_LONG).show();
                            }
                        });

                    }
                }
                return auth;
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

    public static RESTResponse getJSONWithRetries(Context context, String url, Notification notification) {
        RESTResponse retval = null;
        for (int i = 0; i < 5; i++) {
            retval = getJSON(context, url, notification instanceof DownloadProgressNotification ? (DownloadProgressNotification) notification : null);
            if (retval.result == null) {
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

    public static RESTResponse getJSON(Context context, String url, Notification progressNotification) {
        return getJSON(context, url, progressNotification, -1);
    }

    static boolean reportTimes = false;

    public static RESTResponse getJSON(Context context, final String url, final Notification progressNotification, int timeout) {
        boolean compression = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("http_compression", false);
        final RESTResponse[] ret = new RESTResponse[]{null};
        DefaultHttpClient client = new DefaultHttpClient();
        long l = System.currentTimeMillis();
        if (compression)
            initCompressionSupport(client);
        try {
            HttpGet httpGet = new HttpGet(url);
            if (timeout > 0) {
                client.getParams().setParameter("http.connection.timeout", new Integer(timeout));
                httpGet.getParams().setParameter("http.socket.timeout", new Integer(timeout));
                httpGet.getParams().setParameter("http.protocol.head-body-timeout", new Integer(timeout));
            }
            String basicAuth = getBasicAuthString(context.getApplicationContext());
            if (basicAuth.length() > 0 && url.startsWith("http://api.juick.com")) {
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
                        InputStream content = e.getContent();
                        ret[0] = streamToString(content, progressNotification);
                        content.close();
                    } else {
                        if (progressNotification instanceof DownloadErrorNotification) {
                            ((DownloadErrorNotification) progressNotification).notifyDownloadError("HTTP response code: " + o.getStatusLine().getStatusCode());
                        }
                        ret[0] = new RESTResponse("HTTP: "+o.getStatusLine().getStatusCode()+" " + o.getStatusLine().getReasonPhrase(), false, null);
                    }
                    return o;
                }
            });
            l  = System.currentTimeMillis() - l;
            if (reportTimes) {
                Toast.makeText(context, "Load time="+l+" msec", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            if (progressNotification instanceof DownloadErrorNotification) {
                ((DownloadErrorNotification) progressNotification).notifyDownloadError("HTTP connect: " + e.toString());
            }
            Log.e("getJSON", e.toString());
            return new RESTResponse(e.toString(), true, null);
        } finally {
            client.getConnectionManager().shutdown();
        }
        return ret[0];
    }

    public static BINResponse getBinary(Context context, final String url, final Notification progressNotification, int timeout) {
        try {
            URLConnection urlConnection = new URL(url).openConnection();
            InputStream inputStream = urlConnection.getInputStream();
            BINResponse binResponse = streamToByteArray(inputStream, progressNotification);
            inputStream.close();
            return binResponse;
        } catch (Exception e) {
            if (progressNotification instanceof DownloadErrorNotification) {
                ((DownloadErrorNotification) progressNotification).notifyDownloadError("HTTP connect: " + e.toString());
            }
            Log.e("getBinary", e.toString());
            return new BINResponse(e.toString(), true, null);
        }
    }

    public static class GzipDecompressingEntity extends HttpEntityWrapper {

        public GzipDecompressingEntity(final HttpEntity entity) {
            super(entity);
        }

        @Override
        public InputStream getContent()
            throws IOException, IllegalStateException {

              // the wrapped entity's getContent() decides about repeatability
            InputStream wrappedin = wrappedEntity.getContent();

            return new GZIPInputStream(wrappedin);
        }

        @Override
        public long getContentLength() {
            // length of ungzipped content not known in advance
            return -1;
        }

    } // class GzipDecompressingEntity

    private static void initCompressionSupport(DefaultHttpClient httpclient) {



        httpclient.addRequestInterceptor(new HttpRequestInterceptor() {
            public void process(
                    final HttpRequest request,
                    final HttpContext context) throws HttpException, IOException {
                if (!request.containsHeader("Accept-Encoding")) {
                    request.addHeader("Accept-Encoding", "gzip");
                }
            }

        });

        httpclient.addResponseInterceptor(new HttpResponseInterceptor() {
            public void process(
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    Header ceheader = entity.getContentEncoding();
                    if (ceheader != null) {
                        HeaderElement[] codecs = ceheader.getElements();
                        for (int i = 0; i < codecs.length; i++) {
                            if (codecs[i].getName().equalsIgnoreCase("gzip")) {
                                response.setEntity(
                                        new GzipDecompressingEntity(response.getEntity()));
                                return;
                            }
                        }
                    }
                }
            }

        });
    }

    public static class RESTResponse {
        String result;
        String errorText;
        boolean mayRetry;

        public RESTResponse(String errorText, boolean mayRetry, String result) {
            this.errorText = errorText;
            this.mayRetry = mayRetry;
            this.result = result;
        }

        public String getResult() {
            return result;
        }

        public boolean isMayRetry() {
            return mayRetry;
        }

        public String getErrorText() {
            return errorText;
        }
    }

    public static class BINResponse {
        byte[] result;
        String errorText;
        boolean mayRetry;

        public BINResponse(String errorText, boolean mayRetry, byte[] result) {
            this.errorText = errorText;
            this.mayRetry = mayRetry;
            this.result = result;
        }

        public byte[] getResult() {
            return result;
        }

        public boolean isMayRetry() {
            return mayRetry;
        }

        public String getErrorText() {
            return errorText;
        }
    }

    public static RESTResponse postJSON(Context context, String url, String data) {
        RESTResponse ret = null;
        try {
            URL jsonURL = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) jsonURL.openConnection();

            String basicAuth = getBasicAuthString(context.getApplicationContext());
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
                InputStream inputStream = conn.getInputStream();
                ret = streamToString(inputStream, null);
                inputStream.close();
            } else {
                return new RESTResponse("HTTP "+conn.getResponseCode()+" " + conn.getResponseMessage(), false, null);
            }

            conn.disconnect();
            return ret;
        } catch (Exception e) {
            Log.e("getJSON", e.toString());
            return new RESTResponse(e.toString(), false, null);
        }
    }

    public static RESTResponse postJSONHome(Context context, String path, String data) {
        RESTResponse ret = null;
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
                InputStream inputStream = conn.getInputStream();
                ret = streamToString(inputStream, null);
                inputStream.close();
            } else {
                return new RESTResponse("HTTP "+conn.getResponseCode()+" " + conn.getResponseMessage(), false, null);
            }

            conn.disconnect();
            return ret;
        } catch (Exception e) {
            Log.e("getJSONHome", e.toString());
            return new RESTResponse(e.toString(), false, null);
        }
    }


    public static RESTResponse streamToString(InputStream is, Notification progressNotification) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            long l = System.currentTimeMillis();
            byte[] buf = new byte[1024];
            while (true) {
                int len = is.read(buf);
                if (len <= 0) break;
                baos.write(buf, 0, len);
                if (System.currentTimeMillis() - l > 100) {
                    l = System.currentTimeMillis();
                    if (progressNotification instanceof DownloadProgressNotification)
                        ((DownloadProgressNotification) progressNotification).notifyDownloadProgress(baos.size());
                }
            }
            return new RESTResponse(null, false, new String(baos.toByteArray(), "UTF-8"));
        } catch (Exception e) {
            if (progressNotification instanceof DownloadErrorNotification)
                ((DownloadErrorNotification) progressNotification).notifyDownloadError(e.toString());
            Log.e("streamReader", e.toString());
            return new RESTResponse(e.toString(), true, null);
        }
    }

    public static BINResponse streamToByteArray(InputStream is, Notification progressNotification) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            long l = System.currentTimeMillis();
            byte[] buf = new byte[1024];
            while (true) {
                int len = is.read(buf);
                if (len <= 0) break;
                baos.write(buf, 0, len);
                if (System.currentTimeMillis() - l > 100) {
                    l = System.currentTimeMillis();
                    if (progressNotification instanceof DownloadProgressNotification)
                        ((DownloadProgressNotification) progressNotification).notifyDownloadProgress(baos.size());
                }
            }
            return new BINResponse(null, false, baos.toByteArray());
        } catch (Exception e) {
            if (progressNotification instanceof DownloadErrorNotification)
                ((DownloadErrorNotification) progressNotification).notifyDownloadError(e.toString());
            Log.e("streamReader", e.toString());
            return new BINResponse(e.toString(), true, null);
        }
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
        List<String> strings = Arrays.asList(str.split("@"));
        HashSet<String> strings1 = new HashSet<String>();
        for (String string : strings) {
            string = string.replace("[SOBAKA]","@");               // kind of escaped
            strings1.add(string);
        }
        return strings1;
    }

    public static String set2string(Set<String> set) {
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (sb.length() != 0)
                sb.append("@");
            sb.append(s.replace("@","[SOBAKA]"));
        }
        return sb.toString();
    }

    public static String nvl(String value, String def) {
        if (value == null || value.length() == 0) return def;
        return value;
    }


}
